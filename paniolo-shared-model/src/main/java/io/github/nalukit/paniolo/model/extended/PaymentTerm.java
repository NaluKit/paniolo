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

import io.github.nalukit.paniolo.model.common.Payee;
import io.github.nalukit.paniolo.model.common.validation.ModelChecks;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Additional structured payment term of the Factur-X profile EXTENDED, written after the payment
 * terms of the invoice (BT-20, due date BT-9). It carries a partial amount, a cash discount, a
 * penalty, or its own payee, or just a further description or due date, e.g. an instalment
 * without amount. The first plain term of an invoice belongs to {@code Invoice.paymentTerms} and
 * {@code Invoice.dueDate}.
 *
 * @param description   text of the term, or null
 * @param dueDate       due date of the partial amount, or null
 * @param partialAmount amount due under this term, or null
 * @param penalty       late payment penalty, or null
 * @param discount      cash discount, or null
 * @param payee         payee of this term, if it differs from the payee of the invoice; or null
 */
public record PaymentTerm(String description, LocalDate dueDate, BigDecimal partialAmount, PaymentTermsAdjustment penalty, PaymentTermsAdjustment discount, Payee payee) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public PaymentTerm {
    if (description == null && dueDate == null && partialAmount == null && penalty == null && discount == null && payee == null) {
      throw new IllegalArgumentException("An EXTENDED payment term requires a description, a due date, a partial amount, a penalty, a discount, or a payee");
    }
    if (description != null) {
      ModelChecks.required(description,
                           "payment term description");
    }
    if (partialAmount != null) {
      partialAmount = ModelChecks.money(partialAmount);
    }
  }

  /**
   * A payment term without payee.
   *
   * @param description   payment terms description, or null
   * @param dueDate       due date, or null
   * @param partialAmount partial payment amount, or null
   * @param penalty       penalty for late payment, or null
   * @param discount      discount for early payment, or null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public PaymentTerm(String description,
                     LocalDate dueDate,
                     BigDecimal partialAmount,
                     PaymentTermsAdjustment penalty,
                     PaymentTermsAdjustment discount) {
    this(description,
         dueDate,
         partialAmount,
         penalty,
         discount,
         null);
  }

  /**
   * Starts named construction of a {@link PaymentTerm} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable payment term.
   *
   * <p>Mutable construction state for {@link PaymentTerm}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String                 description;
    private LocalDate              dueDate;
    private BigDecimal             partialAmount;
    private PaymentTermsAdjustment penalty;
    private PaymentTermsAdjustment discount;
    private Payee                  payee;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link PaymentTerm#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link PaymentTerm#description() description}.
     *
     * @param description text of the term, or null
     * @return this builder
     */
    public Builder description(String description) {
      this.description = description;
      return this;
    }

    /**
     * Sets {@link PaymentTerm#dueDate() dueDate}.
     *
     * @param dueDate due date of the partial amount, or null
     * @return this builder
     */
    public Builder dueDate(LocalDate dueDate) {
      this.dueDate = dueDate;
      return this;
    }

    /**
     * Sets {@link PaymentTerm#partialAmount() partialAmount}.
     *
     * @param partialAmount amount due under this term, or null
     * @return this builder
     */
    public Builder partialAmount(BigDecimal partialAmount) {
      this.partialAmount = partialAmount;
      return this;
    }

    /**
     * Sets {@link PaymentTerm#penalty() penalty}.
     *
     * @param penalty late payment penalty, or null
     * @return this builder
     */
    public Builder penalty(PaymentTermsAdjustment penalty) {
      this.penalty = penalty;
      return this;
    }

    /**
     * Sets {@link PaymentTerm#discount() discount}.
     *
     * @param discount cash discount, or null
     * @return this builder
     */
    public Builder discount(PaymentTermsAdjustment discount) {
      this.discount = discount;
      return this;
    }

    /**
     * Sets {@link PaymentTerm#payee() payee}.
     *
     * @param payee payee of this term, if it differs from the payee of the invoice; or null
     * @return this builder
     */
    public Builder payee(Payee payee) {
      this.payee = payee;
      return this;
    }

    /**
     * Constructs a new {@link PaymentTerm} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public PaymentTerm build() {
      return new PaymentTerm(description,
                             dueDate,
                             partialAmount,
                             penalty,
                             discount,
                             payee);
    }
  }
}
