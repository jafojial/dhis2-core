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
package org.hisp.dhis.tracker.config;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hisp.dhis.tracker.preheat.supplier.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.common.collect.ImmutableList;

@Configuration( "trackerPreheatConfig" )
public class TrackerPreheatConfig
{
    private final List<Class<? extends PreheatSupplier>> preheatOrder = ImmutableList.of(
        ClassBasedSupplier.class,
        TrackedEntityProgramInstanceSupplier.class,
        ProgramInstanceSupplier.class,
        ProgramInstancesWithAtLeastOneEventSupplier.class,
        ProgramStageInstanceProgramStageMapSupplier.class,
        ProgramOrgUnitsSupplier.class,
        ProgramOwnerSupplier.class,
        PeriodTypeSupplier.class,
        UniqueAttributesSupplier.class,
        UserSupplier.class,
        UsernameValueTypeSupplier.class,
        FileResourceSupplier.class,
        EventCategoryOptionComboSupplier.class );

    @Bean( "preheatOrder" )
    public List<String> getPreheatOrder()
    {
        return preheatOrder.stream().map( Class::getSimpleName )
            .collect( Collectors.toList() );
    }

    @Bean( "preheatStrategies" )
    public Map<String, String> getPreheatStrategies()
    {
        return new PreheatStrategyScanner().scanSupplierStrategies();
    }
}
