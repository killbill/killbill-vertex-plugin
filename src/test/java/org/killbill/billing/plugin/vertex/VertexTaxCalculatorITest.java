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

import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.TestUtils;
import org.killbill.billing.plugin.api.PluginTenantContext;
import org.killbill.billing.plugin.vertex.base.VertexRemoteTestBase;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

public class VertexTaxCalculatorITest extends VertexRemoteTestBase {

    private final Clock clock = new DefaultClock();
    private final Collection<PluginProperty> pluginProperties = new LinkedList<>();
    private final UUID tenantId = UUID.randomUUID();
    private final TenantContext tenantContext = new PluginTenantContext(null, tenantId);

    private Account account;
    private Account account2;
    private Account account3;
    private OSGIKillbillAPI osgiKillbillAPI;
    private VertexTaxCalculator calculator;

    @BeforeMethod(groups = "integration")
    public void setUp() throws Exception {
        account = TestUtils.buildAccount(Currency.USD, "45 Fremont Street", null, "San Francisco", "CA", "94105", "US");
        account2 = TestUtils.buildAccount(Currency.USD, "118 N Clark St Ste 100", null, "San Francisco", "CA", "94105", "US");
        account3 = TestUtils.buildAccount(Currency.USD, "118 N Clark St Ste 100", null, "Chicago", "IL", "60602", "US");

        osgiKillbillAPI = TestUtils.buildOSGIKillbillAPI(account);
        Mockito.when(osgiKillbillAPI.getInvoiceUserApi()).thenReturn(Mockito.mock(InvoiceUserApi.class));

        final VertexApiConfigurationHandler vertexApiConfigurationHandler = new VertexApiConfigurationHandler(VertexActivator.PLUGIN_NAME, osgiKillbillAPI);
        vertexApiConfigurationHandler.setDefaultConfigurable(vertexApiClient);

        pluginProperties.add(new PluginProperty(String.format("%s", VertexTaxCalculator.SELLER_DIVISION), "328", false));

        calculator = new VertexTaxCalculator(vertexApiConfigurationHandler, dao, clock, osgiKillbillAPI);
    }

    @Test(groups = "integration")
    public void testVertexTaxCalculator() throws Exception {
        testComputeItemsOverTime(account, account2);
    }

    @Test(groups = "integration")
    public void testInvoiceItemAdjustmentOnNewInvoice() throws Exception {
        //given
        final Invoice invoice = TestUtils.buildInvoice(account3);
        final InvoiceItem taxableItem1 = TestUtils.buildInvoiceItem(invoice, InvoiceItemType.EXTERNAL_CHARGE, new BigDecimal("100"), null);
        invoice.getInvoiceItems().add(taxableItem1);
        invoice.getInvoiceItems().add(TestUtils.buildInvoiceItem(invoice, InvoiceItemType.ITEM_ADJ, BigDecimal.ONE.negate(), taxableItem1.getId()));

        //when
        final List<InvoiceItem> initialTaxItems = calculator.compute(account3, invoice, false, pluginProperties, tenantContext);

        //then
        Assert.assertEquals(dao.getSuccessfulResponses(invoice.getId(), tenantId).size(), 2);
        Assert.assertEquals(initialTaxItems.size(), 8);
    }

    @Test(groups = "integration")
    public void testComputeWhenAddressHasSpecialCharacters() throws Exception {
        //given
        Account accountWithSpecialChars = TestUtils.buildAccount(Currency.USD, "Alexanderstraße 11", null, "berlin", "BE", "10178", "DE");
        final Invoice invoice = TestUtils.buildInvoice(accountWithSpecialChars);
        final InvoiceItem taxableItem = TestUtils.buildInvoiceItem(invoice, InvoiceItemType.EXTERNAL_CHARGE, new BigDecimal("100"), null);
        invoice.getInvoiceItems().add(taxableItem);

        //when
        calculator.compute(accountWithSpecialChars, invoice, false, pluginProperties, tenantContext);

        //then
        Assert.assertEquals(dao.getSuccessfulResponses(invoice.getId(), tenantId).size(), 1);
    }

    private void testComputeItemsOverTime(final Account... accounts) throws Exception {
        for (final Account account : accounts) {
            testComputeItemsOverTime(account);
        }
    }

    private void testComputeItemsOverTime(final Account account) throws Exception {
        final Invoice invoice = TestUtils.buildInvoice(account);
        // Avalara requires testing multiple descriptions and multiple tax codes for certification
        final InvoiceItem taxableItem1 = TestUtils.buildInvoiceItem(invoice, InvoiceItemType.EXTERNAL_CHARGE, new BigDecimal("100"), null);
        Mockito.when(taxableItem1.getDescription()).thenReturn(UUID.randomUUID().toString());
        pluginProperties.add(new PluginProperty(String.format("%s_%s", VertexTaxCalculator.TAX_CODE, taxableItem1.getId()), "PC030100", false));
        final InvoiceItem taxableItem2 = TestUtils.buildInvoiceItem(invoice, InvoiceItemType.RECURRING, BigDecimal.TEN, null);
        Mockito.when(taxableItem2.getDescription()).thenReturn(UUID.randomUUID().toString());
        pluginProperties.add(new PluginProperty(String.format("%s_%s", VertexTaxCalculator.TAX_CODE, taxableItem2.getId()), "PC040100", false));
        invoice.getInvoiceItems().add(taxableItem1);
        invoice.getInvoiceItems().add(taxableItem2);

        // Verify the initial state
        Assert.assertEquals(dao.getSuccessfulResponses(invoice.getId(), tenantId).size(), 0);

        // Compute the initial tax items
        final List<InvoiceItem> initialTaxItems = calculator.compute(account, invoice, false, pluginProperties, tenantContext);
        Assert.assertEquals(dao.getSuccessfulResponses(invoice.getId(), tenantId).size(), 1);

        // Check the created items
        checkCreatedItems(ImmutableMap.of(taxableItem1.getId(), InvoiceItemType.TAX,
                                          taxableItem2.getId(), InvoiceItemType.TAX), initialTaxItems, invoice);

        // Verify idempotency
        Assert.assertEquals(calculator.compute(account, invoice, false, pluginProperties, tenantContext).size(), 0);
        Assert.assertEquals(dao.getSuccessfulResponses(invoice.getId(), tenantId).size(), 1);

        // Compute a subsequent adjustment
        final InvoiceItem adjustment1ForInvoiceItem1 = TestUtils.buildInvoiceItem(invoice, InvoiceItemType.ITEM_ADJ, BigDecimal.ONE.negate(), taxableItem1.getId());
        invoice.getInvoiceItems().add(adjustment1ForInvoiceItem1);
        final List<InvoiceItem> adjustments1 = calculator.compute(account, invoice, false, pluginProperties, tenantContext);
        Assert.assertEquals(dao.getSuccessfulResponses(invoice.getId(), tenantId).size(), 2);

        // Check the created item
        checkCreatedItems(ImmutableMap.of(taxableItem1.getId(), InvoiceItemType.TAX), adjustments1, invoice);

        // Verify idempotency
        Assert.assertEquals(calculator.compute(account, invoice, false, pluginProperties, tenantContext).size(), 0);
        Assert.assertEquals(dao.getSuccessfulResponses(invoice.getId(), tenantId).size(), 2);

        // Compute a subsequent adjustment (with a new item on a new invoice this time, to simulate a repair)
        final Invoice adjustmentInvoice = TestUtils.buildInvoice(account);
        final InvoiceItem adjustment1ForInvoiceItem2 = TestUtils.buildInvoiceItem(adjustmentInvoice, InvoiceItemType.REPAIR_ADJ, BigDecimal.ONE.negate(), taxableItem2.getId());
        final InvoiceItem taxableItem3 = TestUtils.buildInvoiceItem(adjustmentInvoice, InvoiceItemType.RECURRING, BigDecimal.TEN, null);
        adjustmentInvoice.getInvoiceItems().add(adjustment1ForInvoiceItem2);
        Mockito.when(osgiKillbillAPI.getInvoiceUserApi().getInvoiceByInvoiceItem(Mockito.eq(taxableItem2.getId()), Mockito.any()))
               .thenReturn(invoice);
        adjustmentInvoice.getInvoiceItems().add(taxableItem3);

        final List<InvoiceItem> adjustments2 = calculator.compute(account, adjustmentInvoice, false, pluginProperties, tenantContext);
        Assert.assertEquals(dao.getSuccessfulResponses(adjustmentInvoice.getId(), tenantId).size(), 2);

        // Check the created items
        checkCreatedItems(ImmutableMap.of(taxableItem2.getId(), InvoiceItemType.TAX,
                                          taxableItem3.getId(), InvoiceItemType.TAX), adjustments2, adjustmentInvoice);

        // Verify idempotency
        Assert.assertEquals(calculator.compute(account, adjustmentInvoice, false, pluginProperties, tenantContext).size(), 0);
        Assert.assertEquals(dao.getSuccessfulResponses(adjustmentInvoice.getId(), tenantId).size(), 2);
    }

    private void checkCreatedItems(final Map<UUID, InvoiceItemType> expectedInvoiceItemTypes, final Iterable<InvoiceItem> createdItems, final Invoice newInvoice) {
        for (final InvoiceItem invoiceItem : createdItems) {
            Assert.assertEquals(invoiceItem.getInvoiceId(), newInvoice.getId());
            Assert.assertEquals(invoiceItem.getInvoiceItemType(), expectedInvoiceItemTypes.get(invoiceItem.getLinkedItemId()));
        }
    }
}
