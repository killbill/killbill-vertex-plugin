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

import org.killbill.billing.plugin.vertex.base.VertexRemoteTestBase;
import org.testng.Assert;
import org.testng.annotations.Test;

public class OAuthClientITest extends VertexRemoteTestBase {

    @Test(groups = "integration")
    public void test() {
        OAuthClient client = new OAuthClient();

        String token = client.getToken(getUrl(), getClientId(), getClientSecret()).getAccessToken();
        Assert.assertNotNull(token);
    }

}
