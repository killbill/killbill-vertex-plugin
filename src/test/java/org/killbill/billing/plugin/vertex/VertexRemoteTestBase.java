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

package org.killbill.billing.plugin.vertex;

import java.util.Properties;

import org.killbill.billing.plugin.TestUtils;
import org.killbill.billing.plugin.vertex.dao.VertexDao;
import org.killbill.billing.plugin.vertex.gen.ApiClient;
import org.killbill.billing.plugin.vertex.gen.client.CalculateTaxApi;
import org.killbill.billing.plugin.vertex.oauth.OAuthClient;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_CLIENT_ID_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_CLIENT_SECRET_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_URL_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_SKIP;

public abstract class VertexRemoteTestBase {

    // To run these tests, you need a properties file in the classpath (e.g. src/test/resources/vertex.properties)
    // See README.md for details on the required properties
    private static final String VERTEX_PROPERTIES = "vertex.properties";

    protected CalculateTaxApi calculateTaxApi;
    protected VertexDao dao;

    @BeforeSuite(groups = {"slow", "integration"})
    public void setUpBeforeSuite() throws Exception {
        EmbeddedDbHelper.instance().startDb();
    }

    @BeforeMethod(groups = {"slow", "integration"})
    public void setUpBeforeMethod() throws Exception {
        EmbeddedDbHelper.instance().resetDB();
        dao = new VertexDao(EmbeddedDbHelper.instance().getDataSource());
    }

    @BeforeMethod(groups = "integration")
    public void setUpBeforeMethod2() throws Exception {
        Properties properties = new Properties();
        try {
            properties = TestUtils.loadProperties(VERTEX_PROPERTIES);
        } catch (final RuntimeException ignored) {
            // Look up environment variables instead
            properties.put(VERTEX_OSERIES_URL_PROPERTY, System.getenv("VERTEX_URL"));
            properties.put(VERTEX_OSERIES_CLIENT_ID_PROPERTY, System.getenv("VERTEX_CLIENT_ID"));
            properties.put(VERTEX_OSERIES_CLIENT_SECRET_PROPERTY, System.getenv("VERTEX_SECRET_ID"));
            properties.put(VERTEX_SKIP, System.getenv(VERTEX_SKIP));
        }

        buildCalculateTaxApi(properties);
    }

    @AfterSuite(groups = {"slow", "integration"})
    public void tearDownAfterSuite() throws Exception {
        EmbeddedDbHelper.instance().stopDB();
    }

    private void buildCalculateTaxApi(final Properties properties) {
        String url = properties.getProperty(VERTEX_OSERIES_URL_PROPERTY);
        String clientId = properties.getProperty(VERTEX_OSERIES_CLIENT_ID_PROPERTY);
        String clientSecret = properties.getProperty(VERTEX_OSERIES_CLIENT_SECRET_PROPERTY);

        OAuthClient oAuthClient = new OAuthClient();

        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(url + "/vertex-ws/");
        String token = oAuthClient.getToken(url, clientId, clientSecret).getAccessToken();
        apiClient.setAccessToken(token);
        calculateTaxApi = new CalculateTaxApi(apiClient);
    }

}
