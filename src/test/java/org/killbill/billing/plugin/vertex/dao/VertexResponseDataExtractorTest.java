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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.killbill.billing.plugin.vertex.dao.VertexResponseDataExtractor.AddressInfo;
import org.killbill.billing.plugin.vertex.dao.VertexResponseDataExtractor.TaxInfo;
import org.killbill.billing.plugin.vertex.gen.client.model.ApiSuccessResponseTransactionResponseTypeData;
import org.killbill.billing.plugin.vertex.gen.client.model.CustomerType;
import org.killbill.billing.plugin.vertex.gen.client.model.Discount;
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

        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().lineItems(Arrays.asList(line1, line2));

        //when
        final List<TaxInfo> taxInfos = new VertexResponseDataExtractor(vertexResponse).getTransactionSummary();

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
        assertEquals(new VertexResponseDataExtractor(new ApiSuccessResponseTransactionResponseTypeData().lineItems(null)).getTransactionSummary().size(), 0);
        assertEquals(new VertexResponseDataExtractor(new ApiSuccessResponseTransactionResponseTypeData().lineItems(ImmutableList.of())).getTransactionSummary().size(), 0);
        assertEquals(new VertexResponseDataExtractor(new ApiSuccessResponseTransactionResponseTypeData().lineItems(
                Collections.singletonList(new OwnerResponseLineItemType().taxes(null)))).getTransactionSummary().size(), 0);
    }

    @Test(groups = "fast")
    public void testGetAdditionalData() {
        assertEquals(new VertexResponseDataExtractor(new ApiSuccessResponseTransactionResponseTypeData().roundAtLineLevel(true))
                             .getAdditionalData().get("roundAtLineLevel"), true);
        assertNull(new VertexResponseDataExtractor(new ApiSuccessResponseTransactionResponseTypeData().roundAtLineLevel(null))
                           .getAdditionalData().get("roundAtLineLevel"));
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

        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().customer(customer);

        //when
        final List<AddressInfo> addresses = new VertexResponseDataExtractor(vertexResponse).getAddresses();

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
        assertEquals(new VertexResponseDataExtractor(new ApiSuccessResponseTransactionResponseTypeData()
                                                             .customer(null))
                             .getAddresses().size(), 0);
        assertEquals(new VertexResponseDataExtractor(new ApiSuccessResponseTransactionResponseTypeData()
                                                             .customer(new CustomerType().destination(null)))
                             .getAddresses().size(), 0);
        assertEquals(new VertexResponseDataExtractor(new ApiSuccessResponseTransactionResponseTypeData().customer(
                new CustomerType().taxRegistrations(Collections.singletonList(
                        new TaxRegistrationType().physicalLocations(null))))).getAddresses().size(), 0);

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

        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().lineItems(Arrays.asList(lineItem1, lineItem2));

        //when
        final BigDecimal totalTaxCalculated = new VertexResponseDataExtractor(vertexResponse).getTotalTaxCalculated();

        //then
        assertEquals(totalTaxCalculated, expectedTotalTaxCalculated);
    }

    @Test(groups = "fast")
    public void testGetTotalTaxCalculatedNPE() {
        //given
        final OwnerResponseLineItemType lineItem = new OwnerResponseLineItemType().taxes(Collections.singletonList(new TaxesType().calculatedTax(null)));
        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().lineItems(Collections.singletonList(lineItem));

        //when
        final BigDecimal totalTaxCalculated = new VertexResponseDataExtractor(vertexResponse).getTotalTaxCalculated();

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

        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().lineItems(Arrays.asList(lineItem1, lineItem2));

        //when
        final BigDecimal totalTaxExempt = new VertexResponseDataExtractor(vertexResponse).getTotalTaxExempt();

        //then
        assertEquals(totalTaxExempt, expectedTotalTaxCalculated);
    }

    @Test(groups = "fast")
    public void testGetTotalExemptNPE() {
        //given
        final OwnerResponseLineItemType lineItem = new OwnerResponseLineItemType().taxes(Collections.singletonList(new TaxesType().exempt(null)));
        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().lineItems(Collections.singletonList(lineItem));

        //when
        final BigDecimal totalExempt = new VertexResponseDataExtractor(vertexResponse).getTotalTaxExempt();

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

        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().lineItems(Arrays.asList(lineItem1, lineItem2));

        //when
        final BigDecimal totalTaxable = new VertexResponseDataExtractor(vertexResponse).getTotalTaxable();

        //then
        assertEquals(totalTaxable, expectedTotalTaxable);
    }

    @Test(groups = "fast")
    public void testGetTotalTaxableNullAndEmptyTaxes() {
        //given
        final OwnerResponseLineItemType lineItemNullTaxable = new OwnerResponseLineItemType().taxes(Collections.singletonList(new TaxesType().taxable(null)));
        final OwnerResponseLineItemType lineItemEmptyTaxesList = new OwnerResponseLineItemType().taxes(ImmutableList.of());
        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().lineItems(Arrays.asList(lineItemNullTaxable, lineItemEmptyTaxesList));

        //when
        final BigDecimal totalTaxCalculated = new VertexResponseDataExtractor(vertexResponse).getTotalTaxable();

        //then
        assertEquals(totalTaxCalculated, BigDecimal.ZERO);
    }

    @Test(groups = "fast")
    public void testZeroResultsWhenLineItemIsNull() {
        //given
        VertexResponseDataExtractor vertexResponseDataExtractor = new VertexResponseDataExtractor(new ApiSuccessResponseTransactionResponseTypeData().lineItems(null));
        assertEquals(vertexResponseDataExtractor.getTotalTaxCalculated(), BigDecimal.ZERO);
        assertEquals(vertexResponseDataExtractor.getTotalTaxExempt(), BigDecimal.ZERO);
        assertEquals(vertexResponseDataExtractor.getTotalTaxable(), BigDecimal.ZERO);

        vertexResponseDataExtractor = new VertexResponseDataExtractor(new ApiSuccessResponseTransactionResponseTypeData().lineItems(Collections.singletonList(null)));
        assertEquals(vertexResponseDataExtractor.getTotalTaxCalculated(), BigDecimal.ZERO);
        assertEquals(vertexResponseDataExtractor.getTotalTaxExempt(), BigDecimal.ZERO);
        assertEquals(vertexResponseDataExtractor.getTotalTaxable(), BigDecimal.ZERO);
    }

    @Test(groups = "fast")
    public void testGetDocumentCode() {
        //given
        final String transactionId = "transactionId";
        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().transactionId(transactionId);

        //when
        final String actualDocumentCode = new VertexResponseDataExtractor(vertexResponse).getDocumentCode();

        //then
        assertEquals(actualDocumentCode, transactionId);
    }

    @Test(groups = "fast")
    public void testGetDocumentDate() {
        //given
        final LocalDate documentDate = LocalDate.now();
        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().documentDate(documentDate);

        //when
        final LocalDateTime actualDocumentDate = new VertexResponseDataExtractor(vertexResponse).getDocumentDate();

        //then
        assertEquals(actualDocumentDate, documentDate.atStartOfDay());
    }

    @Test(groups = "fast")
    public void testGetDocumentNull() {
        //given
        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().documentDate(null);

        //when
        final LocalDateTime actualDocumentDate = new VertexResponseDataExtractor(vertexResponse).getDocumentDate();

        //then
        assertNull(actualDocumentDate);
    }

    @Test(groups = "fast")
    public void testGetTotalAmount() {
        //given
        final double totalAmount = 1.1d;
        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().total(totalAmount);

        //when
        final BigDecimal actualTotalAmount = new VertexResponseDataExtractor(vertexResponse).getTotalAmount();

        //then
        assertEquals(actualTotalAmount, BigDecimal.valueOf(totalAmount));
    }

    @Test(groups = "fast")
    public void testGetTotalAmountNull() {
        //given
        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().total(null);

        //when
        final BigDecimal actualTotalAmount = new VertexResponseDataExtractor(vertexResponse).getTotalAmount();

        //then
        assertNull(actualTotalAmount);
    }

    @Test(groups = "fast")
    public void testGetTotalDiscount() {
        //given
        final double totalDiscount = 1.1d;
        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().discount(new Discount().discountValue(totalDiscount));

        //when
        final BigDecimal actualTotalDiscount = new VertexResponseDataExtractor(vertexResponse).getTotalDiscount();

        //then
        assertEquals(actualTotalDiscount, BigDecimal.valueOf(totalDiscount));
    }

    @Test(groups = "fast")
    public void testGetTotalDiscountNull() {
        //given
        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().discount(new Discount().discountValue(null));

        //when
        final BigDecimal actualTotalDiscount = new VertexResponseDataExtractor(vertexResponse).getTotalDiscount();

        //then
        assertNull(actualTotalDiscount);
    }

    @Test(groups = "fast")
    public void testGetTaxLines() {
        //given
        List<OwnerResponseLineItemType> taxLines = Collections.singletonList(new OwnerResponseLineItemType().lineItemId("id"));
        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().lineItems(taxLines);

        //when
        final List<OwnerResponseLineItemType> actualTaxLines = new VertexResponseDataExtractor(vertexResponse).getTaxLines();

        //then
        assertEquals(actualTaxLines.get(0), taxLines.get(0));
    }

    @Test(groups = "fast")
    public void testGetTaxDate() {
        //given
        final LocalDate taxDate = LocalDate.now();
        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData().taxPointDate(taxDate);

        //when
        final LocalDateTime actualTaxDate = new VertexResponseDataExtractor(vertexResponse).getTaxDate();

        //then
        assertEquals(actualTaxDate, taxDate.atStartOfDay());
    }

    @Test(groups = "fast")
    public void testGetTaxDateNull() {
        //given
        final LocalDate documentDate = LocalDate.now();
        final ApiSuccessResponseTransactionResponseTypeData vertexResponse = new ApiSuccessResponseTransactionResponseTypeData()
                .taxPointDate(null).documentDate(documentDate);

        //when
        final LocalDateTime actualTaxDate = new VertexResponseDataExtractor(vertexResponse).getTaxDate();

        //then
        assertEquals(actualTaxDate, documentDate.atStartOfDay());
    }
}
