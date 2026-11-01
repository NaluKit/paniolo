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
 * UNTDID 2005 Date/time/period qualifier
 * Code qualifying a date, time or period
 */
public enum DateTimeQualifier {

  /**
   * Delivery date/time, requested
   */
  CODE_2("2"),

  /**
   * Invoice date/time
   */
  CODE_3("3"),

  /**
   * Order date/time
   */
  CODE_4("4"),

  /**
   * Effective date/time
   */
  CODE_7("7"),

  /**
   * Order received date/time
   */
  CODE_8("8"),

  /**
   * Despatch date and or time
   */
  CODE_11("11"),

  /**
   * Terms net due date
   */
  CODE_13("13"),

  /**
   * Delivery date/time, estimated
   */
  CODE_17("17"),

  /**
   * Delivery date/time, actual
   */
  CODE_34("34"),

  /**
   * Delivery date/time, actual
   */
  CODE_35("35"),

  /**
   * Expiry date
   */
  CODE_36("36"),

  /**
   * Goods receipt date/time
   */
  CODE_50("50"),

  /**
   * Clearance date (Customs)
   */
  CODE_58("58"),

  /**
   * Delivery date/time, promised for
   */
  CODE_69("69"),

  /**
   * Bill of lading date
   */
  CODE_95("95"),

  /**
   * Contract date
   */
  CODE_126("126"),

  /**
   * Tax point date
   */
  CODE_131("131"),

  /**
   * Payment date
   */
  CODE_138("138"),

  /**
   * Issue date
   */
  CODE_182("182"),

  /**
   * Invoicing period
   */
  CODE_263("263"),

  /**
   * Due date
   */
  CODE_265("265"),

  /**
   * Accounting period
   */
  CODE_322("322"),

  /**
   * Paid to date
   */
  CODE_432("432"),

  /**
   * Mutually defined
   */
  CODE_ZZZ("ZZZ");

  private final String code;

  DateTimeQualifier(String code) {
    this.code = code;
  }

  /**
   * Get DateTimeQualifier by code value
   *
   * @param code the code to search for
   * @return the matching DateTimeQualifier or null if not found
   */
  public static DateTimeQualifier of(String code) {
    return Arrays.stream(DateTimeQualifier.values())
                 .filter(e -> e.getCode()
                               .equals(code))
                 .findFirst()
                 .orElse(null);
  }

  /**
   * Returns the code as written in the documents.
   *
   * @return the UNTDID 2005 code
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
   * Check if this code is relevant for xRechnjung
   *
   * @return true if this is a relevant ode for xRechnung
   */
  public boolean isXrechnungRelevant() {
    return code.equals("3") || code.equals("34") || code.equals("432");
  }

  /**
   * Check if this qualifier is related to delivery
   *
   * @return true if this is a delivery-related qualifier
   */
  public boolean isDeliveryRelated() {
    return switch (code) {
      case "2", "17", "34", "35", "69" -> true;
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
      case "13", "138", "265" -> true;
      default -> false;
    };
  }

  /**
   * Check if this qualifier is related to ordering
   *
   * @return true if this is an order-related qualifier
   */
  public boolean isOrderRelated() {
    return switch (code) {
      case "4", "8" -> true;
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
