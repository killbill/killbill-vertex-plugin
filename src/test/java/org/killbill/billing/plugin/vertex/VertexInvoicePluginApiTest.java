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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.invoice.plugin.api.OnSuccessInvoiceResult;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.vertex.dao.VertexDao;
import org.killbill.billing.plugin.vertex.gen.ApiException;
import org.killbill.billing.plugin.vertex.gen.dao.model.tables.records.VertexResponsesRecord;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

public class VertexInvoicePluginApiTest {

    @Mock
    private VertexApiConfigurationHandler vertexApiConfigurationHandler;
    @Mock
    private VertexApiClient vertexApiClient;
    @Mock
    private VertexDao dao;
    @Mock
    private InvoiceContext invoiceContext;
    @Mock
    private CallContext callContext;
    @Mock
    private Invoice invoice;
    @Mock
    private OSGIKillbillAPI killbillAPI;
    @Mock
    private AccountUserApi accountUserApi;
    @Mock
    private CustomFieldUserApi customFieldUserApi;
    @Mock
    private Account account;
    @Mock
    private VertexTaxCalculator vertexTaxCalculator;

    @InjectMocks
    private VertexInvoicePluginApi vertexInvoicePluginApi;

    @BeforeClass(groups = "fast")
    public void setUp() throws AccountApiException {
        MockitoAnnotations.openMocks(this);

        final UUID tenantId = UUID.randomUUID();
        given(invoiceContext.getTenantId()).willReturn(tenantId);

        final UUID invoiceId = UUID.randomUUID();
        given(invoice.getId()).willReturn(invoiceId);
        given(invoiceContext.getInvoice()).willReturn(invoice);

        final UUID accountId = UUID.randomUUID();
        given(account.getId()).willReturn(accountId);
        given(accountUserApi.getAccountById(account.getId(), callContext)).willReturn(account);

        given(invoice.getAccountId()).willReturn(accountId);

        given(vertexApiConfigurationHandler.getConfigurable(tenantId)).willReturn(vertexApiClient);

        given(killbillAPI.getAccountUserApi()).willReturn(accountUserApi);
        given(killbillAPI.getCustomFieldUserApi()).willReturn(customFieldUserApi);
    }

    @AfterMethod(groups = "fast")
    public void tearDown() {
        Mockito.reset(dao);
        Mockito.reset(customFieldUserApi);
        Mockito.reset(vertexTaxCalculator);

        Mockito.clearInvocations(vertexApiConfigurationHandler);
        Mockito.clearInvocations(accountUserApi);
        Mockito.clearInvocations(killbillAPI);
        Mockito.clearInvocations(invoice);
        Mockito.clearInvocations(vertexTaxCalculator);
    }

    @Test(groups = "fast")
    public void testGetAdditionalInvoiceItemsWhenVertexSkipPropertyPresent() throws Exception {
        //given
        final Iterable<PluginProperty> properties = Collections.singletonList(new PluginProperty("VERTEX_SKIP", "anyValue", false));

        //when
        List<InvoiceItem> result = vertexInvoicePluginApi.getAdditionalInvoiceItems(invoice, false, properties, callContext);

        //then
        assertEquals(0, result.size());
        verify(vertexTaxCalculator, times(0)).compute(any(Account.class), any(Invoice.class), anyBoolean(), anyList(), any(CallContext.class));
        verify(invoice, times(0)).getInvoiceItems();
        verify(invoice, times(0)).getAccountId();
        verify(killbillAPI, times(0)).getAccountUserApi();
        verify(accountUserApi, times(0)).getAccountById(any(UUID.class), any(CallContext.class));
    }

    @Test(groups = "fast", expectedExceptions = {RuntimeException.class})
    public void testPreventInvoiceGenerationOnExceptionInGetAdditionalInvoiceItems() throws Exception {
        //given
        doThrow(Exception.class).when(vertexTaxCalculator).compute(any(Account.class), any(Invoice.class), anyBoolean(), anyList(), any(CallContext.class));

        //when
        vertexInvoicePluginApi.getAdditionalInvoiceItems(invoice, false, Collections.emptyList(), callContext);
    }

    @Test(groups = "fast")
    public void testGetAdditionalInvoiceItemsWithoutCustomFields() throws Exception {
        //given
        final Iterable<PluginProperty> properties = Collections.emptyList();
        final List<InvoiceItem> invoiceItems = Collections.singletonList(Mockito.mock(InvoiceItem.class));
        given(vertexTaxCalculator.compute(account, invoice, false, properties, callContext)).willReturn(invoiceItems);

        given(customFieldUserApi.getCustomFieldsForAccountType(invoice.getAccountId(), ObjectType.INVOICE_ITEM, callContext)).willReturn(Collections.emptyList());

        //when
        List<InvoiceItem> result = vertexInvoicePluginApi.getAdditionalInvoiceItems(invoice, false, properties, callContext);

        //then
        assertEquals(invoiceItems.size(), result.size());
        verify(vertexTaxCalculator).compute(account, invoice, false, properties, callContext);

        verify(killbillAPI).getAccountUserApi();
        verify(accountUserApi).getAccountById(any(UUID.class), any(CallContext.class));
    }

    @Test(groups = "fast")
    public void testOnSuccessCallWithVoidOperationProperty() throws SQLException {
        //given
        Iterable<PluginProperty> properties = Collections.singletonList(new PluginProperty(VertexInvoicePluginApi.INVOICE_OPERATION, "void", false));

        final List<VertexResponsesRecord> vertexResponses = Arrays.asList(Mockito.mock(VertexResponsesRecord.class), Mockito.mock(VertexResponsesRecord.class));
        vertexResponses.forEach(
                vertexResponse -> given(vertexResponse.getDocCode()).willReturn(UUID.randomUUID().toString()));

        given(dao.getSuccessfulResponses(invoice.getId(), invoiceContext.getTenantId())).willReturn(vertexResponses);

        //when
        OnSuccessInvoiceResult onSuccessInvoiceResult = vertexInvoicePluginApi.onSuccessCall(invoiceContext, properties);

        //then
        vertexResponses.forEach(vertexResponse -> {
            try {
                verify(vertexApiClient).deleteTransaction(vertexResponse.getDocCode());
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
        assertNotNull(onSuccessInvoiceResult);
        verify(vertexApiConfigurationHandler).getConfigurable(invoiceContext.getTenantId());
        verify(dao).getSuccessfulResponses(invoice.getId(), invoiceContext.getTenantId());
    }

    @Test(groups = "fast")
    public void testOnSuccessCallWithoutInvoiceOperationProperty() throws SQLException {
        //given
        final Iterable<PluginProperty> pluginProperties = Collections.emptyList();

        //when
        OnSuccessInvoiceResult onSuccessInvoiceResult = vertexInvoicePluginApi.onSuccessCall(invoiceContext, pluginProperties);

        //then
        assertNotNull(onSuccessInvoiceResult);
        verify(dao, times(0)).getSuccessfulResponses(any(UUID.class), any(UUID.class));
        verify(vertexApiConfigurationHandler, times(0)).getConfigurable(any(UUID.class));
    }

    @Test(groups = "fast")
    public void testOnSuccessCallDoesNotFailOnSQLException() throws SQLException {
        //given
        doThrow(SQLException.class).when(dao).getSuccessfulResponses(any(UUID.class), any(UUID.class));
        final Iterable<PluginProperty> properties = Collections.singletonList(new PluginProperty(VertexInvoicePluginApi.INVOICE_OPERATION, "commit", false));

        //when
        OnSuccessInvoiceResult onSuccessInvoiceResult = vertexInvoicePluginApi.onSuccessCall(invoiceContext, properties);

        //then
        assertNotNull(onSuccessInvoiceResult);
        verify(vertexApiConfigurationHandler).getConfigurable(invoiceContext.getTenantId());
        verify(dao).getSuccessfulResponses(invoice.getId(), invoiceContext.getTenantId());
    }

    @Test(groups = "fast")
    public void testOnSuccessCallDoesNotFailOnApiException() throws SQLException, ApiException {
        //given
        doThrow(ApiException.class).when(vertexApiClient).deleteTransaction(anyString());
        final Iterable<PluginProperty> properties = Collections.singletonList(new PluginProperty(VertexInvoicePluginApi.INVOICE_OPERATION, "void", false));

        final VertexResponsesRecord vertexResponse = Mockito.mock(VertexResponsesRecord.class);
        given(vertexResponse.getDocCode()).willReturn(UUID.randomUUID().toString());

        final List<VertexResponsesRecord> vertexResponses = Collections.singletonList(vertexResponse);

        given(dao.getSuccessfulResponses(invoice.getId(), invoiceContext.getTenantId())).willReturn(vertexResponses);

        //when
        OnSuccessInvoiceResult onSuccessInvoiceResult = vertexInvoicePluginApi.onSuccessCall(invoiceContext, properties);

        //then
        assertNotNull(onSuccessInvoiceResult);
        verify(vertexApiConfigurationHandler).getConfigurable(invoiceContext.getTenantId());
        verify(dao).getSuccessfulResponses(invoice.getId(), invoiceContext.getTenantId());
    }
}
