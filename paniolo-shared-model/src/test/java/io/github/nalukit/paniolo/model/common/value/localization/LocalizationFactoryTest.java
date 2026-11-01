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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalizationFactoryTest {
  @Test
  void resolvesGermanNamesAndDescriptionsThroughTheEnum() {
    var value = AllowanceChargeIdentification.CODE_95;
    assertEquals("Rabatt",
                 value.getName(Locale.GERMAN));
    assertEquals("Eine Ermäßigung des üblichen Preises oder Listenpreises.",
                 value.getDescription(Locale.GERMAN));
    assertEquals("95 - Rabatt",
                 value.toString(Locale.GERMAN));
  }

  @Test
  void retainsTheEnglishApiAndLanguageIndependentCodes() {
    var value = AllowanceChargeIdentification.CODE_41;
    assertEquals("Bonus for works ahead of schedule",
                 value.getName());
    assertEquals("Bonus for completing work ahead of schedule.",
                 value.getDescription());
    assertEquals("41 - Bonus for works ahead of schedule",
                 value.toString());
    assertEquals("41",
                 value.getCode());
    assertEquals("CODE_41",
                 value.name());
    assertSame(value,
               AllowanceChargeIdentification.of("41"));
    assertNull(AllowanceChargeIdentification.of("unknown"));
    assertNull(AllowanceChargeIdentification.of(null));
  }

  @Test
  void exposesFactoryAccessWithAnExplicitLocale() {
    assertEquals("Discount",
                 LocalizationFactory.getName(AllowanceChargeIdentification.CODE_95,
                                             Locale.ENGLISH));
    assertEquals("Zu- oder Abschlag, berechnet für höheren oder geringeren Materialverbrauch.",
                 LocalizationFactory.getDescription(AllowanceChargeIdentification.CODE_88,
                                                    Locale.GERMAN));
  }

  @Test
  void fallsBackFromRegionToLanguageAndFromUnsupportedLanguagesToEnglish() {
    var value = AllowanceChargeIdentification.CODE_88;
    assertEquals("Material-Zu-/Abschlag",
                 value.getName(Locale.forLanguageTag("de-CH")));
    assertEquals("Material surcharge/deduction",
                 value.getName(Locale.JAPANESE));
    assertEquals("Surcharge/deduction, calculated for higher/ lower material's consumption.",
                 value.getDescription(Locale.JAPANESE));
    assertEquals("Material surcharge/deduction",
                 value.getName(Locale.ROOT));
  }

  @Test
  @ResourceLock(Resources.LOCALE)
  void fallbackAndLegacyMethodsDoNotDependOnTheJvmDefaultLocale() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.GERMAN);
      var value = AllowanceChargeIdentification.CODE_95;
      assertEquals("Discount",
                   value.getName(Locale.JAPANESE));
      assertEquals("A reduction from a usual or list price.",
                   value.getDescription(Locale.JAPANESE));
      assertEquals("Discount",
                   value.getName());
      assertEquals("95 - Discount",
                   value.toString());
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void bothBundlesContainEveryNameAndDescriptionWithoutRelyingOnFallback()
      throws IOException {
    var english = properties("AllowanceChargeIdentification.properties");
    var german  = properties("AllowanceChargeIdentification_de.properties");
    assertEquals(AllowanceChargeIdentification.values().length * 2,
                 english.size());
    assertEquals(english.stringPropertyNames(),
                 german.stringPropertyNames());
    for (var value : AllowanceChargeIdentification.values()) {
      for (var field : new String[] { "name",
                                      "description" }) {
        String key = value.name() + "." + field;
        assertFalse(english.getProperty(key)
                           .isBlank(),
                    key);
        assertFalse(german.getProperty(key)
                          .isBlank(),
                    key);
      }
    }
  }

  @Test
  void missingTranslationsFallBackPerKey() {
    var value = AllowanceChargeIdentification.CODE_95;
    assertEquals("Remise",
                 value.getName(Locale.FRENCH));
    assertEquals("A reduction from a usual or list price.",
                 value.getDescription(Locale.FRENCH));
    assertEquals("Material surcharge/deduction",
                 AllowanceChargeIdentification.CODE_88.getName(Locale.FRENCH));
  }

  @Test
  void loadsUtf8SpecialCharacters() {
    assertEquals("Neueröffnungsrabatt",
                 AllowanceChargeIdentification.CODE_66.getName(Locale.GERMAN));
    assertEquals("Vorübergehend",
                 AllowanceChargeIdentification.CODE_103.getName(Locale.GERMAN));
  }

  @Test
  void rejectsNullLocaleAndNullValueExplicitly() {
    var value = AllowanceChargeIdentification.CODE_95;
    assertThrows(NullPointerException.class,
                 () -> value.getName(null));
    assertThrows(NullPointerException.class,
                 () -> value.getDescription(null));
    assertThrows(NullPointerException.class,
                 () -> LocalizationFactory.getName(null,
                                                   Locale.GERMAN));
    assertThrows(NullPointerException.class,
                 () -> LocalizationFactory.getDescription(null,
                                                          Locale.GERMAN));
  }

  private Properties properties(String filename)
      throws IOException {
    var properties = new Properties();
    try (var input = Objects.requireNonNull(LocalizationFactory.class.getResourceAsStream(filename),
                                            filename); var reader = new InputStreamReader(input,
                                                                                          StandardCharsets.UTF_8)) {
      properties.load(reader);
    }
    return properties;
  }
}
