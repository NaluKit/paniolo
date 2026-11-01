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
 * UNTDID 5305 - Duty or tax or fee category code
 * Coded identification of a duty or tax or fee category.
 */
public enum DutyTaxFeeCategory {

  /**
   * Mixed tax rate
   */
  A("A"),

  /**
   * Lower rate
   */
  AA("AA"),

  /**
   * Exempt for resale
   */
  AB("AB"),

  /**
   * Value Added Tax (VAT) not now due for payment
   */
  AC("AC"),

  /**
   * Value Added Tax (VAT) due from a previous invoice
   */
  AD("AD"),

  /**
   * VAT Reverse Charge
   */
  AE("AE"),

  /**
   * Transferred (VAT)
   */
  B("B"),

  /**
   * Duty paid by supplier
   */
  C("C"),

  /**
   * Exempt from tax
   */
  E("E"),

  /**
   * Free export item, tax not charged
   */
  G("G"),

  /**
   * Higher rate
   */
  H("H"),

  /**
   * Services outside scope of tax
   */
  O("O"),

  /**
   * Intra-community supply
   */
  K("K"),

  /**
   * Canary Islands general indirect tax
   */
  L("L"),

  /**
   * Tax in Ceuta and Melilla
   */
  M("M"),

  /**
   * Standard rate.
   */
  S("S"),

  /**
   * Zero rated goods
   */
  Z("Z");

  private final String code;

  DutyTaxFeeCategory(String code) {
    this.code = code;
  }

  /**
   * Get DutyTaxFeeCategory by code value
   *
   * @param code the code to search for
   * @return the matching DutyTaxFeeCategory or null if not found
   */
  public static DutyTaxFeeCategory of(String code) {
    return Arrays.stream(DutyTaxFeeCategory.values())
                 .filter(e -> e.getCode()
                               .equals(code))
                 .findFirst()
                 .orElse(null);
  }

  /**
   * Returns the code as written in the documents.
   *
   * @return the UNTDID 5305 code, e.g. {@code S}
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
   * Checks weather a code can be used with allowance or not
   *
   * @return true if this is a valid code
   */
  public boolean isAllowanceRelated() {
    return this.code.equals("S") || this.code.equals("Z") || this.code.equals("E") || this.code.equals("AE") || this.code.equals("K") || this.code.equals("G") || this.code.equals("O") || this.code.equals("L") || this.code.equals("M");
  }

  /**
   * Checks weather a code can be used with charges or not
   *
   * @return true if this is a valid code
   */
  public boolean isChargeRelated() {
    return this.code.equals("S") || this.code.equals("Z") || this.code.equals("E") || this.code.equals("AE") || this.code.equals("K") || this.code.equals("G") || this.code.equals("O") || this.code.equals("L") || this.code.equals("M");
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
