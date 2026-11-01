/*
 * Copyright © 2026 Frank Hossfeld
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nalukit.paniolo.mustang;

import java.util.Map;
import java.util.Set;

/**
 * The getters of Mustang's export interfaces that {@link MustangInvoiceAdapter} knows, by
 * interface. A Mustang version newer than the one the adapter is built for may add getters; the
 * adapter reports a set value of any getter not listed here as {@link MappingLoss}, so that no value
 * is lost silently.
 */
final class MustangGetters {
  /**
   * The Mustang release the adapter is compiled and tested against.
   */
  static final String RELEASE = "2.26.0";

  /**
   * All getters of the export interfaces of {@link #RELEASE}, including inherited ones.
   */
  static final Map<String, Set<String>> RELEASE_GETTERS = Map.ofEntries(
      Map.entry("IExportableTransaction",
                Set.of("getAdditionalReferencedDocuments",
                       "getBusinessProcessId",
                       "getBuyerOrderReferencedDocument",
                       "getBuyerOrderReferencedDocumentIssueDateTime",
                       "getCashDiscounts",
                       "getContractReferencedDocument",
                       "getCreditorReferenceID",
                       "getCurrency",
                       "getDeliveryAddress",
                       "getDeliveryDate",
                       "getDeliveryNoteReferencedDocument",
                       "getDeliveryNoteReferencedDocumentDate",
                       "getDeliveryNoteReferencedDocumentID",
                       "getDeliveryTypeCode",
                       "getDespatchAdviceReferencedDocument",
                       "getDespatchAdviceReferencedDocumentID",
                       "getDetailedDeliveryPeriodFrom",
                       "getDetailedDeliveryPeriodTo",
                       "getDocumentCode",
                       "getDocumentName",
                       "getDueDate",
                       "getEndCustomerDeliveryAddress",
                       "getExtendedPaymentTerms",
                       "getInvoiceReferencedDocumentID",
                       "getInvoiceReferencedDocuments",
                       "getInvoiceReferencedIssueDate",
                       "getInvoicee",
                       "getInvoicer",
                       "getIssueDate",
                       "getNotes",
                       "getNotesWithSubjectCode",
                       "getNumber",
                       "getObjectIdentifierReferencedDocument",
                       "getOwnCountry",
                       "getOwnForeignOrganisationID",
                       "getOwnLocation",
                       "getOwnOrganisationFullPlaintextInfo",
                       "getOwnOrganisationName",
                       "getOwnStreet",
                       "getOwnTaxID",
                       "getOwnVATID",
                       "getOwnZIP",
                       "getPayee",
                       "getPaymentReference",
                       "getPaymentTermDescription",
                       "getPaymentTerms",
                       "getRecipient",
                       "getReferenceNumber",
                       "getRelatedReferencedDocument",
                       "getRoundingAmount",
                       "getSellerOrderReferencedDocument",
                       "getSender",
                       "getShipToCountry",
                       "getShipToLocation",
                       "getShipToOrganisationID",
                       "getShipToOrganisationName",
                       "getShipToStreet",
                       "getShipToZIP",
                       "getSpecifiedProcuringProjectID",
                       "getSpecifiedProcuringProjectName",
                       "getSubjectNote",
                       "getTaxConversionRate",
                       "getTaxConversionRateDateTime",
                       "getTaxCurrency",
                       "getTaxRepresentative",
                       "getTenderReferencedDocument",
                       "getTestIndicator",
                       "getTotalPrepaidAmount",
                       "getTradeSettlement",
                       "getVATDueDateTypeCode",
                       "getZFAllowances",
                       "getZFCharges",
                       "getZFItems",
                       "getZFLogisticsServiceCharges",
                       "rebateAgreementExists")),
      Map.entry("IZUGFeRDExportableTradeParty",
                Set.of("getAdditionalAddress",
                       "getAdditionalAddressExtension",
                       "getContact",
                       "getCountry",
                       "getDescription",
                       "getEmail",
                       "getGlobalID",
                       "getGlobalIDScheme",
                       "getID",
                       "getLegalOrganisation",
                       "getLocation",
                       "getName",
                       "getStreet",
                       "getTaxID",
                       "getUriUniversalCommunicationID",
                       "getUriUniversalCommunicationIDScheme",
                       "getVATID",
                       "getZIP")),
      Map.entry("IZUGFeRDExportableContact",
                Set.of("getAdditionalAddress",
                       "getCountry",
                       "getEMail",
                       "getFax",
                       "getID",
                       "getLocation",
                       "getName",
                       "getPhone",
                       "getStreet",
                       "getVATID",
                       "getZIP")),
      Map.entry("IZUGFeRDLegalOrganisation",
                Set.of("getSchemedID",
                       "getTradingBusinessName")),
      Map.entry("IZUGFeRDExportableItem",
                Set.of("getAccountingReference",
                       "getAdditionalReferences",
                       "getBasisQuantity",
                       "getBuyerOrderReferencedDocument",
                       "getBuyerOrderReferencedDocumentLineID",
                       "getCalculation",
                       "getContractReferencedDocument",
                       "getDeliveryNoteReferencedDocument",
                       "getDeliveryNoteReferencedDocumentDate",
                       "getDeliveryNoteReferencedDocumentID",
                       "getDeliveryNoteReferencedDocumentLineID",
                       "getDespatchAdviceReferencedDocument",
                       "getDetailedDeliveryPeriodFrom",
                       "getDetailedDeliveryPeriodTo",
                       "getId",
                       "getItemAllowances",
                       "getItemCharges",
                       "getLineSeller",
                       "getLineStatusReasonCode",
                       "getLineTotalAmount",
                       "getNotes",
                       "getNotesWithSubjectCode",
                       "getParentLineID",
                       "getPrice",
                       "getProduct",
                       "getQuantity",
                       "getReferencedDocuments",
                       "getSellerOrderReferencedDocument",
                       "getValue",
                       "isCalculationRelevant")),
      Map.entry("IZUGFeRDExportableProduct",
                Set.of("getAllowances",
                       "getAttributes",
                       "getBuyerAssignedID",
                       "getCharacteristics",
                       "getCharges",
                       "getClassifications",
                       "getCountryOfOrigin",
                       "getDescription",
                       "getGlobalID",
                       "getGlobalIDScheme",
                       "getIndividualTradeProductInstances",
                       "getName",
                       "getSellerAssignedID",
                       "getTaxCategoryCode",
                       "getTaxExemptionReason",
                       "getTaxExemptionReasonCode",
                       "getUnit",
                       "getVATPercent",
                       "isIntraCommunitySupply",
                       "isReverseCharge")),
      Map.entry("IZUGFeRDAllowanceCharge",
                Set.of("getBasisAmount",
                       "getBasisQuantity",
                       "getCategoryCode",
                       "getPercent",
                       "getReason",
                       "getReasonCode",
                       "getSequenceNumeric",
                       "getTaxAllowanceChargeBasisAmount",
                       "getTaxBasisAmount",
                       "getTaxCalculatedAmount",
                       "getTaxCategoryCode",
                       "getTaxDueDateTypeCode",
                       "getTaxExemptionReason",
                       "getTaxExemptionReasonCode",
                       "getTaxLineTotalBasisAmount",
                       "getTaxPercent",
                       "getTaxPointDate",
                       "getTaxRateApplicablePercent",
                       "isCharge")),
      Map.entry("IZUGFeRDLogisticsServiceCharge",
                Set.of("getAppliedAmount",
                       "getDescription",
                       "getTaxAllowanceChargeBasisAmount",
                       "getTaxBasisAmount",
                       "getTaxCalculatedAmount",
                       "getTaxCategoryCode",
                       "getTaxDueDateTypeCode",
                       "getTaxExemptionReason",
                       "getTaxExemptionReasonCode",
                       "getTaxLineTotalBasisAmount",
                       "getTaxPointDate",
                       "getTaxRateApplicablePercent")),
      Map.entry("IZUGFeRDTradeSettlementPayment",
                Set.of("getAccountName",
                       "getOwnBIC",
                       "getOwnIBAN",
                       "getOwnPaymentInfoText",
                       "getPaymentMeansCode",
                       "getPaymentMeansInformation",
                       "getPaymentXML")),
      Map.entry("IZUGFeRDTradeSettlementDebit",
                Set.of("getIBAN",
                       "getMandate",
                       "getPaymentMeansCode",
                       "getPaymentMeansInformation",
                       "getPaymentXML")),
      Map.entry("IZUGFeRDPaymentTerms",
                Set.of("getDescription",
                       "getDiscountTerms",
                       "getDueDate")),
      Map.entry("IZUGFeRDPaymentDiscountTerms",
                Set.of("getBaseDate",
                       "getBasePeriodMeasure",
                       "getBasePeriodUnitCode",
                       "getCalculationPercentage")));

  /**
   * Getters of newer Mustang versions that the adapter maps; it reads them by reflection.
   */
  static final Map<String, Set<String>> NEWER_GETTERS = Map.of("IExportableTransaction",
                                                               Set.of("getAccountingReference",
                                                                      "getTaxPointDate"),
                                                               "IZUGFeRDExportableProduct",
                                                               Set.of("getIndustryAssignedID"));

  private MustangGetters() {
  }

  /**
   * Returns true if the adapter knows the getter of the interface.
   */
  static boolean known(Class<?> type,
                       String getter) {
    return RELEASE_GETTERS.getOrDefault(type.getSimpleName(),
                                        Set.of())
                          .contains(getter) || NEWER_GETTERS.getOrDefault(type.getSimpleName(),
                                                                          Set.of())
                                                            .contains(getter);
  }
}
