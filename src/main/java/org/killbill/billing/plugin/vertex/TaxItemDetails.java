/*
 * Copyright 2020-2024 Equinix, Inc
 * Copyright 2020-2024 The Billing Project, LLC
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

import java.math.BigDecimal;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.killbill.billing.invoice.usage.details.UsageConsumableInArrearAggregate;
import org.killbill.billing.invoice.usage.details.UsageConsumableInArrearTierUnitAggregate;

/**
 * Wrapper to add taxRate details into itemDetails
 */
public class TaxItemDetails {

    private final UsageConsumableInArrearAggregate usageDetail;
    private final Double taxRate;

    public TaxItemDetails(@Nonnull final Double taxRate, @Nullable final UsageConsumableInArrearAggregate usageDetail) {
        this.usageDetail = usageDetail;
        this.taxRate = taxRate;
    }

    public List<UsageConsumableInArrearTierUnitAggregate> getTierDetails() {
        return usageDetail != null ? usageDetail.getTierDetails() : null;
    }

    public BigDecimal getAmount() {
        return usageDetail != null ? usageDetail.getAmount() : null;
    }

    public Double getTaxRate() {
        return taxRate;
    }
}