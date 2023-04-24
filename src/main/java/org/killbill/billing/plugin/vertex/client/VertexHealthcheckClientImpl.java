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

import org.killbill.billing.plugin.vertex.gen.health.HealthCheckException;
import org.killbill.billing.plugin.vertex.gen.health.HealthCheckService;
import org.killbill.billing.plugin.vertex.gen.health.LoginType;
import org.killbill.billing.plugin.vertex.gen.health.PerformHealthCheckRequest;
import org.killbill.billing.plugin.vertex.gen.health.PerformHealthCheckResponseType;

public class VertexHealthcheckClientImpl implements VertexHealthcheckClient{

    private final HealthCheckService healthCheckService;
    private final PerformHealthCheckRequest healthCheckRequest;

    public VertexHealthcheckClientImpl(final HealthCheckService healthCheckService, final String username, final String password) {
        this.healthCheckService = healthCheckService;
        this.healthCheckRequest = buildHealthcheckRequest(username, password);
    }

    public PerformHealthCheckResponseType healthCheck() throws HealthCheckException {
        return healthCheckService.getHealthCheckPort().performHealthCheck(healthCheckRequest);
    }

    private PerformHealthCheckRequest buildHealthcheckRequest(final String username, final String password) {
        PerformHealthCheckRequest healthCheckRequest = new PerformHealthCheckRequest();

        LoginType login = new LoginType();
        login.setUserName(username);
        login.setPassword(password);
        healthCheckRequest.setLogin(login);

        return healthCheckRequest;
    }
}
