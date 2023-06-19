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

package org.killbill.billing.plugin.vertex.dao;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessResponseTransactionResponseTypeData;
import org.killbill.billing.plugin.vertex.gen.client.model.CustomerType;
import org.killbill.billing.plugin.vertex.gen.client.model.Jurisdiction;
import org.killbill.billing.plugin.vertex.gen.client.model.LocationType;
import org.killbill.billing.plugin.vertex.gen.client.model.OwnerResponseLineItemType;
import org.killbill.billing.plugin.vertex.gen.client.model.PhysicalLocation;
import org.killbill.billing.plugin.vertex.gen.client.model.TaxRegistrationType;
import org.killbill.billing.plugin.vertex.gen.client.model.TaxesType;

import com.google.common.collect.ImmutableList;

public class VertexResponseDataExtractor {

    static class TaxInfo {

        private final Double calculatedTax;
        private final Double effectiveRate;
        private final Double exempt;
        private final Jurisdiction jurisdiction;
        private final Double nominalRate;
        private final Double nonTaxable;
        private final Double taxable;

        TaxInfo(TaxesType taxesType) {
            this.calculatedTax = taxesType.getCalculatedTax();
            this.effectiveRate = taxesType.getEffectiveRate();
            this.exempt = taxesType.getExempt();
            this.jurisdiction = taxesType.getJurisdiction();
            this.nominalRate = taxesType.getNominalRate();
            this.nonTaxable = taxesType.getNonTaxable();
            this.taxable = taxesType.getTaxable();
        }

        public Double getCalculatedTax() {
            return calculatedTax;
        }

        public Double getEffectiveRate() {
            return effectiveRate;
        }

        public Double getExempt() {
            return exempt;
        }

        public Jurisdiction getJurisdiction() {
            return jurisdiction;
        }

        public Double getNominalRate() {
            return nominalRate;
        }

        public Double getNonTaxable() {
            return nonTaxable;
        }

        public Double getTaxable() {
            return taxable;
        }
    }

    static class AddressInfo {

        private final String city;
        private final String streetAddress1;
        private final String streetAddress2;
        private final String mainDivision;
        private final String postalCode;
        private final String country;
        private final String taxAreaId;

        AddressInfo(final LocationType locationType) {
            this.city = locationType.getCity();
            this.mainDivision = locationType.getMainDivision();
            this.streetAddress1 = locationType.getStreetAddress1();
            this.streetAddress2 = locationType.getStreetAddress2();
            this.country = locationType.getCountry();
            this.postalCode = locationType.getPostalCode();
            this.taxAreaId = locationType.getTaxAreaId();
        }

        AddressInfo(final PhysicalLocation physicalLocation) {
            this.city = physicalLocation.getCity();
            this.mainDivision = physicalLocation.getMainDivision();
            this.streetAddress1 = physicalLocation.getStreetAddress1();
            this.streetAddress2 = physicalLocation.getStreetAddress2();
            this.country = physicalLocation.getCountry();
            this.postalCode = physicalLocation.getPostalCode();
            this.taxAreaId = physicalLocation.getTaxAreaId();
        }

        public String getCity() {
            return city;
        }

        public String getStreetAddress1() {
            return streetAddress1;
        }

        public String getStreetAddress2() {
            return streetAddress2;
        }

        public String getMainDivision() {
            return mainDivision;
        }

        public String getPostalCode() {
            return postalCode;
        }

        public String getCountry() {
            return country;
        }

        public String getTaxAreaId() {
            return taxAreaId;
        }
    }

    List<TaxInfo> getTransactionSummary(final List<OwnerResponseLineItemType> lineItems) {
        return lineItems != null
               ? lineItems.stream()
                          .map(OwnerResponseLineItemType::getTaxes)
                          .filter(Objects::nonNull)
                          .flatMap(Collection::stream)
                          .map(TaxInfo::new)
                          .collect(Collectors.toList())
               : ImmutableList.of();
    }

    Map<String, Object> getAdditionalData(final ApiSuccessResponseTransactionResponseTypeData response) {
        final Map<String, Object> additionalData = new HashMap<>();

        additionalData.put("roundAtLineLevel", response.getRoundAtLineLevel()); //todo: add additional useful information if needed

        return additionalData;
    }

    List<AddressInfo> getAddresses(CustomerType customer) {
        if (customer == null) {
            return ImmutableList.of();
        }

        final List<AddressInfo> addresses = new LinkedList<>();

        if (customer.getDestination() != null) {
            addresses.add(new AddressInfo(customer.getDestination()));
        }

        if (customer.getTaxRegistrations() != null && customer.getTaxRegistrations().size() > 0) {
            addresses.addAll(customer.getTaxRegistrations()
                                     .stream()
                                     .map(TaxRegistrationType::getPhysicalLocations)
                                     .filter(Objects::nonNull)
                                     .flatMap(Collection::stream)
                                     .map(AddressInfo::new)
                                     .collect(Collectors.toList()));
        }

        return addresses;
    }

    BigDecimal getTotalTaxCalculated(final List<OwnerResponseLineItemType> lineItems) {
        return lineItems != null
               ? lineItems.stream()
                          .filter(Objects::nonNull)
                          .filter(lineItem -> lineItem.getTaxes() != null)
                          .flatMap(lineItem -> lineItem.getTaxes().stream())
                          .map(TaxesType::getCalculatedTax)
                          .filter(Objects::nonNull)
                          .map(BigDecimal::valueOf)
                          .reduce(BigDecimal.ZERO, BigDecimal::add)
               : BigDecimal.ZERO;
    }

    BigDecimal getTotalTaxExempt(final List<OwnerResponseLineItemType> lineItems) {
        return lineItems != null
               ? lineItems.stream()
                          .filter(Objects::nonNull)
                          .filter(lineItem -> lineItem.getTaxes() != null)
                          .flatMap(lineItem -> lineItem.getTaxes().stream())
                          .map(TaxesType::getExempt)
                          .filter(Objects::nonNull)
                          .map(BigDecimal::valueOf)
                          .reduce(BigDecimal.ZERO, BigDecimal::add)
               : BigDecimal.ZERO;
    }

    BigDecimal getTotalTaxable(final List<OwnerResponseLineItemType> lineItems) {
        return lineItems != null
               ? lineItems.stream()
                          .filter(Objects::nonNull)
                          .filter(lineItem -> lineItem.getTaxes() != null)
                          .map(lineItem -> lineItem.getTaxes().get(0))      //taxable field is the same for all taxes in the lineItem, so get the first one
                          .map(TaxesType::getTaxable)
                          .filter(Objects::nonNull)
                          .map(BigDecimal::valueOf)
                          .reduce(BigDecimal.ZERO, BigDecimal::add)
               : BigDecimal.ZERO;
    }
}
