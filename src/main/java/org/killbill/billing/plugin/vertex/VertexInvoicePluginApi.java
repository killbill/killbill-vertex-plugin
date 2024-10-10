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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.invoice.plugin.api.OnSuccessInvoiceResult;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.api.invoice.PluginInvoiceItem;
import org.killbill.billing.plugin.api.invoice.PluginInvoiceItem.Builder;
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class VertexInvoicePluginApi extends PluginInvoicePluginApi {

    @VisibleForTesting
    static final String INVOICE_OPERATION = "INVOICE_OPERATION";
    private static final Logger logger = LoggerFactory.getLogger(VertexInvoicePluginApi.class);

    private final VertexTaxCalculator calculator;
    private final VertexApiConfigurationHandler vertexApiConfigurationHandler;
    private final VertexDao dao;
    private static final BigDecimal FX_RATE =  new BigDecimal("0.85");

    public VertexInvoicePluginApi(final VertexApiConfigurationHandler vertexApiConfigurationHandler,
                                  final OSGIKillbillAPI killbillApi,
                                  final OSGIConfigPropertiesService configProperties,
                                  final VertexTaxCalculator vertexTaxCalculator,
                                  final VertexDao dao, final Clock clock) {
        super(killbillApi, configProperties, clock);
        this.calculator = vertexTaxCalculator;
        this.vertexApiConfigurationHandler = vertexApiConfigurationHandler;
        this.dao = dao;
    }

    @Override
    public List<InvoiceItem> getAdditionalInvoiceItems(final Invoice invoice,
                                                       final boolean dryRun,
                                                       final Iterable<PluginProperty> properties,
                                                       final CallContext context) {
        if (PluginProperties.findPluginPropertyValue("VERTEX_SKIP", properties) != null) {
            return ImmutableList.of();
        }

        logger.info("Input invoice status " + invoice.getStatus());
        invoice.getInvoiceItems().forEach(item ->
                                          {
                                              logger.info(item.getInvoiceItemType() + " "
                                                         + item.getAmount() + " "
                                                         + item.getItemDetails());
                                          });

        final Collection<PluginProperty> pluginProperties = Lists.newArrayList(properties);

        final Account account = getAccount(invoice.getAccountId(), context);

        checkForTaxCodes(invoice, pluginProperties, context);

        try {
            List<InvoiceItem> items =  calculator.compute(account, invoice, dryRun, pluginProperties, context);
            items.forEach(item ->
                                              {
                                                  logger.info(item.getInvoiceItemType() + " "
                                                              + item.getAmount() + " "
                                                              + item.getItemDetails());
                                              });
            BigDecimal rawConvertedTotal = getConvertedRawTotal(invoice.getInvoiceItems()).add(getConvertedRawTotal(items));
            BigDecimal convertedRoundedTotal = getConvertedRoundedTotal(invoice.getInvoiceItems()).add(getConvertedRoundedTotal(items));

            BigDecimal delta = convertedRoundedTotal.subtract(rawConvertedTotal).divide(FX_RATE, 2, RoundingMode.HALF_UP);
            logger.info("ROUNDING ADJUSTMENT rawConvertedTotal - {}, convertedRoundedTotal - {}, delta - {}", rawConvertedTotal, convertedRoundedTotal, delta);
            final ImmutableList.Builder<InvoiceItem> newInvoiceItemsBuilder = ImmutableList.builder();
            newInvoiceItemsBuilder.addAll(items).add(createRoundingInvoiceItem(invoice, InvoiceItemType.CREDIT_ADJ, delta));
            return newInvoiceItemsBuilder.build();
        } catch (final Exception e) {
            // Prevent invoice generation
            throw new RuntimeException(e);
        }
    }

    private BigDecimal getConvertedRoundedTotal(List<InvoiceItem> items) {
        BigDecimal sum = new BigDecimal("0.0");
        for (InvoiceItem item : items) {
            sum = sum.add(item.getAmount().multiply(FX_RATE)).setScale(2, RoundingMode.HALF_UP);
        }
        return sum;
    }

    private BigDecimal getConvertedRawTotal(List<InvoiceItem> items) {
        BigDecimal sum = new BigDecimal("0.0");
        for (InvoiceItem item : items) {
            sum = sum.add(item.getAmount().multiply(FX_RATE));
        }
        return sum;
    }

    private InvoiceItem createRoundingInvoiceItem(final Invoice invoice, InvoiceItemType type, BigDecimal amount) {
        return new PluginInvoiceItem(new Builder<>()
                                             .withId(UUID.randomUUID())
                                             .withInvoiceItemType(type)
                                             .withInvoiceId(invoice.getId())
                                             .withAccountId(invoice.getAccountId())
                                             .withStartDate(new LocalDate())
                                             .withEndDate(new LocalDate())
                                             .withAmount(amount)
                                             .withCurrency(Currency.USD)
                                             .withDescription("POC TEST " + invoice.getStatus().toString())
                                             .validate().build());
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

    private void addTaxCodeToInvoiceItem(final UUID invoiceItemId, final String taxCode, final Collection<PluginProperty> properties) {
        final String pluginPropertyName = String.format("%s_%s", VertexTaxCalculator.TAX_CODE, invoiceItemId);
        // Already in plugin properties?
        if (PluginProperties.findPluginPropertyValue(pluginPropertyName, properties) == null) {
            properties.add(new PluginProperty(pluginPropertyName, taxCode, false));
        }
    }
}
