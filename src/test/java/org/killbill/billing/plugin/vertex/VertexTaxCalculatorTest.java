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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.plugin.vertex.dao.VertexDao;
import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessResponseTransactionResponseType;
import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessResponseTransactionResponseTypeData;
import org.killbill.billing.plugin.vertex.gen.client.model.Jurisdiction;
import org.killbill.billing.plugin.vertex.gen.client.model.JurisdictionTypeEnum;
import org.killbill.billing.plugin.vertex.gen.client.model.OwnerResponseLineItemType;
import org.killbill.billing.plugin.vertex.gen.client.model.SaleMessageTypeEnum;
import org.killbill.billing.plugin.vertex.gen.client.model.SaleRequestType;
import org.killbill.billing.plugin.vertex.gen.client.model.TaxesType;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.AssertJUnit.assertEquals;

public class VertexTaxCalculatorTest {

    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID TAX_ITEM_ID = UUID.randomUUID();
    private static final UUID ADJUSTMENT_ID = UUID.randomUUID();

    private static final LocalDate INVOICE_DATE = LocalDate.now();
    private static final double MOCK_TAX_AMOUNT_1_01 = 1.01;

    @Mock
    private Account account;
    @Mock
    private Invoice invoice;
    @Mock
    private TenantContext tenantContext;
    @Mock
    private Clock clock;
    @Mock
    private VertexDao vertexDao;
    @Mock
    private VertexApiClient vertexApiClient;
    @Mock
    private VertexApiConfigurationHandler vertexApiConfigurationHandler;
    @Mock
    private ApiSuccessResponseTransactionResponseType taxResponse;
    @Mock
    private OwnerResponseLineItemType responseLineItem;
    @Mock
    private ApiSuccessResponseTransactionResponseTypeData apiResponseData;
    @Mock
    private InvoiceItem taxableInvoiceItem;
    @Mock
    private InvoiceItem adjustment;
    @Mock
    private TaxesType taxesType;

    @InjectMocks
    private VertexTaxCalculator vertexTaxCalculator;

    @BeforeClass(groups = "fast")
    public void beforeClass() {
        MockitoAnnotations.openMocks(this);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {

        Mockito.clearInvocations(vertexDao);
        Mockito.clearInvocations(vertexApiClient);

        given(vertexApiConfigurationHandler.getConfigurable(any(UUID.class))).willReturn(vertexApiClient);
        given(tenantContext.getTenantId()).willReturn(UUID.randomUUID());
        given(invoice.getId()).willReturn(INVOICE_ID);
        given(invoice.getInvoiceDate()).willReturn(INVOICE_DATE);
        given(invoice.getCurrency()).willReturn(Currency.USD);
        given(account.getExternalKey()).willReturn("externalKey");
        given(invoice.getInvoiceItems()).willReturn(Collections.singletonList(taxableInvoiceItem));
        given(clock.getUTCNow()).willReturn(new DateTime(DateTimeZone.UTC));

        given(account.getId()).willReturn(UUID.randomUUID());

        given(taxableInvoiceItem.getAmount()).willReturn(new BigDecimal(1));
        given(taxableInvoiceItem.getInvoiceItemType()).willReturn(InvoiceItemType.RECURRING);
        given(taxableInvoiceItem.getInvoiceId()).willReturn(INVOICE_ID);
        given(taxableInvoiceItem.getId()).willReturn(TAX_ITEM_ID);
        given(taxableInvoiceItem.getStartDate()).willReturn(INVOICE_DATE);
        given(taxableInvoiceItem.getEndDate()).willReturn(INVOICE_DATE.plusMonths(1));

        given(adjustment.getAmount()).willReturn(new BigDecimal(1));
        given(adjustment.getInvoiceItemType()).willReturn(InvoiceItemType.ITEM_ADJ);
        given(adjustment.getInvoiceId()).willReturn(INVOICE_ID);
        given(adjustment.getId()).willReturn(ADJUSTMENT_ID);
        given(adjustment.getLinkedItemId()).willReturn(TAX_ITEM_ID);
        given(adjustment.getStartDate()).willReturn(INVOICE_DATE);
        given(adjustment.getEndDate()).willReturn(INVOICE_DATE.plusMonths(1));

        given(vertexDao.getSuccessfulResponses(any(UUID.class), any(UUID.class))).willReturn(Collections.emptyList());

        given(responseLineItem.getTaxes()).willReturn(Collections.singletonList(taxesType));
        given(taxesType.getCalculatedTax()).willReturn(MOCK_TAX_AMOUNT_1_01);

        given(responseLineItem.getLineItemId()).willReturn(TAX_ITEM_ID.toString());
        given(responseLineItem.getTotalTax()).willReturn(MOCK_TAX_AMOUNT_1_01);

        given(vertexApiClient.calculateTaxes(any(SaleRequestType.class))).willReturn(taxResponse);
        given(taxResponse.getData()).willReturn(apiResponseData);
        given(apiResponseData.getLineItems()).willReturn(Collections.singletonList(responseLineItem));
    }

    @BeforeMethod(groups = "fast")
    public void clearInvocations() {
        Mockito.clearInvocations(vertexDao);
        Mockito.clearInvocations(vertexApiClient);
    }

    @Test(groups = "fast")
    public void testComputeReturnsEmptyIfNoDataInApiResponse() throws Exception {
        //given
        given(taxResponse.getData()).willReturn(null);
        final boolean isDryRun = false;

        //when
        List<InvoiceItem> result = vertexTaxCalculator.compute(account, invoice, isDryRun, Collections.emptyList(), tenantContext);

        //then
        verify(vertexDao).addResponse(any(UUID.class), any(UUID.class), anyMap(), any(ApiSuccessResponseTransactionResponseType.class), any(DateTime.class), any(UUID.class));
        assertEquals(0, result.size());
    }

    @Test(groups = "fast")
    public void testCompute() throws Exception {
        //given
        final boolean isDryRun = false;

        //when
        List<InvoiceItem> result = vertexTaxCalculator.compute(account, invoice, isDryRun, Collections.emptyList(), tenantContext);

        //then
        verify(vertexDao, atLeastOnce()).addResponse(any(UUID.class), any(UUID.class), anyMap(), any(ApiSuccessResponseTransactionResponseType.class), any(DateTime.class), any(UUID.class));
        verify(vertexApiClient).calculateTaxes(argThat(arg -> SaleMessageTypeEnum.INVOICE.equals(arg.getSaleMessageType())));

        assertEquals(1, result.size());
        assertEquals(BigDecimal.valueOf(MOCK_TAX_AMOUNT_1_01), result.get(0).getAmount());

        assertEquals(InvoiceItemType.TAX, result.get(0).getInvoiceItemType());
        assertEquals("Tax", result.get(0).getDescription());

        assertEquals(INVOICE_ID, result.get(0).getInvoiceId());
        assertEquals(INVOICE_DATE, result.get(0).getStartDate());
        assertEquals(INVOICE_DATE.plusMonths(1), result.get(0).getEndDate());
    }

    @Test(groups = "fast")
    public void testComputeDryRun() throws Exception {
        //given
        final boolean isDryRun = true;

        //when
        vertexTaxCalculator.compute(account, invoice, isDryRun, Collections.emptyList(), tenantContext);

        //then
        verify(vertexDao, times(0))
                .addResponse(any(UUID.class), any(UUID.class), anyMap(), any(ApiSuccessResponseTransactionResponseType.class), any(DateTime.class), any(UUID.class));
        verify(vertexApiClient).calculateTaxes(argThat(arg -> SaleMessageTypeEnum.QUOTATION.equals(arg.getSaleMessageType())));
    }

    @Test(groups = "fast")
    public void testTaxDescription() throws Exception {
        //given
        final boolean isDryRun = false;
        given(responseLineItem.getTaxes()).willReturn(null);

        String expectedTaxDescription = "Tax"; //The case when taxes retrieved from total tax field (taxes object is missing in line item)
        List<InvoiceItem> result = vertexTaxCalculator.compute(account, invoice, isDryRun, Collections.emptyList(), tenantContext);
        assertEquals(expectedTaxDescription, result.get(0).getDescription());

        //when taxes retrieved from calculated tax field
        TaxesType taxesType = new TaxesType();
        taxesType.setCalculatedTax(MOCK_TAX_AMOUNT_1_01);
        given(responseLineItem.getTaxes()).willReturn(Collections.singletonList(taxesType));
        expectedTaxDescription = "Tax Code";

        //when jurisdiction is absent and both vertex tax code and tax code are present
        taxesType.setTaxCode(expectedTaxDescription);
        taxesType.setVertexTaxCode("Vertex Tax Code");
        result = vertexTaxCalculator.compute(account, invoice, isDryRun, Collections.emptyList(), tenantContext);
        assertEquals(expectedTaxDescription, result.get(0).getDescription());

        //when jurisdiction is absent and only vertex tax code is present
        taxesType.setTaxCode(null);
        taxesType.setVertexTaxCode("Vertex Tax Code");
        result = vertexTaxCalculator.compute(account, invoice, isDryRun, Collections.emptyList(), tenantContext);
        assertEquals("Vertex Tax Code", result.get(0).getDescription());

        //when jurisdiction exists
        taxesType = taxesType.jurisdiction(new Jurisdiction().jurisdictionType(JurisdictionTypeEnum.STATE).value("CA"));
        given(responseLineItem.getTaxes()).willReturn(Collections.singletonList(taxesType));
        result = vertexTaxCalculator.compute(account, invoice, isDryRun, Collections.emptyList(), tenantContext);
        assertEquals("CA STATE TAX", result.get(0).getDescription());
    }

    @Test(groups = "fast")
    public void testTaxEffectiveRate() throws Exception {
        //given
        given(taxesType.getEffectiveRate()).willReturn(0.09975d);

        //then it persisted in item details invoice item field
        List<InvoiceItem> result = vertexTaxCalculator.compute(account, invoice, true, Collections.emptyList(), tenantContext);
        assertEquals("{\"taxRate\":0.09975}", result.get(0).getItemDetails());
        checkTaxItemFields(result.get(0));
    }

    @Test(groups = "fast")
    public void testTaxItemDetailsWhenUsageDetailsExists() throws Exception {
        //given
        final Double taxRate = 0.09975d;
        given(taxesType.getEffectiveRate()).willReturn(taxRate);

        final String itemDetailsWithTierInfo =
                "{\"tierDetails\":[{\"tier\":1,\"tierUnit\":\"hour\",\"tierPrice\":0.500000000," +
                "\"tierBlockSize\":1,\"quantity\":1,\"amount\":0.500000000}],\"amount\":0.500000000}";
        given(taxableInvoiceItem.getItemDetails()).willReturn(itemDetailsWithTierInfo);

        final String expectedItemDetailsWithTaxRate =
                "{\"tierDetails\":[{\"tier\":1,\"tierUnit\":\"hour\",\"tierPrice\":0.500000000," +
                "\"tierBlockSize\":1,\"quantity\":1,\"amount\":0.500000000}],\"amount\":0.500000000," +
                "\"taxRate\":" + taxRate + "}";

        //when
        List<InvoiceItem> result = vertexTaxCalculator.compute(account, invoice, true, Collections.emptyList(), tenantContext);
        final ObjectMapper objectMapper = new ObjectMapper();
        final JsonNode expectedNode = objectMapper.readTree(expectedItemDetailsWithTaxRate);
        final JsonNode actualNode = objectMapper.readTree(result.get(0).getItemDetails());

        //then
        assertEquals(expectedNode, actualNode);
    }

    @Test(groups = "fast")
    public void testComputeWhenTaxEffectiveRateIsNotPresentedByVertex() throws Exception {
        //given
        final BigDecimal taxableAmount = BigDecimal.valueOf(0.97d);
        given(taxableInvoiceItem.getAmount()).willReturn(taxableAmount);

        final BigDecimal expectedTaxRate = BigDecimal.valueOf(0.09975);
        final TaxesType taxesType = new TaxesType();
        taxesType.setCalculatedTax(taxableAmount.multiply(expectedTaxRate).doubleValue());
        given(responseLineItem.getTaxes()).willReturn(Collections.singletonList(taxesType));

        taxesType.setEffectiveRate(null);

        //when tax rate is calculated and provided in item details in the result
        List<InvoiceItem> result = vertexTaxCalculator.compute(account, invoice, true, Collections.emptyList(), tenantContext);

        //then
        assertEquals(1, result.size());
        assertEquals("{\"taxRate\":" + expectedTaxRate + "}", result.get(0).getItemDetails());
        checkTaxItemFields(result.get(0));
    }

    @Test(groups = "fast")
    public void testComputeWhenEffectiveTaxRateZero() throws Exception {
        //given
        given(taxesType.getEffectiveRate()).willReturn(0d);

        //when calculated tax amount is positive tax item with tax rate is added to the result
        List<InvoiceItem> result = vertexTaxCalculator.compute(account, invoice, true, Collections.emptyList(), tenantContext);

        //then
        assertEquals(1, result.size());
        assertEquals("{\"taxRate\":0.0}", result.get(0).getItemDetails());
        checkTaxItemFields(result.get(0));
    }

    private void checkTaxItemFields(final InvoiceItem taxItem) {
        assertEquals(InvoiceItemType.TAX, taxItem.getInvoiceItemType());
        assertEquals("Tax", taxItem.getDescription());
        assertEquals(INVOICE_ID, taxItem.getInvoiceId());
        assertEquals(INVOICE_DATE, taxItem.getStartDate());
        assertEquals(INVOICE_DATE.plusMonths(1), taxItem.getEndDate());
    }

    @Test(groups = "fast", expectedExceptions = {IllegalStateException.class})
    public void testComputeWithAnomalousAdjustmentsException() throws Exception {
        //given
        given(invoice.getInvoiceItems()).willReturn(Arrays.asList(taxableInvoiceItem, adjustment));
        final boolean isDryRun = false;

        //IllegalStateException is thrown when previous invoice id is missing for adjustments and skipAnomalousAdjustments property is not set
        vertexTaxCalculator.compute(account, invoice, isDryRun, Collections.emptyList(), tenantContext);
    }

    @Test(groups = "fast")
    public void testComputeWithAnomalousAdjustmentsSkipIfPropertyTrue() throws Exception {
        //given
        given(invoice.getInvoiceItems()).willReturn(Arrays.asList(taxableInvoiceItem, adjustment));
        final boolean isDryRun = false;
        given(vertexApiClient.shouldSkipAnomalousAdjustments()).willReturn(true); //vertex-plugin will skip adjustment items if previousInvoiceId is missing

        //when
        List<InvoiceItem> result = vertexTaxCalculator.compute(account, invoice, isDryRun, Collections.emptyList(), tenantContext);

        //then
        verify(vertexDao, atLeastOnce()).addResponse(any(UUID.class), any(UUID.class), anyMap(), any(ApiSuccessResponseTransactionResponseType.class), any(DateTime.class), any(UUID.class));
        verify(vertexApiClient).calculateTaxes(argThat(arg -> SaleMessageTypeEnum.INVOICE.equals(arg.getSaleMessageType())));

        assertEquals(1, result.size());
        assertEquals(BigDecimal.valueOf(MOCK_TAX_AMOUNT_1_01), result.get(0).getAmount());

        assertEquals(InvoiceItemType.TAX, result.get(0).getInvoiceItemType());
        assertEquals("Tax", result.get(0).getDescription());

        assertEquals(INVOICE_ID, result.get(0).getInvoiceId());
        assertEquals(INVOICE_DATE, result.get(0).getStartDate());
        assertEquals(INVOICE_DATE.plusMonths(1), result.get(0).getEndDate());
    }
}
