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

package org.killbill.billing.plugin.vertex.client;

import org.killbill.billing.plugin.vertex.gen.ApiException;
import org.killbill.billing.plugin.vertex.gen.client.TransactionApi;
import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessRemoveTransactionResponseType;

public class VertexTransactionApiClientImpl implements VertexTransactionApiClient {

    private final TransactionApi transactionApi;

    public VertexTransactionApiClientImpl(TransactionApi transactionApi) {
        this.transactionApi = transactionApi;
    }

    @Override
    public ApiSuccessRemoveTransactionResponseType deleteTransaction(final String id) throws ApiException {
        return transactionApi.deleteTransaction(id);
    }
}
