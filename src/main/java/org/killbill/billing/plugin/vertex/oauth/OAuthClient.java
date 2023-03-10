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

package org.killbill.billing.plugin.vertex.oauth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class OAuthClient {

    private final ObjectMapper objectMapper;

    public OAuthClient() {
        this.objectMapper = createObjectMapper();
    }

    public OAuthToken getToken(String url, String clientId, String clientSecret) {
        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost login = new HttpPost(url + "/oseries-auth/oauth/token");
            // form parameters.
            List<NameValuePair> nvps = new ArrayList<>();
            nvps.add(new BasicNameValuePair("grant_type", "client_credentials"));
            nvps.add(new BasicNameValuePair("client_id", clientId));
            nvps.add(new BasicNameValuePair("client_secret", clientSecret));
            login.setEntity(new UrlEncodedFormEntity(nvps));

            return httpclient.execute(login, response -> {
                if (response.getCode() == 200) {
                    return objectMapper.readValue(response.getEntity().getContent(), OAuthToken.class);
                } else {
                    throw new RuntimeException("Request failed with http code " + response.getCode()
                                               + ". Response data: " + EntityUtils.toString(response.getEntity()));
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ObjectMapper createObjectMapper() {
        return JsonMapper.builder()
                         .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                         .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                         .serializationInclusion(JsonInclude.Include.NON_NULL)
                         .build();
    }
}
