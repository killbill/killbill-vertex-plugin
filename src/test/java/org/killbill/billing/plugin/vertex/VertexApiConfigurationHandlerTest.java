/*
 * Copyright 2020-2023 Equinix, Inc
 * Copyright 2020-2023 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.plugin.vertex;

import java.util.Properties;

import org.testng.annotations.Test;

import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_COMPANY_DIVISION_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_COMPANY_NAME_PROPERTY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class VertexApiConfigurationHandlerTest {

    private final VertexApiConfigurationHandler vertexApiConfigurationHandler =
            new VertexApiConfigurationHandler("pluginName", null);

    @Test
    public void testCreateConfigurable() {
        //given
        final Properties properties = new Properties();
        properties.put(VERTEX_OSERIES_COMPANY_NAME_PROPERTY, "companyName");
        properties.put(VERTEX_OSERIES_COMPANY_DIVISION_PROPERTY, "companyDivision");

        //when
        VertexApiClient vertexApiClient = vertexApiConfigurationHandler.createConfigurable(properties);

        //then
        assertNotNull(vertexApiClient);
        assertEquals(vertexApiClient.getCompanyName(), properties.getProperty(VERTEX_OSERIES_COMPANY_NAME_PROPERTY));
        assertEquals(vertexApiClient.getCompanyDivision(), properties.getProperty(VERTEX_OSERIES_COMPANY_DIVISION_PROPERTY));
    }
}
