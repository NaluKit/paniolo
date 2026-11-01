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
package io.github.nalukit.paniolo.migration;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

/**
 * Stand-ins for the export interfaces of Mustang (library 2.24, package
 * {@code org.mustangproject.ZUGFeRD}): the methods an existing integration implements, with
 * Mustang's names and types, reduced to the ones the sample integration uses. Paniolo does not
 * depend on Mustang; in a real migration these are Mustang's own interfaces.
 *
 * <p>Simplifications: {@code getTotalAmount(IAbsoluteValueProvider)} is
 * {@link AllowanceCharge#getTotalAmount()} (the absolute amount), Mustang's
 * {@code IncludedNote}, {@code FileAttachment} and {@code SchemedID} classes are interfaces here.
 */
final class MustangInterfaces {
  private MustangInterfaces() {
  }

  /**
   * {@code IExportableTransaction}.
   */
  interface ExportableTransaction {
    default String getDocumentCode() {
      return "380";
    }

    default String getDocumentName() {
      return null;
    }

    default boolean getTestIndicator() {
      return false;
    }

    String getNumber();

    Date getIssueDate();

    default Date getDueDate() {
      return null;
    }

    Date getDeliveryDate();

    default String getCurrency() {
      return "EUR";
    }

    default String getBusinessProcessId() {
      return null;
    }

    default String getReferenceNumber() {
      return null;
    }

    TradeParty getSender();

    TradeParty getRecipient();

    default TradeParty getPayee() {
      return null;
    }

    default TradeParty getDeliveryAddress() {
      return null;
    }

    default TradeParty getInvoicee() {
      return null;
    }

    Item[] getZFItems();

    default AllowanceCharge[] getZFAllowances() {
      return null;
    }

    default AllowanceCharge[] getZFCharges() {
      return null;
    }

    default AllowanceCharge[] getZFLogisticsServiceCharges() {
      return null;
    }

    default TradeSettlementPayment[] getTradeSettlementPayment() {
      return null;
    }

    default TradeSettlementDebit[] getTradeSettlement() {
      return null;
    }

    default String getCreditorReferenceID() {
      return null;
    }

    default String getPaymentReference() {
      return null;
    }

    default String getPaymentTermDescription() {
      return null;
    }

    default PaymentTerms getPaymentTerms() {
      return null;
    }

    default CashDiscount[] getCashDiscounts() {
      return null;
    }

    default BigDecimal getTotalPrepaidAmount() {
      return null;
    }

    default BigDecimal getRoundingAmount() {
      return null;
    }

    default String getBuyerOrderReferencedDocumentID() {
      return null;
    }

    default String getBuyerOrderReferencedDocumentIssueDateTime() {
      return null;
    }

    default String getSellerOrderReferencedDocumentID() {
      return null;
    }

    default String getContractReferencedDocument() {
      return null;
    }

    default String getSpecifiedProcuringProjectID() {
      return null;
    }

    default String getSpecifiedProcuringProjectName() {
      return null;
    }

    default String getDespatchAdviceReferencedDocumentID() {
      return null;
    }

    default String getDeliveryNoteReferencedDocumentID() {
      return null;
    }

    default Date getDeliveryNoteReferencedDocumentDate() {
      return null;
    }

    default String getInvoiceReferencedDocumentID() {
      return null;
    }

    default Date getInvoiceReferencedIssueDate() {
      return null;
    }

    default Date getDetailedDeliveryPeriodFrom() {
      return null;
    }

    default Date getDetailedDeliveryPeriodTo() {
      return null;
    }

    default String getVATDueDateTypeCode() {
      return null;
    }

    default String[] getNotes() {
      return null;
    }

    default IncludedNote[] getNotesWithSubjectCode() {
      return null;
    }

    default String getSubjectNote() {
      return null;
    }

    default String getOwnOrganisationFullPlaintextInfo() {
      return null;
    }

    default FileAttachment[] getAdditionalReferencedDocuments() {
      return null;
    }
  }

  /**
   * {@code IZUGFeRDExportableTradeParty}.
   */
  interface TradeParty {
    String getName();

    String getStreet();

    default String getAdditionalAddress() {
      return null;
    }

    String getZIP();

    String getLocation();

    String getCountry();

    default String getVATID() {
      return null;
    }

    default String getTaxID() {
      return null;
    }

    default String getID() {
      return null;
    }

    default String getGlobalID() {
      return null;
    }

    default String getGlobalIDScheme() {
      return null;
    }

    default String getUriUniversalCommunicationID() {
      return null;
    }

    default String getUriUniversalCommunicationIDScheme() {
      return null;
    }

    default Contact getContact() {
      return null;
    }

    default LegalOrganisation getLegalOrganisation() {
      return null;
    }

    default String getDescription() {
      return null;
    }
  }

  /**
   * {@code IZUGFeRDExportableContact}.
   */
  interface Contact {
    String getName();

    default String getPhone() {
      return null;
    }

    default String getEMail() {
      return null;
    }

    default String getFax() {
      return null;
    }
  }

  /**
   * {@code IZUGFeRDLegalOrganisation}.
   */
  interface LegalOrganisation {
    SchemedID getSchemedID();

    default String getTradingBusinessName() {
      return null;
    }
  }

  /**
   * Mustang's {@code SchemedID}.
   */
  interface SchemedID {
    String getScheme();

    String getID();
  }

  /**
   * {@code IZUGFeRDExportableItem}.
   */
  interface Item {
    String getId();

    Product getProduct();

    BigDecimal getPrice();

    BigDecimal getQuantity();

    default BigDecimal getBasisQuantity() {
      return BigDecimal.ONE;
    }

    default AllowanceCharge[] getItemAllowances() {
      return null;
    }

    default AllowanceCharge[] getItemCharges() {
      return null;
    }

    default String[] getNotes() {
      return null;
    }

    default Date getDetailedDeliveryPeriodFrom() {
      return null;
    }

    default Date getDetailedDeliveryPeriodTo() {
      return null;
    }

    default String getBuyerOrderReferencedDocumentLineID() {
      return null;
    }

    default String getAccountingReference() {
      return null;
    }

    default BigDecimal getLineTotalAmount() {
      return null;
    }

    default String getParentLineID() {
      return null;
    }
  }

  /**
   * {@code IZUGFeRDExportableProduct}.
   */
  interface Product {
    String getName();

    String getDescription();

    String getUnit();

    BigDecimal getVATPercent();

    default String getTaxCategoryCode() {
      return "S";
    }

    default String getTaxExemptionReason() {
      return null;
    }

    default String getTaxExemptionReasonCode() {
      return null;
    }

    default String getSellerAssignedID() {
      return null;
    }

    default String getBuyerAssignedID() {
      return null;
    }

    default String getGlobalID() {
      return null;
    }

    default String getGlobalIDScheme() {
      return null;
    }

    default String getCountryOfOrigin() {
      return null;
    }

    default Map<String, String> getAttributes() {
      return null;
    }

    default AllowanceCharge[] getAllowances() {
      return null;
    }

    default AllowanceCharge[] getCharges() {
      return null;
    }
  }

  /**
   * {@code IZUGFeRDAllowanceCharge}.
   */
  interface AllowanceCharge {
    BigDecimal getTotalAmount();

    default BigDecimal getPercent() {
      return null;
    }

    default BigDecimal getBasisAmount() {
      return null;
    }

    String getReason();

    String getReasonCode();

    BigDecimal getTaxPercent();

    default String getCategoryCode() {
      return "S";
    }

    boolean isCharge();
  }

  /**
   * {@code IZUGFeRDTradeSettlementPayment}: credit transfer.
   */
  interface TradeSettlementPayment {
    default String getPaymentMeansCode() {
      return "58";
    }

    default String getPaymentMeansInformation() {
      return null;
    }

    String getOwnIBAN();

    default String getOwnBIC() {
      return null;
    }

    default String getAccountName() {
      return null;
    }

    default String getOwnPaymentInfoText() {
      return null;
    }
  }

  /**
   * {@code IZUGFeRDTradeSettlementDebit}: SEPA direct debit.
   */
  interface TradeSettlementDebit {
    default String getPaymentMeansCode() {
      return "59";
    }

    String getIBAN();

    String getMandate();
  }

  /**
   * {@code IZUGFeRDPaymentTerms}.
   */
  interface PaymentTerms {
    String getDescription();

    Date getDueDate();

    PaymentDiscountTerms getDiscountTerms();
  }

  /**
   * {@code IZUGFeRDPaymentDiscountTerms}.
   */
  interface PaymentDiscountTerms {
    BigDecimal getCalculationPercentage();

    Date getBaseDate();

    int getBasePeriodMeasure();

    String getBasePeriodUnitCode();
  }

  /**
   * {@code IZUGFeRDCashDiscount}: Mustang offers the discount as finished text only.
   */
  interface CashDiscount {
    String getAsCII();

    String getAsXRechnung();
  }

  /**
   * Mustang's {@code IncludedNote}.
   */
  interface IncludedNote {
    String getSubjectCode();

    String getContent();
  }

  /**
   * Mustang's {@code FileAttachment}.
   */
  interface FileAttachment {
    String getFilename();

    String getMimetype();

    String getRelation();

    byte[] getData();

    default String getDescription() {
      return null;
    }
  }
}
