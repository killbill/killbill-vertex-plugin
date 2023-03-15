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

import java.util.List;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.api.invoice.PluginInvoicePluginApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.clock.Clock;

import com.google.common.collect.ImmutableList;

import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_SKIP;

public class VertexInvoicePluginApi extends PluginInvoicePluginApi {

    private final VertexTaxCalculator calculator;

    public VertexInvoicePluginApi(final VertexCalculateTaxApiConfigurationHandler vertexCalculateTaxApiConfigurationHandler,
                                  final OSGIKillbillAPI killbillApi,
                                  final OSGIConfigPropertiesService configProperties,
                                  final Clock clock) {
        super(killbillApi, configProperties, clock);
        this.calculator = new VertexTaxCalculator(vertexCalculateTaxApiConfigurationHandler, clock, killbillApi);
    }

    @Override
    public List<InvoiceItem> getAdditionalInvoiceItems(final Invoice invoice,
                                                       final boolean dryRun,
                                                       final Iterable<PluginProperty> properties,
                                                       final CallContext context) {
        if (PluginProperties.findPluginPropertyValue(VERTEX_SKIP, properties) != null) {
            return ImmutableList.of();
        }

        final Account account = getAccount(invoice.getAccountId(), context);

        try {
            return calculator.compute(account, invoice, dryRun, properties, context);
        } catch (final Exception e) {
            // Prevent invoice generation
            throw new RuntimeException(e);
        }
    }
}