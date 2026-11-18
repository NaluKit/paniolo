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
package io.github.nalukit.paniolo.model.extended;

import io.github.nalukit.paniolo.model.common.validation.ModelChecks;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * A tax of another type than VAT in the Factur-X profile EXTENDED, e.g. the insurance tax
 * {@code AAM}. It is kept apart from the VAT category, rate, and breakdown: it has its own tax type
 * code (UNTDID 5153), and its amount is part of the total tax amount (BT-110), the amount with
 * taxes (BT-112), and the amount due (BT-115) of the invoice.
 *
 * <p>On the document level ({@link ExtendedInformation#otherTaxes()}) the tax counts in the
 * totals. On the line level ({@link LineExtension#otherTaxes()}) it only describes the line and is
 * never added to the totals, so the same tax is not counted twice.
 *
 * <p>The CII writer preserves these taxes in Factur-X EXTENDED. Writing them as UBL or in
 * another profile is rejected; a normalized export does not silently discard them.
 *
 * @param typeCode     tax type code; only {@link #INSURANCE_TAX} is supported
 * @param categoryCode tax category code as stated, e.g. {@code S}
 * @param rate         tax rate in percent, or null
 * @param basisAmount  amount the tax is calculated on, or null; the document level states it
 * @param amount       tax amount, rounded to cents
 */
public record OtherTax(String typeCode, String categoryCode, BigDecimal rate, BigDecimal basisAmount, BigDecimal amount) {
  /**
   * Tax type code of the insurance tax (UNTDID 5153).
   */
  public static final String INSURANCE_TAX = "AAM";

  /**
   * The tax type codes the model keeps. Every other tax type is reported when reading and
   * rejected when writing.
   */
  public static final Set<String> SUPPORTED = Set.of(INSURANCE_TAX);

  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public OtherTax {
    Objects.requireNonNull(typeCode,
                           "typeCode");
    if (!SUPPORTED.contains(typeCode)) {
      throw new IllegalArgumentException("Unsupported tax type " + typeCode + "; the model keeps the tax types " + SUPPORTED);
    }
    ModelChecks.required(categoryCode,
                         "tax category code");
    if (rate != null) {
      ModelChecks.nonNegative(rate,
                              "tax rate");
      rate = rate.stripTrailingZeros();
    }
    if (basisAmount != null) {
      basisAmount = ModelChecks.money(basisAmount);
    }
    amount = ModelChecks.money(Objects.requireNonNull(amount,
                                                      "amount"));
  }

  /**
   * Insurance tax calculated from the basis amount and the rate, rounded half up to cents.
   *
   * @param categoryCode tax category code, e.g. {@code S}
   * @param rate         tax rate in percent
   * @param basisAmount  amount the tax is calculated on
   * @return the insurance tax with the calculated amount
   */
  public static OtherTax insuranceTax(String categoryCode,
                                      BigDecimal rate,
                                      BigDecimal basisAmount) {
    return new OtherTax(INSURANCE_TAX,
                        categoryCode,
                        rate,
                        basisAmount,
                        ModelChecks.money(Objects.requireNonNull(basisAmount,
                                                                 "basisAmount")
                                                 .multiply(Objects.requireNonNull(rate,
                                                                                  "rate"))
                                                 .movePointLeft(2)));
  }
}
