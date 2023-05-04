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

import java.sql.SQLException;
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
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.invoice.plugin.api.OnSuccessInvoiceResult;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.api.invoice.PluginInvoicePluginApi;
import org.killbill.billing.plugin.vertex.dao.VertexDao;
import org.killbill.billing.plugin.vertex.gen.ApiException;
import org.killbill.billing.plugin.vertex.gen.dao.model.tables.records.VertexResponsesRecord;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_SKIP;

public class VertexInvoicePluginApi extends PluginInvoicePluginApi {

    private static final Logger logger = LoggerFactory.getLogger(VertexInvoicePluginApi.class);
    private static final String INVOICE_OPERATION = "INVOICE_OPERATION";

    private final VertexTaxCalculator calculator;
    private final VertexApiConfigurationHandler vertexApiConfigurationHandler;
    private final VertexDao dao;

    public VertexInvoicePluginApi(final VertexApiConfigurationHandler vertexApiConfigurationHandler,
                                  final OSGIKillbillAPI killbillApi,
                                  final OSGIConfigPropertiesService configProperties,
                                  final VertexDao dao, final Clock clock) {
        super(killbillApi, configProperties, clock);
        this.calculator = new VertexTaxCalculator(vertexApiConfigurationHandler, dao, clock, killbillApi);
        this.vertexApiConfigurationHandler = vertexApiConfigurationHandler;
        this.dao = dao;
    }

    @Override
    public List<InvoiceItem> getAdditionalInvoiceItems(final Invoice invoice,
                                                       final boolean dryRun,
                                                       final Iterable<PluginProperty> properties,
                                                       final CallContext context) {
        if (PluginProperties.findPluginPropertyValue(VERTEX_SKIP, properties) != null) {
            return ImmutableList.of();
        }

        final Collection<PluginProperty> pluginProperties = Lists.newArrayList(properties);

        final Account account = getAccount(invoice.getAccountId(), context);

        checkForTaxCodes(invoice, pluginProperties, context);

        try {
            return calculator.compute(account, invoice, dryRun, pluginProperties, context);
        } catch (final Exception e) {
            // Prevent invoice generation
            throw new RuntimeException(e);
        }
    }

    @Override
    public OnSuccessInvoiceResult onSuccessCall(final InvoiceContext context, final Iterable<PluginProperty> properties) {
        final String invoiceOperation = PluginProperties.findPluginPropertyValue(INVOICE_OPERATION, properties);
        if (invoiceOperation == null) {
            return super.onSuccessCall(context, properties);
        }

        final Collection<String> docCodes = new HashSet<>();
        try {
            // Find existing transactions
            final List<VertexResponsesRecord> responses = dao.getSuccessfulResponses(context.getInvoice().getId(), context.getTenantId());
            for (final VertexResponsesRecord response : responses) {
                docCodes.add(response.getDocCode());
            }
        } catch (final SQLException e) {
            logger.warn("Unable to {} transaction in Vertex", invoiceOperation, e);
            // Don't fail the whole operation though
        }

        final VertexApiClient vertexApiClient = vertexApiConfigurationHandler.getConfigurable(context.getTenantId());
        for (final String docCode : docCodes) {
            try {
                if ("void".equals(invoiceOperation)) {
                    vertexApiClient.deleteTransaction(docCode);
                }
            } catch (final ApiException e) {
                logger.warn("Unable to {} transaction in Vertex", invoiceOperation, e);
                // Don't fail the whole operation though
            }
        }

        return super.onSuccessCall(context, properties);
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
