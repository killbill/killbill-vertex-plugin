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

import java.util.Properties;

import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.plugin.api.notification.PluginTenantConfigurableConfigurationHandler;
import org.killbill.billing.plugin.vertex.client.TransactionApi;
import org.killbill.billing.plugin.vertex.oauth.OAuthClient;

public class VertexTransactionApiConfigurationHandler extends PluginTenantConfigurableConfigurationHandler<TransactionApi> {

    private final OAuthClient oAuthClient;

    public VertexTransactionApiConfigurationHandler(final String pluginName,
                                                    final OSGIKillbillAPI osgiKillbillAPI, final OAuthClient oAuthClient) {
        super(pluginName, osgiKillbillAPI);
        this.oAuthClient = oAuthClient;
    }

    @Override
    protected TransactionApi createConfigurable(final Properties properties) {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath("https://equinix2.ondemand.vertexinc.com/vertex-ws/");
        String token = oAuthClient.getToken("https://equinix2.ondemand.vertexinc.com/oseries-auth/oauth/token"
                                          , "setClientId", "setClientSecret")
                                  .getAccessToken();
        apiClient.setAccessToken(token);
        return new TransactionApi(apiClient);
    }
}
