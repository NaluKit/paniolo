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

/**
 * Allowance or charge on the item gross price of the Factur-X profile EXTENDED
 * ({@code AppliedTradeAllowanceCharge} of {@code GrossPriceProductTradePrice}). EN 16931 knows one
 * price discount per line (BT-147); EXTENDED states its parts separately, e.g. a customer discount
 * and a promotional discount, or a charge per unit.
 *
 * @param charge     true for a charge, false for an allowance
 * @param amount     amount per price base quantity; not negative
 * @param reasonCode UNTDID 5189 (allowance) or 7161 (charge) reason code, or null
 * @param reason     reason as text, or null
 */
public record GrossPriceAdjustment(boolean charge, BigDecimal amount, String reasonCode, String reason) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public GrossPriceAdjustment {
    ModelChecks.nonNegative(amount,
                            "gross price adjustment amount");
  }

  /**
   * An allowance on the gross price without reason.
   *
   * @param amount amount per price base quantity; not negative
   * @return the allowance
   */
  public static GrossPriceAdjustment allowance(BigDecimal amount) {
    return new GrossPriceAdjustment(false,
                                    amount,
                                    null,
                                    null);
  }

  /**
   * A charge on the gross price without reason.
   *
   * @param amount amount per price base quantity; not negative
   * @return the charge
   */
  public static GrossPriceAdjustment charge(BigDecimal amount) {
    return new GrossPriceAdjustment(true,
                                    amount,
                                    null,
                                    null);
  }

  /**
   * Returns the contribution to the price discount (BT-147): the amount of an allowance, the
   * negated amount of a charge.
   *
   * @return the signed discount amount
   */
  public BigDecimal discount() {
    return charge ?
           amount.negate() :
           amount;
  }
}
