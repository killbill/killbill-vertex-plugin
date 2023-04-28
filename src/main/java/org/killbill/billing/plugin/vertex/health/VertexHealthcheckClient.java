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

package org.killbill.billing.plugin.vertex.health;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import org.jooq.tools.StringUtils;
import org.killbill.billing.plugin.vertex.gen.health.HealthCheckException;
import org.killbill.billing.plugin.vertex.gen.health.HealthCheckService;
import org.killbill.billing.plugin.vertex.gen.health.LoginType;
import org.killbill.billing.plugin.vertex.gen.health.PerformHealthCheckRequest;
import org.killbill.billing.plugin.vertex.gen.health.PerformHealthCheckResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_PASSWORD_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_URL_PROPERTY;
import static org.killbill.billing.plugin.vertex.VertexConfigProperties.VERTEX_OSERIES_USERNAME_PROPERTY;

public class VertexHealthcheckClient {

    private static final Logger logger = LoggerFactory.getLogger(VertexHealthcheckClient.class);
    private final HealthCheckService healthCheckService;
    private final PerformHealthCheckRequest healthCheckRequest;

    public VertexHealthcheckClient(final Properties properties) {
        final String url = properties.getProperty(VERTEX_OSERIES_URL_PROPERTY);
        final String username = properties.getProperty(VERTEX_OSERIES_USERNAME_PROPERTY);
        final String password = properties.getProperty(VERTEX_OSERIES_PASSWORD_PROPERTY);

        this.healthCheckService = buildHealthCheckService(url);
        this.healthCheckRequest = buildHealthcheckRequest(username, password);
    }

    public PerformHealthCheckResponseType performHealthCheck() throws HealthCheckException {
        if (healthCheckService == null || healthCheckRequest == null) {
            throw new IllegalStateException("VertexHealthcheckClient is not configured: url, username and password are required");
        }

        return healthCheckService.getHealthCheckPort().performHealthCheck(healthCheckRequest);
    }

    private HealthCheckService buildHealthCheckService(final String url) {
        if (StringUtils.isBlank(url)) {
            logger.warn("HealthCheckService is not configured: url is required");
            return null;
        }

        try {
            return new HealthCheckService(new URL(url + "/vertex-ws/adminservices/HealthCheck90"));
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    private PerformHealthCheckRequest buildHealthcheckRequest(final String username, final String password) {
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            logger.warn("PerformHealthCheckRequest is not configured: username and password are required");
            return null;
        }

        PerformHealthCheckRequest performHealthCheckRequest = new PerformHealthCheckRequest();

        LoginType login = new LoginType();
        login.setUserName(username);
        login.setPassword(password);
        performHealthCheckRequest.setLogin(login);

        return performHealthCheckRequest;
    }
}
