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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.invoice.PluginTaxCalculator;
import org.killbill.billing.plugin.vertex.dao.VertexDao;
import org.killbill.billing.plugin.vertex.gen.ApiException;
import org.killbill.billing.plugin.vertex.gen.dao.model.tables.records.VertexResponsesRecord;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;

public abstract class VertexCalculatorBase extends PluginTaxCalculator {

    private static final Logger logger = LoggerFactory.getLogger(VertexCalculatorBase.class);

    protected final VertexDao dao;
    protected final Clock clock;

    protected VertexCalculatorBase(final VertexDao dao, final Clock clock, final OSGIKillbillAPI osgiKillbillAPI) {
        super(osgiKillbillAPI);
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
            final String originalInvoiceReferenceCode = responsesForInvoice.isEmpty() ? null : responsesForInvoice.get(0).getDocCode();

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

    protected abstract Collection<InvoiceItem> buildInvoiceItems(final Account account,
                                                                 final Invoice newInvoice,
                                                                 final Invoice invoice,
                                                                 final Map<UUID, InvoiceItem> taxableItems,
                                                                 @Nullable final Map<UUID, List<InvoiceItem>> adjustmentItems,
                                                                 @Nullable final String originalInvoiceReferenceCode,
                                                                 final boolean dryRun,
                                                                 final Iterable<PluginProperty> pluginProperties,
                                                                 final UUID kbTenantId,
                                                                 final Map<UUID, Iterable<InvoiceItem>> kbInvoiceItems,
                                                                 final LocalDate utcToday) throws ApiException, SQLException;
}
