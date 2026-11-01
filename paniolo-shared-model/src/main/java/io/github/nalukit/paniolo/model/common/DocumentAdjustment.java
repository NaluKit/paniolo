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
import io.github.nalukit.paniolo.model.extended.AdjustmentExtension;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Document allowance (charge=false) or charge (charge=true), BG-20 / BG-21. Reason codes are
 * UNTDID 5189 for allowances ({@link AllowanceChargeIdentification}) and UNTDID 7161 for charges.
 * {@code extended} carries the sequence number and base quantity of the Factur-X profile EXTENDED,
 * or null.
 *
 * @param charge     true for a charge (BG-21), false for an allowance (BG-20)
 * @param amount     allowance or charge amount (BT-92 / BT-99); required, rounded to money precision
 * @param baseAmount base amount (BT-93 / BT-100), or null
 * @param percentage percentage of the base amount (BT-94 / BT-101), or null; requires the base amount
 * @param reason     reason text (BT-97 / BT-104); required when no reason code is given
 * @param reasonCode reason code (BT-98 / BT-105), or null; UNTDID 5189 for allowances, 7161 for charges
 * @param tax        VAT category and rate of the adjustment; required
 * @param extended   sequence number and base quantity for Factur-X EXTENDED, or null
 */
public record DocumentAdjustment(boolean charge, BigDecimal amount, BigDecimal baseAmount, BigDecimal percentage, String reason, String reasonCode, TaxCategory tax, AdjustmentExtension extended) {
  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public DocumentAdjustment {
    // EN 16931 does not restrict the sign; corrective invoices use negative amounts.
    amount = ModelChecks.money(Objects.requireNonNull(amount,
                                                      "adjustment amount"));
    if (baseAmount != null) {
      baseAmount = ModelChecks.money(baseAmount);
    }
    // The percentage is not restricted either: corrections state negative percentages of negative bases.
    // It needs the base it refers to (PEPPOL-EN16931-R041); no base is derived from other amounts.
    if (percentage != null && baseAmount == null) {
      throw new IllegalArgumentException("adjustment percentage (BT-94 / BT-101) " + percentage.toPlainString() + " requires the base amount (BT-93 / BT-100)");
    }
    if ((reason == null || reason.isBlank()) && (reasonCode == null || reasonCode.isBlank())) {
      throw new IllegalArgumentException("adjustment reason or reason code must not be blank");
    }
    if (!charge && reasonCode != null && AllowanceChargeIdentification.of(reasonCode) == null) {
      throw new IllegalArgumentException("Unknown UNTDID 5189 allowance reason code: " + reasonCode);
    }
    Objects.requireNonNull(tax,
                           "tax");
  }

  /**
   * An allowance or charge without EXTENDED information.
   *
   * @param charge     true for a charge (BG-21), false for an allowance (BG-20)
   * @param amount     amount (BT-92 / BT-99)
   * @param baseAmount base amount (BT-93 / BT-100), or null
   * @param percentage percentage (BT-94 / BT-101), or null
   * @param reason     reason (BT-97 / BT-104), or null if a reason code is given
   * @param reasonCode reason code (BT-98 / BT-105), or null if a reason is given
   * @param tax        VAT category (BT-95 / BT-102)
   * @throws IllegalArgumentException if a value violates the checks of the model
   * @throws NullPointerException     if a required value is null
   */
  public DocumentAdjustment(boolean charge,
                            BigDecimal amount,
                            BigDecimal baseAmount,
                            BigDecimal percentage,
                            String reason,
                            String reasonCode,
                            TaxCategory tax) {
    this(charge,
         amount,
         baseAmount,
         percentage,
         reason,
         reasonCode,
         tax,
         null);
  }

  /**
   * An allowance or charge with amount, reason, and VAT category only.
   *
   * @param charge true for a charge (BG-21), false for an allowance (BG-20)
   * @param amount amount (BT-92 / BT-99)
   * @param reason reason (BT-97 / BT-104)
   * @param tax    VAT category (BT-95 / BT-102)
   * @throws IllegalArgumentException if a value violates the checks of the model
   * @throws NullPointerException     if a required value is null
   */
  public DocumentAdjustment(boolean charge,
                            BigDecimal amount,
                            String reason,
                            TaxCategory tax) {
    this(charge,
         amount,
         null,
         null,
         reason,
         null,
         tax);
  }

  /**
   * Starts named construction of a {@link DocumentAdjustment} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Starts a document level allowance (BG-20).
   *
   * @return a new builder for an allowance
   */
  public static Builder createAllowance() {
    return new Builder().charge(false);
  }

  /**
   * Starts a document level charge (BG-21).
   *
   * @return a new builder for a charge
   */
  public static Builder createCharge() {
    return new Builder().charge(true);
  }

  /**
   * Named construction of an immutable allowance or charge.
   *
   * <p>Mutable construction state for {@link DocumentAdjustment}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private boolean             charge;
    private BigDecimal          amount;
    private BigDecimal          baseAmount;
    private BigDecimal          percentage;
    private String              reason;
    private String              reasonCode;
    private TaxCategory         tax;
    private AdjustmentExtension extended;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link DocumentAdjustment#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link DocumentAdjustment#charge() charge}.
     *
     * @param charge true for a charge (BG-21), false for an allowance (BG-20)
     * @return this builder
     */
    public Builder charge(boolean charge) {
      this.charge = charge;
      return this;
    }

    /**
     * Sets {@link DocumentAdjustment#amount() amount}.
     *
     * @param amount allowance or charge amount (BT-92 / BT-99); required, rounded to money precision
     * @return this builder
     */
    public Builder amount(BigDecimal amount) {
      this.amount = amount;
      return this;
    }

    /**
     * Base amount (BT-93 / BT-100).
     *
     * @param baseAmount base amount (BT-93 / BT-100), or null
     * @return this builder
     */
    public Builder baseAmount(BigDecimal baseAmount) {
      this.baseAmount = baseAmount;
      return this;
    }

    /**
     * Percentage (BT-94 / BT-101), applied to the base amount.
     *
     * @param percentage percentage of the base amount (BT-94 / BT-101), or null; requires the base amount
     * @return this builder
     */
    public Builder percentage(BigDecimal percentage) {
      this.percentage = percentage;
      return this;
    }

    /**
     * Sets {@link DocumentAdjustment#reason() reason}.
     *
     * @param reason reason text (BT-97 / BT-104); required when no reason code is given
     * @return this builder
     */
    public Builder reason(String reason) {
      this.reason = reason;
      return this;
    }

    /**
     * Reason code (BT-98 / BT-105).
     *
     * @param reasonCode reason code (BT-98 / BT-105), or null; UNTDID 5189 for allowances, 7161 for charges
     * @return this builder
     */
    public Builder reasonCode(String reasonCode) {
      this.reasonCode = reasonCode;
      return this;
    }

    /**
     * Allowance reason code (BT-98).
     *
     * @param reasonCode reason code (BT-98 / BT-105), or null; UNTDID 5189 for allowances, 7161 for charges
     * @return this builder
     */
    public Builder reasonCode(AllowanceChargeIdentification reasonCode) {
      this.reasonCode = reasonCode == null ?
                        null :
                        reasonCode.getCode();
      return this;
    }

    /**
     * Sets {@link DocumentAdjustment#tax() tax}.
     *
     * @param tax VAT category and rate of the adjustment; required
     * @return this builder
     */
    public Builder tax(TaxCategory tax) {
      this.tax = tax;
      return this;
    }

    /**
     * Sequence number and base quantity of the Factur-X profile EXTENDED.
     *
     * @param extended sequence number and base quantity for Factur-X EXTENDED, or null
     * @return this builder
     */
    public Builder extended(AdjustmentExtension extended) {
      this.extended = extended;
      return this;
    }

    /**
     * Constructs a new {@link DocumentAdjustment} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public DocumentAdjustment build() {
      return new DocumentAdjustment(charge,
                                    amount,
                                    baseAmount,
                                    percentage,
                                    reason,
                                    reasonCode,
                                    tax,
                                    extended);
    }
  }
}
