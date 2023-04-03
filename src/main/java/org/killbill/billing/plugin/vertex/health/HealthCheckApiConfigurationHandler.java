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

import java.net.URL;
import java.util.Properties;

import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.plugin.api.notification.PluginTenantConfigurableConfigurationHandler;
import org.killbill.billing.plugin.vertex.gen.health.HealthCheckService;

import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_URL_PROPERTY;

public class HealthCheckApiConfigurationHandler extends PluginTenantConfigurableConfigurationHandler<HealthCheckService> {

    public HealthCheckApiConfigurationHandler(final String pluginName,
                                              final OSGIKillbillAPI osgiKillbillAPI) {
        super(pluginName, osgiKillbillAPI);
    }

    @Override
    public HealthCheckService createConfigurable(final Properties properties) {
        try {
            String url = properties.getProperty(VERTEX_OSERIES_URL_PROPERTY);

            return new HealthCheckService(new URL(url + "/vertex-ws/adminservices/HealthCheck90"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
