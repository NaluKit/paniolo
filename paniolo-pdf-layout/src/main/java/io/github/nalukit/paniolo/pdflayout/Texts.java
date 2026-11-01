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
package io.github.nalukit.paniolo.pdflayout;

import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.common.value.UnitCode;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Labels and number, amount, and date formats of one language: German, English, or French. Missing
 * languages fall back to English, independently of the JVM default locale. Not thread-safe (number
 * formats); one instance per rendering.
 *
 * <p>The texts of the code lists of the model (VAT categories, payment means, units, document types)
 * exist in German and English only. For French the labels provide the common codes; other codes are
 * shown as code instead of an English text, and country names come from the JDK.
 */
final class Texts {
  private static final String      BUNDLE     = "io.github.nalukit.paniolo.pdflayout.Labels";
  /**
   * Languages with own labels but without code list texts in the model. Other languages without own
   * labels fall back to English labels and English code list texts alike.
   */
  private static final Set<String> LABELS_ONLY = Set.of("fr");

  final Locale locale;

  private final boolean codeLists;

  private final ResourceBundle    labels;
  private final DateTimeFormatter dates;
  private final DecimalFormat     numbers;

  Texts(Locale locale) {
    this.locale    = locale;
    this.codeLists = !LABELS_ONLY.contains(locale.getLanguage());
    this.labels  = ResourceBundle.getBundle(BUNDLE,
                                            locale,
                                            Texts.class.getClassLoader(),
                                            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    this.dates   = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                    .withLocale(locale);
    this.numbers = new DecimalFormat("#,##0.##",
                                     DecimalFormatSymbols.getInstance(locale));
  }

  static String iban(String value) {
    if (value == null) {
      return "";
    }
    String compact = value.replace(" ",
                                   "");
    return compact.matches("[A-Z]{2}\\d{2}[A-Z0-9]{10,30}") ?
           compact.replaceAll("(.{4})(?!$)",
                              "$1 ") :
           value;
  }

  String label(String key) {
    return labels.getString(key);
  }

  String label(String key,
               Object... arguments) {
    return new MessageFormat(labels.getString(key),
                             locale).format(arguments);
  }

  String optional(String key) {
    try {
      return labels.getString(key);
    } catch (MissingResourceException e) {
      return null;
    }
  }

  String title(InvoiceTypeCode type) {
    if (type == null) {
      return label("title.default");
    }
    String title = optional("title." + type.getCode());
    if (title != null) {
      return title;
    }
    return codeLists ?
           type.getDescription(locale) :
           label("title.default");
  }

  /**
   * The text of a VAT category, e.g. "Standard rate".
   */
  String vatCategory(DutyTaxFeeCategory category) {
    if (category == null) {
      return "";
    }
    String text = optional("vat.category." + category.getCode());
    if (text != null) {
      return text;
    }
    return codeLists ?
           category.getDescription(locale) :
           category.getCode();
  }

  /**
   * The text of a payment means code, e.g. "SEPA credit transfer".
   */
  String paymentMeans(PaymentMeansCode means) {
    if (means == null) {
      return "";
    }
    String text = optional("payment.means." + means.getCode());
    if (text != null) {
      return text;
    }
    return codeLists ?
           means.getDescription(locale) :
           label("payment.means.code",
                 means.getCode());
  }

  /**
   * An amount with currency, with the scale of the value but at least two decimals.
   */
  String money(BigDecimal value,
               CurrencyCode currency) {
    if (value == null) {
      return "";
    }
    int digits = Math.max(2,
                          value.stripTrailingZeros()
                               .scale());
    NumberFormat format;
    try {
      format = NumberFormat.getCurrencyInstance(locale);
      format.setCurrency(Currency.getInstance(currency.getCode()));
    } catch (IllegalArgumentException | NullPointerException e) {
      format = NumberFormat.getNumberInstance(locale);
      format.setGroupingUsed(true);
      format.setMinimumFractionDigits(digits);
      format.setMaximumFractionDigits(digits);
      return format.format(value) +
             (currency == null ?
              "" :
              " " + currency.getCode());
    }
    format.setMinimumFractionDigits(digits);
    format.setMaximumFractionDigits(digits);
    return format.format(value);
  }

  /**
   * A quantity without trailing zeros.
   */
  String number(BigDecimal value) {
    if (value == null) {
      return "";
    }
    numbers.setMaximumFractionDigits(Math.max(0,
                                              value.stripTrailingZeros()
                                                   .scale()));
    return numbers.format(value);
  }

  String percent(BigDecimal value) {
    return value == null ?
           "" :
           label("format.percent",
                 number(value));
  }

  String date(LocalDate date) {
    return date == null ?
           "" :
           dates.format(date);
  }

  String period(LocalDate start,
                LocalDate end) {
    if (start == null && end == null) {
      return "";
    }
    if (start == null || end == null || start.equals(end)) {
      return date(start != null ?
                  start :
                  end);
    }
    return date(start) + " – " + date(end);
  }

  String country(Country country) {
    if (country == null) {
      return "";
    }
    if (!codeLists) {
      String name = Locale.of("",
                              country.name())
                          .getDisplayCountry(locale);
      if (!name.isBlank() && !name.equals(country.name())) {
        return name;
      }
    }
    return country.getText(locale);
  }

  String unit(UnitCode unit) {
    if (unit == null) {
      return "";
    }
    String abbreviation = optional("unit." + unit.getCode());
    if (abbreviation != null) {
      return abbreviation;
    }
    String description = codeLists ?
                         unit.getDescription(locale) :
                         null;
    return description == null || description.length() > 14 ?
           unit.getCode() :
           description;
  }
}
