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

import org.killbill.billing.plugin.vertex.gen.ApiException;
import org.killbill.billing.plugin.vertex.gen.client.model.AddressLookupRequestType;
import org.killbill.billing.plugin.vertex.gen.client.model.SaleRequestType;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

//Currently only unhappy path is covered
public class VertexApiClientTest {

    @Mock
    private Properties properties;

    @InjectMocks
    private VertexApiClient vertexApiClient;

    @BeforeClass(groups = "fast")
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test(expectedExceptions = IllegalStateException.class, groups = "fast")
    public void testCalculateTaxes() throws ApiException {
        vertexApiClient.calculateTaxes(Mockito.mock(SaleRequestType.class));
    }

    @Test(expectedExceptions = IllegalStateException.class, groups = "fast")
    public void testDeleteTransaction() throws ApiException {
        vertexApiClient.deleteTransaction("id");
    }

    @Test(expectedExceptions = IllegalStateException.class, groups = "fast")
    public void testLookUpTaxAreaByAddress() throws ApiException {
        vertexApiClient.lookUpTaxAreaByAddress(Mockito.mock(AddressLookupRequestType.class));
    }
}
