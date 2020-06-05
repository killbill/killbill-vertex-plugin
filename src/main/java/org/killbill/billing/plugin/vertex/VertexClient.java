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

import java.security.GeneralSecurityException;
import java.util.Properties;

import org.killbill.billing.plugin.util.http.HttpClient;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;

public class VertexClient extends HttpClient {

    public VertexClient(final Properties properties) throws GeneralSecurityException {
        super(properties.getProperty(VertexConfigProperties.PROPERTY_PREFIX + "url"),
              null,
              null,
              properties.getProperty(VertexConfigProperties.PROPERTY_PREFIX + "proxyHost"),
              getIntegerProperty(properties, "proxyPort"),
              getBooleanProperty(properties, "strictSSL"),
              MoreObjects.firstNonNull(getIntegerProperty(properties, "connectTimeout"), 10000),
              MoreObjects.firstNonNull(getIntegerProperty(properties, "readTimeout"), 60000));
    }

    private static Integer getIntegerProperty(final Properties properties, final String key) {
        final String property = properties.getProperty(VertexConfigProperties.PROPERTY_PREFIX + key);
        return Strings.isNullOrEmpty(property) ? null : Integer.valueOf(property);
    }

    private static Boolean getBooleanProperty(final Properties properties, final String key) {
        final String property = properties.getProperty(VertexConfigProperties.PROPERTY_PREFIX + key);
        return Strings.isNullOrEmpty(property) ? true : Boolean.valueOf(property);
    }
}
