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
import java.sql.SQLException;
import java.util.ArrayList;
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
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.vertex.dao.VertexDao;
import org.killbill.billing.plugin.vertex.gen.ApiException;
import org.killbill.billing.plugin.vertex.gen.client.CalculateTaxApi;
import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessResponseTransactionResponseType;
import org.killbill.billing.plugin.vertex.gen.client.model.CurrencyType;
import org.killbill.billing.plugin.vertex.gen.client.model.CustomerCodeType;
import org.killbill.billing.plugin.vertex.gen.client.model.CustomerType;
import org.killbill.billing.plugin.vertex.gen.client.model.LocationType;
import org.killbill.billing.plugin.vertex.gen.client.model.MeasureType;
import org.killbill.billing.plugin.vertex.gen.client.model.OwnerResponseLineItemType;
import org.killbill.billing.plugin.vertex.gen.client.model.Product;
import org.killbill.billing.plugin.vertex.gen.client.model.SaleMessageTypeEnum;
import org.killbill.billing.plugin.vertex.gen.client.model.SaleRequestLineItemType;
import org.killbill.billing.plugin.vertex.gen.client.model.SaleRequestType;
import org.killbill.billing.plugin.vertex.gen.client.model.SaleTransactionTypeEnum;
import org.killbill.billing.plugin.vertex.gen.client.model.SellerType;
import org.killbill.billing.plugin.vertex.gen.client.model.TaxesType;
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
                               final VertexDao dao,
                               final Clock clock,
                               final OSGIKillbillAPI osgiKillbillAPI) {
        super(dao, clock, osgiKillbillAPI);
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
                                                        final LocalDate utcToday) throws ApiException, SQLException {
        final CalculateTaxApi calculateTaxApi = vertexCalculateTaxApiConfigurationHandler.getConfigurable(kbTenantId);
        final String companyCode = "how to get this"; //fixme calculateTaxApi.getCompanyCode();

        final SaleRequestType taxRequest = toTaxRequest(companyCode,
                                                        account,
                                                        invoice,
                                                        taxableItems.values(),
                                                        adjustmentItems,
                                                        originalInvoiceReferenceCode,
                                                        dryRun,
                                                        pluginProperties,
                                                        utcToday);
        logger.info("CreateTransaction req: {}", taxRequest);

        try {
            final ApiSuccessResponseTransactionResponseType taxResult = calculateTaxApi.salePost(taxRequest);
            logger.info("CreateTransaction res: {}", taxResult);
            if (!dryRun) {
                dao.addResponse(account.getId(), newInvoice.getId(), kbInvoiceItems, taxResult, clock.getUTCNow(), kbTenantId);
            }

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
                dao.addResponse(account.getId(), invoice.getId(), kbInvoiceItems, e.getResponseBody(), clock.getUTCNow(), kbTenantId);
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
                                         final Iterable<PluginProperty> pluginProperties,
                                         final LocalDate utcToday) {
        Preconditions.checkState((originalInvoiceReferenceCode == null && (adjustmentItems == null || adjustmentItems.isEmpty())) ||
                                 (originalInvoiceReferenceCode != null && (adjustmentItems != null && !adjustmentItems.isEmpty())),
                                 "Invalid combination of originalInvoiceReferenceCode %s and adjustments %s", originalInvoiceReferenceCode, adjustmentItems);

        Preconditions.checkState((adjustmentItems == null || adjustmentItems.isEmpty()) || adjustmentItems.size() == taxableItems.size(),
                                 "Invalid number of adjustments %s for taxable items %s", adjustmentItems, taxableItems);

        final SaleRequestType taxRequest = new SaleRequestType();
        taxRequest.setTransactionType(SaleTransactionTypeEnum.SALE);
        //taxRequest.type = originalInvoiceReferenceCode == null ? DocType.SalesInvoice : DocType.ReturnInvoice;fixme support return invoice
        // We overload this field to keep a mapping with the Kill Bill invoice
        taxRequest.setTransactionId(invoice.getId().toString());
        taxRequest.setDocumentNumber(invoice.getInvoiceNumber().toString());
        taxRequest.setDocumentDate(java.time.LocalDate.of(invoice.getInvoiceDate().getYear(), invoice.getInvoiceDate().getMonthOfYear(), invoice.getInvoiceDate().getDayOfMonth()));
        taxRequest.setPostingDate(java.time.LocalDate.of(utcToday.getYear(), utcToday.getMonthOfYear(), utcToday.getDayOfMonth()));

        CurrencyType currencyType = new CurrencyType();
        currencyType.setIsoCurrencyCodeAlpha(invoice.getCurrency().name());
        taxRequest.setCurrency(currencyType);

        if (dryRun) {
            taxRequest.setSaleMessageType(SaleMessageTypeEnum.QUOTATION);
        } else {
            taxRequest.setSaleMessageType(SaleMessageTypeEnum.INVOICE);
        }
        CustomerType customerType = new CustomerType();
        LocationType customerDestination = toAddress(account, pluginProperties);
        customerType.setDestination(customerDestination);//todo set it here or per line item
        CustomerCodeType code = new CustomerCodeType();
        code.setValue(MoreObjects.firstNonNull(account.getExternalKey(), account.getId()).toString());
        customerType.setCustomerCode(code);
        taxRequest.setCustomer(customerType);
        SellerType sellerType = new SellerType();
        sellerType.setCompany(PluginProperties.getValue(PROPERTY_COMPANY_CODE, companyCode, pluginProperties));//todo from where take this value
        sellerType.setDivision(PluginProperties.findPluginPropertyValue(CUSTOMER_USAGE_TYPE, pluginProperties));//todo from where take this value
        taxRequest.setSeller(sellerType);
        List<SaleRequestLineItemType> lineItemList = new ArrayList<>();

        long lineNumber = 1;
        for (InvoiceItem invoiceItem : taxableItems) {
            lineItemList.add(toLine(invoiceItem,
                                    adjustmentItems == null ? null : adjustmentItems.get(invoiceItem.getId()),
                                    invoice.getInvoiceDate(),
                                    pluginProperties, lineNumber));
            lineNumber++;
        }

        taxRequest.setLineItems(lineItemList);

        return taxRequest;
    }

    private SaleRequestLineItemType toLine(final InvoiceItem taxableItem,
                                           @Nullable final Iterable<InvoiceItem> adjustmentItems,
                                           @Nullable final LocalDate originalInvoiceDate,
                                           final Iterable<PluginProperty> pluginProperties,
                                           long lineNumber) {
        final SaleRequestLineItemType lineItemModel = new SaleRequestLineItemType();
        lineItemModel.setLineItemId(taxableItem.getId().toString());
        lineItemModel.setLineItemNumber(lineNumber);

        // SKU
        Product product = new Product();
        if (taxableItem.getUsageName() == null) {
            if (taxableItem.getPhaseName() == null) {
                if (taxableItem.getPlanName() == null) {
                    product.setValue(taxableItem.getDescription());
                } else {
                    product.setValue(taxableItem.getPlanName());
                }
            } else {
                product.setValue(taxableItem.getPhaseName());
            }
        } else {
            product.setValue(taxableItem.getUsageName());
        }
        lineItemModel.setProduct(product);
        MeasureType quantity = new MeasureType();
        quantity.setValue(new Double(MoreObjects.firstNonNull(taxableItem.getQuantity(), 1).toString()));
        lineItemModel.setQuantity(quantity);

        // Compute the amount to tax or the amount to adjust
        final BigDecimal adjustmentAmount = sum(adjustmentItems);
        final boolean isReturnDocument = adjustmentAmount.compareTo(BigDecimal.ZERO) < 0;
        Preconditions.checkState((adjustmentAmount.compareTo(BigDecimal.ZERO) == 0) ||
                                 (isReturnDocument && taxableItem.getAmount().compareTo(adjustmentAmount.negate()) >= 0),
                                 "Invalid adjustmentAmount %s for invoice item %s", adjustmentAmount, taxableItem);
        lineItemModel.setExtendedPrice(isReturnDocument ? adjustmentAmount.doubleValue() : taxableItem.getAmount().doubleValue());//fixme is this ExtendedPrice

        //lineItemModel.description = taxableItem.getDescription();fixme FlexibleCodeField?
        //lineItemModel.ref1 = taxableItem.getId().toString();fixme FlexibleCodeField?
        //lineItemModel.ref2 = taxableItem.getInvoiceId().toString();fixme FlexibleCodeField?

        return lineItemModel;
    }

    private LocationType toAddress(final Account account, final Iterable<PluginProperty> pluginProperties) {
        final LocationType addressLocationInfo = new LocationType();

        final String line1 = PluginProperties.findPluginPropertyValue(LOCATION_ADDRESS1, pluginProperties);
        if (line1 != null) {
            addressLocationInfo.setStreetAddress1(line1);
            addressLocationInfo.setStreetAddress2(PluginProperties.findPluginPropertyValue(LOCATION_ADDRESS2, pluginProperties));
            addressLocationInfo.setCity(PluginProperties.findPluginPropertyValue(LOCATION_CITY, pluginProperties));
            addressLocationInfo.setMainDivision(PluginProperties.findPluginPropertyValue(LOCATION_REGION, pluginProperties));
            addressLocationInfo.setPostalCode(PluginProperties.findPluginPropertyValue(LOCATION_POSTAL_CODE, pluginProperties));
            addressLocationInfo.setCountry(PluginProperties.findPluginPropertyValue(LOCATION_COUNTRY, pluginProperties));
        } else {
            addressLocationInfo.setStreetAddress1(account.getAddress1());
            addressLocationInfo.setStreetAddress2(account.getAddress2());
            addressLocationInfo.setCity(account.getCity());
            addressLocationInfo.setMainDivision(account.getStateOrProvince());
            addressLocationInfo.setPostalCode(account.getPostalCode());
            addressLocationInfo.setCountry(account.getCountry());
        }

        return addressLocationInfo;
    }
}