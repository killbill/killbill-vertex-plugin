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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.vertex.client.CalculateTaxApi;
import org.killbill.billing.plugin.vertex.client.model.ApiSuccessResponseTransactionResponseType;
import org.killbill.billing.plugin.vertex.client.model.OwnerResponseLineItemType;
import org.killbill.billing.plugin.vertex.client.model.SaleMessageTypeEnum;
import org.killbill.billing.plugin.vertex.client.model.SaleRequestType;
import org.killbill.billing.plugin.vertex.client.model.SaleTransactionTypeEnum;
import org.killbill.billing.plugin.vertex.client.model.TaxesType;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class VertexTaxCalculator extends VertexCalculatorBase {

    public static final String PROPERTY_COMPANY_CODE = "companyCode";
    public static final String CUSTOMER_USAGE_TYPE = "customerUsageType";
    public static final String TAX_CODE = "taxCode";
    public static final String LOCATION_CODE = "locationCode";
    public static final String LOCATION_ADDRESS1 = "locationAddress1";
    public static final String LOCATION_ADDRESS2 = "locationAddress2";
    public static final String LOCATION_CITY = "locationCity";
    public static final String LOCATION_REGION = "locationRegion";
    public static final String LOCATION_POSTAL_CODE = "locationPostalCode";
    public static final String LOCATION_COUNTRY = "locationCountry";

    private static final Logger logger = LoggerFactory.getLogger(VertexTaxCalculator.class);

    private final VertexCalculateTaxApiConfigurationHandler vertexCalculateTaxApiConfigurationHandler;

    public VertexTaxCalculator(final VertexCalculateTaxApiConfigurationHandler vertexCalculateTaxApiConfigurationHandler,
                               final Clock clock,
                               final OSGIKillbillAPI osgiKillbillAPI) {
        super(clock, osgiKillbillAPI);
        this.vertexCalculateTaxApiConfigurationHandler = vertexCalculateTaxApiConfigurationHandler;
    }

    @Override
    protected Collection<InvoiceItem> buildInvoiceItems(final Account account,
                                                        final Invoice newInvoice,
                                                        final Invoice invoice,
                                                        final Map<UUID, InvoiceItem> taxableItems,
                                                        @Nullable final Map<UUID, List<InvoiceItem>> adjustmentItems,
                                                        @Nullable final String originalInvoiceReferenceCode,
                                                        final boolean dryRun,
                                                        final Iterable<PluginProperty> pluginProperties,
                                                        final UUID kbTenantId,
                                                        final Map<UUID, Iterable<InvoiceItem>> kbInvoiceItems,
                                                        final LocalDate utcToday) throws ApiException {
        final CalculateTaxApi calculateTaxApi = vertexCalculateTaxApiConfigurationHandler.getConfigurable(kbTenantId);
        final String companyCode = "how to get this"; //fixme calculateTaxApi.getCompanyCode();
        final boolean shouldCommitDocuments = true; //fixme calculateTaxApi.shouldCommitDocuments();

        final SaleRequestType taxRequest = toTaxRequest(companyCode,
                                                        account,
                                                        invoice,
                                                        taxableItems.values(),
                                                        adjustmentItems,
                                                        originalInvoiceReferenceCode,
                                                        dryRun,
                                                        shouldCommitDocuments,
                                                        pluginProperties,
                                                        utcToday);
        logger.info("CreateTransaction req: {}", taxRequest);

        try {
            final ApiSuccessResponseTransactionResponseType taxResult = calculateTaxApi.salePost(taxRequest);
            logger.info("CreateTransaction res: {}", taxResult);
          /*  if (!dryRun) { fixme
                dao.addResponse(account.getId(), newInvoice.getId(), kbInvoiceItems, taxResult, clock.getUTCNow(), kbTenantId);
            }*/

            if (taxResult.getData() == null || taxResult.getData().getLineItems() == null ||
                taxResult.getData().getLineItems().isEmpty()) {
                logger.info("Nothing to tax for taxable items: {}", kbInvoiceItems.keySet());
                return ImmutableList.of();
            }

            final Collection<InvoiceItem> invoiceItems = new LinkedList<>();
            for (final OwnerResponseLineItemType ownerResponseLineItem : taxResult.getData().getLineItems()) {
                // See convention in toLine() below
                final UUID invoiceItemId = UUID.fromString(ownerResponseLineItem.getLineItemId());
                final InvoiceItem adjustmentItem;
                if (adjustmentItems != null &&
                    adjustmentItems.get(invoiceItemId) != null &&
                    adjustmentItems.get(invoiceItemId).size() == 1) {
                    // Could be a repair or an item adjustment: in either case, we use it to compute the service period
                    adjustmentItem = adjustmentItems.get(invoiceItemId).get(0);
                } else {
                    // No repair or multiple adjustments: use the original service period
                    adjustmentItem = null;
                }
                invoiceItems.addAll(toInvoiceItems(newInvoice.getId(), taxableItems.get(invoiceItemId), ownerResponseLineItem, adjustmentItem));
            }

            return invoiceItems;
        } catch (final ApiException e) {
            if (e.getResponseBody() != null) {
                //fixme  dao.addResponse(account.getId(), invoice.getId(), kbInvoiceItems, e.getErrors(), clock.getUTCNow(), kbTenantId);
                logger.warn("CreateTransaction res: {}", e.getResponseBody());
            }
            throw e;
        }
    }

    private Collection<InvoiceItem> toInvoiceItems(final UUID invoiceId,
                                                   final InvoiceItem taxableItem,
                                                   final OwnerResponseLineItemType transactionLineModel,
                                                   @Nullable final InvoiceItem adjustmentItem) {
        if (transactionLineModel.getTaxes() == null || transactionLineModel.getTaxes().isEmpty()) {
            final InvoiceItem taxItem = buildTaxItem(taxableItem, invoiceId, adjustmentItem, BigDecimal.valueOf(transactionLineModel.getTotalTax()), "Tax");
            if (taxItem == null) {
                return ImmutableList.of();
            } else {
                return ImmutableList.of(taxItem);
            }
        } else {
            final Collection<InvoiceItem> invoiceItems = new LinkedList<>();
            for (final TaxesType transactionLineDetailModel : transactionLineModel.getTaxes()) {
                final String description = MoreObjects.firstNonNull(transactionLineDetailModel.getTaxCode(), MoreObjects.firstNonNull(transactionLineDetailModel.getVertexTaxCode(), "Tax"));
                final InvoiceItem taxItem = buildTaxItem(taxableItem, invoiceId, adjustmentItem, BigDecimal.valueOf(transactionLineDetailModel.getCalculatedTax()), description);
                if (taxItem != null) {
                    invoiceItems.add(taxItem);
                }
            }
            return invoiceItems;
        }
    }

    private SaleRequestType toTaxRequest(final String companyCode,
                                         final Account account,
                                         final Invoice invoice,
                                         final Collection<InvoiceItem> taxableItems,
                                         @Nullable final Map<UUID, List<InvoiceItem>> adjustmentItems,
                                         @Nullable final String originalInvoiceReferenceCode,
                                         final boolean dryRun,
                                         final boolean shouldCommitDocuments,
                                         final Iterable<PluginProperty> pluginProperties,
                                         final LocalDate utcToday) {
        Preconditions.checkState((originalInvoiceReferenceCode == null && (adjustmentItems == null || adjustmentItems.isEmpty())) ||
                                 (originalInvoiceReferenceCode != null && (adjustmentItems != null && !adjustmentItems.isEmpty())),
                                 "Invalid combination of originalInvoiceReferenceCode %s and adjustments %s", originalInvoiceReferenceCode, adjustmentItems);

        Preconditions.checkState((adjustmentItems == null || adjustmentItems.isEmpty()) || adjustmentItems.size() == taxableItems.size(),
                                 "Invalid number of adjustments %s for taxable items %s", adjustmentItems, taxableItems);

        final SaleRequestType taxRequest = new SaleRequestType();
        taxRequest.setTransactionType(SaleTransactionTypeEnum.SALE);

        if (dryRun) {
            taxRequest.setSaleMessageType(SaleMessageTypeEnum.QUOTATION);
        } else {
            taxRequest.setSaleMessageType(SaleMessageTypeEnum.INVOICE);
        }

        //todo add mapping

        return taxRequest;
    }

}
