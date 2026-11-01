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
 * UNTDID 5189 - Allowance or charge identification code, restricted to the allowance reason codes
 * permitted by EN 16931 (BR-CL-19). Names follow the UN/CEFACT directory. Charge reason codes use
 * UNTDID 7161 and are passed as text.
 */
public enum AllowanceChargeIdentification {

  /**
   * Bonus for works ahead of schedule
   */
  CODE_41("41"),

  /**
   * Other bonus
   */
  CODE_42("42"),

  /**
   * Manufacturer's consumer discount
   */
  CODE_60("60"),

  /**
   * Due to military status
   */
  CODE_62("62"),

  /**
   * Due to work accident
   */
  CODE_63("63"),

  /**
   * Special agreement
   */
  CODE_64("64"),

  /**
   * Production error discount
   */
  CODE_65("65"),

  /**
   * New outlet discount
   */
  CODE_66("66"),

  /**
   * Sample discount
   */
  CODE_67("67"),

  /**
   * End-of-range discount
   */
  CODE_68("68"),

  /**
   * Incoterm discount
   */
  CODE_70("70"),

  /**
   * Point of sales threshold allowance
   */
  CODE_71("71"),

  /**
   * Material surcharge/deduction
   */
  CODE_88("88"),

  /**
   * Discount
   */
  CODE_95("95"),

  /**
   * Special rebate
   */
  CODE_100("100"),

  /**
   * Fixed long term
   */
  CODE_102("102"),

  /**
   * Temporary
   */
  CODE_103("103"),

  /**
   * Standard
   */
  CODE_104("104"),

  /**
   * Yearly turnover
   */
  CODE_105("105");

  private final String code;

  AllowanceChargeIdentification(String code) {
    this.code = code;
  }

  /**
   * Finds an enum value by its code
   *
   * @param code The code to search for
   * @return The corresponding enum value or null
   */
  public static AllowanceChargeIdentification of(String code) {
    return Arrays.stream(AllowanceChargeIdentification.values())
                 .filter(e -> e.getCode()
                               .equals(code))
                 .findFirst()
                 .orElse(null);
  }

  /**
   * Returns the language-independent UNTDID code.
   *
   * @return the code
   */
  public String getCode() {
    return code;
  }

  /**
   * Returns the English name, preserving the original API behavior.
   *
   * @return the English name
   */
  public String getName() {
    return getName(Locale.ENGLISH);
  }

  /**
   * Returns the name for the requested locale, falling back to English.
   *
   * @param locale the requested locale, never null
   * @return the localized name
   */
  public String getName(Locale locale) {
    return LocalizationFactory.getName(this,
                                       locale);
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
   * Formats the code and English name.
   *
   * @return the code and English name
   */
  @Override
  public String toString() {
    return toString(Locale.ENGLISH);
  }

  /**
   * Formats the code and name for the requested locale.
   *
   * @param locale the requested locale, never null
   * @return the code and localized name
   */
  public String toString(Locale locale) {
    return code + " - " + getName(locale);
  }
}
