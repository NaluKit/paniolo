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
 * ISO 4217 Currency codes
 * Three-letter alphabetic codes for currencies
 */
public enum CurrencyCode {

  /**
   * United Arab Emirates Dirham
   */
  AED,
  /**
   * Afghan Afghani
   */
  AFN,
  /**
   * Albanian Lek
   */
  ALL,
  /**
   * Armenian Dram
   */
  AMD,
  /**
   * Netherlands Antillean Guilder
   */
  ANG,
  /**
   * Angolan Kwanza
   */
  AOA,
  /**
   * Argentine Peso
   */
  ARS,
  /**
   * Australian Dollar
   */
  AUD,
  /**
   * Aruban Florin
   */
  AWG,
  /**
   * Azerbaijani Manat
   */
  AZN,
  /**
   * Bosnia-Herzegovina Convertible Mark
   */
  BAM,
  /**
   * Barbadian Dollar
   */
  BBD,
  /**
   * Bangladeshi Taka
   */
  BDT,
  /**
   * Bulgarian Lev
   */
  BGN,
  /**
   * Bahraini Dinar
   */
  BHD,
  /**
   * Burundian Franc
   */
  BIF,
  /**
   * Bermudian Dollar
   */
  BMD,
  /**
   * Brunei Dollar
   */
  BND,
  /**
   * Bolivian Boliviano
   */
  BOB,
  /**
   * Bolivian Mvdol
   */
  BOV,
  /**
   * Brazilian Real
   */
  BRL,
  /**
   * Bahamian Dollar
   */
  BSD,
  /**
   * Bhutanese Ngultrum
   */
  BTN,
  /**
   * Botswana Pula
   */
  BWP,
  /**
   * Belarusian Ruble
   */
  BYN,
  /**
   * Belize Dollar
   */
  BZD,
  /**
   * Canadian Dollar
   */
  CAD,
  /**
   * Congolese Franc
   */
  CDF,
  /**
   * WIR Euro
   */
  CHE,
  /**
   * Swiss Franc
   */
  CHF,
  /**
   * WIR Franc
   */
  CHW,
  /**
   * Chilean Unit of Account (UF)
   */
  CLF,
  /**
   * Chilean Peso
   */
  CLP,
  /**
   * Chinese Yuan
   */
  CNY,
  /**
   * Colombian Peso
   */
  COP,
  /**
   * Colombian Unit of Value
   */
  COU,
  /**
   * Costa Rican Colón
   */
  CRC,
  /**
   * Cuban Convertible Peso
   */
  CUC,
  /**
   * Cuban Peso
   */
  CUP,
  /**
   * Cape Verdean Escudo
   */
  CVE,
  /**
   * Czech Koruna
   */
  CZK,
  /**
   * Djiboutian Franc
   */
  DJF,
  /**
   * Danish Krone
   */
  DKK,
  /**
   * Dominican Peso
   */
  DOP,
  /**
   * Algerian Dinar
   */
  DZD,
  /**
   * Egyptian Pound
   */
  EGP,
  /**
   * Eritrean Nakfa
   */
  ERN,
  /**
   * Ethiopian Birr
   */
  ETB,
  /**
   * Euro
   */
  EUR,
  /**
   * Fijian Dollar
   */
  FJD,
  /**
   * Falkland Islands Pound
   */
  FKP,
  /**
   * British Pound Sterling
   */
  GBP,
  /**
   * Georgian Lari
   */
  GEL,
  /**
   * Ghanaian Cedi
   */
  GHS,
  /**
   * Gibraltar Pound
   */
  GIP,
  /**
   * Gambian Dalasi
   */
  GMD,
  /**
   * Guinean Franc
   */
  GNF,
  /**
   * Guatemalan Quetzal
   */
  GTQ,
  /**
   * Guyanese Dollar
   */
  GYD,
  /**
   * Hong Kong Dollar
   */
  HKD,
  /**
   * Honduran Lempira
   */
  HNL,
  /**
   * Croatian Kuna
   */
  HRK,
  /**
   * Haitian Gourde
   */
  HTG,
  /**
   * Hungarian Forint
   */
  HUF,
  /**
   * Indonesian Rupiah
   */
  IDR,
  /**
   * Israeli New Shekel
   */
  ILS,
  /**
   * Indian Rupee
   */
  INR,
  /**
   * Iraqi Dinar
   */
  IQD,
  /**
   * Iranian Rial
   */
  IRR,
  /**
   * Icelandic Króna
   */
  ISK,
  /**
   * Jamaican Dollar
   */
  JMD,
  /**
   * Jordanian Dinar
   */
  JOD,
  /**
   * Japanese Yen
   */
  JPY,
  /**
   * Kenyan Shilling
   */
  KES,
  /**
   * Kyrgyzstani Som
   */
  KGS,
  /**
   * Cambodian Riel
   */
  KHR,
  /**
   * Comorian Franc
   */
  KMF,
  /**
   * North Korean Won
   */
  KPW,
  /**
   * South Korean Won
   */
  KRW,
  /**
   * Kuwaiti Dinar
   */
  KWD,
  /**
   * Cayman Islands Dollar
   */
  KYD,
  /**
   * Kazakhstani Tenge
   */
  KZT,
  /**
   * Lao Kip
   */
  LAK,
  /**
   * Lebanese Pound
   */
  LBP,
  /**
   * Sri Lankan Rupee
   */
  LKR,
  /**
   * Liberian Dollar
   */
  LRD,
  /**
   * Lesotho Loti
   */
  LSL,
  /**
   * Libyan Dinar
   */
  LYD,
  /**
   * Moroccan Dirham
   */
  MAD,
  /**
   * Moldovan Leu
   */
  MDL,
  /**
   * Malagasy Ariary
   */
  MGA,
  /**
   * Macedonian Denar
   */
  MKD,
  /**
   * Myanmar Kyat
   */
  MMK,
  /**
   * Mongolian Tögrög
   */
  MNT,
  /**
   * Macanese Pataca
   */
  MOP,
  /**
   * Mauritanian Ouguiya
   */
  MRU,
  /**
   * Mauritian Rupee
   */
  MUR,
  /**
   * Maldivian Rufiyaa
   */
  MVR,
  /**
   * Malawian Kwacha
   */
  MWK,
  /**
   * Mexican Peso
   */
  MXN,
  /**
   * Mexican Unit of Investment (UDI)
   */
  MXV,
  /**
   * Malaysian Ringgit
   */
  MYR,
  /**
   * Mozambican Metical
   */
  MZN,
  /**
   * Namibian Dollar
   */
  NAD,
  /**
   * Nigerian Naira
   */
  NGN,
  /**
   * Nicaraguan Córdoba
   */
  NIO,
  /**
   * Norwegian Krone
   */
  NOK,
  /**
   * Nepalese Rupee
   */
  NPR,
  /**
   * New Zealand Dollar
   */
  NZD,
  /**
   * Omani Rial
   */
  OMR,
  /**
   * Panamanian Balboa
   */
  PAB,
  /**
   * Peruvian Sol
   */
  PEN,
  /**
   * Papua New Guinean Kina
   */
  PGK,
  /**
   * Philippine Peso
   */
  PHP,
  /**
   * Pakistani Rupee
   */
  PKR,
  /**
   * Polish Złoty
   */
  PLN,
  /**
   * Paraguayan Guaraní
   */
  PYG,
  /**
   * Qatari Riyal
   */
  QAR,
  /**
   * Romanian Leu
   */
  RON,
  /**
   * Serbian Dinar
   */
  RSD,
  /**
   * Russian Ruble
   */
  RUB,
  /**
   * Rwandan Franc
   */
  RWF,
  /**
   * Saudi Riyal
   */
  SAR,
  /**
   * Solomon Islands Dollar
   */
  SBD,
  /**
   * Seychellois Rupee
   */
  SCR,
  /**
   * Sudanese Pound
   */
  SDG,
  /**
   * Swedish Krona
   */
  SEK,
  /**
   * Singapore Dollar
   */
  SGD,
  /**
   * Saint Helena Pound
   */
  SHP,
  /**
   * Sierra Leonean Leone
   */
  SLE,
  /**
   * Sierra Leonean Leone (old)
   */
  SLL,
  /**
   * Somali Shilling
   */
  SOS,
  /**
   * Seborgan Luigino
   */
  SPL,
  /**
   * Surinamese Dollar
   */
  SRD,
  /**
   * South Sudanese Pound
   */
  SSP,
  /**
   * São Tomé and Príncipe Dobra
   */
  STN,
  /**
   * Syrian Pound
   */
  SYP,
  /**
   * Swazi Lilangeni
   */
  SZL,
  /**
   * Thai Baht
   */
  THB,
  /**
   * Tajikistani Somoni
   */
  TJS,
  /**
   * Turkmenistani Manat
   */
  TMT,
  /**
   * Tunisian Dinar
   */
  TND,
  /**
   * Tongan Paʻanga
   */
  TOP,
  /**
   * Turkish Lira
   */
  TRY,
  /**
   * Trinidad and Tobago Dollar
   */
  TTD,
  /**
   * Tuvaluan Dollar
   */
  TVD,
  /**
   * New Taiwan Dollar
   */
  TWD,
  /**
   * Tanzanian Shilling
   */
  TZS,
  /**
   * Ukrainian Hryvnia
   */
  UAH,
  /**
   * Ugandan Shilling
   */
  UGX,
  /**
   * United States Dollar
   */
  USD,
  /**
   * United States Dollar (Next day)
   */
  USN,
  /**
   * Uruguayan Peso en Unidades Indexadas
   */
  UYI,
  /**
   * Uruguayan Peso
   */
  UYU,
  /**
   * Uruguayan Nominal Wage Index Unit
   */
  UYW,
  /**
   * Uzbekistani Som
   */
  UZS,
  /**
   * Venezuelan Bolívar Digital
   */
  VED,
  /**
   * Venezuelan Bolívar Soberano
   */
  VES,
  /**
   * Vietnamese Đồng
   */
  VND,
  /**
   * Vanuatu Vatu
   */
  VUV,
  /**
   * Samoan Tālā
   */
  WST,
  /**
   * Central African CFA Franc
   */
  XAF,
  /**
   * Silver (troy ounce)
   */
  XAG,
  /**
   * Gold (troy ounce)
   */
  XAU,
  /**
   * European Composite Unit
   */
  XBA,
  /**
   * European Monetary Unit
   */
  XBB,
  /**
   * European Unit of Account 9
   */
  XBC,
  /**
   * European Unit of Account 17
   */
  XBD,
  /**
   * East Caribbean Dollar
   */
  XCD,
  /**
   * Special Drawing Rights
   */
  XDR,
  /**
   * West African CFA Franc
   */
  XOF,
  /**
   * Palladium (troy ounce)
   */
  XPD,
  /**
   * CFP Franc
   */
  XPF,
  /**
   * Platinum (troy ounce)
   */
  XPT,
  /**
   * SUCRE
   */
  XSU,
  /**
   * Code reserved for testing
   */
  XTS,
  /**
   * ADB Unit of Account
   */
  XUA,
  /**
   * No currency
   */
  XXX,
  /**
   * Yemeni Rial
   */
  YER,
  /**
   * South African Rand
   */
  ZAR,
  /**
   * Zambian Kwacha
   */
  ZMW,
  /**
   * Zimbabwean Dollar
   */
  ZWL;

  /**
   * Get CurrencyCode by three-letter code
   *
   * @param code the three-letter currency code (e.g. "EUR", "USD")
   * @return the matching CurrencyCode or null if not found
   */
  public static CurrencyCode of(String code) {
    return Arrays.stream(CurrencyCode.values())
                 .filter(e -> e.getCode()
                               .equals(code))
                 .findFirst()
                 .orElse(null);
  }

  /**
   * Returns the code as written in the documents.
   *
   * @return the ISO 4217 code, e.g. {@code EUR}
   */
  public String getCode() {
    return this.name();
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
