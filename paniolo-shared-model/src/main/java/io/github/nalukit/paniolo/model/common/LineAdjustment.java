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
package io.github.nalukit.paniolo.model.common;

import io.github.nalukit.paniolo.model.common.validation.ModelChecks;
import io.github.nalukit.paniolo.model.common.value.AllowanceChargeIdentification;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Invoice line allowance (charge=false, BG-27) or charge (charge=true, BG-28). It belongs to the
 * line's VAT category and is included in the line net amount (BT-131). Reason codes are UNTDID 5189
 * for allowances ({@link AllowanceChargeIdentification}) and UNTDID 7161 for charges.
 *
 * @param charge     true for a charge (BG-28), false for an allowance (BG-27)
 * @param amount     line allowance or charge amount (BT-136 / BT-141); required
 * @param baseAmount base amount (BT-137 / BT-142), or null
 * @param percentage percentage of the base amount (BT-138 / BT-143), or null; requires the base amount
 * @param reason     reason text (BT-139 / BT-144); required when no reason code is given
 * @param reasonCode reason code (BT-140 / BT-145), or null; UNTDID 5189 for allowances, 7161 for charges
 */
public record LineAdjustment(boolean charge, BigDecimal amount, BigDecimal baseAmount, BigDecimal percentage, String reason, String reasonCode) {
  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public LineAdjustment {
    amount = ModelChecks.money(Objects.requireNonNull(amount,
                                                      "line adjustment amount"));
    if (baseAmount != null) {
      baseAmount = ModelChecks.money(baseAmount);
    }
    // The percentage is not restricted either: corrections state negative percentages of negative bases.
    // It needs the base it refers to (PEPPOL-EN16931-R041); no base is derived from other amounts.
    if (percentage != null && baseAmount == null) {
      throw new IllegalArgumentException("line adjustment percentage (BT-138 / BT-143) " + percentage.toPlainString() + " requires the base amount (BT-137 / BT-142)");
    }
    if ((reason == null || reason.isBlank()) && (reasonCode == null || reasonCode.isBlank())) {
      throw new IllegalArgumentException("line adjustment reason or reason code must not be blank");
    }
    if (!charge && reasonCode != null && AllowanceChargeIdentification.of(reasonCode) == null) {
      throw new IllegalArgumentException("Unknown UNTDID 5189 allowance reason code: " + reasonCode);
    }
  }

  /**
   * Starts an invoice line allowance (BG-27).
   *
   * @return a new builder for an allowance
   */
  public static Builder createAllowance() {
    return new Builder().charge(false);
  }

  /**
   * Starts an invoice line charge (BG-28).
   *
   * @return a new builder for a charge
   */
  public static Builder createCharge() {
    return new Builder().charge(true);
  }

  /**
   * Named construction of an immutable line allowance or charge.
   *
   * <p>Mutable construction state for {@link LineAdjustment}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private boolean    charge;
    private BigDecimal amount;
    private BigDecimal baseAmount;
    private BigDecimal percentage;
    private String     reason;
    private String     reasonCode;

    private Builder() {
    }

    /**
     * Sets {@link LineAdjustment#charge() charge}.
     *
     * @param charge true for a charge (BG-28), false for an allowance (BG-27)
     * @return this builder
     */
    public Builder charge(boolean charge) {
      this.charge = charge;
      return this;
    }

    /**
     * Amount (BT-136 / BT-141).
     *
     * @param amount line allowance or charge amount (BT-136 / BT-141); required
     * @return this builder
     */
    public Builder amount(BigDecimal amount) {
      this.amount = amount;
      return this;
    }

    /**
     * Base amount (BT-137 / BT-142).
     *
     * @param baseAmount base amount (BT-137 / BT-142), or null
     * @return this builder
     */
    public Builder baseAmount(BigDecimal baseAmount) {
      this.baseAmount = baseAmount;
      return this;
    }

    /**
     * Percentage (BT-138 / BT-143).
     *
     * @param percentage percentage of the base amount (BT-138 / BT-143), or null; requires the base amount
     * @return this builder
     */
    public Builder percentage(BigDecimal percentage) {
      this.percentage = percentage;
      return this;
    }

    /**
     * Reason (BT-139 / BT-144).
     *
     * @param reason reason text (BT-139 / BT-144); required when no reason code is given
     * @return this builder
     */
    public Builder reason(String reason) {
      this.reason = reason;
      return this;
    }

    /**
     * Reason code (BT-140 / BT-145).
     *
     * @param reasonCode reason code (BT-140 / BT-145), or null; UNTDID 5189 for allowances, 7161 for charges
     * @return this builder
     */
    public Builder reasonCode(String reasonCode) {
      this.reasonCode = reasonCode;
      return this;
    }

    /**
     * Allowance reason code (BT-140).
     *
     * @param reasonCode reason code (BT-140 / BT-145), or null; UNTDID 5189 for allowances, 7161 for charges
     * @return this builder
     */
    public Builder reasonCode(AllowanceChargeIdentification reasonCode) {
      this.reasonCode = reasonCode == null ?
                        null :
                        reasonCode.getCode();
      return this;
    }

    /**
     * Constructs a new {@link LineAdjustment} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public LineAdjustment build() {
      return new LineAdjustment(charge,
                                amount,
                                baseAmount,
                                percentage,
                                reason,
                                reasonCode);
    }
  }
}
