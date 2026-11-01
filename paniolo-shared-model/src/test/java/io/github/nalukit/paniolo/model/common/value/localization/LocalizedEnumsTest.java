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
package io.github.nalukit.paniolo.model.common.value.localization;

import io.github.nalukit.paniolo.model.common.value.AllowanceChargeIdentification;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.DateTimeQualifier;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.common.value.TextSubjectQualifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizedEnumsTest {
  static Stream<Class<? extends Enum<?>>> enumTypes() {
    return Stream.of(AllowanceChargeIdentification.class,
                     Country.class,
                     CurrencyCode.class,
                     DateTimeQualifier.class,
                     DutyTaxFeeCategory.class,
                     InvoiceTypeCode.class,
                     PaymentMeansCode.class,
                     TextSubjectQualifier.class);
  }

  static Stream<Arguments> examples() {
    return Stream.of(Arguments.of(Country.DE,
                                  "Germany",
                                  "Deutschland"),
                     Arguments.of(CurrencyCode.USD,
                                  "United States Dollar",
                                  "US-Dollar"),
                     Arguments.of(DateTimeQualifier.CODE_3,
                                  "Invoice date/time",
                                  "Rechnungsdatum bzw. Rechnungszeit"),
                     Arguments.of(DutyTaxFeeCategory.S,
                                  "Standard rate",
                                  "Regelsteuersatz"),
                     Arguments.of(InvoiceTypeCode.CODE_381,
                                  "Credit note",
                                  "Gutschrift"),
                     Arguments.of(PaymentMeansCode.CODE_58,
                                  "SEPA credit transfer",
                                  "SEPA-Überweisung"),
                     Arguments.of(TextSubjectQualifier.AAA,
                                  "Goods description",
                                  "Warenbeschreibung"));
  }

  @ParameterizedTest
  @MethodSource("examples")
  void resolvesBothLanguagesThroughTheEnumAndFactory(Enum<?> value,
                                                     String english,
                                                     String german)
      throws Exception {
    assertEquals(english,
                 localizedDescription(value,
                                      Locale.ENGLISH));
    assertEquals(german,
                 localizedDescription(value,
                                      Locale.GERMAN));
    assertEquals(english,
                 LocalizationFactory.getDescription(value,
                                                    Locale.ENGLISH));
    assertEquals(german,
                 LocalizationFactory.getDescription(value,
                                                    Locale.GERMAN));
  }

  @ParameterizedTest
  @MethodSource("enumTypes")
  void bothBundlesHaveAllDisplayFieldsWithoutRelyingOnFallback(Class<? extends Enum<?>> type)
      throws IOException {
    Properties english = properties(type,
                                    "");
    Properties german = properties(type,
                                   "_de");
    String[] fields = type == AllowanceChargeIdentification.class ?
                      new String[] { "name",
                                     "description" } :
                      type == Country.class ?
                      new String[] { "text" } :
                      new String[] { "description" };
    assertEquals(type.getEnumConstants().length * fields.length,
                 english.size());
    assertEquals(english.stringPropertyNames(),
                 german.stringPropertyNames());
    for (Enum<?> value : type.getEnumConstants()) {
      for (String field : fields) {
        String key = value.name() + "." + field;
        assertFalse(Objects.requireNonNull(english.getProperty(key),
                                           key)
                           .isBlank(),
                    key);
        assertFalse(Objects.requireNonNull(german.getProperty(key),
                                           key)
                           .isBlank(),
                    key);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("enumTypes")
  void preservesLegacyGettersFormattingAndCodeLookup(Class<? extends Enum<?>> type)
      throws Exception {
    Properties bundle = properties(type,
                                   type == Country.class ?
                                   "_de" :
                                   "");
    String field = type == Country.class ?
                   "text" :
                   "description";
    for (Enum<?> value : type.getEnumConstants()) {
      String legacy = (String) type.getMethod(type == Country.class ?
                                              "getText" :
                                              "getDescription")
                                   .invoke(value);
      assertEquals(bundle.getProperty(value.name() + "." + field),
                   legacy);
      String code = type == Country.class ?
                    value.name() :
                    (String) type.getMethod("getCode")
                                 .invoke(value);
      assertSame(value,
                 type.getMethod("of",
                                String.class)
                     .invoke(null,
                             code));
      String expected = type == Country.class ?
                        code :
                        code +
                        " - " +
                        (type == AllowanceChargeIdentification.class ?
                         bundle.getProperty(value.name() + ".name") :
                         legacy);
      assertEquals(expected,
                   value.toString());
    }
  }

  @ParameterizedTest
  @MethodSource("enumTypes")
  void supportsRegionalLocalesFallbackAndLocalizedFormatting(Class<? extends Enum<?>> type)
      throws Exception {
    for (Enum<?> value : type.getEnumConstants()) {
      String german = localizedDescription(value,
                                           Locale.GERMAN);
      assertEquals(german,
                   localizedDescription(value,
                                        Locale.forLanguageTag("de-CH")));
      assertEquals(localizedDescription(value,
                                        Locale.ENGLISH),
                   localizedDescription(value,
                                        Locale.JAPANESE));
      String code = type == Country.class ?
                    value.name() :
                    (String) type.getMethod("getCode")
                                 .invoke(value);
      String text = value instanceof AllowanceChargeIdentification allowance ?
                    allowance.getName(Locale.GERMAN) :
                    german;
      assertEquals(code + " - " + text,
                   type.getMethod("toString",
                                  Locale.class)
                       .invoke(value,
                               Locale.GERMAN));
    }
  }

  @Test
  void countryTextRemainsGermanByDefaultAndHasExplicitLocalizedAccess() {
    assertEquals("Deutschland",
                 Country.DE.getText());
    assertEquals("Germany",
                 Country.DE.getText(Locale.ENGLISH));
    assertEquals("Germany",
                 LocalizationFactory.getText(Country.DE,
                                             Locale.ENGLISH));
    assertEquals("DE",
                 Country.DE.toString());
    assertEquals("DE - Deutschland",
                 Country.DE.toString(Locale.GERMAN));
  }

  @Test
  void businessPredicatesUseCodesRegardlessOfDisplayLanguage() {
    assertTrue(DateTimeQualifier.CODE_34.isDeliveryRelated());
    assertTrue(DateTimeQualifier.CODE_265.isPaymentRelated());
    assertTrue(DateTimeQualifier.CODE_8.isOrderRelated());
    assertFalse(DateTimeQualifier.CODE_3.isOrderRelated());
    assertTrue(TextSubjectQualifier.CCI.isCustomsRelated());
    assertTrue(TextSubjectQualifier.INV.isPaymentRelated());
    assertTrue(TextSubjectQualifier.TRA.isDeliveryRelated());
    assertTrue(TextSubjectQualifier.AAC.isDangerousGoodsRelated());
    assertFalse(TextSubjectQualifier.AAI.isDangerousGoodsRelated());
    assertEquals("Lieferinformationen",
                 TextSubjectQualifier.DEL.getDescription(Locale.GERMAN));
    assertTrue(TextSubjectQualifier.DEL.isDeliveryRelated());
  }

  @Test
  void rejectsUnsupportedEnumsAndNullArguments() {
    assertThrows(IllegalArgumentException.class,
                 () -> LocalizationFactory.getDescription(Unsupported.CODE_83,
                                                          Locale.GERMAN));
    assertThrows(NullPointerException.class,
                 () -> LocalizationFactory.getDescription((Enum<?>) null,
                                                          Locale.GERMAN));
    assertThrows(NullPointerException.class,
                 () -> LocalizationFactory.getText(null,
                                                   Locale.GERMAN));
    assertThrows(NullPointerException.class,
                 () -> Country.DE.getText(null));
    assertThrows(NullPointerException.class,
                 () -> CurrencyCode.EUR.getDescription(null));
  }

  private String localizedDescription(Enum<?> value,
                                      Locale locale)
      throws Exception {
    return (String) value.getDeclaringClass()
                         .getMethod(value instanceof Country ?
                                    "getText" :
                                    "getDescription",
                                    Locale.class)
                         .invoke(value,
                                 locale);
  }

  private Properties properties(Class<?> type,
                                String localeSuffix)
      throws IOException {
    String filename   = type.getSimpleName() + localeSuffix + ".properties";
    var    properties = new Properties();
    try (var input = Objects.requireNonNull(LocalizationFactory.class.getResourceAsStream(filename),
                                            filename); var reader = new InputStreamReader(input,
                                                                                          StandardCharsets.UTF_8)) {
      properties.load(reader);
    }
    return properties;
  }

  private enum Unsupported {
    CODE_83
  }
}
