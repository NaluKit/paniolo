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
 * UNTDID 4451 Text subject code qualifier
 * Code specifying subject of a free text
 */
public enum TextSubjectQualifier {

  /**
   * Goods description
   */
  AAA("AAA"),

  /**
   * Terms of payments
   */
  AAB("AAB"),

  /**
   * Dangerous goods additional information
   */
  AAC("AAC"),

  /**
   * Dangerous goods, technical name
   */
  AAD("AAD"),

  /**
   * Acknowledgement description
   */
  AAE("AAE"),

  /**
   * Rate additional information
   */
  AAF("AAF"),

  /**
   * Party instructions
   */
  AAG("AAG"),

  /**
   * Customs information
   */
  AAH("AAH"),

  /**
   * General information
   */
  AAI("AAI"),

  /**
   * Additional conditions of sale/purchase
   */
  AAJ("AAJ"),

  /**
   * Price conditions
   */
  AAK("AAK"),

  /**
   * Terms of delivery
   */
  AAR("AAR"),

  /**
   * Bill of lading remarks
   */
  AAS("AAS"),

  /**
   * Consignment invoice information
   */
  AAU("AAU"),

  /**
   * Clearance invoice information
   */
  AAV("AAV"),

  /**
   * Additional export information
   */
  AAZ("AAZ"),

  /**
   * Tariff statements
   */
  ABA("ABA"),

  /**
   * Conditions of sale or purchase
   */
  ABC("ABC"),

  /**
   * Nature of transaction
   */
  ABD("ABD"),

  /**
   * Government information
   */
  ABL("ABL"),

  /**
   * Accounting information
   */
  ABN("ABN"),

  /**
   * Regulatory information
   */
  ABY("ABY"),

  /**
   * Additional information
   */
  ACB("ACB"),

  /**
   * Reason
   */
  ACD("ACD"),

  /**
   * Dispute
   */
  ACE("ACE"),

  /**
   * Allowance/charge information
   */
  ALC("ALC"),

  /**
   * All documents
   */
  ALL("ALL"),

  /**
   * Arrival conditions
   */
  ARR("ARR"),

  /**
   * Authentication
   */
  AUT("AUT"),

  /**
   * Bill of lading clause
   */
  BLC("BLC"),

  /**
   * Transport document remarks
   */
  BLR("BLR"),

  /**
   * Customs clearance instructions
   */
  CCI("CCI"),

  /**
   * Change information
   */
  CHG("CHG"),

  /**
   * Customs clearance instruction import
   */
  CIP("CIP"),

  /**
   * Order information
   */
  COI("COI"),

  /**
   * Customer remarks
   */
  CUR("CUR"),

  /**
   * Customs declaration information
   */
  CUS("CUS"),

  /**
   * Damage remarks
   */
  DAR("DAR"),

  /**
   * Declaration
   */
  DCL("DCL"),

  /**
   * Delivery information
   */
  DEL("DEL"),

  /**
   * Delivery instructions
   */
  DIN("DIN"),

  /**
   * Documentation instructions
   */
  DOC("DOC"),

  /**
   * Duty declaration
   */
  DUT("DUT"),

  /**
   * Entire transaction set
   */
  GEN("GEN"),

  /**
   * Handling instructions
   */
  HAN("HAN"),

  /**
   * Hazard information
   */
  HAZ("HAZ"),

  /**
   * Information for consignee
   */
  ICN("ICN"),

  /**
   * Insurance instructions
   */
  IIN("IIN"),

  /**
   * Invoice instruction
   */
  INV("INV"),

  /**
   * Line item
   */
  LIN("LIN"),

  /**
   * Loading instruction
   */
  LOI("LOI"),

  /**
   * Additional marks/numbers information
   */
  MKS("MKS"),

  /**
   * Order instruction
   */
  ORI("ORI"),

  /**
   * Other service information
   */
  OSI("OSI"),

  /**
   * Packing/marking information
   */
  PAC("PAC"),

  /**
   * Payment instructions information
   */
  PAI("PAI"),

  /**
   * Payables information
   */
  PAY("PAY"),

  /**
   * Packaging information
   */
  PKG("PKG"),

  /**
   * Payment detail/remittance information
   */
  PMD("PMD"),

  /**
   * Payment information
   */
  PMT("PMT"),

  /**
   * Product information
   */
  PRD("PRD"),

  /**
   * Price calculation formula
   */
  PRF("PRF"),

  /**
   * Priority information
   */
  PRI("PRI"),

  /**
   * Purchasing information
   */
  PUR("PUR"),

  /**
   * Quarantine instructions
   */
  QIN("QIN"),

  /**
   * Quality demands/requirements
   */
  QQD("QQD"),

  /**
   * Quotation instruction/information
   */
  QUT("QUT"),

  /**
   * Risk and handling information
   */
  RAH("RAH"),

  /**
   * Regulatory information
   */
  REG("REG"),

  /**
   * Receivables
   */
  REV("REV"),

  /**
   * Safety information
   */
  SAF("SAF"),

  /**
   * Sender's instruction to carrier
   */
  SIC("SIC"),

  /**
   * Special instructions
   */
  SIN("SIN"),

  /**
   * Special handling
   */
  SPH("SPH"),

  /**
   * Special service request
   */
  SSR("SSR"),

  /**
   * Supplier remarks
   */
  SUR("SUR"),

  /**
   * Tariff and class applied
   */
  TCA("TCA"),

  /**
   * Transport details remarks
   */
  TDT("TDT"),

  /**
   * Transportation information
   */
  TRA("TRA"),

  /**
   * Tax declaration
   */
  TXD("TXD"),

  /**
   * Warehouse instruction/information
   */
  WHI("WHI"),

  /**
   * Mutually defined
   */
  ZZZ("ZZZ");

  private final String code;

  TextSubjectQualifier(String code) {
    this.code = code;
  }

  /**
   * Get TextSubjectQualifier by code value
   *
   * @param code the code to search for
   * @return the matching TextSubjectQualifier or null if not found
   */
  public static TextSubjectQualifier of(String code) {
    return Arrays.stream(TextSubjectQualifier.values())
                 .filter(e -> e.getCode()
                               .equals(code))
                 .findFirst()
                 .orElse(null);
  }

  /**
   * Returns the code as written in the documents.
   *
   * @return the UNTDID 4451 code, e.g. {@code AAI}
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
   * Check if this qualifier is related to customs
   *
   * @return true if this is a customs-related qualifier
   */
  public boolean isCustomsRelated() {
    return switch (code) {
      case "AAH", "CCI", "CIP", "CUS" -> true;
      default -> false;
    };
  }

  /**
   * Check if this qualifier is related to payment
   *
   * @return true if this is a payment-related qualifier
   */
  public boolean isPaymentRelated() {
    return switch (code) {
      case "AAB", "AAU", "AAV", "INV", "PAI", "PMD", "PMT" -> true;
      default -> false;
    };
  }

  /**
   * Check if this qualifier is related to delivery/transport
   *
   * @return true if this is a delivery/transport-related qualifier
   */
  public boolean isDeliveryRelated() {
    return switch (code) {
      case "AAR", "BLR", "DEL", "DIN", "TDT", "TRA" -> true;
      default -> false;
    };
  }

  /**
   * Check if this qualifier is related to dangerous goods
   *
   * @return true if this is a dangerous goods-related qualifier
   */
  public boolean isDangerousGoodsRelated() {
    return switch (code) {
      case "AAC", "AAD" -> true;
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
