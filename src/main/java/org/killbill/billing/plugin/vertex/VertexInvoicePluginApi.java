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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.api.invoice.PluginInvoicePluginApi;
import org.killbill.billing.plugin.vertex.dao.VertexDao;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.clock.Clock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_SKIP;

public class VertexInvoicePluginApi extends PluginInvoicePluginApi {

    private final VertexTaxCalculator calculator;

    public VertexInvoicePluginApi(final VertexCalculateTaxApiConfigurationHandler vertexCalculateTaxApiConfigurationHandler,
                                  final OSGIKillbillAPI killbillApi,
                                  final OSGIConfigPropertiesService configProperties,
                                  final VertexDao dao, final Clock clock) {
        super(killbillApi, configProperties, clock);
        this.calculator = new VertexTaxCalculator(vertexCalculateTaxApiConfigurationHandler, dao, clock, killbillApi, configProperties.getProperties());
    }

    @Override
    public List<InvoiceItem> getAdditionalInvoiceItems(final Invoice invoice,
                                                       final boolean dryRun,
                                                       final Iterable<PluginProperty> properties,
                                                       final CallContext context) {
        final Collection<PluginProperty> pluginProperties = Lists.newArrayList(properties);

        if (PluginProperties.findPluginPropertyValue(VERTEX_SKIP, pluginProperties) != null) {
            return ImmutableList.of();
        }

        final Account account = getAccount(invoice.getAccountId(), context);

        checkForTaxCodes(invoice, pluginProperties, context);

        try {
            return calculator.compute(account, invoice, dryRun, pluginProperties, context);
        } catch (final Exception e) {
            // Prevent invoice generation
            throw new RuntimeException(e);
        }
    }

    private void checkForTaxCodes(final Invoice invoice, final Collection<PluginProperty> properties, final TenantContext context) {
        checkForTaxCodesInCustomFields(invoice, properties, context);
        checkForTaxCodesOnProducts(invoice, properties, context);
    }

    private void checkForTaxCodesInCustomFields(final Invoice invoice, final Collection<PluginProperty> properties, final TenantContext context) {
        final List<CustomField> customFields = killbillAPI.getCustomFieldUserApi().getCustomFieldsForAccountType(invoice.getAccountId(), ObjectType.INVOICE_ITEM, context);
        if (customFields.isEmpty()) {
            return;
        }

        final Collection<UUID> invoiceItemIds = new HashSet<>();
        for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
            invoiceItemIds.add(invoiceItem.getId());
        }

        final Iterable<CustomField> taxCodeCustomFieldsForInvoiceItems = customFields
                .stream().filter(customField -> customField != null &&
                                                VertexTaxCalculator.TAX_CODE.equals(customField.getFieldName()) &&
                                                invoiceItemIds.contains(customField.getObjectId())).collect(Collectors.toList());
        for (final CustomField customField : taxCodeCustomFieldsForInvoiceItems) {
            final UUID invoiceItemId = customField.getObjectId();
            final String taxCode = customField.getFieldValue();
            addTaxCodeToInvoiceItem(invoiceItemId, taxCode, properties);
        }
    }

    private void checkForTaxCodesOnProducts(final Invoice invoice, final Collection<PluginProperty> properties, final TenantContext context) {
        final Map<String, String> planToProductCache = new HashMap<>();
        final Map<String, String> productToTaxCodeCache = new HashMap<>();

        for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
            final String planName = invoiceItem.getPlanName();
            if (planName == null) {
                continue;
            }

            if (planToProductCache.get(planName) == null) {
                try {
                    final StaticCatalog catalog = killbillAPI.getCatalogUserApi().getCurrentCatalog(null, context);
                    final Plan plan = catalog.findPlan(planName);
                    planToProductCache.put(planName, plan.getProduct().getName());
                } catch (final CatalogApiException e) {
                    continue;
                }
            }
            final String productName = planToProductCache.get(planName);
            if (productName == null) {
                continue;
            }

           /* if (productToTaxCodeCache.get(productName) == null) {fixme do we need to store taxcodes in DB?
                try {
                    final String taxCode = dao.getTaxCode(productName, context.getTenantId());
                    productToTaxCodeCache.put(productName, taxCode);
                } catch (final SQLException e) {
                    continue;
                }
            }*/

            final String taxCode = productToTaxCodeCache.get(productName);
            if (taxCode != null) {
                addTaxCodeToInvoiceItem(invoiceItem.getId(), productToTaxCodeCache.get(productName), properties);
            }
        }
    }

    private void addTaxCodeToInvoiceItem(final UUID invoiceItemId, final String taxCode, final Collection<PluginProperty> properties) {
        final String pluginPropertyName = String.format("%s_%s", VertexTaxCalculator.TAX_CODE, invoiceItemId);
        // Already in plugin properties?
        if (PluginProperties.findPluginPropertyValue(pluginPropertyName, properties) == null) {
            properties.add(new PluginProperty(pluginPropertyName, taxCode, false));
        }
    }
}