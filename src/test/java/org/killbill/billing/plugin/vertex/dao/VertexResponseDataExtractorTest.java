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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.killbill.billing.plugin.vertex.dao.VertexResponseDataExtractor.AddressInfo;
import org.killbill.billing.plugin.vertex.dao.VertexResponseDataExtractor.TaxInfo;
import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessResponseTransactionResponseTypeData;
import org.killbill.billing.plugin.vertex.gen.client.model.CustomerType;
import org.killbill.billing.plugin.vertex.gen.client.model.Jurisdiction;
import org.killbill.billing.plugin.vertex.gen.client.model.JurisdictionTypeEnum;
import org.killbill.billing.plugin.vertex.gen.client.model.LocationType;
import org.killbill.billing.plugin.vertex.gen.client.model.OwnerResponseLineItemType;
import org.killbill.billing.plugin.vertex.gen.client.model.PhysicalLocation;
import org.killbill.billing.plugin.vertex.gen.client.model.TaxRegistrationType;
import org.killbill.billing.plugin.vertex.gen.client.model.TaxesType;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class VertexResponseDataExtractorTest {

    private final VertexResponseDataExtractor vertexResponseDataExtractor = new VertexResponseDataExtractor();

    @Test(groups = "fast")
    public void testGetTransactionSummary() {
        //given
        final Double exempt1 = 0.1d;
        final Double nonTaxable1 = 0.1d;
        final Double taxable1 = 1.2d;
        final Double calculatedTax1 = 1.3d;
        final Double effectiveRate1 = 1.4d;
        final Double nominalRate1 = 1.5d;
        final Jurisdiction jurisdiction1 = new Jurisdiction().jurisdictionType(JurisdictionTypeEnum.STATE).value("value1");

        final Double exempt2 = 0.1d;
        final Double nonTaxable2 = 0.0d;
        final Double taxable2 = 0.2d;
        final Double calculatedTax2 = 0.3d;
        final Double effectiveRate2 = 0.4d;
        final Double nominalRate2 = 0.5d;
        final Jurisdiction jurisdiction2 = new Jurisdiction().jurisdictionType(JurisdictionTypeEnum.COUNTRY).value("value2");

        final TaxesType tax1 = new TaxesType()
                .calculatedTax(calculatedTax1)
                .jurisdiction(jurisdiction1)
                .effectiveRate(effectiveRate1)
                .nominalRate(nominalRate1)
                .exempt(exempt1)
                .taxable(taxable1)
                .nonTaxable(nonTaxable1);

        final TaxesType tax2 = new TaxesType()
                .calculatedTax(calculatedTax2)
                .jurisdiction(jurisdiction2)
                .effectiveRate(effectiveRate2)
                .nominalRate(nominalRate2)
                .exempt(exempt2)
                .taxable(taxable2)
                .nonTaxable(nonTaxable2);

        final OwnerResponseLineItemType line1 = new OwnerResponseLineItemType();
        line1.setTaxes(Collections.singletonList(tax1));
        final OwnerResponseLineItemType line2 = new OwnerResponseLineItemType();
        line2.setTaxes(Collections.singletonList(tax2));
        final List<OwnerResponseLineItemType> lineItems = Arrays.asList(line1, line2);

        //when
        final List<TaxInfo> taxInfos = vertexResponseDataExtractor.getTransactionSummary(lineItems);

        //then
        assertEquals(taxInfos.size(), 2);

        assertEquals(taxInfos.get(0).getExempt(), exempt1);
        assertEquals(taxInfos.get(0).getNonTaxable(), nonTaxable1);
        assertEquals(taxInfos.get(0).getTaxable(), taxable1);
        assertEquals(taxInfos.get(0).getCalculatedTax(), calculatedTax1);
        assertEquals(taxInfos.get(0).getEffectiveRate(), effectiveRate1);
        assertEquals(taxInfos.get(0).getNominalRate(), nominalRate1);
        assertEquals(taxInfos.get(0).getJurisdiction(), jurisdiction1);

        assertEquals(taxInfos.get(1).getExempt(), exempt2);
        assertEquals(taxInfos.get(1).getNonTaxable(), nonTaxable2);
        assertEquals(taxInfos.get(1).getTaxable(), taxable2);
        assertEquals(taxInfos.get(1).getCalculatedTax(), calculatedTax2);
        assertEquals(taxInfos.get(1).getEffectiveRate(), effectiveRate2);
        assertEquals(taxInfos.get(1).getNominalRate(), nominalRate2);
        assertEquals(taxInfos.get(1).getJurisdiction(), jurisdiction2);
    }

    @Test(groups = "fast")
    public void testGetTransactionSummaryEmpty() {
        assertEquals(vertexResponseDataExtractor.getTransactionSummary(null).size(), 0);
        assertEquals(vertexResponseDataExtractor.getTransactionSummary(ImmutableList.of()).size(), 0);
        assertEquals(vertexResponseDataExtractor.getTransactionSummary(
                Collections.singletonList(new OwnerResponseLineItemType().taxes(null))).size(), 0);
    }

    @Test(groups = "fast")
    public void testGetAdditionalData() {
        assertEquals(vertexResponseDataExtractor.getAdditionalData(
                             new ApiSuccessResponseTransactionResponseTypeData().roundAtLineLevel(true)).get("roundAtLineLevel"),
                     true);
        assertNull(vertexResponseDataExtractor.getAdditionalData(
                             new ApiSuccessResponseTransactionResponseTypeData().roundAtLineLevel(null)).get("roundAtLineLevel"));
    }

    @Test(groups = "fast")
    public void testGetAddresses() {
        //given
        final LocationType destinationAddress = new LocationType()
                .city("city")
                .streetAddress1("address1")
                .streetAddress2("address2")
                .postalCode("postalCd")
                .mainDivision("state")
                .country("country")
                .taxAreaId("taxAreaId");

        final PhysicalLocation taxRegistrationAddress = new PhysicalLocation()
                .city("city")
                .streetAddress1("address1")
                .streetAddress2("address2")
                .postalCode("postalCd")
                .mainDivision("state")
                .country("country")
                .taxAreaId("taxAreaId");

        final CustomerType customer = new CustomerType()
                .destination(destinationAddress)
                .taxRegistrations(Collections.singletonList(
                        new TaxRegistrationType().physicalLocations(
                                Collections.singletonList(taxRegistrationAddress))));

        //when
        final List<AddressInfo> addresses = vertexResponseDataExtractor.getAddresses(customer);

        //then
        assertEquals(addresses.get(0).getCity(), destinationAddress.getCity());
        assertEquals(addresses.get(0).getStreetAddress1(), destinationAddress.getStreetAddress1());
        assertEquals(addresses.get(0).getStreetAddress2(), destinationAddress.getStreetAddress2());
        assertEquals(addresses.get(0).getPostalCode(), destinationAddress.getPostalCode());
        assertEquals(addresses.get(0).getMainDivision(), destinationAddress.getMainDivision());
        assertEquals(addresses.get(0).getCountry(), destinationAddress.getCountry());
        assertEquals(addresses.get(0).getTaxAreaId(), destinationAddress.getTaxAreaId());

        assertEquals(addresses.get(1).getCity(), taxRegistrationAddress.getCity());
        assertEquals(addresses.get(1).getStreetAddress1(), taxRegistrationAddress.getStreetAddress1());
        assertEquals(addresses.get(1).getStreetAddress2(), taxRegistrationAddress.getStreetAddress2());
        assertEquals(addresses.get(1).getPostalCode(), taxRegistrationAddress.getPostalCode());
        assertEquals(addresses.get(1).getMainDivision(), taxRegistrationAddress.getMainDivision());
        assertEquals(addresses.get(1).getCountry(), taxRegistrationAddress.getCountry());
        assertEquals(addresses.get(1).getTaxAreaId(), taxRegistrationAddress.getTaxAreaId());
    }

    @Test(groups = "fast")
    public void testGetAddressesEmpty() {
        assertEquals(vertexResponseDataExtractor.getAddresses(null).size(), 0);

        assertEquals(vertexResponseDataExtractor.getAddresses(new CustomerType().destination(null)).size(), 0);
        assertEquals(vertexResponseDataExtractor.getAddresses(new CustomerType().taxRegistrations(
                Collections.singletonList(new TaxRegistrationType().physicalLocations(null)))).size(), 0);

    }

    @Test(groups = "fast")
    public void testGetTotalTaxCalculated() {
        //given
        final double calculatedTax1_1 = 0.1d;
        final double calculatedTax1_2 = 1.0d;

        final TaxesType tax1_1 = new TaxesType().calculatedTax(calculatedTax1_1);
        final TaxesType tax1_2 = new TaxesType().calculatedTax(calculatedTax1_2);
        final OwnerResponseLineItemType lineItem1 = new OwnerResponseLineItemType().taxes(Arrays.asList(tax1_1, tax1_2));

        final double calculatedTax2_1 = 1.0d;
        final double calculatedTax2_2 = 2.0d;

        final TaxesType tax2_1 = new TaxesType().calculatedTax(calculatedTax2_1);
        final TaxesType tax2_2 = new TaxesType().calculatedTax(calculatedTax2_2);
        final OwnerResponseLineItemType lineItem2 = new OwnerResponseLineItemType().taxes(Arrays.asList(tax2_1, tax2_2));

        final BigDecimal expectedTotalTaxCalculated = BigDecimal.valueOf(calculatedTax1_1)
                                                                .add(BigDecimal.valueOf(calculatedTax1_2))
                                                                .add(BigDecimal.valueOf(calculatedTax2_1))
                                                                .add(BigDecimal.valueOf(calculatedTax2_2));

        //when
        final BigDecimal totalTaxCalculated = vertexResponseDataExtractor.getTotalTaxCalculated(Arrays.asList(lineItem1, lineItem2));

        //then
        assertEquals(totalTaxCalculated, expectedTotalTaxCalculated);
    }

    @Test(groups = "fast")
    public void testGetTotalTaxCalculatedNPE() {
        //given
        final OwnerResponseLineItemType lineItem = new OwnerResponseLineItemType().taxes(Collections.singletonList(new TaxesType().calculatedTax(null)));

        //when
        final BigDecimal totalTaxCalculated = vertexResponseDataExtractor.getTotalTaxCalculated(Collections.singletonList(lineItem));

        //then
        assertEquals(totalTaxCalculated, BigDecimal.ZERO);
    }

    @Test(groups = "fast")
    public void testGetTotalExempt() {
        //given
        final double exempt1_1 = 0.1d;
        final double exempt1_2 = 1.0d;

        final TaxesType tax1_1 = new TaxesType().exempt(exempt1_1);
        final TaxesType tax1_2 = new TaxesType().exempt(exempt1_2);
        final OwnerResponseLineItemType lineItem1 = new OwnerResponseLineItemType().taxes(Arrays.asList(tax1_1, tax1_2));

        final double exempt2_1 = 1.0d;
        final double exempt2_2 = 2.0d;

        final TaxesType tax2_1 = new TaxesType().exempt(exempt2_1);
        final TaxesType tax2_2 = new TaxesType().exempt(exempt2_2);
        final OwnerResponseLineItemType lineItem2 = new OwnerResponseLineItemType().taxes(Arrays.asList(tax2_1, tax2_2));

        final BigDecimal expectedTotalTaxCalculated = BigDecimal.valueOf(exempt1_1)
                                                                .add(BigDecimal.valueOf(exempt1_2))
                                                                .add(BigDecimal.valueOf(exempt2_1))
                                                                .add(BigDecimal.valueOf(exempt2_2));

        //when
        final BigDecimal totalTaxExempt = vertexResponseDataExtractor.getTotalTaxExempt(Arrays.asList(lineItem1, lineItem2));

        //then
        assertEquals(totalTaxExempt, expectedTotalTaxCalculated);
    }

    @Test(groups = "fast")
    public void testGetTotalExemptNPE() {
        //given
        final OwnerResponseLineItemType lineItem = new OwnerResponseLineItemType().taxes(Collections.singletonList(new TaxesType().exempt(null)));

        //when
        final BigDecimal totalExempt = vertexResponseDataExtractor.getTotalTaxCalculated(Collections.singletonList(lineItem));

        //then
        assertEquals(totalExempt, BigDecimal.ZERO);
    }

    @Test(groups = "fast")
    public void testGetTotalTaxable() {
        //given
        final double taxable1_1 = 0.1d;
        final double taxable1_2 = 1.0d;

        final TaxesType tax1_1 = new TaxesType().taxable(taxable1_1);
        final TaxesType tax1_2 = new TaxesType().taxable(taxable1_2);
        final LinkedList<TaxesType> taxesList1 = new LinkedList<>();
        taxesList1.add(tax1_1);
        taxesList1.add(tax1_2);

        final OwnerResponseLineItemType lineItem1 = new OwnerResponseLineItemType().taxes(taxesList1);

        final double taxable2_1 = 1.0d;
        final double taxable2_2 = 2.0d;

        final TaxesType tax2_1 = new TaxesType().taxable(taxable2_1);
        final TaxesType tax2_2 = new TaxesType().taxable(taxable2_2);
        final LinkedList<TaxesType> taxesList2 = new LinkedList<>();
        taxesList2.add(tax2_1);
        taxesList2.add(tax2_2);

        final OwnerResponseLineItemType lineItem2 = new OwnerResponseLineItemType().taxes(taxesList2);

        final BigDecimal expectedTotalTaxable = BigDecimal.valueOf(taxable1_1)
                                                          .add(BigDecimal.valueOf(taxable2_1)); //only first for each lineItem

        //when
        final BigDecimal totalTaxable = vertexResponseDataExtractor.getTotalTaxable(Arrays.asList(lineItem1, lineItem2));

        //then
        assertEquals(totalTaxable, expectedTotalTaxable);
    }

    @Test(groups = "fast")
    public void testGetTotalTaxableNPE() {
        //given
        final OwnerResponseLineItemType lineItem = new OwnerResponseLineItemType().taxes(Collections.singletonList(new TaxesType().taxable(null)));

        //when
        final BigDecimal totalTaxCalculated = vertexResponseDataExtractor.getTotalTaxCalculated(Collections.singletonList(lineItem));

        //then
        assertEquals(totalTaxCalculated, BigDecimal.ZERO);
    }

    @Test(groups = "fast")
    public void testZeroResultsWhenLineItemIsNull() {
        assertEquals(vertexResponseDataExtractor.getTotalTaxCalculated(null), BigDecimal.ZERO);
        assertEquals(vertexResponseDataExtractor.getTotalTaxExempt(null), BigDecimal.ZERO);
        assertEquals(vertexResponseDataExtractor.getTotalTaxable(null), BigDecimal.ZERO);

        assertEquals(vertexResponseDataExtractor.getTotalTaxCalculated(Collections.singletonList(null)), BigDecimal.ZERO);
        assertEquals(vertexResponseDataExtractor.getTotalTaxExempt(Collections.singletonList(null)), BigDecimal.ZERO);
        assertEquals(vertexResponseDataExtractor.getTotalTaxable(Collections.singletonList(null)), BigDecimal.ZERO);
    }
}
