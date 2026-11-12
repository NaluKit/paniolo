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
package io.github.nalukit.paniolo.xml.util;

import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.CashDiscount;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.value.AllowanceChargeIdentification;
import io.github.nalukit.paniolo.xml.AmountDeviation;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadResult;
import io.github.nalukit.paniolo.xml.ReadWarning;
import io.github.nalukit.paniolo.xml.StatedAmounts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Internal state of one read: the mode, the warnings, the stated line amounts and the deviations
 * collected so far. Readers create one per document, so the readers themselves stay stateless.
 */
public final class ReadContext {
  private final ReadMode                 mode;
  private final List<ReadWarning>        warnings   = new ArrayList<>();
  private final List<StatedAmounts.Line> lines      = new ArrayList<>();
  private final List<AmountDeviation>    deviations = new ArrayList<>();

  /**
   * Creates the state of one read.
   *
   * @param mode how deviations from the model are handled
   */
  public ReadContext(ReadMode mode) {
    this.mode = Objects.requireNonNull(mode,
                                       "mode");
  }

  /**
   * Returns the reason code of an allowance or charge. An allowance code outside UNTDID 5189, e.g.
   * the charge code {@code ABK} of older samples, is rejected by strict reading; lenient reading drops
   * it if a reason text remains.
   *
   * @param charge true for a charge, whose codes (UNTDID 7161) are kept as they are
   * @param code   the stated reason code, or null
   * @param reason the stated reason text, or null
   * @param lineId the line of a line allowance, or null for the document level
   * @return the reason code, or null if there is none or it was dropped
   * @throws IllegalArgumentException if the allowance code is unknown and cannot be dropped
   */
  public String reasonCode(boolean charge,
                           String code,
                           String reason,
                           String lineId) {
    if (code == null || charge || AllowanceChargeIdentification.of(code) != null) {
      return code;
    }
    if (mode == ReadMode.STRICT || reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Unknown UNTDID 5189 allowance reason code: " + code);
    }
    warnings.add(new ReadWarning(ReadWarning.REASON_CODE,
                                 lineId,
                                 (lineId == null ?
                                  "Allowance" :
                                  "Line " + lineId + ": allowance") + " reason code " + code + " is not in UNTDID 5189; dropped, reason " + reason + " kept"));
    return null;
  }

  /**
   * Returns the percentage of an allowance or charge. A percentage without the base amount it refers
   * to is rejected by strict reading; lenient reading drops it, keeps the stated amount, and reports
   * the stated percentage. No base amount is derived.
   *
   * @param charge     true for a charge
   * @param percentage the stated percentage, or null
   * @param baseAmount the stated base amount, or null
   * @param lineId     the line of a line allowance or charge, or null for the document level
   * @return the percentage, or null if there is none or it was dropped
   * @throws IllegalArgumentException if strict reading finds a percentage without base amount
   */
  public BigDecimal percentage(boolean charge,
                               BigDecimal percentage,
                               BigDecimal baseAmount,
                               String lineId) {
    if (percentage == null || baseAmount != null) {
      return percentage;
    }
    String what = (lineId == null ?
                   "" :
                   "Line " + lineId + ": ") + (charge ?
                                              "charge" :
                                              "allowance") + " percentage " + percentage.toPlainString() + " has no base amount";
    if (mode == ReadMode.STRICT) {
      throw new IllegalArgumentException(what);
    }
    warnings.add(new ReadWarning(ReadWarning.PERCENTAGE,
                                 lineId,
                                 what + "; dropped, the stated amount kept"));
    return null;
  }

  /**
   * Splits the payment terms (BT-20) and reports every line that starts like a cash discount but
   * cannot be read as one. Such a line stays in the payment terms text; no discount is guessed.
   *
   * @param paymentTerms the stated payment terms, or null
   * @return the free text and the cash discounts
   */
  public CashDiscount.Terms paymentTerms(String paymentTerms) {
    var parsed = CashDiscount.parse(paymentTerms);
    for (var line : parsed.unreadable()) {
      warnings.add(new ReadWarning(ReadWarning.CASH_DISCOUNT,
                                   null,
                                   "Payment terms (BT-20): " + line.line() + " is no cash discount, " + line.reason() + "; kept as text"));
    }
    return parsed;
  }

  /**
   * Handles a further payment means block that differs from the first one: strict reading rejects
   * the document, lenient reading reports the block without merging it into the payment
   * instruction.
   *
   * @param description the values of the block and how it differs
   * @throws IllegalArgumentException in strict reading
   */
  public void otherPaymentMeans(String description) {
    String message = description + "; the model keeps one payment instruction (BG-16), the block is not merged";
    if (mode == ReadMode.STRICT) {
      throw new IllegalArgumentException(message);
    }
    warnings.add(new ReadWarning(ReadWarning.PAYMENT_MEANS,
                                 null,
                                 message));
  }

  /**
   * Handles a line that carries a text only, without item and quantity, as ZUGFeRD 1.0 allows:
   * strict reading rejects the document, lenient reading reports that the text becomes an invoice
   * note. No item or quantity is invented.
   *
   * @param position the position of the line in the document, from 1
   * @param text     the text of the line
   * @throws IllegalArgumentException in strict reading
   */
  public void textLine(int position,
                       String text) {
    String message = "Line " + position + " has a text only, without item and quantity: " + text;
    if (mode == ReadMode.STRICT) {
      throw new IllegalArgumentException(message);
    }
    warnings.add(new ReadWarning(ReadWarning.TEXT_LINE,
                                 null,
                                 message + "; kept as invoice note"));
  }

  /**
   * Handles a tax of another type than VAT, e.g. the insurance tax {@code AAM} of Factur-X
   * EXTENDED: strict reading rejects the document, lenient reading reports the tax without reading
   * it. The tax is never read as VAT, and no total is adjusted for it.
   *
   * @param typeCode the stated tax type code, e.g. {@code AAM}
   * @param lineId   the line of a line tax, or null for the document level
   * @param location where the tax is stated, e.g. {@code line 2} or {@code header tax 3}
   * @param category the stated category code, or null
   * @param rate     the stated rate, or null
   * @param basis    the stated basis amount, or null
   * @param amount   the stated tax amount, or null
   * @throws IllegalArgumentException in strict reading
   */
  public void otherTax(String typeCode,
                       String lineId,
                       String location,
                       String category,
                       BigDecimal rate,
                       BigDecimal basis,
                       BigDecimal amount) {
    String message = "Tax type " + typeCode + " at " + location + " (category " + category + ", rate " + plain(rate) + ", basis " + plain(basis) + ", amount " + plain(amount) +
                     ") is not supported; the model keeps VAT only";
    if (mode == ReadMode.STRICT) {
      throw new IllegalArgumentException(message);
    }
    warnings.add(new ReadWarning(ReadWarning.OTHER_TAX,
                                 lineId,
                                 message + ", the tax was not read and the calculated totals lack it"));
  }

  /**
   * Handles an exemption reason or code (BT-120, BT-121, BT-X-96, BT-X-97) the model cannot keep
   * next to another one of the same VAT category and rate: strict reading rejects the document,
   * lenient reading reports the stated and the kept value.
   *
   * @param lineId   the line, or null for the document level
   * @param conflict the location and the conflicting values
   * @param handling which value lenient reading keeps
   * @throws IllegalArgumentException in strict reading
   */
  public void taxExemption(String lineId,
                           String conflict,
                           String handling) {
    if (mode == ReadMode.STRICT) {
      throw new IllegalArgumentException(conflict);
    }
    warnings.add(new ReadWarning(ReadWarning.TAX_EXEMPTION,
                                 lineId,
                                 conflict + "; " + handling));
  }

  private static String plain(BigDecimal value) {
    return value == null ?
           "-" :
           value.toPlainString();
  }

  /**
   * Reports a value of the document that the model does not keep.
   *
   * @param code    the warning code, e.g. {@link ReadWarning#IDENTIFIER}
   * @param message the value not kept and what it belongs to
   */
  public void lost(String code,
                   String message) {
    warnings.add(new ReadWarning(code,
                                 null,
                                 message));
  }

  /**
   * Keeps the stated line net amount (BT-131). If quantity x net price does not explain it, strict
   * reading rejects the line; lenient reading recalculates the net price from the stated amount and
   * the line allowances and charges, like the price of a line that states only its amount.
   *
   * @param line            the line read so far
   * @param statedNetAmount the stated line net amount (BT-131)
   * @return the line with the stated net amount
   * @throws IllegalArgumentException if the stated amount cannot be kept
   */
  public InvoiceLine netAmount(InvoiceLine line,
                               BigDecimal statedNetAmount) {
    var result = keepNetAmount(line,
                               statedNetAmount);
    if (line.id() != null) {
      lines.add(new StatedAmounts.Line(line.id(),
                                       line.netUnitPrice(),
                                       statedNetAmount));
    }
    deviate("BT-146",
            line.id(),
            line.netUnitPrice(),
            result.netUnitPrice());
    deviate("BT-131",
            line.id(),
            statedNetAmount,
            result.netAmount());
    return result;
  }

  private InvoiceLine keepNetAmount(InvoiceLine line,
                                    BigDecimal statedNetAmount) {
    try {
      return line.withNetAmount(statedNetAmount);
    } catch (IllegalArgumentException e) {
      // A line without net price takes its amount from its sub-lines; there is no price to recalculate.
      if (mode == ReadMode.STRICT ||
          line.netUnitPrice() == null ||
          line.quantity()
              .signum() == 0) {
        throw e;
      }
      BigDecimal stated = statedNetAmount.setScale(2,
                                                   RoundingMode.HALF_UP);
      // A price with the precision of the stated one, at least cents, keeps the rounding error
      // within the tolerance of the line.
      BigDecimal price = stated.subtract(line.chargeTotal())
                               .add(line.allowanceTotal())
                               .multiply(line.priceBaseQuantity())
                               .divide(line.quantity(),
                                       Math.max(2,
                                                line.netUnitPrice()
                                                    .scale()),
                                       RoundingMode.HALF_UP);
      // A negative price is checked by the invoice: only EXTENDED allows it (BR-27).
      var builder = line.toBuilder()
                        .netUnitPrice(price);
      if (line.grossPrice() != null) {
        // Keeps BT-147 = BT-148 - BT-146; a gross price below the new net price is dropped.
        boolean keep = line.grossPrice()
                           .compareTo(price) >= 0;
        builder.grossPrice(keep ?
                           line.grossPrice() :
                           null)
               .priceDiscount(keep ?
                              line.grossPrice()
                                  .subtract(price) :
                              null);
        // The stated parts of the old discount no longer add up to the recalculated one.
        if (line.extended() != null) {
          builder.extended(line.extended()
                               .withoutPriceAdjustments());
        }
      }
      var result = builder.build()
                          .withNetAmount(stated);
      warnings.add(new ReadWarning(ReadWarning.LINE_NET_PRICE,
                                   line.id(),
                                   "Line " +
                                   line.id() +
                                   ": stated net amount " +
                                   stated.toPlainString() +
                                   " is not quantity x net price " +
                                   line.netUnitPrice()
                                       .toPlainString() +
                                   "; net price recalculated as " +
                                   price.toPlainString()));
      return result;
    }
  }

  /**
   * Compares the stated totals with the totals calculated from the model. Strict reading rejects a
   * different total VAT amount (BT-110) or amount due (BT-115); lenient reading keeps the calculated
   * totals and reports the difference. Every other stated total is kept as it is and compared only.
   *
   * @param invoice the invoice read
   * @param stated  the stated totals; their lines are ignored and replaced by the lines of this read
   * @return the invoice with the stated amounts, warnings and deviations collected during this read
   * @throws IllegalArgumentException if a stated total differs in strict reading
   */
  public ReadResult result(Invoice invoice,
                           StatedAmounts stated) {
    String specificationId = invoice.processControl()
                                    .specificationId();
    if (SpecificationGenerations.isEn16931Of2026(specificationId)) {
      lost(ReadWarning.SPECIFICATION_VERSION,
           "The document states " + specificationId + "; " + SpecificationGenerations.NOT_SUPPORTED + ", so it was read with the business terms of EN 16931:2017 and terms new in EN 16931:2026 were not read");
    }
    var totals = invoice.totals();
    check(ReadWarning.TAX_TOTAL,
          "total VAT amount",
          stated.taxAmount(),
          totals.taxAmount());
    check(ReadWarning.PAYABLE_AMOUNT,
          "amount due for payment",
          stated.payableAmount(),
          totals.payableAmount());
    deviate("BT-106",
            null,
            stated.lineTotal(),
            totals.lineTotal());
    deviate("BT-107",
            null,
            stated.allowanceTotal(),
            totals.allowanceTotal());
    deviate("BT-108",
            null,
            stated.chargeTotal(),
            totals.chargeTotal());
    deviate("BT-109",
            null,
            stated.taxExclusiveAmount(),
            totals.taxExclusiveAmount());
    deviate("BT-110",
            null,
            stated.taxAmount(),
            totals.taxAmount());
    deviate("BT-112",
            null,
            stated.taxInclusiveAmount(),
            totals.taxInclusiveAmount());
    deviate("BT-113",
            null,
            stated.paidAmount(),
            totals.paidAmount());
    deviate("BT-114",
            null,
            stated.roundingAmount(),
            totals.roundingAmount());
    deviate("BT-115",
            null,
            stated.payableAmount(),
            totals.payableAmount());
    return new ReadResult(invoice,
                          warnings,
                          stated.withLines(lines),
                          deviations);
  }

  private void deviate(String businessTerm,
                       String lineId,
                       BigDecimal stated,
                       BigDecimal calculated) {
    var deviation = AmountDeviation.of(businessTerm,
                                       lineId,
                                       stated,
                                       calculated);
    if (deviation != null) {
      deviations.add(deviation);
    }
  }

  private void check(String code,
                     String name,
                     BigDecimal stated,
                     BigDecimal calculated) {
    if (stated == null || stated.compareTo(calculated) == 0) {
      return;
    }
    String message = "Stated " + name + " " + stated.toPlainString() + " differs from calculated " + calculated.toPlainString();
    if (mode == ReadMode.STRICT) {
      throw new IllegalArgumentException(message + "; the document may contain content the Paniolo model does not support");
    }
    warnings.add(new ReadWarning(code,
                                 null,
                                 message + "; the calculated amount is kept"));
  }
}
