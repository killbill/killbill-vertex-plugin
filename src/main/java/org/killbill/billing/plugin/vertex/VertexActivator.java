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

import java.util.Hashtable;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.osgi.api.Healthcheck;
import org.killbill.billing.osgi.api.OSGIPluginProperties;
import org.killbill.billing.osgi.libs.killbill.KillbillActivatorBase;
import org.killbill.billing.plugin.api.notification.PluginConfigurationEventHandler;
import org.killbill.billing.plugin.core.resources.jooby.PluginApp;
import org.killbill.billing.plugin.core.resources.jooby.PluginAppBuilder;
import org.killbill.billing.plugin.vertex.dao.VertexDao;
import org.killbill.billing.plugin.vertex.gen.client.TransactionApi;
import org.killbill.billing.plugin.vertex.gen.health.HealthCheckService;
import org.killbill.billing.plugin.vertex.health.HealthCheckApiConfigurationHandler;
import org.killbill.billing.plugin.vertex.health.VertexHealthcheck;
import org.killbill.billing.plugin.vertex.health.VertexHealthcheckServlet;
import org.killbill.billing.plugin.vertex.oauth.OAuthClient;
import org.osgi.framework.BundleContext;

public class VertexActivator extends KillbillActivatorBase {

    public static final String PLUGIN_NAME = "killbill-vertex";

    private VertexTransactionApiConfigurationHandler vertexTransactionApiConfigurationHandler;
    private VertexCalculateTaxApiConfigurationHandler vertexCalculateTaxApiConfigurationHandler;
    private HealthCheckApiConfigurationHandler healthCheckApiConfigurationHandler;

    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);

        final VertexDao dao = new VertexDao(dataSource.getDataSource());

        OAuthClient oAuthClient = new OAuthClient();
        vertexTransactionApiConfigurationHandler = new VertexTransactionApiConfigurationHandler(PLUGIN_NAME, killbillAPI, oAuthClient);
        vertexCalculateTaxApiConfigurationHandler = new VertexCalculateTaxApiConfigurationHandler(PLUGIN_NAME, killbillAPI);
        healthCheckApiConfigurationHandler = new HealthCheckApiConfigurationHandler(PLUGIN_NAME, killbillAPI);

        final TransactionApi vertexTransactionApiClient = vertexTransactionApiConfigurationHandler.createConfigurable(configProperties.getProperties());
        vertexTransactionApiConfigurationHandler.setDefaultConfigurable(vertexTransactionApiClient);

        final VertexApiClient vertexCalculateTaxApiClient = vertexCalculateTaxApiConfigurationHandler.createConfigurable(configProperties.getProperties());
        vertexCalculateTaxApiConfigurationHandler.setDefaultConfigurable(vertexCalculateTaxApiClient);

        final HealthCheckService healthCheckService = healthCheckApiConfigurationHandler.createConfigurable(configProperties.getProperties());
        healthCheckApiConfigurationHandler.setDefaultConfigurable(healthCheckService);

        // Expose the healthcheck, so other plugins can check on the Vertex status
        final VertexHealthcheck vertexHealthcheck = new VertexHealthcheck(healthCheckApiConfigurationHandler);
        registerHealthcheck(context, vertexHealthcheck);

        // Register the invoice plugin
        final VertexInvoicePluginApi pluginApi = new VertexInvoicePluginApi(vertexCalculateTaxApiConfigurationHandler,
                                                                            vertexTransactionApiConfigurationHandler,
                                                                            killbillAPI,
                                                                            configProperties,
                                                                            dao,
                                                                            clock.getClock());
        registerInvoicePluginApi(context, pluginApi);

        // Register the servlet
        final PluginApp pluginApp = new PluginAppBuilder(PLUGIN_NAME,
                                                         killbillAPI,
                                                         dataSource,
                                                         super.clock,
                                                         configProperties).withRouteClass(VertexHealthcheckServlet.class)
                                                                          .withService(vertexHealthcheck)
                                                                          .withService(dao)
                                                                          .build();

        final HttpServlet invoiceServlet = PluginApp.createServlet(pluginApp);
        registerServlet(context, invoiceServlet);

        registerHandlers();
    }

    public void registerHandlers() {
        final PluginConfigurationEventHandler handler = new PluginConfigurationEventHandler(vertexTransactionApiConfigurationHandler,
                                                                                            vertexCalculateTaxApiConfigurationHandler);
        dispatcher.registerEventHandlers(handler);
    }

    private void registerServlet(final BundleContext context, final HttpServlet servlet) {
        final Hashtable<String, String> props = new Hashtable<>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, Servlet.class, servlet, props);
    }

    private void registerInvoicePluginApi(final BundleContext context, final InvoicePluginApi api) {
        final Hashtable<String, String> props = new Hashtable<>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, InvoicePluginApi.class, api, props);
    }

    private void registerHealthcheck(final BundleContext context, final Healthcheck healthcheck) {
        final Hashtable<String, String> props = new Hashtable<>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, Healthcheck.class, healthcheck, props);
    }
}
