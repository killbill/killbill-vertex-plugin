/*
 * Copyright 2020 Equinix, Inc
 * Copyright 2020 The Billing Project, LLC
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

import org.killbill.billing.osgi.api.Healthcheck;
import org.killbill.billing.tenant.api.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.util.Map;

public class VertexHealthcheck implements Healthcheck {

    private static final Logger logger = LoggerFactory.getLogger(VertexHealthcheck.class);

    private final VertexTransactionApiConfigurationHandler vertexTransactionApiConfigurationHandler;

    public VertexHealthcheck(final VertexTransactionApiConfigurationHandler vertexTransactionApiConfigurationHandler) {
        this.vertexTransactionApiConfigurationHandler = vertexTransactionApiConfigurationHandler;
    }

    @Override
    public HealthStatus getHealthStatus(@Nullable final Tenant tenant, @Nullable final Map properties) {
        if (tenant == null) {
            // The plugin is running
            return HealthStatus.healthy("Vertex OK");
        } else {
            // Specifying the tenant lets you also validate the tenant configuration
            //fixme use health service
            //final VertexClient vertexClient = vertexTransactionApiConfigPropertiesConfigurationHandler.getConfigurable(tenant.getId());
            return HealthStatus.healthy("Vertex OK");
        }
    }
}
