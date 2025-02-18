/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.scheduling;

import static java.lang.String.format;
import static org.hisp.dhis.scheduling.JobProgress.getMessage;

import java.time.Duration;
import java.util.Deque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

import org.hisp.dhis.message.MessageService;

/**
 * The {@link ControlledJobProgress} take care of the flow control aspect of
 * {@link JobProgress} API. Additional tracking can be done by wrapping another
 * {@link JobProgress} as {@link #tracker}.
 *
 * The implementation does allow for parallel items but would merge parallel
 * stages or processes. Stages and processes should always be sequential in a
 * main thread.
 *
 * @author Jan Bernitt
 */
@Slf4j
public class ControlledJobProgress implements JobProgress
{
    private final MessageService messageService;

    private final JobConfiguration configuration;

    private final JobProgress tracker;

    private final boolean abortOnFailure;

    private final AtomicBoolean cancellationRequested = new AtomicBoolean();

    private final AtomicBoolean abortAfterFailure = new AtomicBoolean();

    private final Deque<Process> processes = new ConcurrentLinkedDeque<>();

    private final AtomicReference<Process> incompleteProcess = new AtomicReference<>();

    private final AtomicReference<Stage> incompleteStage = new AtomicReference<>();

    private final ThreadLocal<Item> incompleteItem = new ThreadLocal<>();

    private final boolean usingErrorNotification;

    public ControlledJobProgress( JobConfiguration configuration )
    {
        this( null, configuration, NoopJobProgress.INSTANCE, true );
    }

    public ControlledJobProgress( MessageService messageService, JobConfiguration configuration,
        JobProgress tracker, boolean abortOnFailure )
    {
        this.messageService = messageService;
        this.configuration = configuration;
        this.tracker = tracker;
        this.abortOnFailure = abortOnFailure;
        this.usingErrorNotification = messageService != null && configuration.getJobType().isUsingErrorNotification();
    }

    public void requestCancellation()
    {
        if ( cancellationRequested.compareAndSet( false, true ) )
        {
            processes.forEach( p -> {
                p.cancel();
                logWarn( p, "cancelled", "cancellation requested by user" );
            } );
        }
    }

    public Deque<Process> getProcesses()
    {
        return processes;
    }

    @Override
    public boolean isCancellationRequested()
    {
        return cancellationRequested.get();
    }

    @Override
    public void startingProcess( String description )
    {
        if ( isCancellationRequested() )
        {
            throw new CancellationException();
        }
        tracker.startingProcess( description );
        incompleteProcess.set( null );
        incompleteStage.set( null );
        incompleteItem.remove();
        Process process = addProcessRecord( description );
        logInfo( process, "started", description );
    }

    @Override
    public void completedProcess( String summary )
    {
        tracker.completedProcess( summary );
        Process process = getOrAddLastIncompleteProcess();
        process.complete( summary );
        logInfo( process, "completed", summary );
    }

    @Override
    public void failedProcess( String error )
    {
        tracker.failedProcess( error );
        if ( processes.getLast().getStatus() != Status.CANCELLED )
        {
            automaticAbort( false, error, null );
            Process process = getOrAddLastIncompleteProcess();
            process.completeExceptionally( error, null );
            logError( process, null, error );
        }
    }

    @Override
    public void failedProcess( Exception cause )
    {
        cause = cancellationAsAbort( cause );
        tracker.failedProcess( cause );
        if ( processes.getLast().getStatus() != Status.CANCELLED )
        {
            String message = getMessage( cause );
            automaticAbort( false, message, cause );
            Process process = getOrAddLastIncompleteProcess();
            process.completeExceptionally( message, cause );
            sendErrorNotification( process, cause );
            logError( process, cause, message );
        }
    }

    @Override
    public void startingStage( String description, int workItems )
    {
        if ( isCancellationRequested() )
        {
            throw new CancellationException();
        }
        tracker.startingStage( description, workItems );
        Stage stage = addStageRecord( getOrAddLastIncompleteProcess(), description, workItems );
        logInfo( stage, "started", description );
    }

    @Override
    public void completedStage( String summary )
    {
        tracker.completedStage( summary );
        Stage stage = getOrAddLastIncompleteStage();
        stage.complete( summary );
        logInfo( stage, "completed", summary );
    }

    @Override
    public void failedStage( String error )
    {
        tracker.failedStage( error );
        automaticAbort( error, null );
        Stage stage = getOrAddLastIncompleteStage();
        stage.completeExceptionally( error, null );
        logError( stage, null, error );
    }

    @Override
    public void failedStage( Exception cause )
    {
        cause = cancellationAsAbort( cause );
        tracker.failedStage( cause );
        String message = getMessage( cause );
        automaticAbort( message, cause );
        Stage stage = getOrAddLastIncompleteStage();
        stage.completeExceptionally( message, cause );
        sendErrorNotification( stage, cause );
        logError( stage, cause, message );
    }

    @Override
    public void startingWorkItem( String description )
    {
        tracker.startingWorkItem( description );
        Item item = addItemRecord( getOrAddLastIncompleteStage(), description );
        logDebug( item, "started", description );
    }

    @Override
    public void completedWorkItem( String summary )
    {
        tracker.completedWorkItem( summary );
        Item item = getOrAddLastIncompleteItem();
        item.complete( summary );
        logDebug( item, "completed", summary );
    }

    @Override
    public void failedWorkItem( String error )
    {
        tracker.failedWorkItem( error );
        automaticAbort( error, null );
        Item item = getOrAddLastIncompleteItem();
        item.completeExceptionally( error, null );
        logError( item, null, error );
    }

    @Override
    public void failedWorkItem( Exception cause )
    {
        tracker.failedWorkItem( cause );
        String message = getMessage( cause );
        automaticAbort( message, cause );
        Item item = getOrAddLastIncompleteItem();
        item.completeExceptionally( message, cause );
        sendErrorNotification( item, cause );
        logError( item, cause, message );
    }

    private void automaticAbort( String error, Exception cause )
    {
        automaticAbort( true, error, cause );
    }

    private void automaticAbort( boolean abortProcess, String error, Exception cause )
    {
        if ( abortOnFailure
            // OBS! we only mark abort if we could mark cancellation
            // if we already cancelled manually we do not abort but cancel
            && cancellationRequested.compareAndSet( false, true )
            && abortAfterFailure.compareAndSet( false, true )
            && abortProcess )
        {
            processes.forEach( process -> {
                if ( !process.isComplete() )
                {
                    process.completeExceptionally( error, cause );
                    logWarn( process, "aborted", "aborted after error: " + error );
                }
            } );
        }
    }

    private Process getOrAddLastIncompleteProcess()
    {
        Process process = incompleteProcess.get();
        return process != null && !process.isComplete()
            ? process
            : addProcessRecord( null );
    }

    private Process addProcessRecord( String description )
    {
        Process process = new Process( description );
        if ( configuration != null )
        {
            process.setJobId( configuration.getUid() );
        }
        incompleteProcess.set( process );
        processes.add( process );
        return process;
    }

    private Stage getOrAddLastIncompleteStage()
    {
        Stage stage = incompleteStage.get();
        return stage != null && !stage.isComplete()
            ? stage
            : addStageRecord( getOrAddLastIncompleteProcess(), null, -1 );
    }

    private Stage addStageRecord( Process process, String description, int totalItems )
    {
        Deque<Stage> stages = process.getStages();
        Stage stage = new Stage( description, totalItems );
        stages.addLast( stage );
        incompleteStage.set( stage );
        return stage;
    }

    private Item getOrAddLastIncompleteItem()
    {
        Item item = incompleteItem.get();
        return item != null && !item.isComplete()
            ? item
            : addItemRecord( getOrAddLastIncompleteStage(), null );
    }

    private Item addItemRecord( Stage stage, String description )
    {
        Deque<Item> items = stage.getItems();
        Item item = new Item( description );
        items.addLast( item );
        incompleteItem.set( item );
        return item;
    }

    private Exception cancellationAsAbort( Exception cause )
    {
        return cause instanceof CancellationException && abortAfterFailure.get()
            ? new RuntimeException( "processing aborted: " + getMessage( cause ) )
            : cause;
    }

    private void sendErrorNotification( Node node, Exception cause )
    {
        if ( usingErrorNotification )
        {
            String subject = node.getClass().getSimpleName() + " failed: " + node.getDescription();
            try
            {
                messageService.sendSystemErrorNotification( subject, cause );
            }
            catch ( Exception ex )
            {
                log.debug( "Failed to send error notification for failed job processing" );
            }
        }
    }

    private void logError( Node failed, Exception cause, String message )
    {
        if ( log.isErrorEnabled() )
        {
            String msg = formatLogMessage( failed, "failed", message );
            if ( cause != null )
            {
                log.error( msg, cause );
            }
            else
            {
                log.error( msg );
            }
        }
    }

    private void logDebug( Node source, String action, String message )
    {
        if ( log.isDebugEnabled() )
        {
            log.debug( formatLogMessage( source, action, message ) );
        }
    }

    private void logInfo( Node source, String action, String message )
    {
        if ( log.isInfoEnabled() )
        {
            log.info( formatLogMessage( source, action, message ) );
        }
    }

    private void logWarn( Node source, String action, String message )
    {
        if ( log.isWarnEnabled() )
        {
            log.warn( formatLogMessage( source, action, message ) );
        }
    }

    private String formatLogMessage( Node source, String action, String message )
    {
        String duration = source.isComplete()
            ? " after " + Duration.ofMillis( source.getDuration() ).toString().substring( 2 ).toLowerCase()
            : "";
        String msg = message == null ? "" : ": " + message;
        return format( "[%s %s] %s %s%s%s", configuration.getJobType().name(), configuration.getUid(),
            source.getClass().getSimpleName(), action, duration, msg );
    }
}
