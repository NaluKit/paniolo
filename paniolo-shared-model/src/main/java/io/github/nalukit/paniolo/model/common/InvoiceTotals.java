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

import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.validation.ModelChecks;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EN 16931 totals, rounded per line and VAT group using decimal arithmetic; roundingAmount may be null.
 *
 * @param lineTotal          sum of invoice line net amounts (BT-106)
 * @param allowanceTotal     sum of document allowances (BT-107)
 * @param chargeTotal        sum of document charges (BT-108)
 * @param taxExclusiveAmount invoice total without VAT (BT-109)
 * @param taxAmount          invoice total VAT amount in the invoice currency (BT-110)
 * @param taxInclusiveAmount invoice total with VAT (BT-112)
 * @param paidAmount         paid amount (BT-113)
 * @param roundingAmount     rounding adjustment (BT-114), or null
 * @param payableAmount      amount due for payment (BT-115), including third party payments where applicable
 * @param vatBreakdowns      VAT breakdowns by category and rate (BG-23); copied on construction
 */
public record InvoiceTotals(BigDecimal lineTotal, BigDecimal allowanceTotal, BigDecimal chargeTotal, BigDecimal taxExclusiveAmount, BigDecimal taxAmount, BigDecimal taxInclusiveAmount, BigDecimal paidAmount, BigDecimal roundingAmount,
                            BigDecimal payableAmount, List<VatBreakdown> vatBreakdowns) {

  /**
   * Copies the VAT breakdowns.
   */
  public InvoiceTotals {
    vatBreakdowns = List.copyOf(vatBreakdowns);
  }

  /**
   * Totals of an invoice without lines from its stated totals; allowances and charges come from
   * the document level, the amount with VAT and the amount due are derived (BR-CO-15, BR-CO-16).
   * For MINIMUM, which states no line total, the line, allowance, and charge totals are null.
   *
   * @param invoice the invoice with stated totals
   * @return the totals
   */
  public static InvoiceTotals stated(Invoice invoice) {
    var        stated     = invoice.statedTotals();
    BigDecimal allowances = null;
    BigDecimal charges    = null;
    if (stated.lineTotal() != null) {
      allowances = BigDecimal.ZERO;
      charges    = BigDecimal.ZERO;
      for (var adjustment : invoice.adjustments()) {
        if (adjustment.charge()) {
          charges = charges.add(adjustment.amount());
        } else {
          allowances = allowances.add(adjustment.amount());
        }
      }
      allowances = ModelChecks.money(allowances);
      charges    = ModelChecks.money(charges);
    }
    BigDecimal gross = stated.taxBasisTotal()
                             .add(stated.taxTotal());
    BigDecimal rounding = invoice.roundingAmount();
    BigDecimal payable = gross.subtract(invoice.paidAmount())
                              .add(rounding == null ?
                                   BigDecimal.ZERO :
                                   rounding);
    return new InvoiceTotals(stated.lineTotal(),
                             allowances,
                             charges,
                             stated.taxBasisTotal(),
                             stated.taxTotal(),
                             gross,
                             invoice.paidAmount(),
                             rounding,
                             payable,
                             stated.vatBreakdowns());
  }

  /**
   * Totals calculated from the lines, allowances, and charges of an invoice, with the VAT breakdown
   * per category and rate; a French multi-vendor invoice has one per sub-invoice, category, and rate
   * (BR-FR-MV-09, see {@link ProcessControl#isFrenchMultiVendor()}).
   *
   * @param invoice the invoice
   * @return the totals
   */
  public static InvoiceTotals calculate(Invoice invoice) {
    Map<TaxCategory, BigDecimal> bases      = new LinkedHashMap<>();
    BigDecimal                   lines      = BigDecimal.ZERO;
    BigDecimal                   allowances = BigDecimal.ZERO;
    BigDecimal                   charges    = BigDecimal.ZERO;
    for (var line : invoice.lines()) {
      // EXTENDED subtotal and information lines do not count (BR-FXEXT-CO-10). Their VAT category
      // still needs a breakdown, which BR-FXEXT-E-01 and its siblings check for every line; it
      // adds no amount.
      if (line.extended() != null &&
          !line.extended()
               .counts()) {
        if (line.tax() != null) {
          bases.merge(line.tax(),
                      BigDecimal.ZERO,
                      BigDecimal::add);
        }
        continue;
      }
      lines = lines.add(line.netAmount());
      bases.merge(line.tax(),
                  line.netAmount(),
                  BigDecimal::add);
    }
    for (var adjustment : invoice.adjustments()) {
      if (adjustment.charge()) {
        charges = charges.add(adjustment.amount());
      } else {
        allowances = allowances.add(adjustment.amount());
      }
      bases.merge(adjustment.tax(),
                  adjustment.charge() ?
                  adjustment.amount() :
                  adjustment.amount()
                            .negate(),
                  BigDecimal::add);
    }
    // EXTENDED logistics service charges count like document level charges.
    if (invoice.extended() != null) {
      for (var service : invoice.extended()
                                .logisticsServiceCharges()) {
        charges = charges.add(service.amount());
        bases.merge(service.tax(),
                    service.amount(),
                    BigDecimal::add);
      }
    }
    // One breakdown per category/rate; reject conflicting exemption descriptions. A French
    // multi-vendor invoice has one breakdown per sub-invoice as well (BR-FR-MV-09).
    boolean    subInvoices = invoice.processControl()
                                    .isFrenchMultiVendor();
    var        groupIds    = new java.util.HashSet<String>();
    var        breakdowns  = new ArrayList<VatBreakdown>();
    BigDecimal tax         = BigDecimal.ZERO;
    for (var entry : bases.entrySet()) {
      TaxCategory category = entry.getKey();
      if (!groupIds.add(category.code()
                                .getCode() + ":" + category.rate() + (subInvoices ?
                                                                       "#" + category.subInvoiceNumber() :
                                                                       ""))) {
        throw new IllegalArgumentException("Conflicting exemption information for VAT category/rate");
      }
      BigDecimal amount = category.rate() == null ?
                          ModelChecks.money(BigDecimal.ZERO) :
                          ModelChecks.money(entry.getValue()
                                                 .multiply(category.rate())
                                                 .movePointLeft(2));
      breakdowns.add(new VatBreakdown(category,
                                      ModelChecks.money(entry.getValue()),
                                      amount));
      tax = tax.add(amount);
    }
    BigDecimal net = ModelChecks.money(lines.subtract(allowances)
                                            .add(charges));
    BigDecimal gross    = net.add(tax);
    BigDecimal rounding = invoice.roundingAmount();
    BigDecimal payable = gross.subtract(invoice.paidAmount())
                              .add(rounding == null ?
                                   BigDecimal.ZERO :
                                   rounding);
    // EXTENDED financial adjustments change the amount due only (BR-FXEXT-CO-16).
    if (invoice.extended() != null) {
      for (var adjustment : invoice.extended()
                                   .financialAdjustments()) {
        payable = payable.add(adjustment.amount());
      }
    }
    // XRechnung extension: third party payments are added to the amount due (BR-DEX-09).
    for (var payment : invoice.thirdPartyPayments()) {
      payable = payable.add(payment.amount());
    }
    return new InvoiceTotals(ModelChecks.money(lines),
                             ModelChecks.money(allowances),
                             ModelChecks.money(charges),
                             net,
                             ModelChecks.money(tax),
                             gross,
                             invoice.paidAmount(),
                             rounding,
                             payable,
                             breakdowns);
  }
}
