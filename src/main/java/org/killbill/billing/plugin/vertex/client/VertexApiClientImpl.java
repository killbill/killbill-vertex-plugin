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

import java.util.Properties;

import org.killbill.billing.plugin.vertex.gen.ApiClient;
import org.killbill.billing.plugin.vertex.gen.ApiException;
import org.killbill.billing.plugin.vertex.gen.client.CalculateTaxApi;
import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessResponseTransactionResponseType;
import org.killbill.billing.plugin.vertex.gen.client.model.SaleRequestType;
import org.killbill.billing.plugin.vertex.oauth.OAuthClient;

import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_CLIENT_ID_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_CLIENT_SECRET_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_COMPANY_DIVISION_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_COMPANY_NAME_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_URL_PROPERTY;

public class VertexApiClientImpl implements VertexApiClient {

    private final OAuthClient oAuthClient;
    private final CalculateTaxApi calculateTaxApi;
    private final String companyName;
    private final String companyDivision;

    public VertexApiClientImpl(final Properties properties) {
        final String url = properties.getProperty(VERTEX_OSERIES_URL_PROPERTY);
        final String clientId = properties.getProperty(VERTEX_OSERIES_CLIENT_ID_PROPERTY);
        final String clientSecret = properties.getProperty(VERTEX_OSERIES_CLIENT_SECRET_PROPERTY);

        this.oAuthClient = new OAuthClient();
        this.calculateTaxApi = initApiClient(url, clientId, clientSecret);

        this.companyName = properties.getProperty(VERTEX_OSERIES_COMPANY_NAME_PROPERTY);
        this.companyDivision = properties.getProperty(VERTEX_OSERIES_COMPANY_DIVISION_PROPERTY);
    }

    @Override
    public String getCompanyName() {
        return companyName;
    }

    @Override
    public String getCompanyDivision() {
        return companyDivision;
    }

    @Override
    public ApiSuccessResponseTransactionResponseType calculateTaxes(SaleRequestType taxRequest) throws ApiException {
        return calculateTaxApi.salePost(taxRequest);
    }

    private CalculateTaxApi initApiClient(final String url, final String clientId, final String clientSecret) {
        final String token = oAuthClient.getToken(url, clientId, clientSecret).getAccessToken();

        ApiClient apiClient = new ApiClient();
        apiClient.setAccessToken(token);
        apiClient.setBasePath(url + "/vertex-ws/");

        return new CalculateTaxApi(apiClient);
    }
}
