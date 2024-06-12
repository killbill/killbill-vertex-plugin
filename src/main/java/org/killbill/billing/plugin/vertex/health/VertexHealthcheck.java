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
import org.killbill.billing.plugin.vertex.VertexApiClient;
import org.killbill.billing.plugin.vertex.VertexApiConfigurationHandler;
import org.killbill.billing.plugin.vertex.gen.client.model.AddressLookupRequestType;
import org.killbill.billing.plugin.vertex.gen.client.model.PostalAddressType;
import org.killbill.billing.tenant.api.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.killbill.billing.plugin.vertex.VertexApiClient.NOT_CONFIGURED_MSG;

public class VertexHealthcheck implements Healthcheck {

    private static final Logger logger = LoggerFactory.getLogger(VertexHealthcheck.class);

    private final VertexApiConfigurationHandler vertexApiConfigurationHandler;

    public VertexHealthcheck(final VertexApiConfigurationHandler vertexApiConfigurationHandler) {
        this.vertexApiConfigurationHandler = vertexApiConfigurationHandler;
    }

    @Override
    public HealthStatus getHealthStatus(@Nullable final Tenant tenant, @Nullable final Map properties) {
        if (tenant == null) {
            // The plugin is running
            return HealthStatus.healthy("Vertex OK (unauthenticated)");
        } else {
            final VertexApiClient vertexClient = vertexApiConfigurationHandler.getConfigurable(tenant.getId());
            if (vertexClient == null) {
                logger.warn(NOT_CONFIGURED_MSG);
                return HealthStatus.unHealthy(NOT_CONFIGURED_MSG);
            }

            PostalAddressType address = new PostalAddressType();
            address.setCity("Redwood City");
            address.setCountry("USA");
            address.setMainDivision("California");
            address.setPostalCode("94065");

            AddressLookupRequestType addressLookupRequest = new AddressLookupRequestType();
            addressLookupRequest.setPostalAddress(address);
            try {
                vertexClient.lookUpTaxAreaByAddress(addressLookupRequest);
            } catch (Exception e) {
                logger.error("health-check via TaxAreaLookup API failed - " + e.getMessage());
                return HealthStatus.unHealthy("health check failed");
            }
            return HealthStatus.healthy("Vertex OK");
        }
    }
}
