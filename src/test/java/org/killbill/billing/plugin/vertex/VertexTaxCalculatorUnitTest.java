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

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.vertex.dao.VertexDao;
import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessResponseTransactionResponseType;
import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessResponseTransactionResponseTypeData;
import org.killbill.billing.plugin.vertex.gen.client.model.OwnerResponseLineItemType;
import org.killbill.billing.plugin.vertex.gen.client.model.SaleRequestType;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.testng.AssertJUnit.assertEquals;

public class VertexTaxCalculatorUnitTest {

    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final LocalDate INVOICE_DATE = LocalDate.now();
    private static final double TAX_AMOUNT = 1.01;

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

    @InjectMocks
    private VertexTaxCalculator vertexTaxCalculator;

    @BeforeClass(groups = "fast")
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        given(vertexApiConfigurationHandler.getConfigurable(any(UUID.class))).willReturn(vertexApiClient);
        given(tenantContext.getTenantId()).willReturn(UUID.randomUUID());
        given(invoice.getId()).willReturn(INVOICE_ID);
        given(invoice.getInvoiceDate()).willReturn(INVOICE_DATE);
        given(invoice.getCurrency()).willReturn(Currency.USD);
        given(account.getExternalKey()).willReturn("externalKey");
    }

    @Test(groups = "fast")
    public void testComputeReturnsEmptyIfNoDataInApiResponse() throws Exception {
        //given
        final Iterable<PluginProperty> pluginProperties = Collections.emptyList();

        InvoiceItem taxableInvoiceItem = Mockito.mock(InvoiceItem.class);
        given(taxableInvoiceItem.getAmount()).willReturn(new BigDecimal(1));
        given(taxableInvoiceItem.getInvoiceItemType()).willReturn(InvoiceItemType.RECURRING);
        given(taxableInvoiceItem.getInvoiceId()).willReturn(INVOICE_ID);
        given(taxableInvoiceItem.getId()).willReturn(UUID.randomUUID());

        final List<InvoiceItem> invoiceItems = Collections.singletonList(taxableInvoiceItem);
        given(invoice.getInvoiceItems()).willReturn(invoiceItems);

        given(vertexDao.getSuccessfulResponses(any(UUID.class), any(UUID.class))).willReturn(Collections.emptyList());

        ApiSuccessResponseTransactionResponseType taxResponse = Mockito.mock(ApiSuccessResponseTransactionResponseType.class);
        given(vertexApiClient.calculateTaxes(any(SaleRequestType.class))).willReturn(taxResponse);

        //when
        List<InvoiceItem> result = vertexTaxCalculator.compute(
                account, invoice, false, pluginProperties, tenantContext);

        //then
        assertEquals(0, result.size());
    }

    @Test(groups = "fast")
    public void testCompute() throws Exception {
        //given
        final Iterable<PluginProperty> pluginProperties = Collections.emptyList();

        InvoiceItem taxableInvoiceItem = Mockito.mock(InvoiceItem.class);
        given(taxableInvoiceItem.getAmount()).willReturn(new BigDecimal(1));
        given(taxableInvoiceItem.getInvoiceItemType()).willReturn(InvoiceItemType.RECURRING);
        given(taxableInvoiceItem.getInvoiceId()).willReturn(INVOICE_ID);
        given(taxableInvoiceItem.getId()).willReturn(INVOICE_ID);
        given(taxableInvoiceItem.getStartDate()).willReturn(INVOICE_DATE);
        given(taxableInvoiceItem.getEndDate()).willReturn(INVOICE_DATE.plusMonths(1));

        final List<InvoiceItem> invoiceItems = Arrays.asList(taxableInvoiceItem);
        given(invoice.getInvoiceItems()).willReturn(invoiceItems);

        given(vertexDao.getSuccessfulResponses(any(UUID.class), any(UUID.class))).willReturn(Collections.emptyList());

        ApiSuccessResponseTransactionResponseType taxResponse = Mockito.mock(ApiSuccessResponseTransactionResponseType.class);
        ApiSuccessResponseTransactionResponseTypeData apiResponseData = Mockito.mock(ApiSuccessResponseTransactionResponseTypeData.class);

        OwnerResponseLineItemType responseLineItem = Mockito.mock(OwnerResponseLineItemType.class);
        given(responseLineItem.getLineItemId()).willReturn(INVOICE_ID.toString());
        given(responseLineItem.getTotalTax()).willReturn(TAX_AMOUNT);

        List<OwnerResponseLineItemType> responseLineItemTypeList = Arrays.asList(responseLineItem);
        given(apiResponseData.getLineItems()).willReturn(responseLineItemTypeList);
        given(taxResponse.getData()).willReturn(apiResponseData);
        given(vertexApiClient.calculateTaxes(any(SaleRequestType.class))).willReturn(taxResponse);

        //when
        List<InvoiceItem> result = vertexTaxCalculator.compute(
                account, invoice, false, pluginProperties, tenantContext);

        //then
        assertEquals(1, result.size());
        assertEquals(BigDecimal.valueOf(TAX_AMOUNT), result.get(0).getAmount());
        assertEquals(INVOICE_ID, result.get(0).getInvoiceId());
        assertEquals(InvoiceItemType.TAX, result.get(0).getInvoiceItemType());
        assertEquals("Tax", result.get(0).getDescription());
        assertEquals(INVOICE_DATE, result.get(0).getStartDate());
        assertEquals(INVOICE_DATE.plusMonths(1), result.get(0).getEndDate());
    }
}
