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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Unit of measure code (UN/ECE Recommendation 20, and Recommendation 21 with prefix X), restricted
 * to the codes permitted by EN 16931 (BR-CL-23) as published in the EN 16931 code lists of the
 * European Commission. Codes starting with a digit use the constant prefix {@code CODE_}.
 * German texts exist for units common in invoices; the others fall back to English.
 */
public enum UnitCode {

  /**
   * group
   */
  CODE_10("10"),

  /**
   * outfit
   */
  CODE_11("11"),

  /**
   * ration
   */
  CODE_13("13"),

  /**
   * shot
   */
  CODE_14("14"),

  /**
   * stick, military
   */
  CODE_15("15"),

  /**
   * twenty foot container
   */
  CODE_20("20"),

  /**
   * forty foot container
   */
  CODE_21("21"),

  /**
   * decilitre per gram
   */
  CODE_22("22"),

  /**
   * gram per cubic centimetre
   */
  CODE_23("23"),

  /**
   * theoretical pound
   */
  CODE_24("24"),

  /**
   * gram per square centimetre
   */
  CODE_25("25"),

  /**
   * theoretical ton
   */
  CODE_27("27"),

  /**
   * kilogram per square metre
   */
  CODE_28("28"),

  /**
   * kilopascal square metre per gram
   */
  CODE_33("33"),

  /**
   * kilopascal per millimetre
   */
  CODE_34("34"),

  /**
   * millilitre per square centimetre second
   */
  CODE_35("35"),

  /**
   * ounce per square foot
   */
  CODE_37("37"),

  /**
   * ounce per square foot per 0,01inch
   */
  CODE_38("38"),

  /**
   * millilitre per second
   */
  CODE_40("40"),

  /**
   * millilitre per minute
   */
  CODE_41("41"),

  /**
   * sitas
   */
  CODE_56("56"),

  /**
   * mesh
   */
  CODE_57("57"),

  /**
   * net kilogram
   */
  CODE_58("58"),

  /**
   * part per million
   */
  CODE_59("59"),

  /**
   * percent weight
   */
  CODE_60("60"),

  /**
   * part per billion (US)
   */
  CODE_61("61"),

  /**
   * millipascal
   */
  CODE_74("74"),

  /**
   * milli-inch
   */
  CODE_77("77"),

  /**
   * pound per square inch absolute
   */
  CODE_80("80"),

  /**
   * henry
   */
  CODE_81("81"),

  /**
   * foot pound-force
   */
  CODE_85("85"),

  /**
   * pound per cubic foot
   */
  CODE_87("87"),

  /**
   * poise
   */
  CODE_89("89"),

  /**
   * stokes
   */
  CODE_91("91"),

  /**
   * fixed rate
   */
  CODE_1I("1I"),

  /**
   * radian per second
   */
  CODE_2A("2A"),

  /**
   * radian per second squared
   */
  CODE_2B("2B"),

  /**
   * roentgen
   */
  CODE_2C("2C"),

  /**
   * volt AC
   */
  CODE_2G("2G"),

  /**
   * volt DC
   */
  CODE_2H("2H"),

  /**
   * British thermal unit (international table) per hour
   */
  CODE_2I("2I"),

  /**
   * cubic centimetre per second
   */
  CODE_2J("2J"),

  /**
   * cubic foot per hour
   */
  CODE_2K("2K"),

  /**
   * cubic foot per minute
   */
  CODE_2L("2L"),

  /**
   * centimetre per second
   */
  CODE_2M("2M"),

  /**
   * decibel
   */
  CODE_2N("2N"),

  /**
   * kilobyte
   */
  CODE_2P("2P"),

  /**
   * kilobecquerel
   */
  CODE_2Q("2Q"),

  /**
   * kilocurie
   */
  CODE_2R("2R"),

  /**
   * megagram
   */
  CODE_2U("2U"),

  /**
   * metre per minute
   */
  CODE_2X("2X"),

  /**
   * milliroentgen
   */
  CODE_2Y("2Y"),

  /**
   * millivolt
   */
  CODE_2Z("2Z"),

  /**
   * megajoule
   */
  CODE_3B("3B"),

  /**
   * manmonth
   */
  CODE_3C("3C"),

  /**
   * centistokes
   */
  CODE_4C("4C"),

  /**
   * microlitre
   */
  CODE_4G("4G"),

  /**
   * micrometre (micron)
   */
  CODE_4H("4H"),

  /**
   * milliampere
   */
  CODE_4K("4K"),

  /**
   * megabyte
   */
  CODE_4L("4L"),

  /**
   * milligram per hour
   */
  CODE_4M("4M"),

  /**
   * megabecquerel
   */
  CODE_4N("4N"),

  /**
   * microfarad
   */
  CODE_4O("4O"),

  /**
   * newton per metre
   */
  CODE_4P("4P"),

  /**
   * ounce inch
   */
  CODE_4Q("4Q"),

  /**
   * ounce foot
   */
  CODE_4R("4R"),

  /**
   * picofarad
   */
  CODE_4T("4T"),

  /**
   * pound per hour
   */
  CODE_4U("4U"),

  /**
   * ton (US) per hour
   */
  CODE_4W("4W"),

  /**
   * kilolitre per hour
   */
  CODE_4X("4X"),

  /**
   * barrel (US) per minute
   */
  CODE_5A("5A"),

  /**
   * batch
   */
  CODE_5B("5B"),

  /**
   * MMSCF/day
   */
  CODE_5E("5E"),

  /**
   * hydraulic horse power
   */
  CODE_5J("5J"),

  /**
   * ampere square metre per joule second
   */
  A10("A10"),

  /**
   * angstrom
   */
  A11("A11"),

  /**
   * astronomical unit
   */
  A12("A12"),

  /**
   * attojoule
   */
  A13("A13"),

  /**
   * barn
   */
  A14("A14"),

  /**
   * barn per electronvolt
   */
  A15("A15"),

  /**
   * barn per steradian electronvolt
   */
  A16("A16"),

  /**
   * barn per steradian
   */
  A17("A17"),

  /**
   * becquerel per kilogram
   */
  A18("A18"),

  /**
   * becquerel per cubic metre
   */
  A19("A19"),

  /**
   * ampere per centimetre
   */
  A2("A2"),

  /**
   * British thermal unit (international table) per second square foot degree Rankine
   */
  A20("A20"),

  /**
   * British thermal unit (international table) per pound degree Rankine
   */
  A21("A21"),

  /**
   * British thermal unit (international table) per second foot degree Rankine
   */
  A22("A22"),

  /**
   * British thermal unit (international table) per hour square foot degree Rankine
   */
  A23("A23"),

  /**
   * candela per square metre
   */
  A24("A24"),

  /**
   * coulomb metre
   */
  A26("A26"),

  /**
   * coulomb metre squared per volt
   */
  A27("A27"),

  /**
   * coulomb per cubic centimetre
   */
  A28("A28"),

  /**
   * coulomb per cubic metre
   */
  A29("A29"),

  /**
   * ampere per millimetre
   */
  A3("A3"),

  /**
   * coulomb per cubic millimetre
   */
  A30("A30"),

  /**
   * coulomb per kilogram second
   */
  A31("A31"),

  /**
   * coulomb per mole
   */
  A32("A32"),

  /**
   * coulomb per square centimetre
   */
  A33("A33"),

  /**
   * coulomb per square metre
   */
  A34("A34"),

  /**
   * coulomb per square millimetre
   */
  A35("A35"),

  /**
   * cubic centimetre per mole
   */
  A36("A36"),

  /**
   * cubic decimetre per mole
   */
  A37("A37"),

  /**
   * cubic metre per coulomb
   */
  A38("A38"),

  /**
   * cubic metre per kilogram
   */
  A39("A39"),

  /**
   * ampere per square centimetre
   */
  A4("A4"),

  /**
   * cubic metre per mole
   */
  A40("A40"),

  /**
   * ampere per square metre
   */
  A41("A41"),

  /**
   * curie per kilogram
   */
  A42("A42"),

  /**
   * deadweight tonnage
   */
  A43("A43"),

  /**
   * decalitre
   */
  A44("A44"),

  /**
   * decametre
   */
  A45("A45"),

  /**
   * decitex
   */
  A47("A47"),

  /**
   * degree Rankine
   */
  A48("A48"),

  /**
   * denier
   */
  A49("A49"),

  /**
   * ampere square metre
   */
  A5("A5"),

  /**
   * electronvolt
   */
  A53("A53"),

  /**
   * electronvolt per metre
   */
  A54("A54"),

  /**
   * electronvolt square metre
   */
  A55("A55"),

  /**
   * electronvolt square metre per kilogram
   */
  A56("A56"),

  /**
   * 8-part cloud cover
   */
  A59("A59"),

  /**
   * ampere per square metre kelvin squared
   */
  A6("A6"),

  /**
   * exajoule
   */
  A68("A68"),

  /**
   * farad per metre
   */
  A69("A69"),

  /**
   * ampere per square millimetre
   */
  A7("A7"),

  /**
   * femtojoule
   */
  A70("A70"),

  /**
   * femtometre
   */
  A71("A71"),

  /**
   * foot per second squared
   */
  A73("A73"),

  /**
   * foot pound-force per second
   */
  A74("A74"),

  /**
   * freight ton
   */
  A75("A75"),

  /**
   * gal
   */
  A76("A76"),

  /**
   * ampere second
   */
  A8("A8"),

  /**
   * gigacoulomb per cubic metre
   */
  A84("A84"),

  /**
   * gigaelectronvolt
   */
  A85("A85"),

  /**
   * gigahertz
   */
  A86("A86"),

  /**
   * gigaohm
   */
  A87("A87"),

  /**
   * gigaohm metre
   */
  A88("A88"),

  /**
   * gigapascal
   */
  A89("A89"),

  /**
   * rate
   */
  A9("A9"),

  /**
   * gigawatt
   */
  A90("A90"),

  /**
   * gon
   */
  A91("A91"),

  /**
   * gram per cubic metre
   */
  A93("A93"),

  /**
   * gram per mole
   */
  A94("A94"),

  /**
   * gray
   */
  A95("A95"),

  /**
   * gray per second
   */
  A96("A96"),

  /**
   * hectopascal
   */
  A97("A97"),

  /**
   * henry per metre
   */
  A98("A98"),

  /**
   * bit
   */
  A99("A99"),

  /**
   * ball
   */
  AA("AA"),

  /**
   * bulk pack
   */
  AB("AB"),

  /**
   * acre
   */
  ACR("ACR"),

  /**
   * activity
   */
  ACT("ACT"),

  /**
   * byte
   */
  AD("AD"),

  /**
   * ampere per metre
   */
  AE("AE"),

  /**
   * additional minute
   */
  AH("AH"),

  /**
   * average minute per call
   */
  AI("AI"),

  /**
   * fathom
   */
  AK("AK"),

  /**
   * access line
   */
  AL("AL"),

  /**
   * ampere hour
   */
  AMH("AMH"),

  /**
   * ampere
   */
  AMP("AMP"),

  /**
   * year
   */
  ANN("ANN"),

  /**
   * troy ounce or apothecary ounce
   */
  APZ("APZ"),

  /**
   * anti-hemophilic factor (AHF) unit
   */
  AQ("AQ"),

  /**
   * assortment
   */
  AS("AS"),

  /**
   * alcoholic strength by mass
   */
  ASM("ASM"),

  /**
   * alcoholic strength by volume
   */
  ASU("ASU"),

  /**
   * standard atmosphere
   */
  ATM("ATM"),

  /**
   * american wire gauge
   */
  AWG("AWG"),

  /**
   * assembly
   */
  AY("AY"),

  /**
   * British thermal unit (international table) per pound
   */
  AZ("AZ"),

  /**
   * barrel (US) per day
   */
  B1("B1"),

  /**
   * bit per second
   */
  B10("B10"),

  /**
   * joule per kilogram kelvin
   */
  B11("B11"),

  /**
   * joule per metre
   */
  B12("B12"),

  /**
   * joule per square metre
   */
  B13("B13"),

  /**
   * joule per metre to the fourth power
   */
  B14("B14"),

  /**
   * joule per mole
   */
  B15("B15"),

  /**
   * joule per mole kelvin
   */
  B16("B16"),

  /**
   * credit
   */
  B17("B17"),

  /**
   * joule second
   */
  B18("B18"),

  /**
   * digit
   */
  B19("B19"),

  /**
   * joule square metre per kilogram
   */
  B20("B20"),

  /**
   * kelvin per watt
   */
  B21("B21"),

  /**
   * kiloampere
   */
  B22("B22"),

  /**
   * kiloampere per square metre
   */
  B23("B23"),

  /**
   * kiloampere per metre
   */
  B24("B24"),

  /**
   * kilobecquerel per kilogram
   */
  B25("B25"),

  /**
   * kilocoulomb
   */
  B26("B26"),

  /**
   * kilocoulomb per cubic metre
   */
  B27("B27"),

  /**
   * kilocoulomb per square metre
   */
  B28("B28"),

  /**
   * kiloelectronvolt
   */
  B29("B29"),

  /**
   * batting pound
   */
  B3("B3"),

  /**
   * gibibit
   */
  B30("B30"),

  /**
   * kilogram metre per second
   */
  B31("B31"),

  /**
   * kilogram metre squared
   */
  B32("B32"),

  /**
   * kilogram metre squared per second
   */
  B33("B33"),

  /**
   * kilogram per cubic decimetre
   */
  B34("B34"),

  /**
   * kilogram per litre
   */
  B35("B35"),

  /**
   * barrel, imperial
   */
  B4("B4"),

  /**
   * kilojoule per kelvin
   */
  B41("B41"),

  /**
   * kilojoule per kilogram
   */
  B42("B42"),

  /**
   * kilojoule per kilogram kelvin
   */
  B43("B43"),

  /**
   * kilojoule per mole
   */
  B44("B44"),

  /**
   * kilomole
   */
  B45("B45"),

  /**
   * kilomole per cubic metre
   */
  B46("B46"),

  /**
   * kilonewton
   */
  B47("B47"),

  /**
   * kilonewton metre
   */
  B48("B48"),

  /**
   * kiloohm
   */
  B49("B49"),

  /**
   * kiloohm metre
   */
  B50("B50"),

  /**
   * kilosecond
   */
  B52("B52"),

  /**
   * kilosiemens
   */
  B53("B53"),

  /**
   * kilosiemens per metre
   */
  B54("B54"),

  /**
   * kilovolt per metre
   */
  B55("B55"),

  /**
   * kiloweber per metre
   */
  B56("B56"),

  /**
   * light year
   */
  B57("B57"),

  /**
   * litre per mole
   */
  B58("B58"),

  /**
   * lumen hour
   */
  B59("B59"),

  /**
   * lumen per square metre
   */
  B60("B60"),

  /**
   * lumen per watt
   */
  B61("B61"),

  /**
   * lumen second
   */
  B62("B62"),

  /**
   * lux hour
   */
  B63("B63"),

  /**
   * lux second
   */
  B64("B64"),

  /**
   * megaampere per square metre
   */
  B66("B66"),

  /**
   * megabecquerel per kilogram
   */
  B67("B67"),

  /**
   * gigabit
   */
  B68("B68"),

  /**
   * megacoulomb per cubic metre
   */
  B69("B69"),

  /**
   * cycle
   */
  B7("B7"),

  /**
   * megacoulomb per square metre
   */
  B70("B70"),

  /**
   * megaelectronvolt
   */
  B71("B71"),

  /**
   * megagram per cubic metre
   */
  B72("B72"),

  /**
   * meganewton
   */
  B73("B73"),

  /**
   * meganewton metre
   */
  B74("B74"),

  /**
   * megaohm
   */
  B75("B75"),

  /**
   * megaohm metre
   */
  B76("B76"),

  /**
   * megasiemens per metre
   */
  B77("B77"),

  /**
   * megavolt
   */
  B78("B78"),

  /**
   * megavolt per metre
   */
  B79("B79"),

  /**
   * joule per cubic metre
   */
  B8("B8"),

  /**
   * gigabit per second
   */
  B80("B80"),

  /**
   * reciprocal metre squared reciprocal second
   */
  B81("B81"),

  /**
   * inch per linear foot
   */
  B82("B82"),

  /**
   * metre to the fourth power
   */
  B83("B83"),

  /**
   * microampere
   */
  B84("B84"),

  /**
   * microbar
   */
  B85("B85"),

  /**
   * microcoulomb
   */
  B86("B86"),

  /**
   * microcoulomb per cubic metre
   */
  B87("B87"),

  /**
   * microcoulomb per square metre
   */
  B88("B88"),

  /**
   * microfarad per metre
   */
  B89("B89"),

  /**
   * microhenry
   */
  B90("B90"),

  /**
   * microhenry per metre
   */
  B91("B91"),

  /**
   * micronewton
   */
  B92("B92"),

  /**
   * micronewton metre
   */
  B93("B93"),

  /**
   * microohm
   */
  B94("B94"),

  /**
   * microohm metre
   */
  B95("B95"),

  /**
   * micropascal
   */
  B96("B96"),

  /**
   * microradian
   */
  B97("B97"),

  /**
   * microsecond
   */
  B98("B98"),

  /**
   * microsiemens
   */
  B99("B99"),

  /**
   * bar [unit of pressure]
   */
  BAR("BAR"),

  /**
   * base box
   */
  BB("BB"),

  /**
   * board foot
   */
  BFT("BFT"),

  /**
   * brake horse power
   */
  BHP("BHP"),

  /**
   * billion (EUR)
   */
  BIL("BIL"),

  /**
   * dry barrel (US)
   */
  BLD("BLD"),

  /**
   * barrel (US)
   */
  BLL("BLL"),

  /**
   * hundred board foot
   */
  BP("BP"),

  /**
   * beats per minute
   */
  BPM("BPM"),

  /**
   * becquerel
   */
  BQL("BQL"),

  /**
   * British thermal unit (international table)
   */
  BTU("BTU"),

  /**
   * bushel (US)
   */
  BUA("BUA"),

  /**
   * bushel (UK)
   */
  BUI("BUI"),

  /**
   * call
   */
  C0("C0"),

  /**
   * millifarad
   */
  C10("C10"),

  /**
   * milligal
   */
  C11("C11"),

  /**
   * milligram per metre
   */
  C12("C12"),

  /**
   * milligray
   */
  C13("C13"),

  /**
   * millihenry
   */
  C14("C14"),

  /**
   * millijoule
   */
  C15("C15"),

  /**
   * millimetre per second
   */
  C16("C16"),

  /**
   * millimetre squared per second
   */
  C17("C17"),

  /**
   * millimole
   */
  C18("C18"),

  /**
   * mole per kilogram
   */
  C19("C19"),

  /**
   * millinewton
   */
  C20("C20"),

  /**
   * kibibit
   */
  C21("C21"),

  /**
   * millinewton per metre
   */
  C22("C22"),

  /**
   * milliohm metre
   */
  C23("C23"),

  /**
   * millipascal second
   */
  C24("C24"),

  /**
   * milliradian
   */
  C25("C25"),

  /**
   * millisecond
   */
  C26("C26"),

  /**
   * millisiemens
   */
  C27("C27"),

  /**
   * millisievert
   */
  C28("C28"),

  /**
   * millitesla
   */
  C29("C29"),

  /**
   * microvolt per metre
   */
  C3("C3"),

  /**
   * millivolt per metre
   */
  C30("C30"),

  /**
   * milliwatt
   */
  C31("C31"),

  /**
   * milliwatt per square metre
   */
  C32("C32"),

  /**
   * milliweber
   */
  C33("C33"),

  /**
   * mole
   */
  C34("C34"),

  /**
   * mole per cubic decimetre
   */
  C35("C35"),

  /**
   * mole per cubic metre
   */
  C36("C36"),

  /**
   * kilobit
   */
  C37("C37"),

  /**
   * mole per litre
   */
  C38("C38"),

  /**
   * nanoampere
   */
  C39("C39"),

  /**
   * nanocoulomb
   */
  C40("C40"),

  /**
   * nanofarad
   */
  C41("C41"),

  /**
   * nanofarad per metre
   */
  C42("C42"),

  /**
   * nanohenry
   */
  C43("C43"),

  /**
   * nanohenry per metre
   */
  C44("C44"),

  /**
   * nanometre
   */
  C45("C45"),

  /**
   * nanoohm metre
   */
  C46("C46"),

  /**
   * nanosecond
   */
  C47("C47"),

  /**
   * nanotesla
   */
  C48("C48"),

  /**
   * nanowatt
   */
  C49("C49"),

  /**
   * neper
   */
  C50("C50"),

  /**
   * neper per second
   */
  C51("C51"),

  /**
   * picometre
   */
  C52("C52"),

  /**
   * newton metre second
   */
  C53("C53"),

  /**
   * newton metre squared per kilogram squared
   */
  C54("C54"),

  /**
   * newton per square metre
   */
  C55("C55"),

  /**
   * newton per square millimetre
   */
  C56("C56"),

  /**
   * newton second
   */
  C57("C57"),

  /**
   * newton second per metre
   */
  C58("C58"),

  /**
   * octave
   */
  C59("C59"),

  /**
   * ohm centimetre
   */
  C60("C60"),

  /**
   * ohm metre
   */
  C61("C61"),

  /**
   * one
   */
  C62("C62"),

  /**
   * parsec
   */
  C63("C63"),

  /**
   * pascal per kelvin
   */
  C64("C64"),

  /**
   * pascal second
   */
  C65("C65"),

  /**
   * pascal second per cubic metre
   */
  C66("C66"),

  /**
   * pascal second per metre
   */
  C67("C67"),

  /**
   * petajoule
   */
  C68("C68"),

  /**
   * phon
   */
  C69("C69"),

  /**
   * centipoise
   */
  C7("C7"),

  /**
   * picoampere
   */
  C70("C70"),

  /**
   * picocoulomb
   */
  C71("C71"),

  /**
   * picofarad per metre
   */
  C72("C72"),

  /**
   * picohenry
   */
  C73("C73"),

  /**
   * kilobit per second
   */
  C74("C74"),

  /**
   * picowatt
   */
  C75("C75"),

  /**
   * picowatt per square metre
   */
  C76("C76"),

  /**
   * pound-force
   */
  C78("C78"),

  /**
   * kilovolt ampere hour
   */
  C79("C79"),

  /**
   * millicoulomb per kilogram
   */
  C8("C8"),

  /**
   * rad
   */
  C80("C80"),

  /**
   * radian
   */
  C81("C81"),

  /**
   * radian square metre per mole
   */
  C82("C82"),

  /**
   * radian square metre per kilogram
   */
  C83("C83"),

  /**
   * radian per metre
   */
  C84("C84"),

  /**
   * reciprocal angstrom
   */
  C85("C85"),

  /**
   * reciprocal cubic metre
   */
  C86("C86"),

  /**
   * reciprocal cubic metre per second
   */
  C87("C87"),

  /**
   * reciprocal electron volt per cubic metre
   */
  C88("C88"),

  /**
   * reciprocal henry
   */
  C89("C89"),

  /**
   * coil group
   */
  C9("C9"),

  /**
   * reciprocal joule per cubic metre
   */
  C90("C90"),

  /**
   * reciprocal kelvin or kelvin to the power minus one
   */
  C91("C91"),

  /**
   * reciprocal metre
   */
  C92("C92"),

  /**
   * reciprocal square metre
   */
  C93("C93"),

  /**
   * reciprocal minute
   */
  C94("C94"),

  /**
   * reciprocal mole
   */
  C95("C95"),

  /**
   * reciprocal pascal or pascal to the power minus one
   */
  C96("C96"),

  /**
   * reciprocal second
   */
  C97("C97"),

  /**
   * reciprocal second per metre squared
   */
  C99("C99"),

  /**
   * carrying capacity in metric ton
   */
  CCT("CCT"),

  /**
   * candela
   */
  CDL("CDL"),

  /**
   * degree Celsius
   */
  CEL("CEL"),

  /**
   * hundred
   */
  CEN("CEN"),

  /**
   * card
   */
  CG("CG"),

  /**
   * centigram
   */
  CGM("CGM"),

  /**
   * coulomb per kilogram
   */
  CKG("CKG"),

  /**
   * hundred leave
   */
  CLF("CLF"),

  /**
   * centilitre
   */
  CLT("CLT"),

  /**
   * square centimetre
   */
  CMK("CMK"),

  /**
   * cubic centimetre
   */
  CMQ("CMQ"),

  /**
   * centimetre
   */
  CMT("CMT"),

  /**
   * hundred pack
   */
  CNP("CNP"),

  /**
   * cental (UK)
   */
  CNT("CNT"),

  /**
   * coulomb
   */
  COU("COU"),

  /**
   * content gram
   */
  CTG("CTG"),

  /**
   * metric carat
   */
  CTM("CTM"),

  /**
   * content ton (metric)
   */
  CTN("CTN"),

  /**
   * curie
   */
  CUR("CUR"),

  /**
   * hundred pound (cwt) / hundred weight (US)
   */
  CWA("CWA"),

  /**
   * hundred weight (UK)
   */
  CWI("CWI"),

  /**
   * kilowatt hour per hour
   */
  D03("D03"),

  /**
   * lot [unit of weight]
   */
  D04("D04"),

  /**
   * reciprocal second per steradian
   */
  D1("D1"),

  /**
   * siemens per metre
   */
  D10("D10"),

  /**
   * mebibit
   */
  D11("D11"),

  /**
   * siemens square metre per mole
   */
  D12("D12"),

  /**
   * sievert
   */
  D13("D13"),

  /**
   * sone
   */
  D15("D15"),

  /**
   * square centimetre per erg
   */
  D16("D16"),

  /**
   * square centimetre per steradian erg
   */
  D17("D17"),

  /**
   * metre kelvin
   */
  D18("D18"),

  /**
   * square metre kelvin per watt
   */
  D19("D19"),

  /**
   * reciprocal second per steradian metre squared
   */
  D2("D2"),

  /**
   * square metre per joule
   */
  D20("D20"),

  /**
   * square metre per kilogram
   */
  D21("D21"),

  /**
   * square metre per mole
   */
  D22("D22"),

  /**
   * pen gram (protein)
   */
  D23("D23"),

  /**
   * square metre per steradian
   */
  D24("D24"),

  /**
   * square metre per steradian joule
   */
  D25("D25"),

  /**
   * square metre per volt second
   */
  D26("D26"),

  /**
   * steradian
   */
  D27("D27"),

  /**
   * terahertz
   */
  D29("D29"),

  /**
   * terajoule
   */
  D30("D30"),

  /**
   * terawatt
   */
  D31("D31"),

  /**
   * terawatt hour
   */
  D32("D32"),

  /**
   * tesla
   */
  D33("D33"),

  /**
   * tex
   */
  D34("D34"),

  /**
   * megabit
   */
  D36("D36"),

  /**
   * tonne per cubic metre
   */
  D41("D41"),

  /**
   * tropical year
   */
  D42("D42"),

  /**
   * unified atomic mass unit
   */
  D43("D43"),

  /**
   * var
   */
  D44("D44"),

  /**
   * volt squared per kelvin squared
   */
  D45("D45"),

  /**
   * volt - ampere
   */
  D46("D46"),

  /**
   * volt per centimetre
   */
  D47("D47"),

  /**
   * volt per kelvin
   */
  D48("D48"),

  /**
   * millivolt per kelvin
   */
  D49("D49"),

  /**
   * kilogram per square centimetre
   */
  D5("D5"),

  /**
   * volt per metre
   */
  D50("D50"),

  /**
   * volt per millimetre
   */
  D51("D51"),

  /**
   * watt per kelvin
   */
  D52("D52"),

  /**
   * watt per metre kelvin
   */
  D53("D53"),

  /**
   * watt per square metre
   */
  D54("D54"),

  /**
   * watt per square metre kelvin
   */
  D55("D55"),

  /**
   * watt per square metre kelvin to the fourth power
   */
  D56("D56"),

  /**
   * watt per steradian
   */
  D57("D57"),

  /**
   * watt per steradian square metre
   */
  D58("D58"),

  /**
   * weber per metre
   */
  D59("D59"),

  /**
   * roentgen per second
   */
  D6("D6"),

  /**
   * weber per millimetre
   */
  D60("D60"),

  /**
   * minute [unit of angle]
   */
  D61("D61"),

  /**
   * second [unit of angle]
   */
  D62("D62"),

  /**
   * book
   */
  D63("D63"),

  /**
   * round
   */
  D65("D65"),

  /**
   * number of words
   */
  D68("D68"),

  /**
   * inch to the fourth power
   */
  D69("D69"),

  /**
   * joule square metre
   */
  D73("D73"),

  /**
   * kilogram per mole
   */
  D74("D74"),

  /**
   * megacoulomb
   */
  D77("D77"),

  /**
   * megajoule per second
   */
  D78("D78"),

  /**
   * microwatt
   */
  D80("D80"),

  /**
   * microtesla
   */
  D81("D81"),

  /**
   * microvolt
   */
  D82("D82"),

  /**
   * millinewton metre
   */
  D83("D83"),

  /**
   * microwatt per square metre
   */
  D85("D85"),

  /**
   * millicoulomb
   */
  D86("D86"),

  /**
   * millimole per kilogram
   */
  D87("D87"),

  /**
   * millicoulomb per cubic metre
   */
  D88("D88"),

  /**
   * millicoulomb per square metre
   */
  D89("D89"),

  /**
   * rem
   */
  D91("D91"),

  /**
   * second per cubic metre
   */
  D93("D93"),

  /**
   * second per cubic metre radian
   */
  D94("D94"),

  /**
   * joule per gram
   */
  D95("D95"),

  /**
   * decare
   */
  DAA("DAA"),

  /**
   * ten day
   */
  DAD("DAD"),

  /**
   * day
   */
  DAY("DAY"),

  /**
   * dry pound
   */
  DB("DB"),

  /**
   * Decibel-milliwatts
   */
  DBM("DBM"),

  /**
   * Decibel watt
   */
  DBW("DBW"),

  /**
   * degree [unit of angle]
   */
  DD("DD"),

  /**
   * decade
   */
  DEC("DEC"),

  /**
   * decigram
   */
  DG("DG"),

  /**
   * decagram
   */
  DJ("DJ"),

  /**
   * decilitre
   */
  DLT("DLT"),

  /**
   * cubic decametre
   */
  DMA("DMA"),

  /**
   * square decimetre
   */
  DMK("DMK"),

  /**
   * standard kilolitre
   */
  DMO("DMO"),

  /**
   * cubic decimetre
   */
  DMQ("DMQ"),

  /**
   * decimetre
   */
  DMT("DMT"),

  /**
   * decinewton metre
   */
  DN("DN"),

  /**
   * dozen piece
   */
  DPC("DPC"),

  /**
   * dozen pair
   */
  DPR("DPR"),

  /**
   * displacement tonnage
   */
  DPT("DPT"),

  /**
   * dram (US)
   */
  DRA("DRA"),

  /**
   * dram (UK)
   */
  DRI("DRI"),

  /**
   * dozen roll
   */
  DRL("DRL"),

  /**
   * dry ton
   */
  DT("DT"),

  /**
   * decitonne
   */
  DTN("DTN"),

  /**
   * pennyweight
   */
  DWT("DWT"),

  /**
   * dozen
   */
  DZN("DZN"),

  /**
   * dozen pack
   */
  DZP("DZP"),

  /**
   * newton per square centimetre
   */
  E01("E01"),

  /**
   * megawatt hour per hour
   */
  E07("E07"),

  /**
   * megawatt per hertz
   */
  E08("E08"),

  /**
   * milliampere hour
   */
  E09("E09"),

  /**
   * degree day
   */
  E10("E10"),

  /**
   * mille
   */
  E12("E12"),

  /**
   * kilocalorie (international table)
   */
  E14("E14"),

  /**
   * kilocalorie (thermochemical) per hour
   */
  E15("E15"),

  /**
   * million Btu(IT) per hour
   */
  E16("E16"),

  /**
   * cubic foot per second
   */
  E17("E17"),

  /**
   * tonne per hour
   */
  E18("E18"),

  /**
   * ping
   */
  E19("E19"),

  /**
   * megabit per second
   */
  E20("E20"),

  /**
   * shares
   */
  E21("E21"),

  /**
   * TEU
   */
  E22("E22"),

  /**
   * tyre
   */
  E23("E23"),

  /**
   * active unit
   */
  E25("E25"),

  /**
   * dose
   */
  E27("E27"),

  /**
   * air dry ton
   */
  E28("E28"),

  /**
   * strand
   */
  E30("E30"),

  /**
   * square metre per litre
   */
  E31("E31"),

  /**
   * litre per hour
   */
  E32("E32"),

  /**
   * foot per thousand
   */
  E33("E33"),

  /**
   * gigabyte
   */
  E34("E34"),

  /**
   * terabyte
   */
  E35("E35"),

  /**
   * petabyte
   */
  E36("E36"),

  /**
   * pixel
   */
  E37("E37"),

  /**
   * megapixel
   */
  E38("E38"),

  /**
   * dots per inch
   */
  E39("E39"),

  /**
   * gross kilogram
   */
  E4("E4"),

  /**
   * part per hundred thousand
   */
  E40("E40"),

  /**
   * kilogram-force per square millimetre
   */
  E41("E41"),

  /**
   * kilogram-force per square centimetre
   */
  E42("E42"),

  /**
   * joule per square centimetre
   */
  E43("E43"),

  /**
   * kilogram-force metre per square centimetre
   */
  E44("E44"),

  /**
   * milliohm
   */
  E45("E45"),

  /**
   * kilowatt hour per cubic metre
   */
  E46("E46"),

  /**
   * kilowatt hour per kelvin
   */
  E47("E47"),

  /**
   * service unit
   */
  E48("E48"),

  /**
   * working day
   */
  E49("E49"),

  /**
   * accounting unit
   */
  E50("E50"),

  /**
   * job
   */
  E51("E51"),

  /**
   * run foot
   */
  E52("E52"),

  /**
   * test
   */
  E53("E53"),

  /**
   * trip
   */
  E54("E54"),

  /**
   * use
   */
  E55("E55"),

  /**
   * well
   */
  E56("E56"),

  /**
   * zone
   */
  E57("E57"),

  /**
   * exabit per second
   */
  E58("E58"),

  /**
   * exbibyte
   */
  E59("E59"),

  /**
   * pebibyte
   */
  E60("E60"),

  /**
   * tebibyte
   */
  E61("E61"),

  /**
   * gibibyte
   */
  E62("E62"),

  /**
   * mebibyte
   */
  E63("E63"),

  /**
   * kibibyte
   */
  E64("E64"),

  /**
   * exbibit per metre
   */
  E65("E65"),

  /**
   * exbibit per square metre
   */
  E66("E66"),

  /**
   * exbibit per cubic metre
   */
  E67("E67"),

  /**
   * gigabyte per second
   */
  E68("E68"),

  /**
   * gibibit per metre
   */
  E69("E69"),

  /**
   * gibibit per square metre
   */
  E70("E70"),

  /**
   * gibibit per cubic metre
   */
  E71("E71"),

  /**
   * kibibit per metre
   */
  E72("E72"),

  /**
   * kibibit per square metre
   */
  E73("E73"),

  /**
   * kibibit per cubic metre
   */
  E74("E74"),

  /**
   * mebibit per metre
   */
  E75("E75"),

  /**
   * mebibit per square metre
   */
  E76("E76"),

  /**
   * mebibit per cubic metre
   */
  E77("E77"),

  /**
   * petabit
   */
  E78("E78"),

  /**
   * petabit per second
   */
  E79("E79"),

  /**
   * pebibit per metre
   */
  E80("E80"),

  /**
   * pebibit per square metre
   */
  E81("E81"),

  /**
   * pebibit per cubic metre
   */
  E82("E82"),

  /**
   * terabit
   */
  E83("E83"),

  /**
   * terabit per second
   */
  E84("E84"),

  /**
   * tebibit per metre
   */
  E85("E85"),

  /**
   * tebibit per cubic metre
   */
  E86("E86"),

  /**
   * tebibit per square metre
   */
  E87("E87"),

  /**
   * bit per metre
   */
  E88("E88"),

  /**
   * bit per square metre
   */
  E89("E89"),

  /**
   * reciprocal centimetre
   */
  E90("E90"),

  /**
   * reciprocal day
   */
  E91("E91"),

  /**
   * cubic decimetre per hour
   */
  E92("E92"),

  /**
   * kilogram per hour
   */
  E93("E93"),

  /**
   * kilomole per second
   */
  E94("E94"),

  /**
   * mole per second
   */
  E95("E95"),

  /**
   * degree per second
   */
  E96("E96"),

  /**
   * millimetre per degree Celcius metre
   */
  E97("E97"),

  /**
   * degree Celsius per kelvin
   */
  E98("E98"),

  /**
   * hectopascal per bar
   */
  E99("E99"),

  /**
   * each
   */
  EA("EA"),

  /**
   * electronic mail box
   */
  EB("EB"),

  /**
   * equivalent gallon
   */
  EQ("EQ"),

  /**
   * bit per cubic metre
   */
  F01("F01"),

  /**
   * kelvin per kelvin
   */
  F02("F02"),

  /**
   * kilopascal per bar
   */
  F03("F03"),

  /**
   * millibar per bar
   */
  F04("F04"),

  /**
   * megapascal per bar
   */
  F05("F05"),

  /**
   * poise per bar
   */
  F06("F06"),

  /**
   * pascal per bar
   */
  F07("F07"),

  /**
   * milliampere per inch
   */
  F08("F08"),

  /**
   * kelvin per hour
   */
  F10("F10"),

  /**
   * kelvin per minute
   */
  F11("F11"),

  /**
   * kelvin per second
   */
  F12("F12"),

  /**
   * slug
   */
  F13("F13"),

  /**
   * gram per kelvin
   */
  F14("F14"),

  /**
   * kilogram per kelvin
   */
  F15("F15"),

  /**
   * milligram per kelvin
   */
  F16("F16"),

  /**
   * pound-force per foot
   */
  F17("F17"),

  /**
   * kilogram square centimetre
   */
  F18("F18"),

  /**
   * kilogram square millimetre
   */
  F19("F19"),

  /**
   * pound inch squared
   */
  F20("F20"),

  /**
   * pound-force inch
   */
  F21("F21"),

  /**
   * pound-force foot per ampere
   */
  F22("F22"),

  /**
   * gram per cubic decimetre
   */
  F23("F23"),

  /**
   * kilogram per kilomol
   */
  F24("F24"),

  /**
   * gram per hertz
   */
  F25("F25"),

  /**
   * gram per day
   */
  F26("F26"),

  /**
   * gram per hour
   */
  F27("F27"),

  /**
   * gram per minute
   */
  F28("F28"),

  /**
   * gram per second
   */
  F29("F29"),

  /**
   * kilogram per day
   */
  F30("F30"),

  /**
   * kilogram per minute
   */
  F31("F31"),

  /**
   * milligram per day
   */
  F32("F32"),

  /**
   * milligram per minute
   */
  F33("F33"),

  /**
   * milligram per second
   */
  F34("F34"),

  /**
   * gram per day kelvin
   */
  F35("F35"),

  /**
   * gram per hour kelvin
   */
  F36("F36"),

  /**
   * gram per minute kelvin
   */
  F37("F37"),

  /**
   * gram per second kelvin
   */
  F38("F38"),

  /**
   * kilogram per day kelvin
   */
  F39("F39"),

  /**
   * kilogram per hour kelvin
   */
  F40("F40"),

  /**
   * kilogram per minute kelvin
   */
  F41("F41"),

  /**
   * kilogram per second kelvin
   */
  F42("F42"),

  /**
   * milligram per day kelvin
   */
  F43("F43"),

  /**
   * milligram per hour kelvin
   */
  F44("F44"),

  /**
   * milligram per minute kelvin
   */
  F45("F45"),

  /**
   * milligram per second kelvin
   */
  F46("F46"),

  /**
   * newton per millimetre
   */
  F47("F47"),

  /**
   * pound-force per inch
   */
  F48("F48"),

  /**
   * rod [unit of distance]
   */
  F49("F49"),

  /**
   * micrometre per kelvin
   */
  F50("F50"),

  /**
   * centimetre per kelvin
   */
  F51("F51"),

  /**
   * metre per kelvin
   */
  F52("F52"),

  /**
   * millimetre per kelvin
   */
  F53("F53"),

  /**
   * milliohm per metre
   */
  F54("F54"),

  /**
   * ohm per mile (statute mile)
   */
  F55("F55"),

  /**
   * ohm per kilometre
   */
  F56("F56"),

  /**
   * milliampere per pound-force per square inch
   */
  F57("F57"),

  /**
   * reciprocal bar
   */
  F58("F58"),

  /**
   * milliampere per bar
   */
  F59("F59"),

  /**
   * degree Celsius per bar
   */
  F60("F60"),

  /**
   * kelvin per bar
   */
  F61("F61"),

  /**
   * gram per day bar
   */
  F62("F62"),

  /**
   * gram per hour bar
   */
  F63("F63"),

  /**
   * gram per minute bar
   */
  F64("F64"),

  /**
   * gram per second bar
   */
  F65("F65"),

  /**
   * kilogram per day bar
   */
  F66("F66"),

  /**
   * kilogram per hour bar
   */
  F67("F67"),

  /**
   * kilogram per minute bar
   */
  F68("F68"),

  /**
   * kilogram per second bar
   */
  F69("F69"),

  /**
   * milligram per day bar
   */
  F70("F70"),

  /**
   * milligram per hour bar
   */
  F71("F71"),

  /**
   * milligram per minute bar
   */
  F72("F72"),

  /**
   * milligram per second bar
   */
  F73("F73"),

  /**
   * gram per bar
   */
  F74("F74"),

  /**
   * milligram per bar
   */
  F75("F75"),

  /**
   * milliampere per millimetre
   */
  F76("F76"),

  /**
   * pascal second per kelvin
   */
  F77("F77"),

  /**
   * inch of water
   */
  F78("F78"),

  /**
   * inch of mercury
   */
  F79("F79"),

  /**
   * water horse power
   */
  F80("F80"),

  /**
   * bar per kelvin
   */
  F81("F81"),

  /**
   * hectopascal per kelvin
   */
  F82("F82"),

  /**
   * kilopascal per kelvin
   */
  F83("F83"),

  /**
   * millibar per kelvin
   */
  F84("F84"),

  /**
   * megapascal per kelvin
   */
  F85("F85"),

  /**
   * poise per kelvin
   */
  F86("F86"),

  /**
   * volt per litre minute
   */
  F87("F87"),

  /**
   * newton centimetre
   */
  F88("F88"),

  /**
   * newton metre per degree
   */
  F89("F89"),

  /**
   * newton metre per ampere
   */
  F90("F90"),

  /**
   * bar litre per second
   */
  F91("F91"),

  /**
   * bar cubic metre per second
   */
  F92("F92"),

  /**
   * hectopascal litre per second
   */
  F93("F93"),

  /**
   * hectopascal cubic metre per second
   */
  F94("F94"),

  /**
   * millibar litre per second
   */
  F95("F95"),

  /**
   * millibar cubic metre per second
   */
  F96("F96"),

  /**
   * megapascal litre per second
   */
  F97("F97"),

  /**
   * megapascal cubic metre per second
   */
  F98("F98"),

  /**
   * pascal litre per second
   */
  F99("F99"),

  /**
   * degree Fahrenheit
   */
  FAH("FAH"),

  /**
   * farad
   */
  FAR("FAR"),

  /**
   * fibre metre
   */
  FBM("FBM"),

  /**
   * thousand cubic foot
   */
  FC("FC"),

  /**
   * hundred cubic metre
   */
  FF("FF"),

  /**
   * micromole
   */
  FH("FH"),

  /**
   * failures in time
   */
  FIT("FIT"),

  /**
   * flake ton
   */
  FL("FL"),

  /**
   * Formazin nephelometric unit
   */
  FNU("FNU"),

  /**
   * foot
   */
  FOT("FOT"),

  /**
   * pound per square foot
   */
  FP("FP"),

  /**
   * foot per minute
   */
  FR("FR"),

  /**
   * foot per second
   */
  FS("FS"),

  /**
   * square foot
   */
  FTK("FTK"),

  /**
   * cubic foot
   */
  FTQ("FTQ"),

  /**
   * pascal cubic metre per second
   */
  G01("G01"),

  /**
   * centimetre per bar
   */
  G04("G04"),

  /**
   * metre per bar
   */
  G05("G05"),

  /**
   * millimetre per bar
   */
  G06("G06"),

  /**
   * square inch per second
   */
  G08("G08"),

  /**
   * square metre per second kelvin
   */
  G09("G09"),

  /**
   * stokes per kelvin
   */
  G10("G10"),

  /**
   * gram per cubic centimetre bar
   */
  G11("G11"),

  /**
   * gram per cubic decimetre bar
   */
  G12("G12"),

  /**
   * gram per litre bar
   */
  G13("G13"),

  /**
   * gram per cubic metre bar
   */
  G14("G14"),

  /**
   * gram per millilitre bar
   */
  G15("G15"),

  /**
   * kilogram per cubic centimetre bar
   */
  G16("G16"),

  /**
   * kilogram per litre bar
   */
  G17("G17"),

  /**
   * kilogram per cubic metre bar
   */
  G18("G18"),

  /**
   * newton metre per kilogram
   */
  G19("G19"),

  /**
   * US gallon per minute
   */
  G2("G2"),

  /**
   * pound-force foot per pound
   */
  G20("G20"),

  /**
   * cup [unit of volume]
   */
  G21("G21"),

  /**
   * peck
   */
  G23("G23"),

  /**
   * tablespoon (US)
   */
  G24("G24"),

  /**
   * teaspoon (US)
   */
  G25("G25"),

  /**
   * stere
   */
  G26("G26"),

  /**
   * cubic centimetre per kelvin
   */
  G27("G27"),

  /**
   * litre per kelvin
   */
  G28("G28"),

  /**
   * cubic metre per kelvin
   */
  G29("G29"),

  /**
   * Imperial gallon per minute
   */
  G3("G3"),

  /**
   * millilitre per kelvin
   */
  G30("G30"),

  /**
   * kilogram per cubic centimetre
   */
  G31("G31"),

  /**
   * ounce (avoirdupois) per cubic yard
   */
  G32("G32"),

  /**
   * gram per cubic centimetre kelvin
   */
  G33("G33"),

  /**
   * gram per cubic decimetre kelvin
   */
  G34("G34"),

  /**
   * gram per litre kelvin
   */
  G35("G35"),

  /**
   * gram per cubic metre kelvin
   */
  G36("G36"),

  /**
   * gram per millilitre kelvin
   */
  G37("G37"),

  /**
   * kilogram per cubic centimetre kelvin
   */
  G38("G38"),

  /**
   * kilogram per litre kelvin
   */
  G39("G39"),

  /**
   * kilogram per cubic metre kelvin
   */
  G40("G40"),

  /**
   * square metre per second bar
   */
  G41("G41"),

  /**
   * microsiemens per centimetre
   */
  G42("G42"),

  /**
   * microsiemens per metre
   */
  G43("G43"),

  /**
   * nanosiemens per centimetre
   */
  G44("G44"),

  /**
   * nanosiemens per metre
   */
  G45("G45"),

  /**
   * stokes per bar
   */
  G46("G46"),

  /**
   * cubic centimetre per day
   */
  G47("G47"),

  /**
   * cubic centimetre per hour
   */
  G48("G48"),

  /**
   * cubic centimetre per minute
   */
  G49("G49"),

  /**
   * gallon (US) per hour
   */
  G50("G50"),

  /**
   * litre per second
   */
  G51("G51"),

  /**
   * cubic metre per day
   */
  G52("G52"),

  /**
   * cubic metre per minute
   */
  G53("G53"),

  /**
   * millilitre per day
   */
  G54("G54"),

  /**
   * millilitre per hour
   */
  G55("G55"),

  /**
   * cubic inch per hour
   */
  G56("G56"),

  /**
   * cubic inch per minute
   */
  G57("G57"),

  /**
   * cubic inch per second
   */
  G58("G58"),

  /**
   * milliampere per litre minute
   */
  G59("G59"),

  /**
   * volt per bar
   */
  G60("G60"),

  /**
   * cubic centimetre per day kelvin
   */
  G61("G61"),

  /**
   * cubic centimetre per hour kelvin
   */
  G62("G62"),

  /**
   * cubic centimetre per minute kelvin
   */
  G63("G63"),

  /**
   * cubic centimetre per second kelvin
   */
  G64("G64"),

  /**
   * litre per day kelvin
   */
  G65("G65"),

  /**
   * litre per hour kelvin
   */
  G66("G66"),

  /**
   * litre per minute kelvin
   */
  G67("G67"),

  /**
   * litre per second kelvin
   */
  G68("G68"),

  /**
   * cubic metre per day kelvin
   */
  G69("G69"),

  /**
   * cubic metre per hour kelvin
   */
  G70("G70"),

  /**
   * cubic metre per minute kelvin
   */
  G71("G71"),

  /**
   * cubic metre per second kelvin
   */
  G72("G72"),

  /**
   * millilitre per day kelvin
   */
  G73("G73"),

  /**
   * millilitre per hour kelvin
   */
  G74("G74"),

  /**
   * millilitre per minute kelvin
   */
  G75("G75"),

  /**
   * millilitre per second kelvin
   */
  G76("G76"),

  /**
   * millimetre to the fourth power
   */
  G77("G77"),

  /**
   * cubic centimetre per day bar
   */
  G78("G78"),

  /**
   * cubic centimetre per hour bar
   */
  G79("G79"),

  /**
   * cubic centimetre per minute bar
   */
  G80("G80"),

  /**
   * cubic centimetre per second bar
   */
  G81("G81"),

  /**
   * litre per day bar
   */
  G82("G82"),

  /**
   * litre per hour bar
   */
  G83("G83"),

  /**
   * litre per minute bar
   */
  G84("G84"),

  /**
   * litre per second bar
   */
  G85("G85"),

  /**
   * cubic metre per day bar
   */
  G86("G86"),

  /**
   * cubic metre per hour bar
   */
  G87("G87"),

  /**
   * cubic metre per minute bar
   */
  G88("G88"),

  /**
   * cubic metre per second bar
   */
  G89("G89"),

  /**
   * millilitre per day bar
   */
  G90("G90"),

  /**
   * millilitre per hour bar
   */
  G91("G91"),

  /**
   * millilitre per minute bar
   */
  G92("G92"),

  /**
   * millilitre per second bar
   */
  G93("G93"),

  /**
   * cubic centimetre per bar
   */
  G94("G94"),

  /**
   * litre per bar
   */
  G95("G95"),

  /**
   * cubic metre per bar
   */
  G96("G96"),

  /**
   * millilitre per bar
   */
  G97("G97"),

  /**
   * microhenry per kiloohm
   */
  G98("G98"),

  /**
   * microhenry per ohm
   */
  G99("G99"),

  /**
   * gallon (US) per day
   */
  GB("GB"),

  /**
   * gigabecquerel
   */
  GBQ("GBQ"),

  /**
   * gram, dry weight
   */
  GDW("GDW"),

  /**
   * pound per gallon (US)
   */
  GE("GE"),

  /**
   * gram per metre (gram per 100 centimetres)
   */
  GF("GF"),

  /**
   * gram of fissile isotope
   */
  GFI("GFI"),

  /**
   * great gross
   */
  GGR("GGR"),

  /**
   * gill (US)
   */
  GIA("GIA"),

  /**
   * gram, including container
   */
  GIC("GIC"),

  /**
   * gill (UK)
   */
  GII("GII"),

  /**
   * gram, including inner packaging
   */
  GIP("GIP"),

  /**
   * gram per millilitre
   */
  GJ("GJ"),

  /**
   * gram per litre
   */
  GL("GL"),

  /**
   * dry gallon (US)
   */
  GLD("GLD"),

  /**
   * gallon (UK)
   */
  GLI("GLI"),

  /**
   * gallon (US)
   */
  GLL("GLL"),

  /**
   * gram per square metre
   */
  GM("GM"),

  /**
   * milligram per square metre
   */
  GO("GO"),

  /**
   * milligram per cubic metre
   */
  GP("GP"),

  /**
   * microgram per cubic metre
   */
  GQ("GQ"),

  /**
   * gram
   */
  GRM("GRM"),

  /**
   * grain
   */
  GRN("GRN"),

  /**
   * gross
   */
  GRO("GRO"),

  /**
   * gigajoule
   */
  GV("GV"),

  /**
   * gigawatt hour
   */
  GWH("GWH"),

  /**
   * henry per kiloohm
   */
  H03("H03"),

  /**
   * henry per ohm
   */
  H04("H04"),

  /**
   * millihenry per kiloohm
   */
  H05("H05"),

  /**
   * millihenry per ohm
   */
  H06("H06"),

  /**
   * pascal second per bar
   */
  H07("H07"),

  /**
   * microbecquerel
   */
  H08("H08"),

  /**
   * reciprocal year
   */
  H09("H09"),

  /**
   * reciprocal hour
   */
  H10("H10"),

  /**
   * reciprocal month
   */
  H11("H11"),

  /**
   * degree Celsius per hour
   */
  H12("H12"),

  /**
   * degree Celsius per minute
   */
  H13("H13"),

  /**
   * degree Celsius per second
   */
  H14("H14"),

  /**
   * square centimetre per gram
   */
  H15("H15"),

  /**
   * square decametre
   */
  H16("H16"),

  /**
   * square hectometre
   */
  H18("H18"),

  /**
   * cubic hectometre
   */
  H19("H19"),

  /**
   * cubic kilometre
   */
  H20("H20"),

  /**
   * blank
   */
  H21("H21"),

  /**
   * volt square inch per pound-force
   */
  H22("H22"),

  /**
   * volt per inch
   */
  H23("H23"),

  /**
   * volt per microsecond
   */
  H24("H24"),

  /**
   * percent per kelvin
   */
  H25("H25"),

  /**
   * ohm per metre
   */
  H26("H26"),

  /**
   * degree per metre
   */
  H27("H27"),

  /**
   * microfarad per kilometre
   */
  H28("H28"),

  /**
   * microgram per litre
   */
  H29("H29"),

  /**
   * square micrometre (square micron)
   */
  H30("H30"),

  /**
   * ampere per kilogram
   */
  H31("H31"),

  /**
   * ampere squared second
   */
  H32("H32"),

  /**
   * farad per kilometre
   */
  H33("H33"),

  /**
   * hertz metre
   */
  H34("H34"),

  /**
   * kelvin metre per watt
   */
  H35("H35"),

  /**
   * megaohm per kilometre
   */
  H36("H36"),

  /**
   * megaohm per metre
   */
  H37("H37"),

  /**
   * megaampere
   */
  H38("H38"),

  /**
   * megahertz kilometre
   */
  H39("H39"),

  /**
   * newton per ampere
   */
  H40("H40"),

  /**
   * newton metre watt to the power minus 0,5
   */
  H41("H41"),

  /**
   * pascal per metre
   */
  H42("H42"),

  /**
   * siemens per centimetre
   */
  H43("H43"),

  /**
   * teraohm
   */
  H44("H44"),

  /**
   * volt second per metre
   */
  H45("H45"),

  /**
   * volt per second
   */
  H46("H46"),

  /**
   * watt per cubic metre
   */
  H47("H47"),

  /**
   * attofarad
   */
  H48("H48"),

  /**
   * centimetre per hour
   */
  H49("H49"),

  /**
   * reciprocal cubic centimetre
   */
  H50("H50"),

  /**
   * decibel per kilometre
   */
  H51("H51"),

  /**
   * decibel per metre
   */
  H52("H52"),

  /**
   * kilogram per bar
   */
  H53("H53"),

  /**
   * kilogram per cubic decimetre kelvin
   */
  H54("H54"),

  /**
   * kilogram per cubic decimetre bar
   */
  H55("H55"),

  /**
   * kilogram per square metre second
   */
  H56("H56"),

  /**
   * inch per two pi radiant
   */
  H57("H57"),

  /**
   * metre per volt second
   */
  H58("H58"),

  /**
   * square metre per newton
   */
  H59("H59"),

  /**
   * cubic metre per cubic metre
   */
  H60("H60"),

  /**
   * millisiemens per centimetre
   */
  H61("H61"),

  /**
   * millivolt per minute
   */
  H62("H62"),

  /**
   * milligram per square centimetre
   */
  H63("H63"),

  /**
   * milligram per gram
   */
  H64("H64"),

  /**
   * millilitre per cubic metre
   */
  H65("H65"),

  /**
   * millimetre per year
   */
  H66("H66"),

  /**
   * millimetre per hour
   */
  H67("H67"),

  /**
   * millimole per gram
   */
  H68("H68"),

  /**
   * picopascal per kilometre
   */
  H69("H69"),

  /**
   * picosecond
   */
  H70("H70"),

  /**
   * percent per month
   */
  H71("H71"),

  /**
   * percent per hectobar
   */
  H72("H72"),

  /**
   * percent per decakelvin
   */
  H73("H73"),

  /**
   * watt per metre
   */
  H74("H74"),

  /**
   * decapascal
   */
  H75("H75"),

  /**
   * gram per millimetre
   */
  H76("H76"),

  /**
   * module width
   */
  H77("H77"),

  /**
   * French gauge
   */
  H79("H79"),

  /**
   * rack unit
   */
  H80("H80"),

  /**
   * millimetre per minute
   */
  H81("H81"),

  /**
   * big point
   */
  H82("H82"),

  /**
   * litre per kilogram
   */
  H83("H83"),

  /**
   * gram millimetre
   */
  H84("H84"),

  /**
   * reciprocal week
   */
  H85("H85"),

  /**
   * piece
   */
  H87("H87"),

  /**
   * megaohm kilometre
   */
  H88("H88"),

  /**
   * percent per ohm
   */
  H89("H89"),

  /**
   * percent per degree
   */
  H90("H90"),

  /**
   * percent per ten thousand
   */
  H91("H91"),

  /**
   * percent per one hundred thousand
   */
  H92("H92"),

  /**
   * percent per hundred
   */
  H93("H93"),

  /**
   * percent per thousand
   */
  H94("H94"),

  /**
   * percent per volt
   */
  H95("H95"),

  /**
   * percent per bar
   */
  H96("H96"),

  /**
   * percent per inch
   */
  H98("H98"),

  /**
   * percent per metre
   */
  H99("H99"),

  /**
   * hank
   */
  HA("HA"),

  /**
   * Piece Day
   */
  HAD("HAD"),

  /**
   * hectobar
   */
  HBA("HBA"),

  /**
   * hundred boxes
   */
  HBX("HBX"),

  /**
   * hundred count
   */
  HC("HC"),

  /**
   * hundred kilogram, dry weight
   */
  HDW("HDW"),

  /**
   * head
   */
  HEA("HEA"),

  /**
   * hectogram
   */
  HGM("HGM"),

  /**
   * hundred cubic foot
   */
  HH("HH"),

  /**
   * hundred international unit
   */
  HIU("HIU"),

  /**
   * hundred kilogram, net mass
   */
  HKM("HKM"),

  /**
   * hectolitre
   */
  HLT("HLT"),

  /**
   * mile per hour (statute mile)
   */
  HM("HM"),

  /**
   * Piece Month
   */
  HMO("HMO"),

  /**
   * million cubic metre
   */
  HMQ("HMQ"),

  /**
   * hectometre
   */
  HMT("HMT"),

  /**
   * hectolitre of pure alcohol
   */
  HPA("HPA"),

  /**
   * hertz
   */
  HTZ("HTZ"),

  /**
   * hour
   */
  HUR("HUR"),

  /**
   * Piece Week
   */
  HWE("HWE"),

  /**
   * inch pound (pound inch)
   */
  IA("IA"),

  /**
   * person
   */
  IE("IE"),

  /**
   * inch
   */
  INH("INH"),

  /**
   * square inch
   */
  INK("INK"),

  /**
   * cubic inch
   */
  INQ("INQ"),

  /**
   * international sugar degree
   */
  ISD("ISD"),

  /**
   * inch per second
   */
  IU("IU"),

  /**
   * international unit per gram
   */
  IUG("IUG"),

  /**
   * inch per second squared
   */
  IV("IV"),

  /**
   * percent per millimetre
   */
  J10("J10"),

  /**
   * per mille per psi
   */
  J12("J12"),

  /**
   * degree API
   */
  J13("J13"),

  /**
   * degree Baume (origin scale)
   */
  J14("J14"),

  /**
   * degree Baume (US heavy)
   */
  J15("J15"),

  /**
   * degree Baume (US light)
   */
  J16("J16"),

  /**
   * degree Balling
   */
  J17("J17"),

  /**
   * degree Brix
   */
  J18("J18"),

  /**
   * degree Fahrenheit hour square foot per British thermal unit (thermochemical)
   */
  J19("J19"),

  /**
   * joule per kilogram
   */
  J2("J2"),

  /**
   * degree Fahrenheit per kelvin
   */
  J20("J20"),

  /**
   * degree Fahrenheit per bar
   */
  J21("J21"),

  /**
   * degree Fahrenheit hour square foot per British thermal unit (international table)
   */
  J22("J22"),

  /**
   * degree Fahrenheit per hour
   */
  J23("J23"),

  /**
   * degree Fahrenheit per minute
   */
  J24("J24"),

  /**
   * degree Fahrenheit per second
   */
  J25("J25"),

  /**
   * reciprocal degree Fahrenheit
   */
  J26("J26"),

  /**
   * degree Oechsle
   */
  J27("J27"),

  /**
   * degree Rankine per hour
   */
  J28("J28"),

  /**
   * degree Rankine per minute
   */
  J29("J29"),

  /**
   * degree Rankine per second
   */
  J30("J30"),

  /**
   * degree Twaddell
   */
  J31("J31"),

  /**
   * micropoise
   */
  J32("J32"),

  /**
   * microgram per kilogram
   */
  J33("J33"),

  /**
   * microgram per cubic metre kelvin
   */
  J34("J34"),

  /**
   * microgram per cubic metre bar
   */
  J35("J35"),

  /**
   * microlitre per litre
   */
  J36("J36"),

  /**
   * baud
   */
  J38("J38"),

  /**
   * British thermal unit (mean)
   */
  J39("J39"),

  /**
   * British thermal unit (international table) foot per hour square foot degree Fahrenheit
   */
  J40("J40"),

  /**
   * British thermal unit (international table) inch per hour square foot degree Fahrenheit
   */
  J41("J41"),

  /**
   * British thermal unit (international table) inch per second square foot degree Fahrenheit
   */
  J42("J42"),

  /**
   * British thermal unit (international table) per pound degree Fahrenheit
   */
  J43("J43"),

  /**
   * British thermal unit (international table) per minute
   */
  J44("J44"),

  /**
   * British thermal unit (international table) per second
   */
  J45("J45"),

  /**
   * British thermal unit (thermochemical) foot per hour square foot degree Fahrenheit
   */
  J46("J46"),

  /**
   * British thermal unit (thermochemical) per hour
   */
  J47("J47"),

  /**
   * British thermal unit (thermochemical) inch per hour square foot degree Fahrenheit
   */
  J48("J48"),

  /**
   * British thermal unit (thermochemical) inch per second square foot degree Fahrenheit
   */
  J49("J49"),

  /**
   * British thermal unit (thermochemical) per pound degree Fahrenheit
   */
  J50("J50"),

  /**
   * British thermal unit (thermochemical) per minute
   */
  J51("J51"),

  /**
   * British thermal unit (thermochemical) per second
   */
  J52("J52"),

  /**
   * coulomb square metre per kilogram
   */
  J53("J53"),

  /**
   * megabaud
   */
  J54("J54"),

  /**
   * watt second
   */
  J55("J55"),

  /**
   * bar per bar
   */
  J56("J56"),

  /**
   * barrel (UK petroleum)
   */
  J57("J57"),

  /**
   * barrel (UK petroleum) per minute
   */
  J58("J58"),

  /**
   * barrel (UK petroleum) per day
   */
  J59("J59"),

  /**
   * barrel (UK petroleum) per hour
   */
  J60("J60"),

  /**
   * barrel (UK petroleum) per second
   */
  J61("J61"),

  /**
   * barrel (US petroleum) per hour
   */
  J62("J62"),

  /**
   * barrel (US petroleum) per second
   */
  J63("J63"),

  /**
   * bushel (UK) per day
   */
  J64("J64"),

  /**
   * bushel (UK) per hour
   */
  J65("J65"),

  /**
   * bushel (UK) per minute
   */
  J66("J66"),

  /**
   * bushel (UK) per second
   */
  J67("J67"),

  /**
   * bushel (US dry) per day
   */
  J68("J68"),

  /**
   * bushel (US dry) per hour
   */
  J69("J69"),

  /**
   * bushel (US dry) per minute
   */
  J70("J70"),

  /**
   * bushel (US dry) per second
   */
  J71("J71"),

  /**
   * centinewton metre
   */
  J72("J72"),

  /**
   * centipoise per kelvin
   */
  J73("J73"),

  /**
   * centipoise per bar
   */
  J74("J74"),

  /**
   * calorie (mean)
   */
  J75("J75"),

  /**
   * calorie (international table) per gram degree Celsius
   */
  J76("J76"),

  /**
   * calorie (thermochemical) per centimetre second degree Celsius
   */
  J78("J78"),

  /**
   * calorie (thermochemical) per gram degree Celsius
   */
  J79("J79"),

  /**
   * calorie (thermochemical) per minute
   */
  J81("J81"),

  /**
   * calorie (thermochemical) per second
   */
  J82("J82"),

  /**
   * clo
   */
  J83("J83"),

  /**
   * centimetre per second kelvin
   */
  J84("J84"),

  /**
   * centimetre per second bar
   */
  J85("J85"),

  /**
   * cubic centimetre per cubic metre
   */
  J87("J87"),

  /**
   * cubic decimetre per day
   */
  J90("J90"),

  /**
   * cubic decimetre per cubic metre
   */
  J91("J91"),

  /**
   * cubic decimetre per minute
   */
  J92("J92"),

  /**
   * cubic decimetre per second
   */
  J93("J93"),

  /**
   * ounce (UK fluid) per day
   */
  J95("J95"),

  /**
   * ounce (UK fluid) per hour
   */
  J96("J96"),

  /**
   * ounce (UK fluid) per minute
   */
  J97("J97"),

  /**
   * ounce (UK fluid) per second
   */
  J98("J98"),

  /**
   * ounce (US fluid) per day
   */
  J99("J99"),

  /**
   * joule per kelvin
   */
  JE("JE"),

  /**
   * megajoule per kilogram
   */
  JK("JK"),

  /**
   * megajoule per cubic metre
   */
  JM("JM"),

  /**
   * pipeline joint
   */
  JNT("JNT"),

  /**
   * joule
   */
  JOU("JOU"),

  /**
   * hundred metre
   */
  JPS("JPS"),

  /**
   * number of jewels
   */
  JWL("JWL"),

  /**
   * kilowatt demand
   */
  K1("K1"),

  /**
   * ounce (US fluid) per hour
   */
  K10("K10"),

  /**
   * ounce (US fluid) per minute
   */
  K11("K11"),

  /**
   * ounce (US fluid) per second
   */
  K12("K12"),

  /**
   * foot per degree Fahrenheit
   */
  K13("K13"),

  /**
   * foot per hour
   */
  K14("K14"),

  /**
   * foot pound-force per hour
   */
  K15("K15"),

  /**
   * foot pound-force per minute
   */
  K16("K16"),

  /**
   * foot per psi
   */
  K17("K17"),

  /**
   * foot per second degree Fahrenheit
   */
  K18("K18"),

  /**
   * foot per second psi
   */
  K19("K19"),

  /**
   * kilovolt ampere reactive demand
   */
  K2("K2"),

  /**
   * reciprocal cubic foot
   */
  K20("K20"),

  /**
   * cubic foot per degree Fahrenheit
   */
  K21("K21"),

  /**
   * cubic foot per day
   */
  K22("K22"),

  /**
   * cubic foot per psi
   */
  K23("K23"),

  /**
   * gallon (UK) per day
   */
  K26("K26"),

  /**
   * gallon (UK) per hour
   */
  K27("K27"),

  /**
   * gallon (UK) per second
   */
  K28("K28"),

  /**
   * kilovolt ampere reactive hour
   */
  K3("K3"),

  /**
   * gallon (US liquid) per second
   */
  K30("K30"),

  /**
   * gram-force per square centimetre
   */
  K31("K31"),

  /**
   * gill (UK) per day
   */
  K32("K32"),

  /**
   * gill (UK) per hour
   */
  K33("K33"),

  /**
   * gill (UK) per minute
   */
  K34("K34"),

  /**
   * gill (UK) per second
   */
  K35("K35"),

  /**
   * gill (US) per day
   */
  K36("K36"),

  /**
   * gill (US) per hour
   */
  K37("K37"),

  /**
   * gill (US) per minute
   */
  K38("K38"),

  /**
   * gill (US) per second
   */
  K39("K39"),

  /**
   * standard acceleration of free fall
   */
  K40("K40"),

  /**
   * grain per gallon (US)
   */
  K41("K41"),

  /**
   * horsepower (boiler)
   */
  K42("K42"),

  /**
   * horsepower (electric)
   */
  K43("K43"),

  /**
   * inch per degree Fahrenheit
   */
  K45("K45"),

  /**
   * inch per psi
   */
  K46("K46"),

  /**
   * inch per second degree Fahrenheit
   */
  K47("K47"),

  /**
   * inch per second psi
   */
  K48("K48"),

  /**
   * reciprocal cubic inch
   */
  K49("K49"),

  /**
   * kilobaud
   */
  K50("K50"),

  /**
   * kilocalorie (mean)
   */
  K51("K51"),

  /**
   * kilocalorie (international table) per hour metre degree Celsius
   */
  K52("K52"),

  /**
   * kilocalorie (thermochemical)
   */
  K53("K53"),

  /**
   * kilocalorie (thermochemical) per minute
   */
  K54("K54"),

  /**
   * kilocalorie (thermochemical) per second
   */
  K55("K55"),

  /**
   * kilomole per hour
   */
  K58("K58"),

  /**
   * kilomole per cubic metre kelvin
   */
  K59("K59"),

  /**
   * kilolitre
   */
  K6("K6"),

  /**
   * kilomole per cubic metre bar
   */
  K60("K60"),

  /**
   * kilomole per minute
   */
  K61("K61"),

  /**
   * litre per litre
   */
  K62("K62"),

  /**
   * reciprocal litre
   */
  K63("K63"),

  /**
   * pound (avoirdupois) per degree Fahrenheit
   */
  K64("K64"),

  /**
   * pound (avoirdupois) square foot
   */
  K65("K65"),

  /**
   * pound (avoirdupois) per day
   */
  K66("K66"),

  /**
   * pound per foot hour
   */
  K67("K67"),

  /**
   * pound per foot second
   */
  K68("K68"),

  /**
   * pound (avoirdupois) per cubic foot degree Fahrenheit
   */
  K69("K69"),

  /**
   * pound (avoirdupois) per cubic foot psi
   */
  K70("K70"),

  /**
   * pound (avoirdupois) per gallon (UK)
   */
  K71("K71"),

  /**
   * pound (avoirdupois) per hour degree Fahrenheit
   */
  K73("K73"),

  /**
   * pound (avoirdupois) per hour psi
   */
  K74("K74"),

  /**
   * pound (avoirdupois) per cubic inch degree Fahrenheit
   */
  K75("K75"),

  /**
   * pound (avoirdupois) per cubic inch psi
   */
  K76("K76"),

  /**
   * pound (avoirdupois) per psi
   */
  K77("K77"),

  /**
   * pound (avoirdupois) per minute
   */
  K78("K78"),

  /**
   * pound (avoirdupois) per minute degree Fahrenheit
   */
  K79("K79"),

  /**
   * pound (avoirdupois) per minute psi
   */
  K80("K80"),

  /**
   * pound (avoirdupois) per second
   */
  K81("K81"),

  /**
   * pound (avoirdupois) per second degree Fahrenheit
   */
  K82("K82"),

  /**
   * pound (avoirdupois) per second psi
   */
  K83("K83"),

  /**
   * pound per cubic yard
   */
  K84("K84"),

  /**
   * pound-force per square foot
   */
  K85("K85"),

  /**
   * pound-force per square inch degree Fahrenheit
   */
  K86("K86"),

  /**
   * psi cubic inch per second
   */
  K87("K87"),

  /**
   * psi litre per second
   */
  K88("K88"),

  /**
   * psi cubic metre per second
   */
  K89("K89"),

  /**
   * psi cubic yard per second
   */
  K90("K90"),

  /**
   * pound-force second per square foot
   */
  K91("K91"),

  /**
   * pound-force second per square inch
   */
  K92("K92"),

  /**
   * reciprocal psi
   */
  K93("K93"),

  /**
   * quart (UK liquid) per day
   */
  K94("K94"),

  /**
   * quart (UK liquid) per hour
   */
  K95("K95"),

  /**
   * quart (UK liquid) per minute
   */
  K96("K96"),

  /**
   * quart (UK liquid) per second
   */
  K97("K97"),

  /**
   * quart (US liquid) per day
   */
  K98("K98"),

  /**
   * quart (US liquid) per hour
   */
  K99("K99"),

  /**
   * cake
   */
  KA("KA"),

  /**
   * katal
   */
  KAT("KAT"),

  /**
   * kilocharacter
   */
  KB("KB"),

  /**
   * kilobar
   */
  KBA("KBA"),

  /**
   * kilogram of choline chloride
   */
  KCC("KCC"),

  /**
   * kilogram drained net weight
   */
  KDW("KDW"),

  /**
   * kelvin
   */
  KEL("KEL"),

  /**
   * kilogram
   */
  KGM("KGM"),

  /**
   * kilogram per second
   */
  KGS("KGS"),

  /**
   * kilogram of hydrogen peroxide
   */
  KHY("KHY"),

  /**
   * kilohertz
   */
  KHZ("KHZ"),

  /**
   * kilogram per millimetre width
   */
  KI("KI"),

  /**
   * kilogram, including container
   */
  KIC("KIC"),

  /**
   * kilogram, including inner packaging
   */
  KIP("KIP"),

  /**
   * kilosegment
   */
  KJ("KJ"),

  /**
   * kilojoule
   */
  KJO("KJO"),

  /**
   * kilogram per metre
   */
  KL("KL"),

  /**
   * lactic dry material percentage
   */
  KLK("KLK"),

  /**
   * kilolux
   */
  KLX("KLX"),

  /**
   * kilogram of methylamine
   */
  KMA("KMA"),

  /**
   * kilometre per hour
   */
  KMH("KMH"),

  /**
   * square kilometre
   */
  KMK("KMK"),

  /**
   * kilogram per cubic metre
   */
  KMQ("KMQ"),

  /**
   * kilometre
   */
  KMT("KMT"),

  /**
   * kilogram of nitrogen
   */
  KNI("KNI"),

  /**
   * kilonewton per square metre
   */
  KNM("KNM"),

  /**
   * kilogram named substance
   */
  KNS("KNS"),

  /**
   * knot
   */
  KNT("KNT"),

  /**
   * milliequivalence caustic potash per gram of product
   */
  KO("KO"),

  /**
   * kilopascal
   */
  KPA("KPA"),

  /**
   * kilogram of potassium hydroxide (caustic potash)
   */
  KPH("KPH"),

  /**
   * kilogram of potassium oxide
   */
  KPO("KPO"),

  /**
   * kilogram of phosphorus pentoxide (phosphoric anhydride)
   */
  KPP("KPP"),

  /**
   * kiloroentgen
   */
  KR("KR"),

  /**
   * kilogram of substance 90 % dry
   */
  KSD("KSD"),

  /**
   * kilogram of sodium hydroxide (caustic soda)
   */
  KSH("KSH"),

  /**
   * kit
   */
  KT("KT"),

  /**
   * kilotonne
   */
  KTN("KTN"),

  /**
   * kilogram of uranium
   */
  KUR("KUR"),

  /**
   * kilovolt - ampere
   */
  KVA("KVA"),

  /**
   * kilovar
   */
  KVR("KVR"),

  /**
   * kilovolt
   */
  KVT("KVT"),

  /**
   * kilogram per millimetre
   */
  KW("KW"),

  /**
   * kilowatt hour
   */
  KWH("KWH"),

  /**
   * Kilowatt hour per normalized cubic metre
   */
  KWN("KWN"),

  /**
   * kilogram of tungsten trioxide
   */
  KWO("KWO"),

  /**
   * Kilowatt hour per standard cubic metre
   */
  KWS("KWS"),

  /**
   * kilowatt
   */
  KWT("KWT"),

  /**
   * kilowatt year
   */
  KWY("KWY"),

  /**
   * millilitre per kilogram
   */
  KX("KX"),

  /**
   * quart (US liquid) per minute
   */
  L10("L10"),

  /**
   * quart (US liquid) per second
   */
  L11("L11"),

  /**
   * metre per second kelvin
   */
  L12("L12"),

  /**
   * metre per second bar
   */
  L13("L13"),

  /**
   * square metre hour degree Celsius per kilocalorie (international table)
   */
  L14("L14"),

  /**
   * millipascal second per kelvin
   */
  L15("L15"),

  /**
   * millipascal second per bar
   */
  L16("L16"),

  /**
   * milligram per cubic metre kelvin
   */
  L17("L17"),

  /**
   * milligram per cubic metre bar
   */
  L18("L18"),

  /**
   * millilitre per litre
   */
  L19("L19"),

  /**
   * litre per minute
   */
  L2("L2"),

  /**
   * reciprocal cubic millimetre
   */
  L20("L20"),

  /**
   * cubic millimetre per cubic metre
   */
  L21("L21"),

  /**
   * mole per hour
   */
  L23("L23"),

  /**
   * mole per kilogram kelvin
   */
  L24("L24"),

  /**
   * mole per kilogram bar
   */
  L25("L25"),

  /**
   * mole per litre kelvin
   */
  L26("L26"),

  /**
   * mole per litre bar
   */
  L27("L27"),

  /**
   * mole per cubic metre kelvin
   */
  L28("L28"),

  /**
   * mole per cubic metre bar
   */
  L29("L29"),

  /**
   * mole per minute
   */
  L30("L30"),

  /**
   * milliroentgen aequivalent men
   */
  L31("L31"),

  /**
   * nanogram per kilogram
   */
  L32("L32"),

  /**
   * ounce (avoirdupois) per day
   */
  L33("L33"),

  /**
   * ounce (avoirdupois) per hour
   */
  L34("L34"),

  /**
   * ounce (avoirdupois) per minute
   */
  L35("L35"),

  /**
   * ounce (avoirdupois) per second
   */
  L36("L36"),

  /**
   * ounce (avoirdupois) per gallon (UK)
   */
  L37("L37"),

  /**
   * ounce (avoirdupois) per gallon (US)
   */
  L38("L38"),

  /**
   * ounce (avoirdupois) per cubic inch
   */
  L39("L39"),

  /**
   * ounce (avoirdupois)-force
   */
  L40("L40"),

  /**
   * ounce (avoirdupois)-force inch
   */
  L41("L41"),

  /**
   * picosiemens per metre
   */
  L42("L42"),

  /**
   * peck (UK)
   */
  L43("L43"),

  /**
   * peck (UK) per day
   */
  L44("L44"),

  /**
   * peck (UK) per hour
   */
  L45("L45"),

  /**
   * peck (UK) per minute
   */
  L46("L46"),

  /**
   * peck (UK) per second
   */
  L47("L47"),

  /**
   * peck (US dry) per day
   */
  L48("L48"),

  /**
   * peck (US dry) per hour
   */
  L49("L49"),

  /**
   * peck (US dry) per minute
   */
  L50("L50"),

  /**
   * peck (US dry) per second
   */
  L51("L51"),

  /**
   * psi per psi
   */
  L52("L52"),

  /**
   * pint (UK) per day
   */
  L53("L53"),

  /**
   * pint (UK) per hour
   */
  L54("L54"),

  /**
   * pint (UK) per minute
   */
  L55("L55"),

  /**
   * pint (UK) per second
   */
  L56("L56"),

  /**
   * pint (US liquid) per day
   */
  L57("L57"),

  /**
   * pint (US liquid) per hour
   */
  L58("L58"),

  /**
   * pint (US liquid) per minute
   */
  L59("L59"),

  /**
   * pint (US liquid) per second
   */
  L60("L60"),

  /**
   * slug per day
   */
  L63("L63"),

  /**
   * slug per foot second
   */
  L64("L64"),

  /**
   * slug per cubic foot
   */
  L65("L65"),

  /**
   * slug per hour
   */
  L66("L66"),

  /**
   * slug per minute
   */
  L67("L67"),

  /**
   * slug per second
   */
  L68("L68"),

  /**
   * tonne per kelvin
   */
  L69("L69"),

  /**
   * tonne per bar
   */
  L70("L70"),

  /**
   * tonne per day
   */
  L71("L71"),

  /**
   * tonne per day kelvin
   */
  L72("L72"),

  /**
   * tonne per day bar
   */
  L73("L73"),

  /**
   * tonne per hour kelvin
   */
  L74("L74"),

  /**
   * tonne per hour bar
   */
  L75("L75"),

  /**
   * tonne per cubic metre kelvin
   */
  L76("L76"),

  /**
   * tonne per cubic metre bar
   */
  L77("L77"),

  /**
   * tonne per minute
   */
  L78("L78"),

  /**
   * tonne per minute kelvin
   */
  L79("L79"),

  /**
   * tonne per minute bar
   */
  L80("L80"),

  /**
   * tonne per second
   */
  L81("L81"),

  /**
   * tonne per second kelvin
   */
  L82("L82"),

  /**
   * tonne per second bar
   */
  L83("L83"),

  /**
   * ton (UK shipping)
   */
  L84("L84"),

  /**
   * ton long per day
   */
  L85("L85"),

  /**
   * ton (US shipping)
   */
  L86("L86"),

  /**
   * ton short per degree Fahrenheit
   */
  L87("L87"),

  /**
   * ton short per day
   */
  L88("L88"),

  /**
   * ton short per hour degree Fahrenheit
   */
  L89("L89"),

  /**
   * ton short per hour psi
   */
  L90("L90"),

  /**
   * ton short per psi
   */
  L91("L91"),

  /**
   * ton (UK long) per cubic yard
   */
  L92("L92"),

  /**
   * ton (US short) per cubic yard
   */
  L93("L93"),

  /**
   * ton-force (US short)
   */
  L94("L94"),

  /**
   * common year
   */
  L95("L95"),

  /**
   * sidereal year
   */
  L96("L96"),

  /**
   * yard per degree Fahrenheit
   */
  L98("L98"),

  /**
   * yard per psi
   */
  L99("L99"),

  /**
   * pound per cubic inch
   */
  LA("LA"),

  /**
   * lactose excess percentage
   */
  LAC("LAC"),

  /**
   * pound
   */
  LBR("LBR"),

  /**
   * troy pound (US)
   */
  LBT("LBT"),

  /**
   * litre per day
   */
  LD("LD"),

  /**
   * leaf
   */
  LEF("LEF"),

  /**
   * linear foot
   */
  LF("LF"),

  /**
   * labour hour
   */
  LH("LH"),

  /**
   * link
   */
  LK("LK"),

  /**
   * linear metre
   */
  LM("LM"),

  /**
   * length
   */
  LN("LN"),

  /**
   * lot [unit of procurement]
   */
  LO("LO"),

  /**
   * liquid pound
   */
  LP("LP"),

  /**
   * litre of pure alcohol
   */
  LPA("LPA"),

  /**
   * layer
   */
  LR("LR"),

  /**
   * lump sum
   */
  LS("LS"),

  /**
   * ton (UK) or long ton (US)
   */
  LTN("LTN"),

  /**
   * litre
   */
  LTR("LTR"),

  /**
   * metric ton, lubricating oil
   */
  LUB("LUB"),

  /**
   * lumen
   */
  LUM("LUM"),

  /**
   * lux
   */
  LUX("LUX"),

  /**
   * linear yard
   */
  LY("LY"),

  /**
   * milligram per litre
   */
  M1("M1"),

  /**
   * reciprocal cubic yard
   */
  M10("M10"),

  /**
   * cubic yard per degree Fahrenheit
   */
  M11("M11"),

  /**
   * cubic yard per day
   */
  M12("M12"),

  /**
   * cubic yard per hour
   */
  M13("M13"),

  /**
   * cubic yard per psi
   */
  M14("M14"),

  /**
   * cubic yard per minute
   */
  M15("M15"),

  /**
   * cubic yard per second
   */
  M16("M16"),

  /**
   * kilohertz metre
   */
  M17("M17"),

  /**
   * gigahertz metre
   */
  M18("M18"),

  /**
   * Beaufort
   */
  M19("M19"),

  /**
   * reciprocal megakelvin or megakelvin to the power minus one
   */
  M20("M20"),

  /**
   * reciprocal kilovolt - ampere reciprocal hour
   */
  M21("M21"),

  /**
   * millilitre per square centimetre minute
   */
  M22("M22"),

  /**
   * newton per centimetre
   */
  M23("M23"),

  /**
   * ohm kilometre
   */
  M24("M24"),

  /**
   * percent per degree Celsius
   */
  M25("M25"),

  /**
   * gigaohm per metre
   */
  M26("M26"),

  /**
   * megahertz metre
   */
  M27("M27"),

  /**
   * kilogram per kilogram
   */
  M29("M29"),

  /**
   * reciprocal volt - ampere reciprocal second
   */
  M30("M30"),

  /**
   * kilogram per kilometre
   */
  M31("M31"),

  /**
   * pascal second per litre
   */
  M32("M32"),

  /**
   * millimole per litre
   */
  M33("M33"),

  /**
   * newton metre per square metre
   */
  M34("M34"),

  /**
   * millivolt - ampere
   */
  M35("M35"),

  /**
   * 30-day month
   */
  M36("M36"),

  /**
   * actual/360
   */
  M37("M37"),

  /**
   * kilometre per second squared
   */
  M38("M38"),

  /**
   * centimetre per second squared
   */
  M39("M39"),

  /**
   * monetary value
   */
  M4("M4"),

  /**
   * yard per second squared
   */
  M40("M40"),

  /**
   * millimetre per second squared
   */
  M41("M41"),

  /**
   * mile (statute mile) per second squared
   */
  M42("M42"),

  /**
   * mil
   */
  M43("M43"),

  /**
   * revolution
   */
  M44("M44"),

  /**
   * degree [unit of angle] per second squared
   */
  M45("M45"),

  /**
   * revolution per minute
   */
  M46("M46"),

  /**
   * circular mil
   */
  M47("M47"),

  /**
   * square mile (based on U.S. survey foot)
   */
  M48("M48"),

  /**
   * chain (based on U.S. survey foot)
   */
  M49("M49"),

  /**
   * microcurie
   */
  M5("M5"),

  /**
   * furlong
   */
  M50("M50"),

  /**
   * foot (U.S. survey)
   */
  M51("M51"),

  /**
   * mile (based on U.S. survey foot)
   */
  M52("M52"),

  /**
   * metre per pascal
   */
  M53("M53"),

  /**
   * metre per radiant
   */
  M55("M55"),

  /**
   * shake
   */
  M56("M56"),

  /**
   * mile per minute
   */
  M57("M57"),

  /**
   * mile per second
   */
  M58("M58"),

  /**
   * metre per second pascal
   */
  M59("M59"),

  /**
   * metre per hour
   */
  M60("M60"),

  /**
   * inch per year
   */
  M61("M61"),

  /**
   * kilometre per second
   */
  M62("M62"),

  /**
   * inch per minute
   */
  M63("M63"),

  /**
   * yard per second
   */
  M64("M64"),

  /**
   * yard per minute
   */
  M65("M65"),

  /**
   * yard per hour
   */
  M66("M66"),

  /**
   * acre-foot (based on U.S. survey foot)
   */
  M67("M67"),

  /**
   * cord (128 ft3)
   */
  M68("M68"),

  /**
   * cubic mile (UK statute)
   */
  M69("M69"),

  /**
   * micro-inch
   */
  M7("M7"),

  /**
   * ton, register
   */
  M70("M70"),

  /**
   * cubic metre per pascal
   */
  M71("M71"),

  /**
   * bel
   */
  M72("M72"),

  /**
   * kilogram per cubic metre pascal
   */
  M73("M73"),

  /**
   * kilogram per pascal
   */
  M74("M74"),

  /**
   * kilopound-force
   */
  M75("M75"),

  /**
   * poundal
   */
  M76("M76"),

  /**
   * kilogram metre per second squared
   */
  M77("M77"),

  /**
   * pond
   */
  M78("M78"),

  /**
   * square foot per hour
   */
  M79("M79"),

  /**
   * stokes per pascal
   */
  M80("M80"),

  /**
   * square centimetre per second
   */
  M81("M81"),

  /**
   * square metre per second pascal
   */
  M82("M82"),

  /**
   * denier
   */
  M83("M83"),

  /**
   * pound per yard
   */
  M84("M84"),

  /**
   * ton, assay
   */
  M85("M85"),

  /**
   * pfund
   */
  M86("M86"),

  /**
   * kilogram per second pascal
   */
  M87("M87"),

  /**
   * tonne per month
   */
  M88("M88"),

  /**
   * tonne per year
   */
  M89("M89"),

  /**
   * million Btu per 1000 cubic foot
   */
  M9("M9"),

  /**
   * kilopound per hour
   */
  M90("M90"),

  /**
   * pound per pound
   */
  M91("M91"),

  /**
   * pound-force foot
   */
  M92("M92"),

  /**
   * newton metre per radian
   */
  M93("M93"),

  /**
   * kilogram metre
   */
  M94("M94"),

  /**
   * poundal foot
   */
  M95("M95"),

  /**
   * poundal inch
   */
  M96("M96"),

  /**
   * dyne metre
   */
  M97("M97"),

  /**
   * kilogram centimetre per second
   */
  M98("M98"),

  /**
   * gram centimetre per second
   */
  M99("M99"),

  /**
   * megavolt ampere reactive hour
   */
  MAH("MAH"),

  /**
   * megalitre
   */
  MAL("MAL"),

  /**
   * megametre
   */
  MAM("MAM"),

  /**
   * megavar
   */
  MAR("MAR"),

  /**
   * megawatt
   */
  MAW("MAW"),

  /**
   * thousand standard brick equivalent
   */
  MBE("MBE"),

  /**
   * thousand board foot
   */
  MBF("MBF"),

  /**
   * millibar
   */
  MBR("MBR"),

  /**
   * microgram
   */
  MC("MC"),

  /**
   * millicurie
   */
  MCU("MCU"),

  /**
   * air dry metric ton
   */
  MD("MD"),

  /**
   * milligram
   */
  MGM("MGM"),

  /**
   * megahertz
   */
  MHZ("MHZ"),

  /**
   * square mile (statute mile)
   */
  MIK("MIK"),

  /**
   * thousand
   */
  MIL("MIL"),

  /**
   * minute [unit of time]
   */
  MIN("MIN"),

  /**
   * million
   */
  MIO("MIO"),

  /**
   * million international unit
   */
  MIU("MIU"),

  /**
   * Square Metre Day
   */
  MKD("MKD"),

  /**
   * Square Metre Month
   */
  MKM("MKM"),

  /**
   * Square Metre Week
   */
  MKW("MKW"),

  /**
   * milliard
   */
  MLD("MLD"),

  /**
   * millilitre
   */
  MLT("MLT"),

  /**
   * square millimetre
   */
  MMK("MMK"),

  /**
   * cubic millimetre
   */
  MMQ("MMQ"),

  /**
   * millimetre
   */
  MMT("MMT"),

  /**
   * kilogram, dry weight
   */
  MND("MND"),

  /**
   * Mega Joule per Normalised cubic Metre
   */
  MNJ("MNJ"),

  /**
   * month
   */
  MON("MON"),

  /**
   * megapascal
   */
  MPA("MPA"),

  /**
   * Cubic Metre Day
   */
  MQD("MQD"),

  /**
   * cubic metre per hour
   */
  MQH("MQH"),

  /**
   * Cubic Metre Month
   */
  MQM("MQM"),

  /**
   * cubic metre per second
   */
  MQS("MQS"),

  /**
   * Cubic Metre Week
   */
  MQW("MQW"),

  /**
   * Metre Day
   */
  MRD("MRD"),

  /**
   * Metre Month
   */
  MRM("MRM"),

  /**
   * Metre Week
   */
  MRW("MRW"),

  /**
   * metre per second squared
   */
  MSK("MSK"),

  /**
   * square metre
   */
  MTK("MTK"),

  /**
   * cubic metre
   */
  MTQ("MTQ"),

  /**
   * metre
   */
  MTR("MTR"),

  /**
   * metre per second
   */
  MTS("MTS"),

  /**
   * milihertz
   */
  MTZ("MTZ"),

  /**
   * megavolt - ampere
   */
  MVA("MVA"),

  /**
   * megawatt hour (1000 kW.h)
   */
  MWH("MWH"),

  /**
   * pen calorie
   */
  N1("N1"),

  /**
   * pound foot per second
   */
  N10("N10"),

  /**
   * pound inch per second
   */
  N11("N11"),

  /**
   * Pferdestaerke
   */
  N12("N12"),

  /**
   * centimetre of mercury (0 ºC)
   */
  N13("N13"),

  /**
   * centimetre of water (4 ºC)
   */
  N14("N14"),

  /**
   * foot of water (39.2 ºF)
   */
  N15("N15"),

  /**
   * inch of mercury (32 ºF)
   */
  N16("N16"),

  /**
   * inch of mercury (60 ºF)
   */
  N17("N17"),

  /**
   * inch of water (39.2 ºF)
   */
  N18("N18"),

  /**
   * inch of water (60 ºF)
   */
  N19("N19"),

  /**
   * kip per square inch
   */
  N20("N20"),

  /**
   * poundal per square foot
   */
  N21("N21"),

  /**
   * ounce (avoirdupois) per square inch
   */
  N22("N22"),

  /**
   * conventional metre of water
   */
  N23("N23"),

  /**
   * gram per square millimetre
   */
  N24("N24"),

  /**
   * pound per square yard
   */
  N25("N25"),

  /**
   * poundal per square inch
   */
  N26("N26"),

  /**
   * foot to the fourth power
   */
  N27("N27"),

  /**
   * cubic decimetre per kilogram
   */
  N28("N28"),

  /**
   * cubic foot per pound
   */
  N29("N29"),

  /**
   * print point
   */
  N3("N3"),

  /**
   * cubic inch per pound
   */
  N30("N30"),

  /**
   * kilonewton per metre
   */
  N31("N31"),

  /**
   * poundal per inch
   */
  N32("N32"),

  /**
   * pound-force per yard
   */
  N33("N33"),

  /**
   * poundal second per square foot
   */
  N34("N34"),

  /**
   * poise per pascal
   */
  N35("N35"),

  /**
   * newton second per square metre
   */
  N36("N36"),

  /**
   * kilogram per metre second
   */
  N37("N37"),

  /**
   * kilogram per metre minute
   */
  N38("N38"),

  /**
   * kilogram per metre day
   */
  N39("N39"),

  /**
   * kilogram per metre hour
   */
  N40("N40"),

  /**
   * gram per centimetre second
   */
  N41("N41"),

  /**
   * poundal second per square inch
   */
  N42("N42"),

  /**
   * pound per foot minute
   */
  N43("N43"),

  /**
   * pound per foot day
   */
  N44("N44"),

  /**
   * cubic metre per second pascal
   */
  N45("N45"),

  /**
   * foot poundal
   */
  N46("N46"),

  /**
   * inch poundal
   */
  N47("N47"),

  /**
   * watt per square centimetre
   */
  N48("N48"),

  /**
   * watt per square inch
   */
  N49("N49"),

  /**
   * British thermal unit (international table) per square foot hour
   */
  N50("N50"),

  /**
   * British thermal unit (thermochemical) per square foot hour
   */
  N51("N51"),

  /**
   * British thermal unit (thermochemical) per square foot minute
   */
  N52("N52"),

  /**
   * British thermal unit (international table) per square foot second
   */
  N53("N53"),

  /**
   * British thermal unit (thermochemical) per square foot second
   */
  N54("N54"),

  /**
   * British thermal unit (international table) per square inch second
   */
  N55("N55"),

  /**
   * calorie (thermochemical) per square centimetre minute
   */
  N56("N56"),

  /**
   * calorie (thermochemical) per square centimetre second
   */
  N57("N57"),

  /**
   * British thermal unit (international table) per cubic foot
   */
  N58("N58"),

  /**
   * British thermal unit (thermochemical) per cubic foot
   */
  N59("N59"),

  /**
   * British thermal unit (international table) per degree Fahrenheit
   */
  N60("N60"),

  /**
   * British thermal unit (thermochemical) per degree Fahrenheit
   */
  N61("N61"),

  /**
   * British thermal unit (international table) per degree Rankine
   */
  N62("N62"),

  /**
   * British thermal unit (thermochemical) per degree Rankine
   */
  N63("N63"),

  /**
   * British thermal unit (thermochemical) per pound degree Rankine
   */
  N64("N64"),

  /**
   * kilocalorie (international table) per gram kelvin
   */
  N65("N65"),

  /**
   * British thermal unit (39 ºF)
   */
  N66("N66"),

  /**
   * British thermal unit (59 ºF)
   */
  N67("N67"),

  /**
   * British thermal unit (60 ºF)
   */
  N68("N68"),

  /**
   * calorie (20 ºC)
   */
  N69("N69"),

  /**
   * quad (1015 BtuIT)
   */
  N70("N70"),

  /**
   * therm (EC)
   */
  N71("N71"),

  /**
   * therm (U.S.)
   */
  N72("N72"),

  /**
   * British thermal unit (thermochemical) per pound
   */
  N73("N73"),

  /**
   * British thermal unit (international table) per hour square foot degree Fahrenheit
   */
  N74("N74"),

  /**
   * British thermal unit (thermochemical) per hour square foot degree Fahrenheit
   */
  N75("N75"),

  /**
   * British thermal unit (international table) per second square foot degree Fahrenheit
   */
  N76("N76"),

  /**
   * British thermal unit (thermochemical) per second square foot degree Fahrenheit
   */
  N77("N77"),

  /**
   * kilowatt per square metre kelvin
   */
  N78("N78"),

  /**
   * kelvin per pascal
   */
  N79("N79"),

  /**
   * watt per metre degree Celsius
   */
  N80("N80"),

  /**
   * kilowatt per metre kelvin
   */
  N81("N81"),

  /**
   * kilowatt per metre degree Celsius
   */
  N82("N82"),

  /**
   * metre per degree Celcius metre
   */
  N83("N83"),

  /**
   * degree Fahrenheit hour per British thermal unit (international table)
   */
  N84("N84"),

  /**
   * degree Fahrenheit hour per British thermal unit (thermochemical)
   */
  N85("N85"),

  /**
   * degree Fahrenheit second per British thermal unit (international table)
   */
  N86("N86"),

  /**
   * degree Fahrenheit second per British thermal unit (thermochemical)
   */
  N87("N87"),

  /**
   * degree Fahrenheit hour square foot per British thermal unit (international table) inch
   */
  N88("N88"),

  /**
   * degree Fahrenheit hour square foot per British thermal unit (thermochemical) inch
   */
  N89("N89"),

  /**
   * kilofarad
   */
  N90("N90"),

  /**
   * reciprocal joule
   */
  N91("N91"),

  /**
   * picosiemens
   */
  N92("N92"),

  /**
   * ampere per pascal
   */
  N93("N93"),

  /**
   * franklin
   */
  N94("N94"),

  /**
   * ampere minute
   */
  N95("N95"),

  /**
   * biot
   */
  N96("N96"),

  /**
   * gilbert
   */
  N97("N97"),

  /**
   * volt per pascal
   */
  N98("N98"),

  /**
   * picovolt
   */
  N99("N99"),

  /**
   * milligram per kilogram
   */
  NA("NA"),

  /**
   * number of articles
   */
  NAR("NAR"),

  /**
   * number of cells
   */
  NCL("NCL"),

  /**
   * newton
   */
  NEW("NEW"),

  /**
   * message
   */
  NF("NF"),

  /**
   * nil
   */
  NIL("NIL"),

  /**
   * number of international units
   */
  NIU("NIU"),

  /**
   * load
   */
  NL("NL"),

  /**
   * Normalised cubic metre
   */
  NM3("NM3"),

  /**
   * nautical mile
   */
  NMI("NMI"),

  /**
   * number of packs
   */
  NMP("NMP"),

  /**
   * number of parts
   */
  NPT("NPT"),

  /**
   * net ton
   */
  NT("NT"),

  /**
   * Nephelometric turbidity unit
   */
  NTU("NTU"),

  /**
   * newton metre
   */
  NU("NU"),

  /**
   * part per thousand
   */
  NX("NX"),

  /**
   * panel
   */
  OA("OA"),

  /**
   * ozone depletion equivalent
   */
  ODE("ODE"),

  /**
   * ODS Grams
   */
  ODG("ODG"),

  /**
   * ODS Kilograms
   */
  ODK("ODK"),

  /**
   * ODS Milligrams
   */
  ODM("ODM"),

  /**
   * ohm
   */
  OHM("OHM"),

  /**
   * ounce per square yard
   */
  ON("ON"),

  /**
   * ounce (avoirdupois)
   */
  ONZ("ONZ"),

  /**
   * oscillations per minute
   */
  OPM("OPM"),

  /**
   * overtime hour
   */
  OT("OT"),

  /**
   * fluid ounce (US)
   */
  OZA("OZA"),

  /**
   * fluid ounce (UK)
   */
  OZI("OZI"),

  /**
   * percent
   */
  P1("P1"),

  /**
   * coulomb per metre
   */
  P10("P10"),

  /**
   * kiloweber
   */
  P11("P11"),

  /**
   * gamma
   */
  P12("P12"),

  /**
   * kilotesla
   */
  P13("P13"),

  /**
   * joule per second
   */
  P14("P14"),

  /**
   * joule per minute
   */
  P15("P15"),

  /**
   * joule per hour
   */
  P16("P16"),

  /**
   * joule per day
   */
  P17("P17"),

  /**
   * kilojoule per second
   */
  P18("P18"),

  /**
   * kilojoule per minute
   */
  P19("P19"),

  /**
   * pound per foot
   */
  P2("P2"),

  /**
   * kilojoule per hour
   */
  P20("P20"),

  /**
   * kilojoule per day
   */
  P21("P21"),

  /**
   * nanoohm
   */
  P22("P22"),

  /**
   * ohm circular-mil per foot
   */
  P23("P23"),

  /**
   * kilohenry
   */
  P24("P24"),

  /**
   * lumen per square foot
   */
  P25("P25"),

  /**
   * phot
   */
  P26("P26"),

  /**
   * footcandle
   */
  P27("P27"),

  /**
   * candela per square inch
   */
  P28("P28"),

  /**
   * footlambert
   */
  P29("P29"),

  /**
   * lambert
   */
  P30("P30"),

  /**
   * stilb
   */
  P31("P31"),

  /**
   * candela per square foot
   */
  P32("P32"),

  /**
   * kilocandela
   */
  P33("P33"),

  /**
   * millicandela
   */
  P34("P34"),

  /**
   * Hefner-Kerze
   */
  P35("P35"),

  /**
   * international candle
   */
  P36("P36"),

  /**
   * British thermal unit (international table) per square foot
   */
  P37("P37"),

  /**
   * British thermal unit (thermochemical) per square foot
   */
  P38("P38"),

  /**
   * calorie (thermochemical) per square centimetre
   */
  P39("P39"),

  /**
   * langley
   */
  P40("P40"),

  /**
   * decade (logarithmic)
   */
  P41("P41"),

  /**
   * pascal squared second
   */
  P42("P42"),

  /**
   * bel per metre
   */
  P43("P43"),

  /**
   * pound mole
   */
  P44("P44"),

  /**
   * pound mole per second
   */
  P45("P45"),

  /**
   * pound mole per minute
   */
  P46("P46"),

  /**
   * kilomole per kilogram
   */
  P47("P47"),

  /**
   * pound mole per pound
   */
  P48("P48"),

  /**
   * newton square metre per ampere
   */
  P49("P49"),

  /**
   * five pack
   */
  P5("P5"),

  /**
   * weber metre
   */
  P50("P50"),

  /**
   * mol per kilogram pascal
   */
  P51("P51"),

  /**
   * mol per cubic metre pascal
   */
  P52("P52"),

  /**
   * unit pole
   */
  P53("P53"),

  /**
   * milligray per second
   */
  P54("P54"),

  /**
   * microgray per second
   */
  P55("P55"),

  /**
   * nanogray per second
   */
  P56("P56"),

  /**
   * gray per minute
   */
  P57("P57"),

  /**
   * milligray per minute
   */
  P58("P58"),

  /**
   * microgray per minute
   */
  P59("P59"),

  /**
   * nanogray per minute
   */
  P60("P60"),

  /**
   * gray per hour
   */
  P61("P61"),

  /**
   * milligray per hour
   */
  P62("P62"),

  /**
   * microgray per hour
   */
  P63("P63"),

  /**
   * nanogray per hour
   */
  P64("P64"),

  /**
   * sievert per second
   */
  P65("P65"),

  /**
   * millisievert per second
   */
  P66("P66"),

  /**
   * microsievert per second
   */
  P67("P67"),

  /**
   * nanosievert per second
   */
  P68("P68"),

  /**
   * rem per second
   */
  P69("P69"),

  /**
   * sievert per hour
   */
  P70("P70"),

  /**
   * millisievert per hour
   */
  P71("P71"),

  /**
   * microsievert per hour
   */
  P72("P72"),

  /**
   * nanosievert per hour
   */
  P73("P73"),

  /**
   * sievert per minute
   */
  P74("P74"),

  /**
   * millisievert per minute
   */
  P75("P75"),

  /**
   * microsievert per minute
   */
  P76("P76"),

  /**
   * nanosievert per minute
   */
  P77("P77"),

  /**
   * reciprocal square inch
   */
  P78("P78"),

  /**
   * pascal square metre per kilogram
   */
  P79("P79"),

  /**
   * millipascal per metre
   */
  P80("P80"),

  /**
   * kilopascal per metre
   */
  P81("P81"),

  /**
   * hectopascal per metre
   */
  P82("P82"),

  /**
   * standard atmosphere per metre
   */
  P83("P83"),

  /**
   * technical atmosphere per metre
   */
  P84("P84"),

  /**
   * torr per metre
   */
  P85("P85"),

  /**
   * psi per inch
   */
  P86("P86"),

  /**
   * cubic metre per second square metre
   */
  P87("P87"),

  /**
   * rhe
   */
  P88("P88"),

  /**
   * pound-force foot per inch
   */
  P89("P89"),

  /**
   * pound-force inch per inch
   */
  P90("P90"),

  /**
   * perm (0 ºC)
   */
  P91("P91"),

  /**
   * perm (23 ºC)
   */
  P92("P92"),

  /**
   * byte per second
   */
  P93("P93"),

  /**
   * kilobyte per second
   */
  P94("P94"),

  /**
   * megabyte per second
   */
  P95("P95"),

  /**
   * reciprocal volt
   */
  P96("P96"),

  /**
   * reciprocal radian
   */
  P97("P97"),

  /**
   * pascal to the power sum of stoichiometric numbers
   */
  P98("P98"),

  /**
   * mole per cubiv metre to the power sum of stoichiometric numbers
   */
  P99("P99"),

  /**
   * pascal
   */
  PAL("PAL"),

  /**
   * pad
   */
  PD("PD"),

  /**
   * proof litre
   */
  PFL("PFL"),

  /**
   * proof gallon
   */
  PGL("PGL"),

  /**
   * pitch
   */
  PI("PI"),

  /**
   * degree Plato
   */
  PLA("PLA"),

  /**
   * pound per inch of length
   */
  PO("PO"),

  /**
   * page per inch
   */
  PQ("PQ"),

  /**
   * pair
   */
  PR("PR"),

  /**
   * pound-force per square inch
   */
  PS("PS"),

  /**
   * dry pint (US)
   */
  PTD("PTD"),

  /**
   * pint (UK)
   */
  PTI("PTI"),

  /**
   * liquid pint (US)
   */
  PTL("PTL"),

  /**
   * portion
   */
  PTN("PTN"),

  /**
   * joule per tesla
   */
  Q10("Q10"),

  /**
   * erlang
   */
  Q11("Q11"),

  /**
   * octet
   */
  Q12("Q12"),

  /**
   * octet per second
   */
  Q13("Q13"),

  /**
   * shannon
   */
  Q14("Q14"),

  /**
   * hartley
   */
  Q15("Q15"),

  /**
   * natural unit of information
   */
  Q16("Q16"),

  /**
   * shannon per second
   */
  Q17("Q17"),

  /**
   * hartley per second
   */
  Q18("Q18"),

  /**
   * natural unit of information per second
   */
  Q19("Q19"),

  /**
   * second per kilogramm
   */
  Q20("Q20"),

  /**
   * watt square metre
   */
  Q21("Q21"),

  /**
   * second per radian cubic metre
   */
  Q22("Q22"),

  /**
   * weber to the power minus one
   */
  Q23("Q23"),

  /**
   * reciprocal inch
   */
  Q24("Q24"),

  /**
   * dioptre
   */
  Q25("Q25"),

  /**
   * one per one
   */
  Q26("Q26"),

  /**
   * newton metre per metre
   */
  Q27("Q27"),

  /**
   * kilogram per square metre pascal second
   */
  Q28("Q28"),

  /**
   * microgram per hectogram
   */
  Q29("Q29"),

  /**
   * meal
   */
  Q3("Q3"),

  /**
   * pH (potential of Hydrogen)
   */
  Q30("Q30"),

  /**
   * kilojoule per gram
   */
  Q31("Q31"),

  /**
   * femtolitre
   */
  Q32("Q32"),

  /**
   * picolitre
   */
  Q33("Q33"),

  /**
   * nanolitre
   */
  Q34("Q34"),

  /**
   * megawatts per minute
   */
  Q35("Q35"),

  /**
   * square metre per cubic metre
   */
  Q36("Q36"),

  /**
   * Standard cubic metre per day
   */
  Q37("Q37"),

  /**
   * Standard cubic metre per hour
   */
  Q38("Q38"),

  /**
   * Normalized cubic metre per day
   */
  Q39("Q39"),

  /**
   * Normalized cubic metre per hour
   */
  Q40("Q40"),

  /**
   * Joule per normalised cubic metre
   */
  Q41("Q41"),

  /**
   * Joule per standard cubic metre
   */
  Q42("Q42"),

  /**
   * page - facsimile
   */
  QA("QA"),

  /**
   * quarter (of a year)
   */
  QAN("QAN"),

  /**
   * page - hardcopy
   */
  QB("QB"),

  /**
   * quire
   */
  QR("QR"),

  /**
   * dry quart (US)
   */
  QTD("QTD"),

  /**
   * quart (UK)
   */
  QTI("QTI"),

  /**
   * liquid quart (US)
   */
  QTL("QTL"),

  /**
   * quarter (UK)
   */
  QTR("QTR"),

  /**
   * pica
   */
  R1("R1"),

  /**
   * thousand cubic metre
   */
  R9("R9"),

  /**
   * running or operating hour
   */
  RH("RH"),

  /**
   * ream
   */
  RM("RM"),

  /**
   * room
   */
  ROM("ROM"),

  /**
   * pound per ream
   */
  RP("RP"),

  /**
   * revolutions per minute
   */
  RPM("RPM"),

  /**
   * revolutions per second
   */
  RPS("RPS"),

  /**
   * revenue ton mile
   */
  RT("RT"),

  /**
   * square foot per second
   */
  S3("S3"),

  /**
   * square metre per second
   */
  S4("S4"),

  /**
   * half year (6 months)
   */
  SAN("SAN"),

  /**
   * score
   */
  SCO("SCO"),

  /**
   * scruple
   */
  SCR("SCR"),

  /**
   * second [unit of time]
   */
  SEC("SEC"),

  /**
   * set
   */
  SET("SET"),

  /**
   * segment
   */
  SG("SG"),

  /**
   * siemens
   */
  SIE("SIE"),

  /**
   * Standard cubic metre
   */
  SM3("SM3"),

  /**
   * mile (statute mile)
   */
  SMI("SMI"),

  /**
   * square
   */
  SQ("SQ"),

  /**
   * square, roofing
   */
  SQR("SQR"),

  /**
   * strip
   */
  SR("SR"),

  /**
   * stick
   */
  STC("STC"),

  /**
   * stone (UK)
   */
  STI("STI"),

  /**
   * stick, cigarette
   */
  STK("STK"),

  /**
   * standard litre
   */
  STL("STL"),

  /**
   * ton (US) or short ton (UK/US)
   */
  STN("STN"),

  /**
   * straw
   */
  STW("STW"),

  /**
   * skein
   */
  SW("SW"),

  /**
   * shipment
   */
  SX("SX"),

  /**
   * syringe
   */
  SYR("SYR"),

  /**
   * telecommunication line in service
   */
  T0("T0"),

  /**
   * thousand piece
   */
  T3("T3"),

  /**
   * kiloampere hour (thousand ampere hour)
   */
  TAH("TAH"),

  /**
   * total acid number
   */
  TAN("TAN"),

  /**
   * thousand square inch
   */
  TI("TI"),

  /**
   * metric ton, including container
   */
  TIC("TIC"),

  /**
   * metric ton, including inner packaging
   */
  TIP("TIP"),

  /**
   * tonne kilometre
   */
  TKM("TKM"),

  /**
   * kilogram of imported meat, less offal
   */
  TMS("TMS"),

  /**
   * tonne (metric ton)
   */
  TNE("TNE"),

  /**
   * ten pack
   */
  TP("TP"),

  /**
   * teeth per inch
   */
  TPI("TPI"),

  /**
   * ten pair
   */
  TPR("TPR"),

  /**
   * thousand cubic metre per day
   */
  TQD("TQD"),

  /**
   * trillion (EUR)
   */
  TRL("TRL"),

  /**
   * ten set
   */
  TST("TST"),

  /**
   * ten thousand sticks
   */
  TTS("TTS"),

  /**
   * treatment
   */
  U1("U1"),

  /**
   * tablet
   */
  U2("U2"),

  /**
   * telecommunication line in service average
   */
  UB("UB"),

  /**
   * telecommunication port
   */
  UC("UC"),

  /**
   * volt - ampere per kilogram
   */
  VA("VA"),

  /**
   * volt
   */
  VLT("VLT"),

  /**
   * percent volume
   */
  VP("VP"),

  /**
   * wet kilo
   */
  W2("W2"),

  /**
   * watt per kilogram
   */
  WA("WA"),

  /**
   * wet pound
   */
  WB("WB"),

  /**
   * cord
   */
  WCD("WCD"),

  /**
   * wet ton
   */
  WE("WE"),

  /**
   * weber
   */
  WEB("WEB"),

  /**
   * week
   */
  WEE("WEE"),

  /**
   * wine gallon
   */
  WG("WG"),

  /**
   * watt hour
   */
  WHR("WHR"),

  /**
   * working month
   */
  WM("WM"),

  /**
   * standard
   */
  WSD("WSD"),

  /**
   * watt
   */
  WTT("WTT"),

  /**
   * Gunter's chain
   */
  X1("X1"),

  /**
   * square yard
   */
  YDK("YDK"),

  /**
   * cubic yard
   */
  YDQ("YDQ"),

  /**
   * yard
   */
  YRD("YRD"),

  /**
   * hanging container
   */
  Z11("Z11"),

  /**
   * nanomole
   */
  Z9("Z9"),

  /**
   * page
   */
  ZP("ZP"),

  /**
   * mutually defined
   */
  ZZ("ZZ"),

  /**
   * Drum, steel
   */
  X1A("X1A"),

  /**
   * Drum, aluminium
   */
  X1B("X1B"),

  /**
   * Drum, plywood
   */
  X1D("X1D"),

  /**
   * Container, flexible
   */
  X1F("X1F"),

  /**
   * Drum, fibre
   */
  X1G("X1G"),

  /**
   * Drum, wooden
   */
  X1W("X1W"),

  /**
   * Barrel, wooden
   */
  X2C("X2C"),

  /**
   * Jerrican, steel
   */
  X3A("X3A"),

  /**
   * Jerrican, plastic
   */
  X3H("X3H"),

  /**
   * Bag, super bulk
   */
  X43("X43"),

  /**
   * Bag, polybag
   */
  X44("X44"),

  /**
   * Box, steel
   */
  X4A("X4A"),

  /**
   * Box, aluminium
   */
  X4B("X4B"),

  /**
   * Box, natural wood
   */
  X4C("X4C"),

  /**
   * Box, plywood
   */
  X4D("X4D"),

  /**
   * Box, reconstituted wood
   */
  X4F("X4F"),

  /**
   * Box, fibreboard
   */
  X4G("X4G"),

  /**
   * Box, plastic
   */
  X4H("X4H"),

  /**
   * Bag, woven plastic
   */
  X5H("X5H"),

  /**
   * Bag, textile
   */
  X5L("X5L"),

  /**
   * Bag, paper
   */
  X5M("X5M"),

  /**
   * Composite packaging, plastic receptacle
   */
  X6H("X6H"),

  /**
   * Composite packaging, glass receptacle
   */
  X6P("X6P"),

  /**
   * Case, car
   */
  X7A("X7A"),

  /**
   * Case, wooden
   */
  X7B("X7B"),

  /**
   * Pallet, wooden
   */
  X8A("X8A"),

  /**
   * Crate, wooden
   */
  X8B("X8B"),

  /**
   * Bundle, wooden
   */
  X8C("X8C"),

  /**
   * Intermediate bulk container, rigid plastic
   */
  XAA("XAA"),

  /**
   * Receptacle, fibre
   */
  XAB("XAB"),

  /**
   * Receptacle, paper
   */
  XAC("XAC"),

  /**
   * Receptacle, wooden
   */
  XAD("XAD"),

  /**
   * Aerosol
   */
  XAE("XAE"),

  /**
   * Pallet, modular, collars 80cms * 60cms
   */
  XAF("XAF"),

  /**
   * Pallet, shrinkwrapped
   */
  XAG("XAG"),

  /**
   * Pallet, 100cms * 110cms
   */
  XAH("XAH"),

  /**
   * Clamshell
   */
  XAI("XAI"),

  /**
   * Cone
   */
  XAJ("XAJ"),

  /**
   * Ball
   */
  XAL("XAL"),

  /**
   * Ampoule, non-protected
   */
  XAM("XAM"),

  /**
   * Ampoule, protected
   */
  XAP("XAP"),

  /**
   * Atomizer
   */
  XAT("XAT"),

  /**
   * Capsule
   */
  XAV("XAV"),

  /**
   * Belt
   */
  XB4("XB4"),

  /**
   * Barrel
   */
  XBA("XBA"),

  /**
   * Bobbin
   */
  XBB("XBB"),

  /**
   * Bottlecrate / bottlerack
   */
  XBC("XBC"),

  /**
   * Board
   */
  XBD("XBD"),

  /**
   * Bundle
   */
  XBE("XBE"),

  /**
   * Balloon, non-protected
   */
  XBF("XBF"),

  /**
   * Bag
   */
  XBG("XBG"),

  /**
   * Bunch
   */
  XBH("XBH"),

  /**
   * Bin
   */
  XBI("XBI"),

  /**
   * Bucket
   */
  XBJ("XBJ"),

  /**
   * Basket
   */
  XBK("XBK"),

  /**
   * Bale, compressed
   */
  XBL("XBL"),

  /**
   * Basin
   */
  XBM("XBM"),

  /**
   * Bale, non-compressed
   */
  XBN("XBN"),

  /**
   * Bottle, non-protected, cylindrical
   */
  XBO("XBO"),

  /**
   * Balloon, protected
   */
  XBP("XBP"),

  /**
   * Bottle, protected cylindrical
   */
  XBQ("XBQ"),

  /**
   * Bar
   */
  XBR("XBR"),

  /**
   * Bottle, non-protected, bulbous
   */
  XBS("XBS"),

  /**
   * Bolt
   */
  XBT("XBT"),

  /**
   * Butt
   */
  XBU("XBU"),

  /**
   * Bottle, protected bulbous
   */
  XBV("XBV"),

  /**
   * Box, for liquids
   */
  XBW("XBW"),

  /**
   * Box
   */
  XBX("XBX"),

  /**
   * Board, in bundle/bunch/truss
   */
  XBY("XBY"),

  /**
   * Bars, in bundle/bunch/truss
   */
  XBZ("XBZ"),

  /**
   * Can, rectangular
   */
  XCA("XCA"),

  /**
   * Crate, beer
   */
  XCB("XCB"),

  /**
   * Churn
   */
  XCC("XCC"),

  /**
   * Can, with handle and spout
   */
  XCD("XCD"),

  /**
   * Creel
   */
  XCE("XCE"),

  /**
   * Coffer
   */
  XCF("XCF"),

  /**
   * Cage
   */
  XCG("XCG"),

  /**
   * Chest
   */
  XCH("XCH"),

  /**
   * Canister
   */
  XCI("XCI"),

  /**
   * Coffin
   */
  XCJ("XCJ"),

  /**
   * Cask
   */
  XCK("XCK"),

  /**
   * Coil
   */
  XCL("XCL"),

  /**
   * Card
   */
  XCM("XCM"),

  /**
   * Container, not otherwise specified as transport equipment
   */
  XCN("XCN"),

  /**
   * Carboy, non-protected
   */
  XCO("XCO"),

  /**
   * Carboy, protected
   */
  XCP("XCP"),

  /**
   * Cartridge
   */
  XCQ("XCQ"),

  /**
   * Crate
   */
  XCR("XCR"),

  /**
   * Case
   */
  XCS("XCS"),

  /**
   * Carton
   */
  XCT("XCT"),

  /**
   * Cup
   */
  XCU("XCU"),

  /**
   * Cover
   */
  XCV("XCV"),

  /**
   * Cage, roll
   */
  XCW("XCW"),

  /**
   * Can, cylindrical
   */
  XCX("XCX"),

  /**
   * Cylinder
   */
  XCY("XCY"),

  /**
   * Canvas
   */
  XCZ("XCZ"),

  /**
   * Crate, multiple layer, plastic
   */
  XDA("XDA"),

  /**
   * Crate, multiple layer, wooden
   */
  XDB("XDB"),

  /**
   * Crate, multiple layer, cardboard
   */
  XDC("XDC"),

  /**
   * Cage, Commonwealth Handling Equipment Pool (CHEP)
   */
  XDG("XDG"),

  /**
   * Box, Commonwealth Handling Equipment Pool (CHEP), Eurobox
   */
  XDH("XDH"),

  /**
   * Drum, iron
   */
  XDI("XDI"),

  /**
   * Demijohn, non-protected
   */
  XDJ("XDJ"),

  /**
   * Crate, bulk, cardboard
   */
  XDK("XDK"),

  /**
   * Crate, bulk, plastic
   */
  XDL("XDL"),

  /**
   * Crate, bulk, wooden
   */
  XDM("XDM"),

  /**
   * Dispenser
   */
  XDN("XDN"),

  /**
   * Demijohn, protected
   */
  XDP("XDP"),

  /**
   * Drum
   */
  XDR("XDR"),

  /**
   * Tray, one layer no cover, plastic
   */
  XDS("XDS"),

  /**
   * Tray, one layer no cover, wooden
   */
  XDT("XDT"),

  /**
   * Tray, one layer no cover, polystyrene
   */
  XDU("XDU"),

  /**
   * Tray, one layer no cover, cardboard
   */
  XDV("XDV"),

  /**
   * Tray, two layers no cover, plastic tray
   */
  XDW("XDW"),

  /**
   * Tray, two layers no cover, wooden
   */
  XDX("XDX"),

  /**
   * Tray, two layers no cover, cardboard
   */
  XDY("XDY"),

  /**
   * Bag, plastic
   */
  XEC("XEC"),

  /**
   * Case, with pallet base
   */
  XED("XED"),

  /**
   * Case, with pallet base, wooden
   */
  XEE("XEE"),

  /**
   * Case, with pallet base, cardboard
   */
  XEF("XEF"),

  /**
   * Case, with pallet base, plastic
   */
  XEG("XEG"),

  /**
   * Case, with pallet base, metal
   */
  XEH("XEH"),

  /**
   * Case, isothermic
   */
  XEI("XEI"),

  /**
   * Envelope
   */
  XEN("XEN"),

  /**
   * Flexibag
   */
  XFB("XFB"),

  /**
   * Crate, fruit
   */
  XFC("XFC"),

  /**
   * Crate, framed
   */
  XFD("XFD"),

  /**
   * Flexitank
   */
  XFE("XFE"),

  /**
   * Firkin
   */
  XFI("XFI"),

  /**
   * Flask
   */
  XFL("XFL"),

  /**
   * Footlocker
   */
  XFO("XFO"),

  /**
   * Filmpack
   */
  XFP("XFP"),

  /**
   * Frame
   */
  XFR("XFR"),

  /**
   * Foodtainer
   */
  XFT("XFT"),

  /**
   * Cart, flatbed
   */
  XFW("XFW"),

  /**
   * Bag, flexible container
   */
  XFX("XFX"),

  /**
   * Bottle, gas
   */
  XGB("XGB"),

  /**
   * Girder
   */
  XGI("XGI"),

  /**
   * Container, gallon
   */
  XGL("XGL"),

  /**
   * Receptacle, glass
   */
  XGR("XGR"),

  /**
   * Tray, containing horizontally stacked flat items
   */
  XGU("XGU"),

  /**
   * Bag, gunny
   */
  XGY("XGY"),

  /**
   * Girders, in bundle/bunch/truss
   */
  XGZ("XGZ"),

  /**
   * Basket, with handle, plastic
   */
  XHA("XHA"),

  /**
   * Basket, with handle, wooden
   */
  XHB("XHB"),

  /**
   * Basket, with handle, cardboard
   */
  XHC("XHC"),

  /**
   * Hogshead
   */
  XHG("XHG"),

  /**
   * Hanger
   */
  XHN("XHN"),

  /**
   * Hamper
   */
  XHR("XHR"),

  /**
   * Package, display, wooden
   */
  XIA("XIA"),

  /**
   * Package, display, cardboard
   */
  XIB("XIB"),

  /**
   * Package, display, plastic
   */
  XIC("XIC"),

  /**
   * Package, display, metal
   */
  XID("XID"),

  /**
   * Package, show
   */
  XIE("XIE"),

  /**
   * Package, flow
   */
  XIF("XIF"),

  /**
   * Package, paper wrapped
   */
  XIG("XIG"),

  /**
   * Drum, plastic
   */
  XIH("XIH"),

  /**
   * Package, cardboard, with bottle grip-holes
   */
  XIK("XIK"),

  /**
   * Tray, rigid, lidded stackable (CEN TS 14482:2002)
   */
  XIL("XIL"),

  /**
   * Ingot
   */
  XIN("XIN"),

  /**
   * Ingots, in bundle/bunch/truss
   */
  XIZ("XIZ"),

  /**
   * Bag, jumbo
   */
  XJB("XJB"),

  /**
   * Jerrican, rectangular
   */
  XJC("XJC"),

  /**
   * Jug
   */
  XJG("XJG"),

  /**
   * Jar
   */
  XJR("XJR"),

  /**
   * Jutebag
   */
  XJT("XJT"),

  /**
   * Jerrican, cylindrical
   */
  XJY("XJY"),

  /**
   * Keg
   */
  XKG("XKG"),

  /**
   * Kit
   */
  XKI("XKI"),

  /**
   * Luggage
   */
  XLE("XLE"),

  /**
   * Log
   */
  XLG("XLG"),

  /**
   * Lot
   */
  XLT("XLT"),

  /**
   * Lug
   */
  XLU("XLU"),

  /**
   * Liftvan
   */
  XLV("XLV"),

  /**
   * Logs, in bundle/bunch/truss
   */
  XLZ("XLZ"),

  /**
   * Crate, metal
   */
  XMA("XMA"),

  /**
   * Bag, multiply
   */
  XMB("XMB"),

  /**
   * Crate, milk
   */
  XMC("XMC"),

  /**
   * Container, metal
   */
  XME("XME"),

  /**
   * Receptacle, metal
   */
  XMR("XMR"),

  /**
   * Sack, multi-wall
   */
  XMS("XMS"),

  /**
   * Mat
   */
  XMT("XMT"),

  /**
   * Receptacle, plastic wrapped
   */
  XMW("XMW"),

  /**
   * Matchbox
   */
  XMX("XMX"),

  /**
   * Not available
   */
  XNA("XNA"),

  /**
   * Unpacked or unpackaged
   */
  XNE("XNE"),

  /**
   * Unpacked or unpackaged, single unit
   */
  XNF("XNF"),

  /**
   * Unpacked or unpackaged, multiple units
   */
  XNG("XNG"),

  /**
   * Nest
   */
  XNS("XNS"),

  /**
   * Net
   */
  XNT("XNT"),

  /**
   * Net, tube, plastic
   */
  XNU("XNU"),

  /**
   * Net, tube, textile
   */
  XNV("XNV"),

  /**
   * Two sided cage on wheels with fixing strap
   */
  XO1("XO1"),

  /**
   * Trolley
   */
  XO2("XO2"),

  /**
   * Oneway pallet ISO 0 - 1/2 EURO Pallet
   */
  XO3("XO3"),

  /**
   * Oneway pallet ISO 1 - 1/1 EURO Pallet
   */
  XO4("XO4"),

  /**
   * Oneway pallet ISO 2 - 2/1 EURO Pallet
   */
  XO5("XO5"),

  /**
   * Pallet with exceptional dimensions
   */
  XO6("XO6"),

  /**
   * Wooden pallet 40 cm x 80 cm
   */
  XO7("XO7"),

  /**
   * Plastic pallet SRS 60 cm x 80 cm
   */
  XO8("XO8"),

  /**
   * Plastic pallet SRS 80 cm x 120 cm
   */
  XO9("XO9"),

  /**
   * Pallet, CHEP 40 cm x 60 cm
   */
  XOA("XOA"),

  /**
   * Pallet, CHEP 80 cm x 120 cm
   */
  XOB("XOB"),

  /**
   * Pallet, CHEP 100 cm x 120 cm
   */
  XOC("XOC"),

  /**
   * Pallet, AS 4068-1993
   */
  XOD("XOD"),

  /**
   * Pallet, ISO T11
   */
  XOE("XOE"),

  /**
   * Platform, unspecified weight or dimension
   */
  XOF("XOF"),

  /**
   * Pallet ISO 0 - 1/2 EURO Pallet
   */
  XOG("XOG"),

  /**
   * Pallet ISO 1 - 1/1 EURO Pallet
   */
  XOH("XOH"),

  /**
   * Pallet ISO 2 – 2/1 EURO Pallet
   */
  XOI("XOI"),

  /**
   * 1/4 EURO Pallet
   */
  XOJ("XOJ"),

  /**
   * Block
   */
  XOK("XOK"),

  /**
   * 1/8 EURO Pallet
   */
  XOL("XOL"),

  /**
   * Synthetic pallet ISO 1
   */
  XOM("XOM"),

  /**
   * Synthetic pallet ISO 2
   */
  XON("XON"),

  /**
   * Wholesaler pallet
   */
  XOP("XOP"),

  /**
   * Pallet 80 X 100 cm
   */
  XOQ("XOQ"),

  /**
   * Pallet 60 X 100 cm
   */
  XOR("XOR"),

  /**
   * Oneway pallet
   */
  XOS("XOS"),

  /**
   * Octabin
   */
  XOT("XOT"),

  /**
   * Container, outer
   */
  XOU("XOU"),

  /**
   * Returnable pallet
   */
  XOV("XOV"),

  /**
   * Large bag, pallet sized
   */
  XOW("XOW"),

  /**
   * A wheeled pallet with raised rim (81 x 67 x 135)
   */
  XOX("XOX"),

  /**
   * A Wheeled pallet with raised rim (81 x 72 x 135)
   */
  XOY("XOY"),

  /**
   * Wheeled pallet with raised rim ( 81 x 60 x 16)
   */
  XOZ("XOZ"),

  /**
   * CHEP pallet 60 cm x 80 cm
   */
  XP1("XP1"),

  /**
   * Pan
   */
  XP2("XP2"),

  /**
   * LPR pallet 60 cm x 80 cm
   */
  XP3("XP3"),

  /**
   * LPR pallet 80 cm x 120 cm
   */
  XP4("XP4"),

  /**
   * Packet
   */
  XPA("XPA"),

  /**
   * Pallet, box Combined open-ended box and pallet
   */
  XPB("XPB"),

  /**
   * Parcel
   */
  XPC("XPC"),

  /**
   * Pallet, modular, collars 80cms * 100cms
   */
  XPD("XPD"),

  /**
   * Pallet, modular, collars 80cms * 120cms
   */
  XPE("XPE"),

  /**
   * Pen
   */
  XPF("XPF"),

  /**
   * Plate
   */
  XPG("XPG"),

  /**
   * Pitcher
   */
  XPH("XPH"),

  /**
   * Pipe
   */
  XPI("XPI"),

  /**
   * Punnet
   */
  XPJ("XPJ"),

  /**
   * Package
   */
  XPK("XPK"),

  /**
   * Pail
   */
  XPL("XPL"),

  /**
   * Plank
   */
  XPN("XPN"),

  /**
   * Pouch
   */
  XPO("XPO"),

  /**
   * Piece
   */
  XPP("XPP"),

  /**
   * Receptacle, plastic
   */
  XPR("XPR"),

  /**
   * Pot
   */
  XPT("XPT"),

  /**
   * Tray
   */
  XPU("XPU"),

  /**
   * Pipes, in bundle/bunch/truss
   */
  XPV("XPV"),

  /**
   * Pallet
   */
  XPX("XPX"),

  /**
   * Plates, in bundle/bunch/truss
   */
  XPY("XPY"),

  /**
   * Planks, in bundle/bunch/truss
   */
  XPZ("XPZ"),

  /**
   * Drum, steel, non-removable head
   */
  XQA("XQA"),

  /**
   * Drum, steel, removable head
   */
  XQB("XQB"),

  /**
   * Drum, aluminium, non-removable head
   */
  XQC("XQC"),

  /**
   * Drum, aluminium, removable head
   */
  XQD("XQD"),

  /**
   * Drum, plastic, non-removable head
   */
  XQF("XQF"),

  /**
   * Drum, plastic, removable head
   */
  XQG("XQG"),

  /**
   * Barrel, wooden, bung type
   */
  XQH("XQH"),

  /**
   * Barrel, wooden, removable head
   */
  XQJ("XQJ"),

  /**
   * Jerrican, steel, non-removable head
   */
  XQK("XQK"),

  /**
   * Jerrican, steel, removable head
   */
  XQL("XQL"),

  /**
   * Jerrican, plastic, non-removable head
   */
  XQM("XQM"),

  /**
   * Jerrican, plastic, removable head
   */
  XQN("XQN"),

  /**
   * Box, wooden, natural wood, ordinary
   */
  XQP("XQP"),

  /**
   * Box, wooden, natural wood, with sift proof walls
   */
  XQQ("XQQ"),

  /**
   * Box, plastic, expanded
   */
  XQR("XQR"),

  /**
   * Box, plastic, solid
   */
  XQS("XQS"),

  /**
   * Rod
   */
  XRD("XRD"),

  /**
   * Ring
   */
  XRG("XRG"),

  /**
   * Rack, clothing hanger
   */
  XRJ("XRJ"),

  /**
   * Rack
   */
  XRK("XRK"),

  /**
   * Reel
   */
  XRL("XRL"),

  /**
   * Roll
   */
  XRO("XRO"),

  /**
   * Rednet
   */
  XRT("XRT"),

  /**
   * Rods, in bundle/bunch/truss
   */
  XRZ("XRZ"),

  /**
   * Sack
   */
  XSA("XSA"),

  /**
   * Slab
   */
  XSB("XSB"),

  /**
   * Crate, shallow
   */
  XSC("XSC"),

  /**
   * Spindle
   */
  XSD("XSD"),

  /**
   * Sea-chest
   */
  XSE("XSE"),

  /**
   * Sachet
   */
  XSH("XSH"),

  /**
   * Skid
   */
  XSI("XSI"),

  /**
   * Case, skeleton
   */
  XSK("XSK"),

  /**
   * Slipsheet
   */
  XSL("XSL"),

  /**
   * Sheetmetal
   */
  XSM("XSM"),

  /**
   * Spool
   */
  XSO("XSO"),

  /**
   * Sheet, plastic wrapping
   */
  XSP("XSP"),

  /**
   * Case, steel
   */
  XSS("XSS"),

  /**
   * Sheet
   */
  XST("XST"),

  /**
   * Suitcase
   */
  XSU("XSU"),

  /**
   * Envelope, steel
   */
  XSV("XSV"),

  /**
   * Shrinkwrapped
   */
  XSW("XSW"),

  /**
   * Set
   */
  XSX("XSX"),

  /**
   * Sleeve
   */
  XSY("XSY"),

  /**
   * Sheets, in bundle/bunch/truss
   */
  XSZ("XSZ"),

  /**
   * Tablet
   */
  XT1("XT1"),

  /**
   * Tub
   */
  XTB("XTB"),

  /**
   * Tea-chest
   */
  XTC("XTC"),

  /**
   * Tube, collapsible
   */
  XTD("XTD"),

  /**
   * Tyre
   */
  XTE("XTE"),

  /**
   * Tank container, generic
   */
  XTG("XTG"),

  /**
   * Tierce
   */
  XTI("XTI"),

  /**
   * Tank, rectangular
   */
  XTK("XTK"),

  /**
   * Tub, with lid
   */
  XTL("XTL"),

  /**
   * Tin
   */
  XTN("XTN"),

  /**
   * Tun
   */
  XTO("XTO"),

  /**
   * Trunk
   */
  XTR("XTR"),

  /**
   * Truss
   */
  XTS("XTS"),

  /**
   * Bag, tote
   */
  XTT("XTT"),

  /**
   * Tube
   */
  XTU("XTU"),

  /**
   * Tube, with nozzle
   */
  XTV("XTV"),

  /**
   * Pallet, triwall
   */
  XTW("XTW"),

  /**
   * Tank, cylindrical
   */
  XTY("XTY"),

  /**
   * Tubes, in bundle/bunch/truss
   */
  XTZ("XTZ"),

  /**
   * Uncaged
   */
  XUC("XUC"),

  /**
   * Unit
   */
  XUN("XUN"),

  /**
   * Vat
   */
  XVA("XVA"),

  /**
   * Bulk, gas (at 1031 mbar and 15°C)
   */
  XVG("XVG"),

  /**
   * Vial
   */
  XVI("XVI"),

  /**
   * Vanpack
   */
  XVK("XVK"),

  /**
   * Bulk, liquid
   */
  XVL("XVL"),

  /**
   * Vehicle
   */
  XVN("XVN"),

  /**
   * Bulk, solid, large particles (“nodules”)
   */
  XVO("XVO"),

  /**
   * Vacuum-packed
   */
  XVP("XVP"),

  /**
   * Bulk, liquefied gas (at abnormal temperature/pressure)
   */
  XVQ("XVQ"),

  /**
   * Bulk, solid, granular particles (“grains”)
   */
  XVR("XVR"),

  /**
   * Bulk, scrap metal
   */
  XVS("XVS"),

  /**
   * Bulk, solid, fine particles (“powders”)
   */
  XVY("XVY"),

  /**
   * Intermediate bulk container
   */
  XWA("XWA"),

  /**
   * Wickerbottle
   */
  XWB("XWB"),

  /**
   * Intermediate bulk container, steel
   */
  XWC("XWC"),

  /**
   * Intermediate bulk container, aluminium
   */
  XWD("XWD"),

  /**
   * Intermediate bulk container, metal
   */
  XWF("XWF"),

  /**
   * Intermediate bulk container, steel, pressurised &gt; 10 kpa
   */
  XWG("XWG"),

  /**
   * Intermediate bulk container, aluminium, pressurised &gt; 10 kpa
   */
  XWH("XWH"),

  /**
   * Intermediate bulk container, metal, pressure 10 kpa
   */
  XWJ("XWJ"),

  /**
   * Intermediate bulk container, steel, liquid
   */
  XWK("XWK"),

  /**
   * Intermediate bulk container, aluminium, liquid
   */
  XWL("XWL"),

  /**
   * Intermediate bulk container, metal, liquid
   */
  XWM("XWM"),

  /**
   * Intermediate bulk container, woven plastic, without coat/liner
   */
  XWN("XWN"),

  /**
   * Intermediate bulk container, woven plastic, coated
   */
  XWP("XWP"),

  /**
   * Intermediate bulk container, woven plastic, with liner
   */
  XWQ("XWQ"),

  /**
   * Intermediate bulk container, woven plastic, coated and liner
   */
  XWR("XWR"),

  /**
   * Intermediate bulk container, plastic film
   */
  XWS("XWS"),

  /**
   * Intermediate bulk container, textile with out coat/liner
   */
  XWT("XWT"),

  /**
   * Intermediate bulk container, natural wood, with inner liner
   */
  XWU("XWU"),

  /**
   * Intermediate bulk container, textile, coated
   */
  XWV("XWV"),

  /**
   * Intermediate bulk container, textile, with liner
   */
  XWW("XWW"),

  /**
   * Intermediate bulk container, textile, coated and liner
   */
  XWX("XWX"),

  /**
   * Intermediate bulk container, plywood, with inner liner
   */
  XWY("XWY"),

  /**
   * Intermediate bulk container, reconstituted wood, with inner liner
   */
  XWZ("XWZ"),

  /**
   * Bag, woven plastic, without inner coat/liner
   */
  XXA("XXA"),

  /**
   * Bag, woven plastic, sift proof
   */
  XXB("XXB"),

  /**
   * Bag, woven plastic, water resistant
   */
  XXC("XXC"),

  /**
   * Bag, plastics film
   */
  XXD("XXD"),

  /**
   * Bag, textile, without inner coat/liner
   */
  XXF("XXF"),

  /**
   * Bag, textile, sift proof
   */
  XXG("XXG"),

  /**
   * Bag, textile, water resistant
   */
  XXH("XXH"),

  /**
   * Bag, paper, multi-wall
   */
  XXJ("XXJ"),

  /**
   * Bag, paper, multi-wall, water resistant
   */
  XXK("XXK"),

  /**
   * Composite packaging, plastic receptacle in steel drum
   */
  XYA("XYA"),

  /**
   * Composite packaging, plastic receptacle in steel crate box
   */
  XYB("XYB"),

  /**
   * Composite packaging, plastic receptacle in aluminium drum
   */
  XYC("XYC"),

  /**
   * Composite packaging, plastic receptacle in aluminium crate
   */
  XYD("XYD"),

  /**
   * Composite packaging, plastic receptacle in wooden box
   */
  XYF("XYF"),

  /**
   * Composite packaging, plastic receptacle in plywood drum
   */
  XYG("XYG"),

  /**
   * Composite packaging, plastic receptacle in plywood box
   */
  XYH("XYH"),

  /**
   * Composite packaging, plastic receptacle in fibre drum
   */
  XYJ("XYJ"),

  /**
   * Composite packaging, plastic receptacle in fibreboard box
   */
  XYK("XYK"),

  /**
   * Composite packaging, plastic receptacle in plastic drum
   */
  XYL("XYL"),

  /**
   * Composite packaging, plastic receptacle in solid plastic box
   */
  XYM("XYM"),

  /**
   * Composite packaging, glass receptacle in steel drum
   */
  XYN("XYN"),

  /**
   * Composite packaging, glass receptacle in steel crate box
   */
  XYP("XYP"),

  /**
   * Composite packaging, glass receptacle in aluminium drum
   */
  XYQ("XYQ"),

  /**
   * Composite packaging, glass receptacle in aluminium crate
   */
  XYR("XYR"),

  /**
   * Composite packaging, glass receptacle in wooden box
   */
  XYS("XYS"),

  /**
   * Composite packaging, glass receptacle in plywood drum
   */
  XYT("XYT"),

  /**
   * Composite packaging, glass receptacle in wickerwork hamper
   */
  XYV("XYV"),

  /**
   * Composite packaging, glass receptacle in fibre drum
   */
  XYW("XYW"),

  /**
   * Composite packaging, glass receptacle in fibreboard box
   */
  XYX("XYX"),

  /**
   * Composite packaging, glass receptacle in expandable plastic pack
   */
  XYY("XYY"),

  /**
   * Composite packaging, glass receptacle in solid plastic pack
   */
  XYZ("XYZ"),

  /**
   * Intermediate bulk container, paper, multi-wall
   */
  XZA("XZA"),

  /**
   * Bag, large
   */
  XZB("XZB"),

  /**
   * Intermediate bulk container, paper, multi-wall, water resistant
   */
  XZC("XZC"),

  /**
   * Intermediate bulk container, rigid plastic, with structural equipment, solids
   */
  XZD("XZD"),

  /**
   * Intermediate bulk container, rigid plastic, freestanding, solids
   */
  XZF("XZF"),

  /**
   * Intermediate bulk container, rigid plastic, with structural equipment, pressurised
   */
  XZG("XZG"),

  /**
   * Intermediate bulk container, rigid plastic, freestanding, pressurised
   */
  XZH("XZH"),

  /**
   * Intermediate bulk container, rigid plastic, with structural equipment, liquids
   */
  XZJ("XZJ"),

  /**
   * Intermediate bulk container, rigid plastic, freestanding, liquids
   */
  XZK("XZK"),

  /**
   * Intermediate bulk container, composite, rigid plastic, solids
   */
  XZL("XZL"),

  /**
   * Intermediate bulk container, composite, flexible plastic, solids
   */
  XZM("XZM"),

  /**
   * Intermediate bulk container, composite, rigid plastic, pressurised
   */
  XZN("XZN"),

  /**
   * Intermediate bulk container, composite, flexible plastic, pressurised
   */
  XZP("XZP"),

  /**
   * Intermediate bulk container, composite, rigid plastic, liquids
   */
  XZQ("XZQ"),

  /**
   * Intermediate bulk container, composite, flexible plastic, liquids
   */
  XZR("XZR"),

  /**
   * Intermediate bulk container, composite
   */
  XZS("XZS"),

  /**
   * Intermediate bulk container, fibreboard
   */
  XZT("XZT"),

  /**
   * Intermediate bulk container, flexible
   */
  XZU("XZU"),

  /**
   * Intermediate bulk container, metal, other than steel
   */
  XZV("XZV"),

  /**
   * Intermediate bulk container, natural wood
   */
  XZW("XZW"),

  /**
   * Intermediate bulk container, plywood
   */
  XZX("XZX"),

  /**
   * Intermediate bulk container, reconstituted wood
   */
  XZY("XZY"),

  /**
   * Mutually defined
   */
  XZZ("XZZ");

  private static final Map<String, UnitCode> BY_CODE = Arrays.stream(values())
                                                             .collect(Collectors.toUnmodifiableMap(UnitCode::getCode,
                                                                                                   Function.identity()));

  private final String code;

  UnitCode(String code) {
    this.code = code;
  }

  /**
   * Get UnitCode by code value
   *
   * @param code the code to search for, e.g. "HUR"
   * @return the matching UnitCode or null if not found
   */
  public static UnitCode of(String code) {
    return code == null ?
           null :
           BY_CODE.get(code);
  }

  /**
   * Returns the code as written in the documents.
   *
   * @return the unit code, e.g. {@code HUR}
   */
  public String getCode() {
    return code;
  }

  /**
   * Returns the English description, e.g. "hour".
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
