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

public class VertexConfigProperties {

    public static final String PROPERTY_PREFIX = "org.killbill.billing.plugin.vertex.";

    public static final String VERTEX_OSERIES_URL_PROPERTY = PROPERTY_PREFIX + "url";
    public static final String VERTEX_OSERIES_CLIENT_ID_PROPERTY = PROPERTY_PREFIX + "clientId";
    public static final String VERTEX_OSERIES_CLIENT_SECRET_PROPERTY = PROPERTY_PREFIX + "clientSecret";

    public static final String VERTEX_OSERIES_COMPANY_NAME_PROPERTY = PROPERTY_PREFIX + "companyName";
    public static final String VERTEX_OSERIES_COMPANY_DIVISION_PROPERTY = PROPERTY_PREFIX + "companyDivision";
    public static final String VERTEX_ADJUSTMENTS_LENIENT_MODE_PROPERTY = PROPERTY_PREFIX + "adjustments.lenientMode";
}
