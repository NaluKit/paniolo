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
 * Country codes with localized display texts supplied by LocalizationFactory.
 */
public enum Country {

  /**
   * Afghanistan
   */
  AF,
  /**
   * Egypt
   */
  EG,
  /**
   * Albania
   */
  AL,
  /**
   * Algeria
   */
  DZ,
  /**
   * Andorra
   */
  AD,
  /**
   * Angola
   */
  AO,
  /**
   * Antigua &amp; Barbuda
   */
  AG,
  /**
   * Equatorial Guinea
   */
  GQ,
  /**
   * Argentina
   */
  AR,
  /**
   * Armenia
   */
  AM,
  /**
   * Azerbaijan
   */
  AZ,
  /**
   * Ethiopia
   */
  ET,
  /**
   * Australia
   */
  AU,
  /**
   * Bahamas
   */
  BS,
  /**
   * Bahrain
   */
  BH,
  /**
   * Bangladesh
   */
  BD,
  /**
   * Barbados
   */
  BB,
  /**
   * Belgium
   */
  BE,
  /**
   * Belize
   */
  BZ,
  /**
   * Benin
   */
  BJ,
  /**
   * Bhutan
   */
  BT,
  /**
   * Bolivia
   */
  BO,
  /**
   * Bosnia &amp; Herzegovina
   */
  BA,
  /**
   * Botswana
   */
  BW,
  /**
   * Brazil
   */
  BR,
  /**
   * Brunei
   */
  BN,
  /**
   * Bulgaria
   */
  BG,
  /**
   * Burkina Faso
   */
  BF,
  /**
   * Burundi
   */
  BI,
  /**
   * Chile
   */
  CL,
  /**
   * China
   */
  CN,
  /**
   * Cook Islands
   */
  CK,
  /**
   * Costa Rica
   */
  CR,
  /**
   * Côte d’Ivoire
   */
  CI,
  /**
   * Denmark
   */
  DK,
  /**
   * Germany
   */
  DE,
  /**
   * Dominica
   */
  DM,
  /**
   * Dominican Republic
   */
  DO,
  /**
   * Djibouti
   */
  DJ,
  /**
   * Ecuador
   */
  EC,
  /**
   * El Salvador
   */
  SV,
  /**
   * Eritrea
   */
  ER,
  /**
   * Estonia
   */
  EE,
  /**
   * Fiji
   */
  FJ,
  /**
   * Finland
   */
  FI,
  /**
   * France
   */
  FR,
  /**
   * Gabon
   */
  GA,
  /**
   * Gambia
   */
  GM,
  /**
   * Georgia
   */
  GE,
  /**
   * Ghana
   */
  GH,
  /**
   * Grenada
   */
  GD,
  /**
   * Greece
   */
  GR,
  /**
   * Guatemala
   */
  GT,
  /**
   * Guinea
   */
  GN,
  /**
   * Guinea-Bissau
   */
  GW,
  /**
   * Guyana
   */
  GY,
  /**
   * Haiti
   */
  HT,
  /**
   * Honduras
   */
  HN,
  /**
   * India
   */
  IN,
  /**
   * Indonesia
   */
  ID,
  /**
   * Iraq
   */
  IQ,
  /**
   * Iran
   */
  IR,
  /**
   * Ireland
   */
  IE,
  /**
   * Iceland
   */
  IS,
  /**
   * Israel
   */
  IL,
  /**
   * Italy
   */
  IT,
  /**
   * Jamaica
   */
  JM,
  /**
   * Japan
   */
  JP,
  /**
   * Yemen
   */
  YE,
  /**
   * Jordan
   */
  JO,
  /**
   * Cambodia
   */
  KH,
  /**
   * Cameroon
   */
  CM,
  /**
   * Canada
   */
  CA,
  /**
   * Cape Verde
   */
  CV,
  /**
   * Kazakhstan
   */
  KZ,
  /**
   * Qatar
   */
  QA,
  /**
   * Kenya
   */
  KE,
  /**
   * Kyrgyzstan
   */
  KG,
  /**
   * Kiribati
   */
  KI,
  /**
   * Colombia
   */
  CO,
  /**
   * Comoros
   */
  KM,
  /**
   * Congo - Brazzaville
   */
  CG,
  /**
   * Congo - Kinshasa
   */
  CD,
  /**
   * North Korea
   */
  KP,
  /**
   * South Korea
   */
  KR,
  /**
   * Croatia
   */
  HR,
  /**
   * Cuba
   */
  CU,
  /**
   * Kuwait
   */
  KW,
  /**
   * Laos
   */
  LA,
  /**
   * Lesotho
   */
  LS,
  /**
   * Latvia
   */
  LV,
  /**
   * Lebanon
   */
  LB,
  /**
   * Liberia
   */
  LR,
  /**
   * Libya
   */
  LY,
  /**
   * Liechtenstein
   */
  LI,
  /**
   * Lithuania
   */
  LT,
  /**
   * Luxembourg
   */
  LU,
  /**
   * Madagascar
   */
  MG,
  /**
   * Malawi
   */
  MW,
  /**
   * Malaysia
   */
  MY,
  /**
   * Maldives
   */
  MV,
  /**
   * Mali
   */
  ML,
  /**
   * Malta
   */
  MT,
  /**
   * Morocco
   */
  MA,
  /**
   * Marshall Islands
   */
  MH,
  /**
   * Mauritania
   */
  MR,
  /**
   * Mauritius
   */
  MU,
  /**
   * North Macedonia
   */
  MK,
  /**
   * Mexico
   */
  MX,
  /**
   * Micronesia
   */
  FM,
  /**
   * Moldova
   */
  MD,
  /**
   * Monaco
   */
  MC,
  /**
   * Mongolia
   */
  MN,
  /**
   * Montenegro
   */
  ME,
  /**
   * Mozambique
   */
  MZ,
  /**
   * Myanmar (Burma)
   */
  MM,
  /**
   * Namibia
   */
  NA,
  /**
   * Nauru
   */
  NR,
  /**
   * Nepal
   */
  NP,
  /**
   * New Zealand
   */
  NZ,
  /**
   * Nicaragua
   */
  NI,
  /**
   * Netherlands
   */
  NL,
  /**
   * Niger
   */
  NE,
  /**
   * Nigeria
   */
  NG,
  /**
   * Niue
   */
  NU,
  /**
   * Norway
   */
  NO,
  /**
   * Oman
   */
  OM,
  /**
   * Austria
   */
  AT,
  /**
   * Pakistan
   */
  PK,
  /**
   * Palau
   */
  PW,
  /**
   * Panama
   */
  PA,
  /**
   * Papua New Guinea
   */
  PG,
  /**
   * Paraguay
   */
  PY,
  /**
   * Peru
   */
  PE,
  /**
   * Philippines
   */
  PH,
  /**
   * Poland
   */
  PL,
  /**
   * Portugal
   */
  PT,
  /**
   * Rwanda
   */
  RW,
  /**
   * Romania
   */
  RO,
  /**
   * Russia
   */
  RU,
  /**
   * Solomon Islands
   */
  SB,
  /**
   * Zambia
   */
  ZM,
  /**
   * Samoa
   */
  WS,
  /**
   * San Marino
   */
  SM,
  /**
   * São Tomé &amp; Príncipe
   */
  ST,
  /**
   * Saudi Arabia
   */
  SA,
  /**
   * Sweden
   */
  SE,
  /**
   * Switzerland
   */
  CH,
  /**
   * Senegal
   */
  SN,
  /**
   * Serbia
   */
  RS,
  /**
   * Seychelles
   */
  SC,
  /**
   * Sierra Leone
   */
  SL,
  /**
   * Zimbabwe
   */
  ZW,
  /**
   * Singapore
   */
  SG,
  /**
   * Slovakia
   */
  SK,
  /**
   * Slovenia
   */
  SI,
  /**
   * Somalia
   */
  SO,
  /**
   * Spain
   */
  ES,
  /**
   * Sri Lanka
   */
  LK,
  /**
   * St. Kitts &amp; Nevis
   */
  KN,
  /**
   * St. Lucia
   */
  LC,
  /**
   * St. Vincent &amp; Grenadines
   */
  VC,
  /**
   * South Africa
   */
  ZA,
  /**
   * Sudan
   */
  SD,
  /**
   * Suriname
   */
  SR,
  /**
   * Eswatini
   */
  SZ,
  /**
   * Syria
   */
  SY,
  /**
   * Tajikistan
   */
  TJ,
  /**
   * Tanzania
   */
  TZ,
  /**
   * Thailand
   */
  TH,
  /**
   * Timor-Leste
   */
  TL,
  /**
   * Togo
   */
  TG,
  /**
   * Tonga
   */
  TO,
  /**
   * Trinidad &amp; Tobago
   */
  TT,
  /**
   * Chad
   */
  TD,
  /**
   * Czechia
   */
  CZ,
  /**
   * Tunisia
   */
  TN,
  /**
   * Türkiye
   */
  TR,
  /**
   * Turkmenistan
   */
  TM,
  /**
   * Tuvalu
   */
  TV,
  /**
   * Uganda
   */
  UG,
  /**
   * Ukraine
   */
  UA,
  /**
   * Hungary
   */
  HU,
  /**
   * Uruguay
   */
  UY,
  /**
   * Uzbekistan
   */
  UZ,
  /**
   * Vanuatu
   */
  VU,
  /**
   * Vatican City
   */
  VA,
  /**
   * Venezuela
   */
  VE,
  /**
   * United Arab Emirates
   */
  AE,
  /**
   * United States
   */
  US,
  /**
   * United Kingdom
   */
  GB,
  /**
   * Vietnam
   */
  VN,
  /**
   * Belarus
   */
  BY,
  /**
   * Central African Republic
   */
  CF,
  /**
   * Cyprus
   */
  CY;

  /**
   * Returns the country of an ISO 3166-1 alpha-2 code.
   *
   * @param name the code, e.g. {@code DE}
   * @return the country, or null if the code is unknown
   */
  public static Country of(String name) {
    return Arrays.stream(Country.values())
                 .filter(e -> e.name()
                               .equals(name))
                 .findFirst()
                 .orElse(null);
  }

  /**
   * Returns the German country text, preserving the original API behavior.
   *
   * @return the German country text
   */
  public String getText() {
    return getText(Locale.GERMAN);
  }

  /**
   * Returns the country text for the requested locale, falling back to English.
   *
   * @param locale the requested locale, never null
   * @return the localized country text
   */
  public String getText(Locale locale) {
    return LocalizationFactory.getText(this,
                                       locale);
  }

  /**
   * Formats the country code and localized text. The inherited no-argument toString keeps returning the code.
   *
   * @param locale the requested locale, never null
   * @return the code and localized country text
   */
  public String toString(Locale locale) {
    return name() + " - " + getText(locale);
  }
}
