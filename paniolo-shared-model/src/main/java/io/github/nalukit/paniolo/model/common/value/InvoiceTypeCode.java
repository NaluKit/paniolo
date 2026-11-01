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
package io.github.nalukit.paniolo.model.common.value;

import io.github.nalukit.paniolo.model.common.value.localization.LocalizationFactory;

import java.util.Arrays;
import java.util.Locale;

/**
 * UNTDID 1001 Document name code, restricted to the invoice and credit note codes permitted by
 * EN 16931 (BR-CL-01). Names follow the UN/CEFACT directory.
 */
public enum InvoiceTypeCode {

  /**
   * Request for payment
   */
  CODE_71("71"),

  /**
   * Debit note related to goods or services
   */
  CODE_80("80"),

  /**
   * Credit note related to goods or services
   */
  CODE_81("81"),

  /**
   * Metered services invoice
   */
  CODE_82("82"),

  /**
   * Credit note related to financial adjustments
   */
  CODE_83("83"),

  /**
   * Debit note related to financial adjustments
   */
  CODE_84("84"),

  /**
   * Tax notification
   */
  CODE_102("102"),

  /**
   * Invoicing data sheet
   */
  CODE_130("130"),

  /**
   * Direct payment valuation
   */
  CODE_202("202"),

  /**
   * Provisional payment valuation
   */
  CODE_203("203"),

  /**
   * Payment valuation
   */
  CODE_204("204"),

  /**
   * Interim application for payment
   */
  CODE_211("211"),

  /**
   * Final payment request based on completion of work
   */
  CODE_218("218"),

  /**
   * Payment request for completed units
   */
  CODE_219("219"),

  /**
   * Self billed credit note
   */
  CODE_261("261"),

  /**
   * Consolidated credit note - goods and services
   */
  CODE_262("262"),

  /**
   * Price variation invoice
   */
  CODE_295("295"),

  /**
   * Credit note for price variation
   */
  CODE_296("296"),

  /**
   * Delcredere credit note
   */
  CODE_308("308"),

  /**
   * Proforma invoice
   */
  CODE_325("325"),

  /**
   * Partial invoice
   */
  CODE_326("326"),

  /**
   * Commercial invoice which includes a packing list
   */
  CODE_331("331"),

  /**
   * Commercial invoice
   */
  CODE_380("380"),

  /**
   * Credit note
   */
  CODE_381("381"),

  /**
   * Commission note
   */
  CODE_382("382"),

  /**
   * Debit note
   */
  CODE_383("383"),

  /**
   * Corrected invoice
   */
  CODE_384("384"),

  /**
   * Consolidated invoice
   */
  CODE_385("385"),

  /**
   * Prepayment invoice
   */
  CODE_386("386"),

  /**
   * Hire invoice
   */
  CODE_387("387"),

  /**
   * Tax invoice
   */
  CODE_388("388"),

  /**
   * Self-billed invoice
   */
  CODE_389("389"),

  /**
   * Delcredere invoice
   */
  CODE_390("390"),

  /**
   * Factored invoice
   */
  CODE_393("393"),

  /**
   * Lease invoice
   */
  CODE_394("394"),

  /**
   * Consignment invoice
   */
  CODE_395("395"),

  /**
   * Factored credit note
   */
  CODE_396("396"),

  /**
   * Optical Character Reading (OCR) payment credit note
   */
  CODE_420("420"),

  /**
   * Debit advice
   */
  CODE_456("456"),

  /**
   * Reversal of debit
   */
  CODE_457("457"),

  /**
   * Reversal of credit
   */
  CODE_458("458"),

  /**
   * Self-billed corrective invoice
   */
  CODE_471("471"),

  /**
   * Factored corrective Invoice
   */
  CODE_472("472"),

  /**
   * Self-billed factored corrective invoice
   */
  CODE_473("473"),

  /**
   * Self billed prepayment invoice
   */
  CODE_500("500"),

  /**
   * Self billed factored invoice
   */
  CODE_501("501"),

  /**
   * Self billed factored credit note
   */
  CODE_502("502"),

  /**
   * Prepayment credit note
   */
  CODE_503("503"),

  /**
   * Self billed debit note
   */
  CODE_527("527"),

  /**
   * Forwarder's credit note
   */
  CODE_532("532"),

  /**
   * Forwarder's invoice discrepancy report
   */
  CODE_553("553"),

  /**
   * Insurer's invoice
   */
  CODE_575("575"),

  /**
   * Forwarder's invoice
   */
  CODE_623("623"),

  /**
   * Port charges documents
   */
  CODE_633("633"),

  /**
   * Invoice information for accounting purposes
   */
  CODE_751("751"),

  /**
   * Freight invoice
   */
  CODE_780("780"),

  /**
   * Claim notification
   */
  CODE_817("817"),

  /**
   * Consular invoice
   */
  CODE_870("870"),

  /**
   * Partial construction invoice
   */
  CODE_875("875"),

  /**
   * Partial final construction invoice
   */
  CODE_876("876"),

  /**
   * Final construction invoice
   */
  CODE_877("877"),

  /**
   * Customs invoice
   */
  CODE_935("935");

  private final String code;

  InvoiceTypeCode(String code) {
    this.code = code;
  }

  /**
   * Get InvoiceTypeCode by code value
   *
   * @param code the code to search for
   * @return the matching InvoiceTypeCode or null if not found
   */
  public static InvoiceTypeCode of(String code) {
    return Arrays.stream(InvoiceTypeCode.values())
                 .filter(e -> e.getCode()
                               .equals(code))
                 .findFirst()
                 .orElse(null);
  }

  /**
   * Returns the code as written in the documents.
   *
   * @return the UNTDID 1001 code, e.g. {@code 380}
   */
  public String getCode() {
    return code;
  }

  /**
   * Returns the English description, preserving the original API behavior.
   *
   * @return the English description
   */
  public String getDescription() {
    return getDescription(Locale.ENGLISH);
  }

  /**
   * Returns the description for the requested locale, falling back to English.
   *
   * @param locale the requested locale, never null
   * @return the localized description
   */
  public String getDescription(Locale locale) {
    return LocalizationFactory.getDescription(this,
                                              locale);
  }

  /**
   * Check if this is an invoice type, i.e. not a credit note
   *
   * @return true if this code is written as UBL Invoice
   */
  public boolean isInvoice() {
    return !isCreditNote();
  }

  /**
   * Check if this is a credit note type
   *
   * @return true if this is a credit note
   */
  public boolean isCreditNote() {
    // The codes EN 16931 maps to the UBL CreditNote document.
    return switch (code) {
      case "83", "261", "262", "296", "308", "381", "396", "420", "458", "502", "503", "532" -> true;
      default -> false;
    };
  }

  /**
   * Check if this is a debit note type
   *
   * @return true if this is a debit note
   */
  public boolean isDebitNote() {
    return switch (code) {
      case "80", "84", "383", "527" -> true;
      default -> false;
    };
  }

  /**
   * Check if XRechnung permits this type code (BR-DE-17)
   *
   * @return true for 326, 380, 381, 384, 389, 875, 876 and 877
   */
  public boolean isXrechnungAllowed() {
    return switch (code) {
      case "326", "380", "381", "384", "389", "875", "876", "877" -> true;
      default -> false;
    };
  }

  @Override
  public String toString() {
    return toString(Locale.ENGLISH);
  }

  /**
   * Formats the code and localized description.
   *
   * @param locale the requested locale, never null
   * @return the code and localized description
   */
  public String toString(Locale locale) {
    return getCode() + " - " + getDescription(locale);
  }
}
