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

import org.killbill.billing.osgi.api.Healthcheck.HealthStatus;
import org.killbill.billing.plugin.vertex.base.VertexRemoteTestBase;
import org.killbill.billing.plugin.vertex.gen.health.HealthCheckService;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.boilerplate.TenantImp;
import org.testng.Assert;
import org.testng.annotations.Test;

public class VertexHealthCheckTest extends VertexRemoteTestBase {

    @Test
    public void test() {
        HealthCheckApiConfigurationHandler handler = new HealthCheckApiConfigurationHandler(null, null);
        HealthCheckService healthCheckService = handler.createConfigurable(properties);
        handler.setDefaultConfigurable(healthCheckService);
        VertexHealthcheck vertexHealthcheck = new VertexHealthcheck(handler);

        TenantImp.Builder tenantBuilder = new TenantImp.Builder<>();
        Tenant tenant = tenantBuilder.withApiKey(username).withApiSecret(password).build();
        HealthStatus healthStatus = vertexHealthcheck.getHealthStatus(tenant, null);
        Assert.assertEquals(healthStatus.getDetails().get("message"), "Vertex CalcEngine status: OK");
    }

}