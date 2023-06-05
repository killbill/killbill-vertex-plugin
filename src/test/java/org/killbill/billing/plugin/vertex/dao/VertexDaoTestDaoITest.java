/*
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.billing.plugin.vertex.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.plugin.TestUtils;
import org.killbill.billing.plugin.vertex.base.VertexRemoteTestBase;
import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessResponseTransactionResponseType;
import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessResponseTransactionResponseTypeData;
import org.killbill.billing.plugin.vertex.gen.dao.model.tables.records.VertexResponsesRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class VertexDaoTestDaoITest extends VertexRemoteTestBase {

    @Test(groups = "slow")
    public void testCreateReadResponses() throws Exception {
        final Account account = TestUtils.buildAccount(Currency.USD, "US");
        final Invoice invoice = TestUtils.buildInvoice(account);
        final UUID kbAccountId = account.getId();
        final UUID kbInvoiceId = invoice.getId();
        final UUID kbTenantId = UUID.randomUUID();

        final InvoiceItem taxableItem1 = TestUtils.buildInvoiceItem(invoice, InvoiceItemType.RECURRING, BigDecimal.TEN, null);
        final InvoiceItem adjustmentItem11 = TestUtils.buildInvoiceItem(invoice, InvoiceItemType.ITEM_ADJ, BigDecimal.ONE.negate(), taxableItem1.getId());
        final InvoiceItem adjustmentItem12 = TestUtils.buildInvoiceItem(invoice, InvoiceItemType.REPAIR_ADJ, BigDecimal.ONE.negate(), taxableItem1.getId());

        final InvoiceItem taxableItem2 = TestUtils.buildInvoiceItem(invoice, InvoiceItemType.RECURRING, BigDecimal.TEN, null);
        final InvoiceItem adjustmentItem21 = TestUtils.buildInvoiceItem(invoice, InvoiceItemType.REPAIR_ADJ, BigDecimal.ONE.negate(), taxableItem2.getId());

        final ApiSuccessResponseTransactionResponseType taxResultS1 = new ApiSuccessResponseTransactionResponseType();
        ApiSuccessResponseTransactionResponseTypeData dataS1 = new ApiSuccessResponseTransactionResponseTypeData();
        dataS1.setTotal(13d);
        taxResultS1.setData(dataS1);

        final ApiSuccessResponseTransactionResponseType taxResultS2 = new ApiSuccessResponseTransactionResponseType();
        ApiSuccessResponseTransactionResponseTypeData dataS2 = new ApiSuccessResponseTransactionResponseTypeData();
        dataS2.setTotal(123d);
        taxResultS2.setData(dataS2);

        // Success
        dao.addResponse(kbAccountId,
                        kbInvoiceId,
                        ImmutableMap.of(taxableItem1.getId(), ImmutableList.of(),
                                        taxableItem2.getId(), ImmutableList.of()),
                        taxResultS1,
                        new DateTime(DateTimeZone.UTC),
                        kbTenantId);
        // Success (subsequent adjustments)
        dao.addResponse(kbAccountId,
                        kbInvoiceId,
                        ImmutableMap.of(taxableItem1.getId(), ImmutableList.of(adjustmentItem11, adjustmentItem12),
                                        taxableItem2.getId(), ImmutableList.of(adjustmentItem21)),
                        taxResultS2,
                        new DateTime(DateTimeZone.UTC),
                        kbTenantId);
        // Error
        dao.addResponse(kbAccountId,
                        kbInvoiceId,
                        ImmutableMap.of(),
                        "VertexErrors response",
                        new DateTime(DateTimeZone.UTC),
                        kbTenantId);
        // Other invoice
        dao.addResponse(kbAccountId,
                        UUID.randomUUID(),
                        ImmutableMap.of(),
                        new ApiSuccessResponseTransactionResponseType(),
                        new DateTime(DateTimeZone.UTC),
                        kbTenantId);

        final List<VertexResponsesRecord> responses = dao.getSuccessfulResponses(kbInvoiceId, kbTenantId);
        Assert.assertEquals(responses.size(), 2);
        Assert.assertEquals(responses.get(0).getTotalAmount().doubleValue(), taxResultS1.getData().getTotal().doubleValue());
        Assert.assertEquals(responses.get(1).getTotalAmount().doubleValue(), taxResultS2.getData().getTotal().doubleValue());

        final Map<UUID, Set<UUID>> kbInvoiceItems = dao.getTaxedItemsWithAdjustments(responses);
        Assert.assertEquals(kbInvoiceItems.size(), 2);
        Assert.assertEquals(kbInvoiceItems.get(taxableItem1.getId()).size(), 2);
        Assert.assertTrue(kbInvoiceItems.get(taxableItem1.getId()).contains(adjustmentItem11.getId()));
        Assert.assertTrue(kbInvoiceItems.get(taxableItem1.getId()).contains(adjustmentItem12.getId()));
        Assert.assertEquals(kbInvoiceItems.get(taxableItem2.getId()).size(), 1);
        Assert.assertTrue(kbInvoiceItems.get(taxableItem2.getId()).contains(adjustmentItem21.getId()));
    }
}
