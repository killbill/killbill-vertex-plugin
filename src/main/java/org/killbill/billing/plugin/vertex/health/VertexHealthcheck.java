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

import java.util.Map;

import javax.annotation.Nullable;

import org.killbill.billing.osgi.api.Healthcheck;
import org.killbill.billing.plugin.vertex.client.VertexHealthcheckClient;
import org.killbill.billing.plugin.vertex.gen.health.PerformHealthCheckResponseType;
import org.killbill.billing.tenant.api.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertexHealthcheck implements Healthcheck {

    private static final Logger logger = LoggerFactory.getLogger(VertexHealthcheck.class);

    private final HealthCheckApiConfigurationHandler healthCheckApiConfigurationHandler;

    public VertexHealthcheck(final HealthCheckApiConfigurationHandler healthCheckApiConfigurationHandler) {
        this.healthCheckApiConfigurationHandler = healthCheckApiConfigurationHandler;
    }

    @Override
    public HealthStatus getHealthStatus(@Nullable final Tenant tenant, @Nullable final Map properties) {
        if (tenant == null) {
            // The plugin is running
            return HealthStatus.healthy("Vertex OK (unauthenticated)");
        } else {
            // Specifying the tenant lets you also validate the tenant configuration
            final VertexHealthcheckClient vertexHealthcheckClient = healthCheckApiConfigurationHandler.getConfigurable(tenant.getId());

            try {
                PerformHealthCheckResponseType healthCheckResponse = vertexHealthcheckClient.healthCheck();

                boolean healthy = "OK".equals(healthCheckResponse.getCalcEngine());
                return healthy ? HealthStatus.healthy("Vertex CalcEngine status: OK") : HealthStatus.unHealthy("Vertex CalcEngine status: " + healthCheckResponse.getCalcEngine());
            } catch (Exception e) {
                return HealthStatus.unHealthy(e.getMessage());
            }
        }
    }
}
