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

import java.util.Properties;

import org.killbill.billing.osgi.api.Healthcheck.HealthStatus;
import org.killbill.billing.plugin.vertex.VertexApiClient;
import org.killbill.billing.plugin.vertex.VertexApiConfigurationHandler;
import org.killbill.billing.plugin.vertex.base.VertexRemoteTestBase;
import org.killbill.billing.tenant.api.Tenant;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.killbill.billing.plugin.vertex.VertexActivator.PLUGIN_NAME;

public class VertexHealthCheckTest extends VertexRemoteTestBase {

    @Test(groups = "integration")
    public void getHealthStatus_ReturnsHealthy_WhenNoTenant() {
        VertexApiConfigurationHandler handler = new VertexApiConfigurationHandler(PLUGIN_NAME, null);
        VertexHealthcheck vertexHealthcheck = new VertexHealthcheck(handler);

        HealthStatus healthStatus = vertexHealthcheck.getHealthStatus(null, null);
        Assert.assertTrue(healthStatus.isHealthy());
    }

    @Test(groups = "integration")
    public void getHealthStatus_ReturnsUnHealthy_WhenVertexNotConfigured() {
        VertexApiConfigurationHandler handler = new VertexApiConfigurationHandler(PLUGIN_NAME, null);
        final VertexApiClient vertexApiClient = new VertexApiClient(new Properties());
        handler.setDefaultConfigurable(vertexApiClient);

        VertexHealthcheck vertexHealthcheck = new VertexHealthcheck(handler);
        Tenant tenant = Mockito.mock(Tenant.class);

        HealthStatus healthStatus = vertexHealthcheck.getHealthStatus(tenant, null);
        Assert.assertFalse(healthStatus.isHealthy());
        Assert.assertEquals(healthStatus.getDetails().get("message"), "health check failed");
    }

    @Test(groups = "integration")
    public void getHealthStatus_ReturnsHealthy_WhenVertexConfigured() {
        VertexApiConfigurationHandler handler = new VertexApiConfigurationHandler(PLUGIN_NAME, null);
        handler.setDefaultConfigurable(vertexApiClient);

        VertexHealthcheck vertexHealthcheck = new VertexHealthcheck(handler);
        Tenant tenant = Mockito.mock(Tenant.class);

        HealthStatus healthStatus = vertexHealthcheck.getHealthStatus(tenant, null);
        Assert.assertTrue(healthStatus.isHealthy());
    }

}
