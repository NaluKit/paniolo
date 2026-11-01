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

import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Resolves display texts for the enums in model.common.value from UTF-8 resource bundles.
 * Each enum has a bundle named after its class in this package. Requested region and language
 * bundles fall back to the English root bundle, independently of the JVM default locale.
 * ResourceBundle caches loaded bundles.
 */
public final class LocalizationFactory {
  private static final ResourceBundle.Control CONTROL = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

  private LocalizationFactory() {
  }

  /**
   * Returns the localized allowance or charge name.
   *
   * @param value  the enum value, never null
   * @param locale the requested locale, never null
   * @return the localized name, or its English fallback
   */
  public static String getName(AllowanceChargeIdentification value,
                               Locale locale) {
    return getValue(value,
                    locale,
                    "name");
  }

  /**
   * Returns the localized allowance or charge description.
   *
   * @param value  the enum value, never null
   * @param locale the requested locale, never null
   * @return the localized description, or its English fallback
   */
  public static String getDescription(AllowanceChargeIdentification value,
                                      Locale locale) {
    return getDescription((Enum<?>) value,
                          locale);
  }

  /**
   * Returns a localized description for an enum from model.common.value.
   * For Country, this returns its country text.
   *
   * @param value  the enum value, never null
   * @param locale the requested locale, never null
   * @return the localized description, or its English fallback
   * @throws IllegalArgumentException if the enum belongs to another package
   */
  public static String getDescription(Enum<?> value,
                                      Locale locale) {
    return getValue(value,
                    locale,
                    value instanceof Country ?
                    "text" :
                    "description");
  }

  /**
   * Returns the localized country text.
   *
   * @param value  the country, never null
   * @param locale the requested locale, never null
   * @return the localized text, or its English fallback
   */
  public static String getText(Country value,
                               Locale locale) {
    return getValue(value,
                    locale,
                    "text");
  }

  private static String getValue(Enum<?> value,
                                 Locale locale,
                                 String field) {
    Objects.requireNonNull(value,
                           "value");
    Objects.requireNonNull(locale,
                           "locale");
    Class<?> type = value.getDeclaringClass();
    if (!Country.class.getPackageName()
                      .equals(type.getPackageName())) {
      throw new IllegalArgumentException("Unsupported localized enum: " + type.getName());
    }
    String bundleName = LocalizationFactory.class.getPackageName() + "." + type.getSimpleName();
    ResourceBundle bundle = ResourceBundle.getBundle(bundleName,
                                                     locale,
                                                     LocalizationFactory.class.getClassLoader(),
                                                     CONTROL);
    return bundle.getString(value.name() + "." + field);
  }
}
