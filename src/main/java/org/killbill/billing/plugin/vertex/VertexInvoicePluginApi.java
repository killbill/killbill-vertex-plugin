/*
 * Copyright 2020 Equinix, Inc
 * Copyright 2020 The Billing Project, LLC
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
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.money.CurrencyUnit;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillLogService;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.invoice.PluginInvoiceItem;
import org.killbill.billing.plugin.api.invoice.PluginInvoicePluginApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.entity.Entity;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class VertexInvoicePluginApi extends PluginInvoicePluginApi {

    private static final Logger log = LoggerFactory.getLogger(VertexInvoicePluginApi.class);

    private final VertexConfigPropertiesConfigurationHandler vertexConfigPropertiesConfigurationHandler;

    public VertexInvoicePluginApi(final VertexConfigPropertiesConfigurationHandler vertexConfigPropertiesConfigurationHandler,
                                  final OSGIKillbillAPI killbillAPI,
                                  final OSGIConfigPropertiesService configProperties,
                                  final OSGIKillbillLogService logService,
                                  final Clock clock) {
        super(killbillAPI, configProperties, logService, clock);
        this.vertexConfigPropertiesConfigurationHandler = vertexConfigPropertiesConfigurationHandler;
    }

    @Override
    public List<InvoiceItem> getAdditionalInvoiceItems(final Invoice newInvoice,
                                                       final boolean dryRun,
                                                       final Iterable<PluginProperty> properties,
                                                       final CallContext context) {
        final List<InvoiceItem> newItems = extractNewInvoiceItems(newInvoice, context);
        final VertexClient vertexClient = vertexConfigPropertiesConfigurationHandler.getConfigurable(context.getTenantId());

        final List<InvoiceItem> additionalInvoiceItems = new LinkedList<InvoiceItem>();
        for (final InvoiceItem newItem : newItems) {
            // TODO Do Tax Request
            // TODO Save Tax Response
            final BigDecimal taxAmount = BigDecimal.ZERO;

            if (BigDecimal.ZERO.compareTo(taxAmount) == 0) {
                continue;
            }

            if (isTaxableItem(newItem)) {
                additionalInvoiceItems.add(buildTaxItem(newItem, newInvoice.getId(), taxAmount, "Tax"));
            } else if (isAdjustmentItem(newItem)) {
                additionalInvoiceItems.add(buildTaxItem(newItem, newInvoice.getId(), taxAmount, "Tax adj"));
            }
        }

        return additionalInvoiceItems;
    }

    private InvoiceItem buildTaxItem(final InvoiceItem originalItem,
                                     final UUID invoiceId,
                                     final BigDecimal taxAmount,
                                     @Nullable final String description) {
        return PluginInvoiceItem.createTaxItem(originalItem,
                                               invoiceId,
                                               originalItem.getStartDate(),
                                               originalItem.getEndDate(),
                                               taxAmount,
                                               MoreObjects.firstNonNull(description, "Tax"));
    }

    // TODO This does not always work: https://github.com/killbill/killbill/issues/265
    private List<InvoiceItem> extractNewInvoiceItems(final Invoice newInvoice, final CallContext context) {
        try {
            final Invoice existingInvoice = killbillAPI.getInvoiceUserApi().getInvoice(newInvoice.getId(), context);
            final Set<UUID> existingItemIds = ImmutableSet.<UUID>copyOf(Iterables.<InvoiceItem, UUID>transform(existingInvoice.getInvoiceItems(),
                                                                                                               Entity::getId));
            return ImmutableList.copyOf(Iterables.<InvoiceItem>filter(newInvoice.getInvoiceItems(),
                                                                      input -> !existingItemIds.contains(Objects.requireNonNull(input).getId())));
        } catch (final InvoiceApiException e) {
            if (e.getCode() == ErrorCode.INVOICE_NOT_FOUND.getCode()) {
                return newInvoice.getInvoiceItems();
            } else {
                throw new RuntimeException(e);
            }
        } catch (IllegalStateException e) {
            // :facepalm: Our code does a weird conversion which creates this exception instead of the expected InvoiceApiException#INVOICE_NOT_FOUND
            // See https://github.com/killbill/killbill/blob/killbill-0.21.5/invoice/src/main/java/org/killbill/billing/invoice/api/user/DefaultInvoiceUserApi.java#L239
            if (e.getMessage() != null && e.getMessage().endsWith("INVOICE doesn't exist!")) {
                return newInvoice.getInvoiceItems();
            } else {
                throw new RuntimeException(e);
            }
        }
    }
}