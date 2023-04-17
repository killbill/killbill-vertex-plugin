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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.api.invoice.PluginTaxCalculator;
import org.killbill.billing.plugin.vertex.dao.VertexDao;
import org.killbill.billing.plugin.vertex.gen.ApiException;
import org.killbill.billing.plugin.vertex.gen.client.CalculateTaxApi;
import org.killbill.billing.plugin.vertex.gen.client.model.*;
import org.killbill.billing.plugin.vertex.gen.dao.model.tables.records.VertexResponsesRecord;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_COMPANY_DIVISION_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_COMPANY_NAME_PROPERTY;

public class VertexTaxCalculator extends PluginTaxCalculator {

    public static final String TAX_CODE = "taxCode";
    public static final String CUSTOMER_USAGE_TYPE = "customerUsageType";
    public static final String LOCATION_ADDRESS1 = "locationAddress1";
    public static final String LOCATION_ADDRESS2 = "locationAddress2";
    public static final String LOCATION_CITY = "locationCity";
    public static final String LOCATION_REGION = "locationRegion";
    public static final String LOCATION_POSTAL_CODE = "locationPostalCode";
    public static final String LOCATION_COUNTRY = "locationCountry";

    private static final Logger logger = LoggerFactory.getLogger(VertexTaxCalculator.class);

    private final VertexCalculateTaxApiConfigurationHandler vertexCalculateTaxApiConfigurationHandler;
    private final VertexDao dao;
    private final Clock clock;

    public VertexTaxCalculator(final VertexCalculateTaxApiConfigurationHandler vertexCalculateTaxApiConfigurationHandler,
                               final VertexDao dao,
                               final Clock clock,
                               final OSGIKillbillAPI osgiKillbillAPI) {
        super(osgiKillbillAPI);
        this.vertexCalculateTaxApiConfigurationHandler = vertexCalculateTaxApiConfigurationHandler;
        this.clock = clock;
        this.dao = dao;
    }


    public List<InvoiceItem> compute(final Account account,
                                     final Invoice newInvoice,
                                     final boolean dryRun,
                                     final Iterable<PluginProperty> pluginProperties,
                                     final TenantContext tenantContext) throws Exception {
        // Retrieve what we've already taxed (Tax Rates API) or sent (AvaTax)
        final List<VertexResponsesRecord> responses = dao.getSuccessfulResponses(newInvoice.getId(), tenantContext.getTenantId());
        final Map<UUID, Set<UUID>> alreadyTaxedItemsWithAdjustments = dao.getTaxedItemsWithAdjustments(responses);

        // For AvaTax, we can only send one type of document at a time (Sales or Return). In some cases, we need to send both, for example
        // in the case of repairs (adjustment for the original item, tax for the new item -- all generated items would be on the new invoice)
        final List<NewItemToTax> newItemsToTax = computeTaxItems(newInvoice, alreadyTaxedItemsWithAdjustments, tenantContext);
        final Map<UUID, InvoiceItem> salesTaxItems = new HashMap<>();
        for (final NewItemToTax newItemToTax : newItemsToTax) {
            if (!newItemToTax.isReturnOnly()) {
                salesTaxItems.put(newItemToTax.getTaxableItem().getId(), newItemToTax.getTaxableItem());
            }
        }

        final ImmutableList.Builder<InvoiceItem> newInvoiceItemsBuilder = ImmutableList.builder();
        if (!salesTaxItems.isEmpty()) {
            newInvoiceItemsBuilder.addAll(getTax(account,
                    newInvoice,
                    newInvoice,
                    salesTaxItems,
                    null,
                    null,
                    dryRun,
                    pluginProperties,
                    tenantContext.getTenantId()));
        }

        // Handle returns by original invoice (1 return call for each original invoice)
        final Multimap<UUID, NewItemToTax> itemsToReturnByInvoiceId = HashMultimap.create();
        for (final NewItemToTax newItemToTax : newItemsToTax) {
            if (newItemToTax.getAdjustmentItems() == null) {
                continue;
            }
            itemsToReturnByInvoiceId.put(newItemToTax.getInvoice().getId(), newItemToTax);
        }
        for (final UUID invoiceId : itemsToReturnByInvoiceId.keySet()) {
            final Collection<NewItemToTax> itemsToReturn = itemsToReturnByInvoiceId.get(invoiceId);

            final Invoice invoice = itemsToReturn.iterator().next().getInvoice();
            final Map<UUID, InvoiceItem> taxableItemsToReturn = new HashMap<>();
            final Map<UUID, List<InvoiceItem>> adjustmentItems = new HashMap<>();
            for (final NewItemToTax itemToReturn : itemsToReturn) {
                taxableItemsToReturn.put(itemToReturn.getTaxableItem().getId(), itemToReturn.getTaxableItem());
                adjustmentItems.put(itemToReturn.getTaxableItem().getId(), itemToReturn.getAdjustmentItems());
            }

            final List<VertexResponsesRecord> responsesForInvoice = dao.getSuccessfulResponses(invoice.getId(), tenantContext.getTenantId());
            final String originalInvoiceReferenceCode = responsesForInvoice.isEmpty() ? null : responsesForInvoice.get(0).getKbInvoiceId();

            newInvoiceItemsBuilder.addAll(getTax(account,
                    newInvoice,
                    invoice,
                    taxableItemsToReturn,
                    adjustmentItems,
                    originalInvoiceReferenceCode,
                    dryRun,
                    pluginProperties,
                    tenantContext.getTenantId()));
        }
        return newInvoiceItemsBuilder.build();
    }

    private Iterable<InvoiceItem> getTax(final Account account,
                                         final Invoice newInvoice,
                                         final Invoice invoice,
                                         final Map<UUID, InvoiceItem> taxableItems,
                                         @Nullable final Map<UUID, List<InvoiceItem>> adjustmentItems,
                                         @Nullable final String originalInvoiceReferenceCode,
                                         final boolean dryRun,
                                         final Iterable<PluginProperty> pluginProperties,
                                         final UUID kbTenantId) throws Exception {
        // Keep track of the invoice items and adjustments we've already taxed (Tax Rates API) or sent (AvaTax)
        final Map<UUID, Iterable<InvoiceItem>> kbInvoiceItems = new HashMap<>();
        if (adjustmentItems != null) {
            kbInvoiceItems.putAll(adjustmentItems);
        }
        for (final InvoiceItem taxableItem : taxableItems.values()) {
            if (kbInvoiceItems.get(taxableItem.getId()) == null) {
                kbInvoiceItems.put(taxableItem.getId(), ImmutableList.of());
            }
        }
        // Don't use clock.getUTCToday(), see https://github.com/killbill/killbill-platform/issues/4
        final LocalDate taxItemsDate = newInvoice.getInvoiceDate();

        return buildInvoiceItems(account,
                newInvoice,
                invoice,
                taxableItems,
                adjustmentItems,
                originalInvoiceReferenceCode,
                dryRun,
                pluginProperties,
                kbTenantId,
                kbInvoiceItems,
                taxItemsDate);
    }

    private Collection<InvoiceItem> buildInvoiceItems(final Account account,
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

        final SaleRequestType taxRequest = toTaxRequest(account,
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

    private SaleRequestType toTaxRequest(final Account account,
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

        if (dryRun) {
            taxRequest.setSaleMessageType(SaleMessageTypeEnum.QUOTATION);
        } else {
            taxRequest.setSaleMessageType(SaleMessageTypeEnum.INVOICE);
        }

        taxRequest.setTransactionType(SaleTransactionTypeEnum.SALE);

        // We overload this field to keep a mapping with the Kill Bill invoice
        taxRequest.setTransactionId(invoice.getId().toString());
        taxRequest.setDocumentNumber(invoice.getInvoiceNumber().toString());
        taxRequest.setDocumentDate(java.time.LocalDate.of(invoice.getInvoiceDate().getYear(), invoice.getInvoiceDate().getMonthOfYear(), invoice.getInvoiceDate().getDayOfMonth()));
        taxRequest.setPostingDate(java.time.LocalDate.of(utcToday.getYear(), utcToday.getMonthOfYear(), utcToday.getDayOfMonth()));//fixme is this ok?

        CurrencyType currencyType = new CurrencyType();
        currencyType.setIsoCurrencyCodeAlpha(invoice.getCurrency().name());
        taxRequest.setCurrency(currencyType);

        CustomerType customerType = new CustomerType();
        LocationType customerDestination = toAddress(account, pluginProperties);
        customerType.setDestination(customerDestination);//todo set it here or per line item
        CustomerCodeType code = new CustomerCodeType();
        code.setValue(MoreObjects.firstNonNull(account.getExternalKey(), account.getId()).toString());//fixme is this ok?
        customerType.setCustomerCode(code);
        taxRequest.setCustomer(customerType);
        SellerType sellerType = new SellerType();

        sellerType.setCompany(PluginProperties.findPluginPropertyValue(VERTEX_OSERIES_COMPANY_NAME_PROPERTY, pluginProperties));
        sellerType.setDivision(PluginProperties.findPluginPropertyValue(VERTEX_OSERIES_COMPANY_DIVISION_PROPERTY, pluginProperties));
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
        // lineItemModel.setTaxDate();fixme do we need this and which field take

        // SKU
        Product product = new Product();
        product.setProductClass(PluginProperties.findPluginPropertyValue(String.format("%s_%s", TAX_CODE, taxableItem.getId()), pluginProperties));
        lineItemModel.setProduct(product);

        // Compute the amount to tax or the amount to adjust
        final BigDecimal adjustmentAmount = sum(adjustmentItems);
        final boolean isReturnDocument = adjustmentAmount.compareTo(BigDecimal.ZERO) < 0;
        Preconditions.checkState((adjustmentAmount.compareTo(BigDecimal.ZERO) == 0) ||
                        (isReturnDocument && taxableItem.getAmount().compareTo(adjustmentAmount.negate()) >= 0),
                "Invalid adjustmentAmount %s for invoice item %s", adjustmentAmount, taxableItem);
        lineItemModel.setExtendedPrice(isReturnDocument ? adjustmentAmount.doubleValue() : taxableItem.getAmount().doubleValue());

        FlexibleFields flexibleFields = new FlexibleFields();
        FlexibleCodeField field20 = new FlexibleCodeField();
        field20.setFieldId(20);

        if (taxableItem.getUsageName() == null) {
            if (taxableItem.getPhaseName() == null) {
                if (taxableItem.getPlanName() == null) {
                    field20.setValue(taxableItem.getDescription());
                } else {
                    field20.setValue(taxableItem.getPlanName());
                }
            } else {
                field20.setValue(taxableItem.getPhaseName());
            }
        } else {
            field20.setValue(taxableItem.getUsageName());
        }

        flexibleFields.addFlexibleCodeFieldsItem(field20);
        lineItemModel.setFlexibleFields(flexibleFields);

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
