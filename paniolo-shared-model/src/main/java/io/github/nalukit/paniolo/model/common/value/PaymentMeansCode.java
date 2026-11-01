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
 * UN/EDIFACT 4461 Payment means code: the codes permitted by EN 16931 (BR-CL-16), named as in the
 * UN/CEFACT directory. Defines the payment method/instrument used for a transaction
 */
public enum PaymentMeansCode {

  /**
   * Instrument not defined
   */
  CODE_1("1"),

  /**
   * Automated clearing house credit
   */
  CODE_2("2"),

  /**
   * Automated clearing house debit
   */
  CODE_3("3"),

  /**
   * ACH demand debit reversal
   */
  CODE_4("4"),

  /**
   * ACH demand credit reversal
   */
  CODE_5("5"),

  /**
   * ACH demand credit
   */
  CODE_6("6"),

  /**
   * ACH demand debit
   */
  CODE_7("7"),

  /**
   * Hold
   */
  CODE_8("8"),

  /**
   * National or regional clearing
   */
  CODE_9("9"),

  /**
   * In cash
   */
  CODE_10("10"),

  /**
   * ACH savings credit reversal
   */
  CODE_11("11"),

  /**
   * ACH savings debit reversal
   */
  CODE_12("12"),

  /**
   * ACH savings credit
   */
  CODE_13("13"),

  /**
   * ACH savings debit
   */
  CODE_14("14"),

  /**
   * Bookentry credit
   */
  CODE_15("15"),

  /**
   * Bookentry debit
   */
  CODE_16("16"),

  /**
   * ACH demand cash concentration/disbursement (CCD) credit
   */
  CODE_17("17"),

  /**
   * ACH demand cash concentration/disbursement (CCD) debit
   */
  CODE_18("18"),

  /**
   * ACH demand corporate trade payment (CTP) credit
   */
  CODE_19("19"),

  /**
   * Cheque
   */
  CODE_20("20"),

  /**
   * Banker's draft
   */
  CODE_21("21"),

  /**
   * Certified banker's draft
   */
  CODE_22("22"),

  /**
   * Bank cheque (issued by a banking or similar establishment)
   */
  CODE_23("23"),

  /**
   * Bill of exchange awaiting acceptance
   */
  CODE_24("24"),

  /**
   * Certified cheque
   */
  CODE_25("25"),

  /**
   * Local cheque
   */
  CODE_26("26"),

  /**
   * ACH demand corporate trade payment (CTP) debit
   */
  CODE_27("27"),

  /**
   * ACH demand corporate trade exchange (CTX) credit
   */
  CODE_28("28"),

  /**
   * ACH demand corporate trade exchange (CTX) debit
   */
  CODE_29("29"),

  /**
   * Credit transfer
   */
  CODE_30("30"),

  /**
   * Debit transfer
   */
  CODE_31("31"),

  /**
   * ACH demand cash concentration/disbursement plus (CCD+) credit
   */
  CODE_32("32"),

  /**
   * ACH demand cash concentration/disbursement plus (CCD+) debit
   */
  CODE_33("33"),

  /**
   * ACH prearranged payment and deposit (PPD)
   */
  CODE_34("34"),

  /**
   * ACH savings cash concentration/disbursement (CCD) credit
   */
  CODE_35("35"),

  /**
   * ACH savings cash concentration/disbursement (CCD) debit
   */
  CODE_36("36"),

  /**
   * ACH savings corporate trade payment (CTP) credit
   */
  CODE_37("37"),

  /**
   * ACH savings corporate trade payment (CTP) debit
   */
  CODE_38("38"),

  /**
   * ACH savings corporate trade exchange (CTX) credit
   */
  CODE_39("39"),

  /**
   * ACH savings corporate trade exchange (CTX) debit
   */
  CODE_40("40"),

  /**
   * ACH savings cash concentration/disbursement plus (CCD+) credit
   */
  CODE_41("41"),

  /**
   * Payment to bank account
   */
  CODE_42("42"),

  /**
   * ACH savings cash concentration/disbursement plus (CCD+) debit
   */
  CODE_43("43"),

  /**
   * Accepted bill of exchange
   */
  CODE_44("44"),

  /**
   * Referenced home-banking credit transfer
   */
  CODE_45("45"),

  /**
   * Interbank debit transfer
   */
  CODE_46("46"),

  /**
   * Home-banking debit transfer
   */
  CODE_47("47"),

  /**
   * Bank card
   */
  CODE_48("48"),

  /**
   * Direct debit
   */
  CODE_49("49"),

  /**
   * Payment by postgiro
   */
  CODE_50("50"),

  /**
   * FR, norme 6 97-Telereglement CFONB (French Organisation for Banking Standards) - Option A
   */
  CODE_51("51"),

  /**
   * Urgent commercial payment
   */
  CODE_52("52"),

  /**
   * Urgent Treasury Payment
   */
  CODE_53("53"),

  /**
   * Credit card
   */
  CODE_54("54"),

  /**
   * Debit card
   */
  CODE_55("55"),

  /**
   * Bankgiro
   */
  CODE_56("56"),

  /**
   * Standing agreement
   */
  CODE_57("57"),

  /**
   * SEPA credit transfer
   */
  CODE_58("58"),

  /**
   * SEPA direct debit
   */
  CODE_59("59"),

  /**
   * Promissory note
   */
  CODE_60("60"),

  /**
   * Promissory note signed by the debtor
   */
  CODE_61("61"),

  /**
   * Promissory note signed by the debtor and endorsed by a bank
   */
  CODE_62("62"),

  /**
   * Promissory note signed by the debtor and endorsed by a third party
   */
  CODE_63("63"),

  /**
   * Promissory note signed by a bank
   */
  CODE_64("64"),

  /**
   * Promissory note signed by a bank and endorsed by another bank
   */
  CODE_65("65"),

  /**
   * Promissory note signed by a third party
   */
  CODE_66("66"),

  /**
   * Promissory note signed by a third party and endorsed by a bank
   */
  CODE_67("67"),

  /**
   * Online payment service
   */
  CODE_68("68"),

  /**
   * Transfer Advice
   */
  CODE_69("69"),

  /**
   * Bill drawn by the creditor on the debtor
   */
  CODE_70("70"),

  /**
   * Bill drawn by the creditor on a bank
   */
  CODE_74("74"),

  /**
   * Bill drawn by the creditor, endorsed by another bank
   */
  CODE_75("75"),

  /**
   * Bill drawn by the creditor on a bank and endorsed by a third party
   */
  CODE_76("76"),

  /**
   * Bill drawn by the creditor on a third party
   */
  CODE_77("77"),

  /**
   * Bill drawn by creditor on third party, accepted and endorsed by bank
   */
  CODE_78("78"),

  /**
   * Not transferable banker's draft
   */
  CODE_91("91"),

  /**
   * Not transferable local cheque
   */
  CODE_92("92"),

  /**
   * Reference giro
   */
  CODE_93("93"),

  /**
   * Urgent giro
   */
  CODE_94("94"),

  /**
   * Free format giro
   */
  CODE_95("95"),

  /**
   * Requested method for payment was not used
   */
  CODE_96("96"),

  /**
   * Clearing between partners
   */
  CODE_97("97"),

  /**
   * JP, Electronically Recorded Monetary Claims
   */
  CODE_98("98"),

  /**
   * Mutually defined
   */
  CODE_ZZZ("ZZZ");

  private final String code;

  PaymentMeansCode(String code) {
    this.code = code;
  }

  /**
   * Get PaymentMeansCode by code value
   *
   * @param code the code to search for
   * @return the matching PaymentMeansCode or null if not found
   */
  public static PaymentMeansCode of(String code) {
    return Arrays.stream(PaymentMeansCode.values())
                 .filter(e -> e.getCode()
                               .equals(code))
                 .findFirst()
                 .orElse(null);
  }

  /**
   * Returns the code as written in the documents.
   *
   * @return the UNTDID 4461 code, e.g. {@code 58}
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
