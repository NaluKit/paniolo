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
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * VAT category, percentage and optional exemption text / VATEX code.
 *
 * @param code                VAT category code (UNTDID 5305); required
 * @param rate                VAT rate in percent, or null for category O
 * @param exemptionReason     VAT exemption reason text (BT-120), or null
 * @param exemptionReasonCode VATEX exemption reason code (BT-121), or null
 */
public record TaxCategory(DutyTaxFeeCategory code, BigDecimal rate, String exemptionReason, String exemptionReasonCode) {

  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public TaxCategory {
    Objects.requireNonNull(code,
                           "tax category code");
    if (!code.isAllowanceRelated()) {
      throw new IllegalArgumentException("Unsupported EN 16931 VAT category: " + code);
    }
    if (code == DutyTaxFeeCategory.O) {
      if (rate != null) {
        throw new IllegalArgumentException("Outside-scope VAT must not have a rate");
      }
    } else {
      rate = ModelChecks.nonNegative(rate,
                                     "tax rate")
                        .stripTrailingZeros();
      if (code == DutyTaxFeeCategory.S && rate.signum() == 0) {
        throw new IllegalArgumentException("Standard VAT requires a positive rate");
      }
      if (code != DutyTaxFeeCategory.S && code != DutyTaxFeeCategory.L && code != DutyTaxFeeCategory.M && rate.signum() != 0) {
        throw new IllegalArgumentException("This VAT category requires a zero rate");
      }
    }
  }

  /**
   * Standard rated VAT (category S) with the given rate.
   *
   * @param rate the VAT rate in percent, e.g. {@code 19}; must be positive
   * @return the tax category
   * @throws IllegalArgumentException if the rate is not positive
   */
  public static TaxCategory standard(BigDecimal rate) {
    return create().code(DutyTaxFeeCategory.S)
                   .rate(rate)
                   .build();
  }

  /**
   * The sub-invoice number of a French multi-vendor invoice, which the exemption reason states
   * between # at its start, e.g. {@code F202600004} for {@code #F202600004#} (BR-FR-MV-08).
   *
   * @return the sub-invoice number, or null if the exemption reason does not start with one
   */
  public String subInvoiceNumber() {
    if (exemptionReason == null || !exemptionReason.startsWith("#")) {
      return null;
    }
    int end = exemptionReason.indexOf('#',
                                      1);
    return end > 1 ?
           exemptionReason.substring(1,
                                     end) :
           null;
  }

  /**
   * Starts named construction of a {@link TaxCategory} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable tax category.
   *
   * <p>Mutable construction state for {@link TaxCategory}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private DutyTaxFeeCategory code;
    private BigDecimal         rate;
    private String             exemptionReason;
    private String             exemptionReasonCode;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link TaxCategory#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link TaxCategory#code() code}.
     *
     * @param code VAT category code (UNTDID 5305); required
     * @return this builder
     */
    public Builder code(DutyTaxFeeCategory code) {
      this.code = code;
      return this;
    }

    /**
     * Sets {@link TaxCategory#rate() rate}.
     *
     * @param rate VAT rate in percent, or null for category O
     * @return this builder
     */
    public Builder rate(BigDecimal rate) {
      this.rate = rate;
      return this;
    }

    /**
     * Sets {@link TaxCategory#exemptionReason() exemptionReason}.
     *
     * @param exemptionReason VAT exemption reason text (BT-120), or null
     * @return this builder
     */
    public Builder exemptionReason(String exemptionReason) {
      this.exemptionReason = exemptionReason;
      return this;
    }

    /**
     * Sets {@link TaxCategory#exemptionReasonCode() exemptionReasonCode}.
     *
     * @param exemptionReasonCode VATEX exemption reason code (BT-121), or null
     * @return this builder
     */
    public Builder exemptionReasonCode(String exemptionReasonCode) {
      this.exemptionReasonCode = exemptionReasonCode;
      return this;
    }

    /**
     * Constructs a new {@link TaxCategory} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public TaxCategory build() {
      return new TaxCategory(code,
                             rate,
                             exemptionReason,
                             exemptionReasonCode);
    }
  }
}
