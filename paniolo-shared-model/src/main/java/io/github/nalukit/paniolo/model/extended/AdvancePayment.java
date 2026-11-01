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

import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.validation.ModelChecks;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Advance payment of the Factur-X profile EXTENDED. It documents an amount already received; the
 * paid amount (BT-113) of the invoice is not derived from it and must be set separately.
 *
 * @param amount           paid amount
 * @param receivedDate     date the payment was received, or null
 * @param tax              VAT category and rate included in the payment
 * @param taxAmount        VAT amount included in the payment, or null
 * @param invoiceReference number of the advance payment invoice, or null
 */
public record AdvancePayment(BigDecimal amount, LocalDate receivedDate, TaxCategory tax, BigDecimal taxAmount, String invoiceReference) {
  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public AdvancePayment {
    amount = ModelChecks.money(Objects.requireNonNull(amount,
                                                      "advance payment amount"));
    Objects.requireNonNull(tax,
                           "tax");
    if (taxAmount != null) {
      taxAmount = ModelChecks.money(taxAmount);
    }
    if (invoiceReference != null) {
      ModelChecks.required(invoiceReference,
                           "advance payment invoice reference");
    }
  }

  /**
   * Starts named construction of a {@link AdvancePayment} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable advance payment.
   *
   * <p>Mutable construction state for {@link AdvancePayment}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private BigDecimal  amount;
    private LocalDate   receivedDate;
    private TaxCategory tax;
    private BigDecimal  taxAmount;
    private String      invoiceReference;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link AdvancePayment#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link AdvancePayment#amount() amount}.
     *
     * @param amount paid amount
     * @return this builder
     */
    public Builder amount(BigDecimal amount) {
      this.amount = amount;
      return this;
    }

    /**
     * Sets {@link AdvancePayment#receivedDate() receivedDate}.
     *
     * @param receivedDate date the payment was received, or null
     * @return this builder
     */
    public Builder receivedDate(LocalDate receivedDate) {
      this.receivedDate = receivedDate;
      return this;
    }

    /**
     * Sets {@link AdvancePayment#tax() tax}.
     *
     * @param tax VAT category and rate included in the payment
     * @return this builder
     */
    public Builder tax(TaxCategory tax) {
      this.tax = tax;
      return this;
    }

    /**
     * Sets {@link AdvancePayment#taxAmount() taxAmount}.
     *
     * @param taxAmount VAT amount included in the payment, or null
     * @return this builder
     */
    public Builder taxAmount(BigDecimal taxAmount) {
      this.taxAmount = taxAmount;
      return this;
    }

    /**
     * Sets {@link AdvancePayment#invoiceReference() invoiceReference}.
     *
     * @param invoiceReference number of the advance payment invoice, or null
     * @return this builder
     */
    public Builder invoiceReference(String invoiceReference) {
      this.invoiceReference = invoiceReference;
      return this;
    }

    /**
     * Constructs a new {@link AdvancePayment} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public AdvancePayment build() {
      return new AdvancePayment(amount,
                                receivedDate,
                                tax,
                                taxAmount,
                                invoiceReference);
    }
  }
}
