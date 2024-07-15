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

package org.killbill.billing.plugin.vertex.dao;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.sql.DataSource;

import org.joda.time.DateTime;
import org.jooq.impl.DSL;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.dao.PluginDao;
import org.killbill.billing.plugin.vertex.gen.dao.model.tables.records.VertexResponsesRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

import static org.killbill.billing.plugin.vertex.gen.dao.model.Tables.VERTEX_RESPONSES;

public class VertexDao extends PluginDao {

    private static final Logger logger = LoggerFactory.getLogger(VertexDao.class);
    private static final String SUCCESS = "SUCCESS";
    private static final String ERROR = "ERROR";

    static {
        objectMapper.registerModule(new JavaTimeModule());
    }

    public VertexDao(final DataSource dataSource) throws SQLException {
        super(dataSource);
    }

    // Success
    public void addResponse(final UUID kbAccountId,
                            final UUID kbInvoiceId,
                            final Map<UUID, Iterable<InvoiceItem>> kbInvoiceItems,
                            final VertexResponseDataExtractor vertexResponseDataExtractor,
                            final DateTime utcNow,
                            final UUID kbTenantId) throws SQLException {

        if (vertexResponseDataExtractor == null) {
            return;
        }

        execute(dataSource.getConnection(),
                (WithConnectionCallback<Void>) conn -> {
                    DSL.using(conn, dialect, settings)
                       .insertInto(VERTEX_RESPONSES,
                                   VERTEX_RESPONSES.KB_ACCOUNT_ID,
                                   VERTEX_RESPONSES.KB_INVOICE_ID,
                                   VERTEX_RESPONSES.KB_INVOICE_ITEM_IDS,
                                   VERTEX_RESPONSES.DOC_CODE,
                                   VERTEX_RESPONSES.DOC_DATE,
                                   VERTEX_RESPONSES.TIMESTAMP,
                                   VERTEX_RESPONSES.TOTAL_AMOUNT,
                                   VERTEX_RESPONSES.TOTAL_DISCOUNT,
                                   VERTEX_RESPONSES.TOTAL_EXEMPTION,
                                   VERTEX_RESPONSES.TOTAL_TAXABLE,
                                   VERTEX_RESPONSES.TOTAL_TAX,
                                   VERTEX_RESPONSES.TOTAL_TAX_CALCULATED,
                                   VERTEX_RESPONSES.TAX_DATE,
                                   VERTEX_RESPONSES.TAX_LINES,
                                   VERTEX_RESPONSES.TAX_SUMMARY,
                                   VERTEX_RESPONSES.TAX_ADDRESSES,
                                   VERTEX_RESPONSES.RESULT_CODE,
                                   VERTEX_RESPONSES.MESSAGES,
                                   VERTEX_RESPONSES.ADDITIONAL_DATA,
                                   VERTEX_RESPONSES.CREATED_DATE,
                                   VERTEX_RESPONSES.KB_TENANT_ID)
                       .values(kbAccountId.toString(),
                               kbInvoiceId.toString(),
                               kbInvoiceItemsIdsAsString(kbInvoiceItems),
                               vertexResponseDataExtractor.getDocumentCode(),
                               vertexResponseDataExtractor.getDocumentDate(),
                               null,
                               vertexResponseDataExtractor.getTotalAmount(),
                               vertexResponseDataExtractor.getTotalDiscount(),
                               vertexResponseDataExtractor.getTotalTaxExempt(),
                               vertexResponseDataExtractor.getTotalTaxable(),
                               vertexResponseDataExtractor.getTotalTax(),
                               vertexResponseDataExtractor.getTotalTaxCalculated(),
                               vertexResponseDataExtractor.getTaxDate(),
                               asString(vertexResponseDataExtractor.getTaxLines()),
                               asString(vertexResponseDataExtractor.getTaxSummary()),
                               asString(vertexResponseDataExtractor.getAddresses()),
                               SUCCESS,
                               null,
                               null,
                               toLocalDateTime(utcNow),
                               kbTenantId.toString())
                       .execute();
                    return null;
                });
    }

    // !Success
    public void addResponse(final UUID kbAccountId,
                            final UUID kbInvoiceId,
                            final Map<UUID, Iterable<InvoiceItem>> kbInvoiceItems,
                            final String errors,
                            final DateTime utcNow,
                            final UUID kbTenantId) throws SQLException {
        execute(dataSource.getConnection(),
                (WithConnectionCallback<Void>) conn -> {
                    DSL.using(conn, dialect, settings)
                       .insertInto(VERTEX_RESPONSES,
                                   VERTEX_RESPONSES.KB_ACCOUNT_ID,
                                   VERTEX_RESPONSES.KB_INVOICE_ID,
                                   VERTEX_RESPONSES.KB_INVOICE_ITEM_IDS,
                                   VERTEX_RESPONSES.RESULT_CODE,
                                   VERTEX_RESPONSES.ADDITIONAL_DATA,
                                   VERTEX_RESPONSES.CREATED_DATE,
                                   VERTEX_RESPONSES.KB_TENANT_ID)
                       .values(kbAccountId.toString(),
                               kbInvoiceId.toString(),
                               kbInvoiceItemsIdsAsString(kbInvoiceItems),
                               ERROR,
                               errors,
                               toLocalDateTime(utcNow),
                               kbTenantId.toString())
                       .execute();
                    return null;
                });
    }

    public List<VertexResponsesRecord> getSuccessfulResponses(final UUID invoiceId, final UUID kbTenantId) throws SQLException {
        return execute(dataSource.getConnection(),
                       (WithConnectionCallback<List<VertexResponsesRecord>>) conn ->
                               DSL.using(conn, dialect, settings)
                                  .selectFrom(VERTEX_RESPONSES)
                                  .where(VERTEX_RESPONSES.KB_INVOICE_ID.equal(invoiceId.toString()))
                                  .and(VERTEX_RESPONSES.RESULT_CODE.equal(SUCCESS))
                                  .and(VERTEX_RESPONSES.KB_TENANT_ID.equal(kbTenantId.toString()))
                                  .orderBy(VERTEX_RESPONSES.RECORD_ID.asc())
                                  .fetch());
    }

    public Map<UUID, Set<UUID>> getTaxedItemsWithAdjustments(final Iterable<VertexResponsesRecord> responses) {
        final Map<UUID, Set<UUID>> kbInvoiceItemsIds = new HashMap<>();
        for (final VertexResponsesRecord response : responses) {
            try {
                kbInvoiceItemsIdsFromString(response.getKbInvoiceItemIds(), kbInvoiceItemsIds);
            } catch (final IOException e) {
                logger.warn("Corrupted entry for response record_id {}: {}", response.getRecordId(), response.getKbInvoiceItemIds());
            }
        }

        return kbInvoiceItemsIds;
    }

    private void kbInvoiceItemsIdsFromString(@Nullable final String kbInvoiceItemsIdsAsString, final Map<UUID, Set<UUID>> kbInvoiceItemsIds) throws IOException {
        if (Strings.emptyToNull(kbInvoiceItemsIdsAsString) != null) {
            final Map<UUID, Set<UUID>> kbInvoiceItemsIdsAsMap = objectMapper.readValue(kbInvoiceItemsIdsAsString, new TypeReference<Map<UUID, Set<UUID>>>() {});
            for (final Entry<UUID, Set<UUID>> entry : kbInvoiceItemsIdsAsMap.entrySet()) {
                kbInvoiceItemsIds.computeIfAbsent(entry.getKey(), k -> new HashSet<>());
                kbInvoiceItemsIds.get(entry.getKey()).addAll(entry.getValue());
            }
        }
    }

    private String kbInvoiceItemsIdsAsString(final Map<UUID, Iterable<InvoiceItem>> kbInvoiceItems) throws SQLException {
        final Map<UUID, Set<UUID>> kbInvoiceItemsIds =
                Maps.transformValues(kbInvoiceItems,
                                     invoiceItems -> {
                                         final Set<UUID> invoiceItemIds = new HashSet<>();
                                         if (invoiceItems == null) {
                                             return invoiceItemIds;
                                         }
                                         for (final InvoiceItem invoiceItem : invoiceItems) {
                                             invoiceItemIds.add(invoiceItem.getId());
                                         }
                                         return invoiceItemIds;
                                     });
        return asString(kbInvoiceItemsIds);
    }
}
