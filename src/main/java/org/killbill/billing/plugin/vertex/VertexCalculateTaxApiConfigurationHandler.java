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

import org.jooq.tools.StringUtils;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.plugin.api.notification.PluginTenantConfigurableConfigurationHandler;
import org.killbill.billing.plugin.vertex.client.NotConfiguredVertexApiClient;
import org.killbill.billing.plugin.vertex.client.VertexApiClient;
import org.killbill.billing.plugin.vertex.client.VertexApiClientImpl;

import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_CLIENT_ID_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_CLIENT_SECRET_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_URL_PROPERTY;

public class VertexCalculateTaxApiConfigurationHandler extends PluginTenantConfigurableConfigurationHandler<VertexApiClient> {

    public VertexCalculateTaxApiConfigurationHandler(final String pluginName, final OSGIKillbillAPI osgiKillbillAPI) {
        super(pluginName, osgiKillbillAPI);
    }

    @Override
    protected VertexApiClient createConfigurable(final Properties properties) {
        final String url = properties.getProperty(VERTEX_OSERIES_URL_PROPERTY);
        final String clientId = properties.getProperty(VERTEX_OSERIES_CLIENT_ID_PROPERTY);
        final String clientSecret = properties.getProperty(VERTEX_OSERIES_CLIENT_SECRET_PROPERTY);

        if (StringUtils.isBlank(url) || StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
            return new NotConfiguredVertexApiClient();
        }

        return new VertexApiClientImpl(properties);
    }
}
