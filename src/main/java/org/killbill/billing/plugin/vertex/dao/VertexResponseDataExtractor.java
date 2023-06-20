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
import java.time.LocalDateTime;
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

import com.google.common.base.MoreObjects;
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
            this(locationType.getCity(),
                 locationType.getMainDivision(),
                 locationType.getStreetAddress1(),
                 locationType.getStreetAddress2(),
                 locationType.getCountry(),
                 locationType.getPostalCode(),
                 locationType.getTaxAreaId());
        }

        AddressInfo(final PhysicalLocation physicalLocation) {
            this(physicalLocation.getCity(),
                 physicalLocation.getMainDivision(),
                 physicalLocation.getStreetAddress1(),
                 physicalLocation.getStreetAddress2(),
                 physicalLocation.getCountry(),
                 physicalLocation.getPostalCode(),
                 physicalLocation.getTaxAreaId());
        }

        private AddressInfo(final String city, final String mainDivision, final String streetAddress1, final String streetAddress2, final String country, final String postalCode, final String taxAreaId) {
            this.city = city;
            this.mainDivision = mainDivision;
            this.streetAddress1 = streetAddress1;
            this.streetAddress2 = streetAddress2;
            this.country = country;
            this.postalCode = postalCode;
            this.taxAreaId = taxAreaId;
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

    private final ApiSuccessResponseTransactionResponseTypeData vertexResponseData;

    private final List<TaxInfo> transactionSummary;
    private final Map<String, Object> additionalData;
    private final List<AddressInfo> addresses;
    private final BigDecimal totalTaxCalculated;
    private final BigDecimal totalTaxExempt;
    private final BigDecimal totalTaxable;

    public VertexResponseDataExtractor(final ApiSuccessResponseTransactionResponseTypeData vertexResponseData) {
        this.vertexResponseData = vertexResponseData;

        this.transactionSummary = buildTransactionSummary();
        this.additionalData = buildAdditionalData();
        this.addresses = buildAddresses();

        this.totalTaxCalculated = computeTotalTaxCalculated();
        this.totalTaxExempt = computeTotalTaxExempt();
        this.totalTaxable = computeTotalTaxable();
    }

    Map<String, Object> getAdditionalData() {
        return additionalData;
    }

    List<TaxInfo> getTransactionSummary() {
        return transactionSummary;
    }

    List<AddressInfo> getAddresses() {
        return addresses;
    }

    BigDecimal getTotalTaxCalculated() {
        return totalTaxCalculated;
    }

    BigDecimal getTotalTaxExempt() {
        return totalTaxExempt;
    }

    BigDecimal getTotalTaxable() {
        return totalTaxable;
    }

    String getDocumentCode() {
        return vertexResponseData.getTransactionId();
    }

    LocalDateTime getDocumentDate() {
        return vertexResponseData.getDocumentDate() != null ? vertexResponseData.getDocumentDate().atStartOfDay() : null;
    }

    BigDecimal getTotalAmount() {
        return vertexResponseData.getTotal() != null ? BigDecimal.valueOf(vertexResponseData.getTotal()) : null;
    }

    BigDecimal getTotalDiscount() {
        return vertexResponseData.getDiscount() != null && vertexResponseData.getDiscount().getDiscountValue() != null
               ? BigDecimal.valueOf(vertexResponseData.getDiscount().getDiscountValue())
               : null;
    }

    BigDecimal getTotalTax() {
        return vertexResponseData.getTotalTax() != null ? BigDecimal.valueOf(vertexResponseData.getTotalTax()) : null;
    }

    LocalDateTime getTaxDate() {
        final LocalDateTime nullableTaxDate = vertexResponseData.getTaxPointDate() != null
                                      ? vertexResponseData.getTaxPointDate().atStartOfDay()
                                      : null;

        return MoreObjects.firstNonNull(nullableTaxDate, getDocumentDate());
    }

    List<OwnerResponseLineItemType> getTaxLines() {
        return vertexResponseData.getLineItems();
    }

    private List<TaxInfo> buildTransactionSummary() {
        final List<OwnerResponseLineItemType> lineItems = vertexResponseData.getLineItems();
        return lineItems != null
               ? lineItems.stream()
                          .filter(Objects::nonNull)
                          .map(OwnerResponseLineItemType::getTaxes)
                          .filter(Objects::nonNull)
                          .flatMap(Collection::stream)
                          .map(TaxInfo::new)
                          .collect(Collectors.toList())
               : ImmutableList.of();
    }

    private Map<String, Object> buildAdditionalData() {
        final Map<String, Object> additionalData = new HashMap<>();

        additionalData.put("roundAtLineLevel", vertexResponseData.getRoundAtLineLevel()); //todo: add additional useful information if needed

        return additionalData;
    }

    private List<AddressInfo> buildAddresses() {
        final CustomerType customer = vertexResponseData.getCustomer();
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

    private BigDecimal computeTotalTaxCalculated() {
        final List<OwnerResponseLineItemType> lineItems = vertexResponseData.getLineItems();
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

    private BigDecimal computeTotalTaxExempt() {
        final List<OwnerResponseLineItemType> lineItems = vertexResponseData.getLineItems();
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

    private BigDecimal computeTotalTaxable() {
        final List<OwnerResponseLineItemType> lineItems = vertexResponseData.getLineItems();
        return lineItems != null
               ? lineItems.stream()
                          .filter(Objects::nonNull)
                          .filter(lineItem -> lineItem.getTaxes() != null && lineItem.getTaxes().size() > 0)
                          .map(lineItem -> lineItem.getTaxes().get(0))      //taxable field is the same for all taxes in the lineItem, so get the first one
                          .map(TaxesType::getTaxable)
                          .filter(Objects::nonNull)
                          .map(BigDecimal::valueOf)
                          .reduce(BigDecimal.ZERO, BigDecimal::add)
               : BigDecimal.ZERO;
    }
}
