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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Stated totals of an invoice without lines, as in the Factur-X profiles MINIMUM and BASIC WL.
 * Invoices with lines derive these amounts from their lines instead. The amount with VAT (BT-112)
 * and the amount due (BT-115) follow from these totals, the paid amount, and the rounding amount.
 *
 * @param lineTotal     sum of the line net amounts (BT-106); required for BASIC WL, null for MINIMUM
 * @param taxBasisTotal invoice total amount without VAT (BT-109)
 * @param taxTotal      invoice total VAT amount (BT-110)
 * @param vatBreakdowns VAT breakdown (BG-23); required for BASIC WL, empty for MINIMUM
 */
public record DocumentTotals(BigDecimal lineTotal, BigDecimal taxBasisTotal, BigDecimal taxTotal, List<VatBreakdown> vatBreakdowns) {
  /**
   * Applies the checks of the model and copies the lists.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public DocumentTotals {
    if (lineTotal != null) {
      lineTotal = ModelChecks.money(lineTotal);
    }
    taxBasisTotal = ModelChecks.money(Objects.requireNonNull(taxBasisTotal,
                                                             "taxBasisTotal"));
    taxTotal      = ModelChecks.money(Objects.requireNonNull(taxTotal,
                                                             "taxTotal"));
    vatBreakdowns = List.copyOf(vatBreakdowns);
    if (!vatBreakdowns.isEmpty()) {
      var        groups = new HashSet<String>();
      BigDecimal bases  = BigDecimal.ZERO;
      BigDecimal taxes  = BigDecimal.ZERO;
      for (VatBreakdown breakdown : vatBreakdowns) {
        Objects.requireNonNull(breakdown.tax(),
                               "VAT breakdown category");
        // One breakdown per category and rate.
        String group = breakdown.tax()
                                .code()
                                .getCode() +
                       " " +
                       breakdown.tax()
                                .rate();
        if (!groups.add(group)) {
          throw new IllegalArgumentException("Duplicate VAT breakdown for category and rate " + group);
        }
        bases = bases.add(breakdown.taxableAmount());
        taxes = taxes.add(breakdown.taxAmount());
      }
      // BR-CO-14: the total VAT amount is the sum of the VAT category tax amounts.
      if (taxes.compareTo(taxTotal) != 0) {
        throw new IllegalArgumentException("Total VAT amount " + taxTotal.toPlainString() + " differs from the sum of the VAT breakdown " + taxes.toPlainString() + " (BR-CO-14)");
      }
      if (bases.compareTo(taxBasisTotal) != 0) {
        throw new IllegalArgumentException("Total amount without VAT " + taxBasisTotal.toPlainString() + " differs from the sum of the VAT taxable amounts " + bases.toPlainString());
      }
    }
  }

  /**
   * Starts named construction of a {@link DocumentTotals} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of immutable document totals. Without an explicit total, the amount without
   * VAT and the VAT amount are the sums of the VAT breakdown.
   *
   * <p>Mutable construction state for {@link DocumentTotals}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private final List<VatBreakdown> vatBreakdowns = new ArrayList<>();
    private       BigDecimal         lineTotal;
    private       BigDecimal         taxBasisTotal;
    private       BigDecimal         taxTotal;

    private Builder() {
    }

    /**
     * Sets {@link DocumentTotals#lineTotal() lineTotal}.
     *
     * @param lineTotal sum of the line net amounts (BT-106); required for BASIC WL, null for MINIMUM
     * @return this builder
     */
    public Builder lineTotal(BigDecimal lineTotal) {
      this.lineTotal = lineTotal;
      return this;
    }

    /**
     * Sets {@link DocumentTotals#taxBasisTotal() taxBasisTotal}.
     *
     * @param taxBasisTotal invoice total amount without VAT (BT-109)
     * @return this builder
     */
    public Builder taxBasisTotal(BigDecimal taxBasisTotal) {
      this.taxBasisTotal = taxBasisTotal;
      return this;
    }

    /**
     * Sets {@link DocumentTotals#taxTotal() taxTotal}.
     *
     * @param taxTotal invoice total VAT amount (BT-110)
     * @return this builder
     */
    public Builder taxTotal(BigDecimal taxTotal) {
      this.taxTotal = taxTotal;
      return this;
    }

    /**
     * Adds a VAT breakdown (BG-23) with the taxable amount (BT-116) and the tax amount (BT-117).
     *
     * @param tax           VAT category and rate
     * @param taxableAmount the taxable amount (BT-116)
     * @param taxAmount     the tax amount (BT-117)
     * @return this builder
     */
    public Builder addVatBreakdown(TaxCategory tax,
                                   BigDecimal taxableAmount,
                                   BigDecimal taxAmount) {
      vatBreakdowns.add(new VatBreakdown(tax,
                                         ModelChecks.money(Objects.requireNonNull(taxableAmount,
                                                                                  "taxableAmount")),
                                         ModelChecks.money(Objects.requireNonNull(taxAmount,
                                                                                  "taxAmount"))));
      return this;
    }

    /**
     * Constructs a new {@link DocumentTotals} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public DocumentTotals build() {
      BigDecimal bases = taxBasisTotal;
      BigDecimal taxes = taxTotal;
      if (!vatBreakdowns.isEmpty()) {
        if (bases == null) {
          bases = vatBreakdowns.stream()
                               .map(VatBreakdown::taxableAmount)
                               .reduce(BigDecimal.ZERO,
                                       BigDecimal::add);
        }
        if (taxes == null) {
          taxes = vatBreakdowns.stream()
                               .map(VatBreakdown::taxAmount)
                               .reduce(BigDecimal.ZERO,
                                       BigDecimal::add);
        }
      }
      return new DocumentTotals(lineTotal,
                                bases,
                                taxes,
                                vatBreakdowns);
    }
  }
}
