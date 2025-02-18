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
package org.hisp.dhis.webapi.controller.tracker.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.hisp.dhis.tracker.TrackerIdScheme;
import org.hisp.dhis.tracker.TrackerIdSchemeParam;
import org.hisp.dhis.tracker.TrackerIdSchemeParams;
import org.hisp.dhis.tracker.domain.MetadataIdentifier;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class MetadataIdentifierMapperTest
{

    private static final MetadataIdentifierMapper MAPPER = Mappers.getMapper( MetadataIdentifierMapper.class );

    @Test
    void programIdentifierFromUID()
    {

        TrackerIdSchemeParams params = TrackerIdSchemeParams.builder()
            .idScheme( TrackerIdSchemeParam.CODE )
            .build();

        MetadataIdentifier id = MAPPER.from( "RiNIt1yJoge", params );

        assertEquals( TrackerIdScheme.UID, id.getIdScheme() );
        assertEquals( "RiNIt1yJoge", id.getIdentifier() );
    }

    @Test
    void programIdentifierFromAttribute()
    {

        TrackerIdSchemeParams params = TrackerIdSchemeParams.builder()
            .idScheme( TrackerIdSchemeParam.CODE )
            .programIdScheme( TrackerIdSchemeParam.ofAttribute( "RiNIt1yJoge" ) )
            .build();

        MetadataIdentifier id = MAPPER.from( "clouds", params );

        assertEquals( TrackerIdScheme.ATTRIBUTE, id.getIdScheme() );
        assertEquals( "RiNIt1yJoge", id.getIdentifier() );
        assertEquals( "clouds", id.getAttributeValue() );
    }

    @Test
    void programIdentifierFromUIDIfNull()
    {

        TrackerIdSchemeParams params = TrackerIdSchemeParams.builder()
            .idScheme( TrackerIdSchemeParam.CODE )
            .build();

        MetadataIdentifier id = MAPPER.from( null, params );

        assertEquals( TrackerIdScheme.UID, id.getIdScheme() );
        assertNull( id.getIdentifier() );
    }
}