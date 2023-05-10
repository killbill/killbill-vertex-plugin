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

package org.killbill.billing.plugin.vertex.health;

import java.util.UUID;

import org.killbill.billing.osgi.api.Healthcheck.HealthStatus;
import org.killbill.billing.plugin.vertex.VertexApiClient;
import org.killbill.billing.plugin.vertex.VertexApiConfigurationHandler;
import org.killbill.billing.plugin.vertex.gen.ApiException;
import org.killbill.billing.plugin.vertex.gen.client.model.AddressLookupRequestType;
import org.killbill.billing.tenant.api.Tenant;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class VertexHealthcheckUnitTest {

    @Mock
    private Tenant tenant;
    @Mock
    private VertexApiConfigurationHandler vertexApiConfigurationHandler;
    @Mock
    private VertexApiClient vertexApiClient;

    @InjectMocks
    private VertexHealthcheck vertexHealthcheck;

    @BeforeClass(groups = "fast")
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        given(tenant.getId()).willReturn(UUID.randomUUID());
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() {
        Mockito.reset(vertexApiConfigurationHandler);
        Mockito.reset(vertexApiClient);
    }

    @Test(groups = "fast")
    public void testHealthCheckFailedOnVertexApiException() throws ApiException {
        //given
        given(vertexApiConfigurationHandler.getConfigurable(tenant.getId())).willReturn(vertexApiClient);
        doThrow(ApiException.class).when(vertexApiClient).lookUpTaxAreaByAddress(any(AddressLookupRequestType.class));

        //when
        HealthStatus status = vertexHealthcheck.getHealthStatus(tenant, null);

        //then
        assertFalse(status.isHealthy());
        assertEquals(status.getDetails().get("message"), "health check failed");
        verify(vertexApiConfigurationHandler).getConfigurable(tenant.getId());
    }

    @Test(groups = "fast")
    public void testHealthCheckFailedWhenVertexApiClientIsNotConfgiured() {
        //given
        given(vertexApiConfigurationHandler.getConfigurable(tenant.getId())).willReturn(null);

        //when
        HealthStatus status = vertexHealthcheck.getHealthStatus(tenant, null);

        //then
        assertFalse(status.isHealthy());
        assertEquals(status.getDetails().get("message"), VertexApiClient.NOT_CONFIGURED_MSG);
        verify(vertexApiConfigurationHandler).getConfigurable(tenant.getId());
    }

    @Test(groups = "fast")
    public void testHealthCheckHealthy() throws ApiException {
        //given
        given(vertexApiConfigurationHandler.getConfigurable(any(UUID.class))).willReturn(vertexApiClient);

        //when
        HealthStatus status = vertexHealthcheck.getHealthStatus(tenant, null);

        //then
        assertTrue(status.isHealthy());
        verify(vertexApiClient).lookUpTaxAreaByAddress(any(AddressLookupRequestType.class));
        verify(vertexApiConfigurationHandler).getConfigurable(any(UUID.class));
    }

    @Test(groups = "fast")
    public void testHealthStatusIsHealthyWhenTenantIsNull() {
        //when
        HealthStatus status = vertexHealthcheck.getHealthStatus(null, null);

        //then
        assertTrue(status.isHealthy());
        assertEquals(status.getDetails().get("message"), "Vertex OK (unauthenticated)");
    }
}
