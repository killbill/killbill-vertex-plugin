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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.api.invoice.PluginInvoiceItem;
import org.killbill.billing.plugin.api.invoice.PluginInvoiceItem.Builder;
import org.killbill.billing.plugin.api.invoice.PluginTaxCalculator;
import org.killbill.billing.plugin.vertex.dao.VertexDao;
import org.killbill.billing.plugin.vertex.gen.ApiException;
import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessResponseTransactionResponseType;
import org.killbill.billing.plugin.vertex.gen.client.model.CurrencyType;
import org.killbill.billing.plugin.vertex.gen.client.model.CustomerCodeType;
import org.killbill.billing.plugin.vertex.gen.client.model.CustomerType;
import org.killbill.billing.plugin.vertex.gen.client.model.FlexibleCodeField;
import org.killbill.billing.plugin.vertex.gen.client.model.FlexibleFields;
import org.killbill.billing.plugin.vertex.gen.client.model.Jurisdiction;
import org.killbill.billing.plugin.vertex.gen.client.model.LocationType;
import org.killbill.billing.plugin.vertex.gen.client.model.OwnerResponseLineItemType;
import org.killbill.billing.plugin.vertex.gen.client.model.Product;
import org.killbill.billing.plugin.vertex.gen.client.model.SaleMessageTypeEnum;
import org.killbill.billing.plugin.vertex.gen.client.model.SaleRequestLineItemType;
import org.killbill.billing.plugin.vertex.gen.client.model.SaleRequestType;
import org.killbill.billing.plugin.vertex.gen.client.model.SaleTransactionTypeEnum;
import org.killbill.billing.plugin.vertex.gen.client.model.SellerType;
import org.killbill.billing.plugin.vertex.gen.client.model.TaxRegistrationType;
import org.killbill.billing.plugin.vertex.gen.client.model.TaxesType;
import org.killbill.billing.plugin.vertex.gen.dao.model.tables.records.VertexResponsesRecord;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

public class VertexTaxCalculator extends PluginTaxCalculator {

    public static final String TAX_CODE = "taxCode";
    public static final String PRODUCT_VALUE = "productValue";
    public static final String USAGE_CLASS = "usageClass";

    public static final String TAX_REGISTRATION_NUMBER = "taxRegistrationNumber";
    public static final String LOCATION_ADDRESS1 = "locationAddress1";
    public static final String LOCATION_ADDRESS2 = "locationAddress2";
    public static final String LOCATION_CITY = "locationCity";
    public static final String LOCATION_REGION = "locationRegion";
    public static final String LOCATION_POSTAL_CODE = "locationPostalCode";
    public static final String LOCATION_COUNTRY = "locationCountry";

    public static final String SELLER_DIVISION = "sellerDivision";
    public static final String SELLER_ADDRESS1 = "sellerAddress1";
    public static final String SELLER_ADDRESS2 = "sellerAddress2";
    public static final String SELLER_CITY = "sellerCity";
    public static final String SELLER_REGION = "sellerRegion";
    public static final String SELLER_POSTAL_CODE = "sellerPostalCode";
    public static final String SELLER_COUNTRY = "sellerCountry";

    public static final String KB_TRANSACTION_PREFIX = "kb_";

    private static final Logger logger = LoggerFactory.getLogger(VertexTaxCalculator.class);

    private final VertexApiConfigurationHandler vertexApiConfigurationHandler;
    private final VertexDao dao;
    private final Clock clock;

    public VertexTaxCalculator(final VertexApiConfigurationHandler vertexApiConfigurationHandler,
                               final VertexDao dao,
                               final Clock clock,
                               final OSGIKillbillAPI osgiKillbillAPI) {
        super(osgiKillbillAPI);
        this.vertexApiConfigurationHandler = vertexApiConfigurationHandler;
        this.clock = clock;
        this.dao = dao;
    }

    public List<InvoiceItem> compute(final Account account,
                                     final Invoice newInvoice,
                                     final boolean dryRun,
                                     final Iterable<PluginProperty> pluginProperties,
                                     final TenantContext tenantContext) throws Exception {
        // Retrieve what we've already taxed
        final List<VertexResponsesRecord> responses = dao.getSuccessfulResponses(newInvoice.getId(), tenantContext.getTenantId());
        final Map<UUID, Set<UUID>> alreadyTaxedItemsWithAdjustments = dao.getTaxedItemsWithAdjustments(responses);

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
        // Keep track of the invoice items and adjustments we've already taxed
        final Map<UUID, Iterable<InvoiceItem>> kbInvoiceItems = new HashMap<>();
        if (adjustmentItems != null) {
            kbInvoiceItems.putAll(adjustmentItems);
        }
        for (final InvoiceItem taxableItem : taxableItems.values()) {
            kbInvoiceItems.computeIfAbsent(taxableItem.getId(), k -> ImmutableList.of());
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
                                                      final LocalDate taxItemsDate) throws ApiException, SQLException {
        final VertexApiClient vertexApiClient = vertexApiConfigurationHandler.getConfigurable(kbTenantId);

        final SaleRequestType taxRequest = toTaxRequest(account,
                                                        invoice,
                                                        taxableItems.values(),
                                                        adjustmentItems,
                                                        originalInvoiceReferenceCode,
                                                        dryRun,
                                                        pluginProperties,
                                                        taxItemsDate,
                                                        vertexApiClient.getCompanyName(),
                                                        vertexApiClient.shouldSkipAnomalousAdjustments());

        if (taxRequest == null) {
            return ImmutableList.of();
        }

        logger.info("CreateTransaction req: {}", taxRequest);

        try {
            final ApiSuccessResponseTransactionResponseType taxResult = vertexApiClient.calculateTaxes(taxRequest);
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
                final UUID invoiceItemId = ownerResponseLineItem.getLineItemId() != null ? UUID.fromString(ownerResponseLineItem.getLineItemId()) : null;
                final InvoiceItem adjustmentItem;
                if (adjustmentItems != null &&
                    invoiceItemId != null &&
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
            final BigDecimal totalTax = transactionLineModel.getTotalTax() != null ? BigDecimal.valueOf(transactionLineModel.getTotalTax()) : null;
            final InvoiceItem taxItem = buildTaxItem(taxableItem, invoiceId, adjustmentItem, totalTax, "Tax");
            if (taxItem == null) {
                return ImmutableList.of();
            } else {
                return ImmutableList.of(taxItem);
            }
        } else {
            final Collection<InvoiceItem> invoiceItems = new LinkedList<>();
            for (final TaxesType transactionLineDetailModel : transactionLineModel.getTaxes()) {
                final String description = getTaxDescription(transactionLineDetailModel);
                final BigDecimal calculatedTax = transactionLineDetailModel.getCalculatedTax() != null ? BigDecimal.valueOf(transactionLineDetailModel.getCalculatedTax()) : null;
                final InvoiceItem taxItem = createTaxInvoiceItem(taxableItem, invoiceId, adjustmentItem, calculatedTax, description, transactionLineDetailModel.getEffectiveRate());
                if (taxItem != null) {
                    invoiceItems.add(taxItem);
                }
            }
            return invoiceItems;
        }
    }

    private InvoiceItem createTaxInvoiceItem(final InvoiceItem taxableItem, final UUID invoiceId, @Nullable final InvoiceItem adjustmentItem, final BigDecimal calculatedTax, @Nullable final String description, @Nullable Double taxRate) {
        final InvoiceItem taxItem = buildTaxItem(taxableItem, invoiceId, adjustmentItem, calculatedTax, description);
        if (taxItem == null) {
            return null;
        }

        if (taxRate == null) {
            logger.warn("The tax rate is not provided in the Vertex response for the tax item with ID: {} and calculated tax: {}", taxItem.getId(), calculatedTax);
            taxRate = calculatedTax.divide(taxableItem.getAmount(), 5, RoundingMode.FLOOR).doubleValue();
        }

        final String taxItemDetails = addTaxRateToItemDetails(taxItem.getItemDetails(), taxRate);

        return new PluginInvoiceItem(new Builder<>()
                                             .withId(taxItem.getId())
                                             .withInvoiceItemType(taxItem.getInvoiceItemType())
                                             .withInvoiceId(taxItem.getInvoiceId())
                                             .withAccountId(taxItem.getAccountId())
                                             .withChildAccountId(taxItem.getChildAccountId())
                                             .withStartDate(taxItem.getStartDate())
                                             .withEndDate(taxItem.getEndDate())
                                             .withAmount(taxItem.getAmount())
                                             .withCurrency(taxItem.getCurrency())
                                             .withDescription(taxItem.getDescription())
                                             .withSubscriptionId(taxItem.getSubscriptionId())
                                             .withBundleId(taxItem.getBundleId())
                                             .withCatalogEffectiveDate(taxItem.getCatalogEffectiveDate())
                                             .withProductName(taxItem.getProductName())
                                             .withPrettyProductName(taxItem.getPrettyProductName())
                                             .withPlanName(taxItem.getPlanName())
                                             .withPrettyPlanName(taxItem.getPrettyPlanName())
                                             .withPhaseName(taxItem.getPhaseName())
                                             .withPrettyPhaseName(taxItem.getPrettyPhaseName())
                                             .withRate(taxItem.getRate())
                                             .withLinkedItemId(taxItem.getLinkedItemId())
                                             .withUsageName(taxItem.getUsageName())
                                             .withPrettyUsageName(taxItem.getPrettyUsageName())
                                             .withQuantity(taxItem.getQuantity())
                                             .withItemDetails(taxItemDetails)
                                             .withCreatedDate(taxItem.getCreatedDate())
                                             .withUpdatedDate(taxItem.getUpdatedDate())
                                             .validate().build());
    }

    private String addTaxRateToItemDetails(@Nullable final String itemDetails, @Nonnull final Double taxRate) {
        ObjectNode existingItemsDetailsJson = null;
        final ObjectMapper objectMapper =  new ObjectMapper();

        if (itemDetails != null && !itemDetails.isEmpty()) {
            try {
                final JsonNode jsonNode = objectMapper.readTree(itemDetails);
                if (!jsonNode.isObject()) {
                    return itemDetails;
                }
                existingItemsDetailsJson =  (ObjectNode) jsonNode;
            } catch (JsonProcessingException e) {
                logger.error("Couldn't deserialize the item details: {}", itemDetails, e);
                return itemDetails;
            }
        }

        final Object itemDetailsWithTaxRate;
        if (existingItemsDetailsJson != null) {
            existingItemsDetailsJson.put("taxRate", taxRate);
            itemDetailsWithTaxRate = existingItemsDetailsJson;
        } else {
            itemDetailsWithTaxRate = ImmutableMap.of("taxRate", taxRate);
        }

        try {
            return objectMapper.writeValueAsString(itemDetailsWithTaxRate);
        } catch (JsonProcessingException exception) {
            logger.error("Couldn't serialize the tax item details {} with tax rate: {}", itemDetailsWithTaxRate, taxRate, exception);
            return itemDetails;
        }
    }

    private String getTaxDescription(final TaxesType transactionLineDetailModel) {
        final Jurisdiction jurisdiction = transactionLineDetailModel.getJurisdiction();
        return jurisdiction != null
               ? String.format("%s %s TAX", jurisdiction.getValue(), jurisdiction.getJurisdictionType())
               : MoreObjects.firstNonNull(transactionLineDetailModel.getTaxCode(), MoreObjects.firstNonNull(transactionLineDetailModel.getVertexTaxCode(), "Tax"));
    }

    private SaleRequestType toTaxRequest(final Account account,
                                         final Invoice invoice,
                                         final Collection<InvoiceItem> taxableItems,
                                         @Nullable final Map<UUID, List<InvoiceItem>> adjustmentItems,
                                         @Nullable final String originalInvoiceReferenceCode,
                                         final boolean dryRun,
                                         final Iterable<PluginProperty> pluginProperties,
                                         final LocalDate taxItemsDate,
                                         final String companyName,
                                         final boolean skipAnomalousAdjustments) {

        try {
            Preconditions.checkState((originalInvoiceReferenceCode == null && (adjustmentItems == null || adjustmentItems.isEmpty())) ||
                                     (originalInvoiceReferenceCode != null && (adjustmentItems != null && !adjustmentItems.isEmpty())),
                                     "Invalid combination of originalInvoiceReferenceCode %s and adjustments %s", originalInvoiceReferenceCode, adjustmentItems);
        } catch (IllegalStateException e) {
            if (skipAnomalousAdjustments) {
                logger.warn("Ignoring tax request due to inconsistent adjustments: originalInvoiceReferenceCode={}, adjustmentItems={}", originalInvoiceReferenceCode, adjustmentItems);
                return null;
            } else {
                throw e;
            }
        }

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
        taxRequest.setTransactionId(KB_TRANSACTION_PREFIX + UUID.randomUUID());

        // Considering there could be multiple documents for same invoice, using random string in addition to invoice_id
        String docNumber = String.format("%s_%s", invoice.getId().toString(), UUID.randomUUID().toString().substring(0, 12));
        taxRequest.setDocumentNumber(docNumber);

        taxRequest.setDocumentDate(java.time.LocalDate.of(invoice.getInvoiceDate().getYear(), invoice.getInvoiceDate().getMonthOfYear(), invoice.getInvoiceDate().getDayOfMonth()));
        taxRequest.setPostingDate(java.time.LocalDate.of(taxItemsDate.getYear(), taxItemsDate.getMonthOfYear(), taxItemsDate.getDayOfMonth()));

        CurrencyType currencyType = new CurrencyType();
        currencyType.setIsoCurrencyCodeAlpha(invoice.getCurrency().name());
        taxRequest.setCurrency(currencyType);

        taxRequest.setCustomer(buildCustomer(account, pluginProperties));
        taxRequest.setSeller(buildSeller(pluginProperties, companyName));

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

    private SellerType buildSeller(final Iterable<PluginProperty> pluginProperties, final String companyName) {
        final SellerType sellerType = new SellerType();

        sellerType.setCompany(companyName);
        sellerType.setDivision(PluginProperties.findPluginPropertyValue(SELLER_DIVISION, pluginProperties));

        final String sellerCountry = PluginProperties.findPluginPropertyValue(SELLER_COUNTRY, pluginProperties);
        if (sellerCountry != null) { //country is required field
            sellerType.setPhysicalOrigin(buildSellerAddress(pluginProperties));
        }

        return sellerType;
    }

    private LocationType buildSellerAddress(final Iterable<PluginProperty> pluginProperties) {
        final LocationType sellerAddress = new LocationType();

        sellerAddress.setStreetAddress1(PluginProperties.findPluginPropertyValue(SELLER_ADDRESS1, pluginProperties));
        sellerAddress.setStreetAddress2(PluginProperties.findPluginPropertyValue(SELLER_ADDRESS2, pluginProperties));
        sellerAddress.setCity(PluginProperties.findPluginPropertyValue(SELLER_CITY, pluginProperties));
        sellerAddress.setMainDivision(PluginProperties.findPluginPropertyValue(SELLER_REGION, pluginProperties));
        sellerAddress.setPostalCode(PluginProperties.findPluginPropertyValue(SELLER_POSTAL_CODE, pluginProperties));
        sellerAddress.setCountry(PluginProperties.findPluginPropertyValue(SELLER_COUNTRY, pluginProperties));

        return sellerAddress;
    }

    private CustomerType buildCustomer(final Account account, final Iterable<PluginProperty> pluginProperties) {
        final CustomerType customerType = new CustomerType();

        final LocationType customerDestination = toAddress(account, pluginProperties);
        customerType.setDestination(customerDestination);

        final CustomerCodeType code = new CustomerCodeType();
        code.setValue(MoreObjects.firstNonNull(account.getExternalKey(), account.getId()).toString());
        customerType.setCustomerCode(code);

        customerType.setTaxRegistrations(buildCustomerTaxRegistrations(pluginProperties));

        return customerType;
    }

    private List<TaxRegistrationType> buildCustomerTaxRegistrations(final Iterable<PluginProperty> pluginProperties) {
        final TaxRegistrationType taxRegistration = new TaxRegistrationType();

        taxRegistration.setHasPhysicalPresenceIndicator(true);
        taxRegistration.setIsoCountryCode(PluginProperties.findPluginPropertyValue(LOCATION_COUNTRY, pluginProperties));
        taxRegistration.setTaxRegistrationNumber(PluginProperties.findPluginPropertyValue(TAX_REGISTRATION_NUMBER, pluginProperties));

        return Collections.singletonList(taxRegistration);
    }

    private SaleRequestLineItemType toLine(final InvoiceItem taxableItem,
                                           @Nullable final Iterable<InvoiceItem> adjustmentItems,
                                           @Nullable final LocalDate originalInvoiceDate,
                                           final Iterable<PluginProperty> pluginProperties,
                                           long lineNumber) {
        final SaleRequestLineItemType lineItemModel = new SaleRequestLineItemType();
        lineItemModel.setLineItemId(taxableItem.getId().toString());
        lineItemModel.setLineItemNumber(lineNumber);
        lineItemModel.setUsageClass(PluginProperties.findPluginPropertyValue(String.format("%s_%s", USAGE_CLASS, taxableItem.getId()), pluginProperties));
        // lineItemModel.setTaxDate(); //set to taxItemsDate if needed

        // SKU
        Product product = new Product();
        product.setProductClass(PluginProperties.findPluginPropertyValue(String.format("%s_%s", TAX_CODE, taxableItem.getId()), pluginProperties));
        product.setValue(PluginProperties.findPluginPropertyValue(String.format("%s_%s", PRODUCT_VALUE, taxableItem.getId()), pluginProperties));
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
