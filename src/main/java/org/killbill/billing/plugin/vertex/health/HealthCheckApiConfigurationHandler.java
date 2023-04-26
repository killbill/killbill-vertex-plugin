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

import org.jooq.tools.StringUtils;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.plugin.api.notification.PluginTenantConfigurableConfigurationHandler;
import org.killbill.billing.plugin.vertex.gen.health.HealthCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_URL_PROPERTY;

public class HealthCheckApiConfigurationHandler extends PluginTenantConfigurableConfigurationHandler<HealthCheckService> {

    static final String NOT_CONFIGURED_MSG = "HealthCheckService is not configured: url is required";
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckApiConfigurationHandler.class);

    public HealthCheckApiConfigurationHandler(final String pluginName, final OSGIKillbillAPI osgiKillbillAPI) {
        super(pluginName, osgiKillbillAPI);
    }

    @Override
    public HealthCheckService createConfigurable(final Properties properties) {
        try {
            String url = properties.getProperty(VERTEX_OSERIES_URL_PROPERTY);

            if (StringUtils.isBlank(url)) {
                logger.warn(NOT_CONFIGURED_MSG);
                return null;
            }
            return new HealthCheckService(new URL(url + "/vertex-ws/adminservices/HealthCheck90"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
