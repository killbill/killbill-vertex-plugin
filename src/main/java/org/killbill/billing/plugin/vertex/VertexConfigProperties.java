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

import com.google.common.base.Strings;

import java.util.Map;
import java.util.Properties;

public class VertexConfigProperties {

    public static final String PROPERTY_PREFIX = "org.killbill.billing.plugin.vertex.";

    private static final String ENTRY_DELIMITER = "|";
    private static final String KEY_VALUE_DELIMITER = "#";

    private final String region;

    public VertexConfigProperties(final Properties properties, final String region) {
        this.region = region;
    }

    private synchronized void refillMap(final Map<String, String> map, final String stringToSplit) {
        map.clear();
        if (!Strings.isNullOrEmpty(stringToSplit)) {
            for (final String entry : stringToSplit.split("\\" + ENTRY_DELIMITER)) {
                final String[] split = entry.split(KEY_VALUE_DELIMITER);
                if (split.length > 1) {
                    map.put(split[0], split[1]);
                }
            }
        }
    }
}