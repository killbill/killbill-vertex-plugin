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

import java.util.Properties;

import org.jooq.tools.StringUtils;
import org.killbill.billing.plugin.vertex.gen.ApiClient;
import org.killbill.billing.plugin.vertex.gen.ApiException;
import org.killbill.billing.plugin.vertex.gen.client.CalculateTaxApi;
import org.killbill.billing.plugin.vertex.gen.client.TaxAreaLookupApi;
import org.killbill.billing.plugin.vertex.gen.client.TransactionApi;
import org.killbill.billing.plugin.vertex.gen.client.model.AddressLookupRequestType;
import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessRemoveTransactionResponseType;
import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessResponseTaxAreaLookupResponseType;
import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessResponseTransactionResponseType;
import org.killbill.billing.plugin.vertex.gen.client.model.SaleRequestType;
import org.killbill.billing.plugin.vertex.oauth.OAuthClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_CLIENT_ID_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_CLIENT_SECRET_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_COMPANY_DIVISION_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_COMPANY_NAME_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_URL_PROPERTY;

public class VertexApiClient {

    public static final String NOT_CONFIGURED_MSG = "VertexApiClient is not configured: url, clientId and clientSecret are required";
    private static final Logger logger = LoggerFactory.getLogger(VertexApiClient.class);

    private final CalculateTaxApi calculateTaxApi;
    private final TransactionApi transactionApi;
    private final TaxAreaLookupApi taxAreaLookupApi;

    private final String companyName;
    private final String companyDivision;

    public VertexApiClient(final Properties properties) {
        final String url = properties.getProperty(VERTEX_OSERIES_URL_PROPERTY);
        final String clientId = properties.getProperty(VERTEX_OSERIES_CLIENT_ID_PROPERTY);
        final String clientSecret = properties.getProperty(VERTEX_OSERIES_CLIENT_SECRET_PROPERTY);

        this.companyName = properties.getProperty(VERTEX_OSERIES_COMPANY_NAME_PROPERTY);
        this.companyDivision = properties.getProperty(VERTEX_OSERIES_COMPANY_DIVISION_PROPERTY);

        final ApiClient apiClient = initApiClient(url, clientId, clientSecret);
        this.calculateTaxApi = apiClient != null ? new CalculateTaxApi(apiClient) : null;
        this.transactionApi = apiClient != null ? new TransactionApi(apiClient) : null;
        this.taxAreaLookupApi = apiClient != null ? new TaxAreaLookupApi(apiClient) : null;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getCompanyDivision() {
        return companyDivision;
    }

    public ApiSuccessResponseTransactionResponseType calculateTaxes(SaleRequestType taxRequest) throws ApiException {
        if (calculateTaxApi == null) {
            throw new IllegalStateException(NOT_CONFIGURED_MSG);
        }
        return calculateTaxApi.salePost(taxRequest);
    }

    public ApiSuccessRemoveTransactionResponseType deleteTransaction(final String id) throws ApiException {
        if (transactionApi == null) {
            throw new IllegalStateException(NOT_CONFIGURED_MSG);
        }
        return transactionApi.deleteTransaction(id);
    }

    public ApiSuccessResponseTaxAreaLookupResponseType lookUpTaxAreaByAddress(AddressLookupRequestType addressLookupRequest) throws ApiException {
        if (transactionApi == null) {
            throw new IllegalStateException(NOT_CONFIGURED_MSG);
        }
        return this.taxAreaLookupApi.addressLookupPost(addressLookupRequest);
    }

    private ApiClient initApiClient(final String url, final String clientId, final String clientSecret) {
        if (StringUtils.isBlank(url) || StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
            logger.warn(NOT_CONFIGURED_MSG);
            return null;
        }

        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(url + "/vertex-ws/");

        OAuthClient oAuthClient = new OAuthClient();
        final String token = oAuthClient.getToken(url, clientId, clientSecret).getAccessToken();

        apiClient.setAccessToken(token);

        return apiClient;
    }
}
