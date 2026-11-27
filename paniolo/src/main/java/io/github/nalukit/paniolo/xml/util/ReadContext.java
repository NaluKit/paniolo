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
import io.github.nalukit.paniolo.model.common.AccountIdType;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
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
  private       boolean                  negativePrices;

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
   * Whether the profile of the document allows a negative item net price, which only the Factur-X
   * profiles EXTENDED and EXTENDED-CTC-FR do (no BR-27). If not, lenient reading does not recalculate
   * a net price that comes out negative from a stated line amount, but keeps the message that the
   * stated amount differs from quantity x net price. Not allowed by default.
   *
   * @param allowed true if the profile allows negative item net prices
   */
  public void negativePrices(boolean allowed) {
    this.negativePrices = allowed;
  }

  /**
   * Runs a read and reports a required value the document lacks as an
   * {@link IllegalArgumentException} that names the value, instead of the
   * {@link NullPointerException} with which the model rejects it, e.g. a VAT breakdown without
   * category or an allowance without amount. Other exceptions pass unchanged.
   *
   * @param read the read
   * @return the result of the read
   * @throws IllegalArgumentException if the document lacks a value the model requires
   */
  public static ReadResult requiredValues(java.util.function.Supplier<ReadResult> read) {
    try {
      return read.get();
    } catch (NullPointerException e) {
      var origin = e.getStackTrace().length == 0 ?
                   null :
                   e.getStackTrace()[0].getClassName();
      if (e.getMessage() == null || origin == null || !origin.equals(Objects.class.getName()) && !origin.startsWith("io.github.nalukit.paniolo.model.")) {
        throw e;
      }
      throw new IllegalArgumentException("The document lacks a value the invoice model requires: " + e.getMessage(),
                                         e);
    }
  }

  /**
   * Reads an invoicing period (BG-14, BG-26). A date that is no calendar date or an end date before
   * the start date is rejected by strict reading; lenient reading drops the period and reports
   * {@link ReadWarning#PERIOD}.
   *
   * @param period the period element; required
   * @param iso    true for ISO dates of UBL ({@code cbc:StartDate}, {@code cbc:EndDate}), false for
   *               the CII date time strings
   * @param lineId the line of a line period, or null for the document level
   * @return the period, or null if lenient reading dropped it
   * @throws IllegalArgumentException in strict reading if the period is invalid
   */
  public InvoicePeriod period(XmlElement period,
                              boolean iso,
                              String lineId) {
    try {
      return iso ?
             new InvoicePeriod(period.isoDate("cbc:StartDate"),
                               period.isoDate("cbc:EndDate")) :
             new InvoicePeriod(period.basicDate("ram:StartDateTime/udt:DateTimeString"),
                               period.basicDate("ram:EndDateTime/udt:DateTimeString"));
    } catch (IllegalArgumentException e) {
      if (mode == ReadMode.STRICT) {
        throw e;
      }
      warnings.add(new ReadWarning(ReadWarning.PERIOD,
                                   lineId,
                                   "Invoicing period at " + period.location() + " not read: " + e.getMessage()));
      return null;
    }
  }

  /**
   * Handles a due date (BT-9) that the document states twice with different dates: strict reading
   * rejects the document, lenient reading keeps the date of the stronger source and reports the other.
   *
   * @param location   the element the dates were read from
   * @param keptSource what the kept date is, e.g. {@code PaymentDueDate}
   * @param kept       the kept date
   * @param otherSource what the dropped date is
   * @param other      the dropped date
   * @throws IllegalArgumentException in strict reading
   */
  public void dueDateConflict(XmlElement location,
                              String keptSource,
                              java.time.LocalDate kept,
                              String otherSource,
                              java.time.LocalDate other) {
    String message = "Due date at " + location.location() + ": " + otherSource + " " + other +
                     " differs from " + keptSource + " " + kept;
    if (mode == ReadMode.STRICT) {
      throw new IllegalArgumentException(message);
    }
    warnings.add(new ReadWarning(ReadWarning.DUE_DATE,
                                 null,
                                 message +"; " + keptSource + " is kept"));
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
   * Returns the payment means code (BT-81) of a payment means block. A block without code, which
   * ZUGFeRD 1.0 allows, is read as {@code 1} (instrument not defined) and reported as
   * {@link ReadWarning#PAYMENT_MEANS}; no other code is guessed from the accounts. Where the syntax
   * requires the code, strict reading rejects the block instead.
   *
   * @param code     the stated code, or null
   * @param optional true if the syntax of the document allows a block without code
   * @param at       the payment means block, for the location in messages
   * @return the payment means code
   * @throws IllegalArgumentException if the code is unknown, or missing in strict reading where it
   *                                  is required
   */
  public PaymentMeansCode paymentMeansCode(String code,
                                           boolean optional,
                                           XmlElement at) {
    if (code != null && !code.isBlank()) {
      return XmlElement.code(code,
                             PaymentMeansCode::of,
                             "payment means code");
    }
    String message = "Payment means at " + at.location() + " states no payment means code (BT-81)";
    if (!optional && mode == ReadMode.STRICT) {
      throw new IllegalArgumentException(message);
    }
    warnings.add(new ReadWarning(ReadWarning.PAYMENT_MEANS,
                                 null,
                                 message + "; read as 1 (instrument not defined)"));
    return PaymentMeansCode.CODE_1;
  }

  /**
   * Reads the payee account (BG-17) of a CII payment means block: {@code ram:IBANID} is an IBAN,
   * {@code ram:ProprietaryID} another account number, and the model keeps the kind. The model has
   * one identifier (BT-84) per account, so an account with both elements is reported: strict
   * reading rejects it, lenient reading keeps the IBAN and reports the other identifier as
   * {@link ReadWarning#IDENTIFIER}.
   *
   * @param account the {@code ram:PayeePartyCreditorFinancialAccount} element
   * @param bic     the BIC (BT-86) of the block, or null
   * @return the account
   * @throws IllegalArgumentException in strict reading if both identifiers are stated, or if the
   *                                  account states none
   */
  public CreditTransfer creditTransfer(XmlElement account,
                                       String bic) {
    String iban        = account.text("ram:IBANID");
    String proprietary = account.text("ram:ProprietaryID");
    if (iban != null && proprietary != null) {
      String message = "Payment account states IBANID " + iban + " and ProprietaryID " + proprietary + "; the model keeps one payment account identifier (BT-84) per account, the IBAN is kept";
      if (mode == ReadMode.STRICT) {
        throw new IllegalArgumentException(message);
      }
      lost(ReadWarning.IDENTIFIER,
           message);
    }
    String name = account.text("ram:AccountName");
    return iban == null ?
           new CreditTransfer(proprietary,
                              name,
                              bic,
                              AccountIdType.PROPRIETARY) :
           new CreditTransfer(iban,
                              name,
                              bic,
                              AccountIdType.IBAN);
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
   * Handles a tax of a type the model does not keep, i.e. other than VAT and the insurance tax
   * {@code AAM} of Factur-X EXTENDED: strict reading rejects the document, lenient reading reports the tax without reading
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
                     ") is not supported; the model keeps VAT and the insurance tax AAM only";
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

  /**
   * Returns the VAT category of a VAT block. Outside-scope VAT (O) keeps no rate. The categories Z,
   * E, AE, K, and G have no rate other than zero in the model: a stated other rate, which EN 16931
   * forbids for lines, allowances, and charges (e.g. BR-E-5) but not for the VAT breakdown, or a
   * missing rate is rejected by strict reading; lenient reading keeps rate zero and reports
   * {@link ReadWarning#TAX_RATE}. A missing rate of the categories S, L, and M is always rejected,
   * as no rate can be derived.
   *
   * @param code                the VAT category code; required
   * @param rate                the stated rate, or null
   * @param exemptionReason     the stated exemption reason, or null
   * @param exemptionReasonCode the stated exemption reason code, or null
   * @param lineId              the line the block belongs to, or null
   * @param at                  the VAT block, for the location in messages
   * @return the VAT category
   * @throws IllegalArgumentException if the rate does not fit the category and cannot be kept
   */
  public TaxCategory taxCategory(DutyTaxFeeCategory code,
                                 BigDecimal rate,
                                 String exemptionReason,
                                 String exemptionReasonCode,
                                 String lineId,
                                 XmlElement at) {
    Objects.requireNonNull(code,
                           "code");
    if (code == DutyTaxFeeCategory.O) {
      // Outside-scope breakdowns state a zero rate for XRechnung; the model keeps no rate.
      rate = null;
    } else if (rate == null && (code == DutyTaxFeeCategory.S || code == DutyTaxFeeCategory.L || code == DutyTaxFeeCategory.M)) {
      throw new IllegalArgumentException("VAT category " + code.getCode() + " at " + at.location() + " states no VAT rate (BT-96, BT-102, BT-119, BT-152)");
    } else if (code.isAllowanceRelated() && code != DutyTaxFeeCategory.S && code != DutyTaxFeeCategory.L && code != DutyTaxFeeCategory.M && (rate == null || rate.signum() != 0)) {
      String message = "VAT category " + code.getCode() + " at " + at.location() + (rate == null ?
                                                                                     " states no VAT rate" :
                                                                                     " states the VAT rate " + rate.toPlainString()) + "; the category has no rate other than zero";
      if (mode == ReadMode.STRICT) {
        throw new IllegalArgumentException(message);
      }
      warnings.add(new ReadWarning(ReadWarning.TAX_RATE,
                                   lineId,
                                   message + ", rate 0 kept"));
      rate = BigDecimal.ZERO;
    } else if (code == DutyTaxFeeCategory.S && rate != null && rate.signum() == 0) {
      throw new IllegalArgumentException("VAT category S at " + at.location() + " states the VAT rate 0; standard VAT requires a positive rate");
    }
    return new TaxCategory(code,
                           rate,
                           exemptionReason,
                           exemptionReasonCode);
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
    lost(code,
         null,
         message);
  }

  /**
   * Reports a value of a line of the document that the model does not keep, or that it takes from
   * somewhere else.
   *
   * @param code    the warning code, e.g. {@link ReadWarning#TAX_CATEGORY}
   * @param lineId  the line it belongs to, or null for the document level
   * @param message the value and what happened to it
   */
  public void lost(String code,
                   String lineId,
                   String message) {
    warnings.add(new ReadWarning(code,
                                 lineId,
                                 message));
  }

  /**
   * Returns the gross price (BT-148) of a line. A price discount (BT-147) without a gross price, as
   * UBL allows it, is no value the model may keep alone: the stated price is the net price, so lenient
   * reading derives the gross price as net price plus discount and reports it; strict reading rejects
   * the line. Without net price nothing can be derived.
   *
   * @param lineId   the line, or null
   * @param net      the net price (BT-146), or null
   * @param gross    the stated gross price, or null
   * @param discount the stated price discount, or null
   * @return the gross price, or null if the line states neither
   * @throws IllegalArgumentException if a discount without gross price cannot be completed
   */
  public BigDecimal grossPrice(String lineId,
                               BigDecimal net,
                               BigDecimal gross,
                               BigDecimal discount) {
    if (gross != null || discount == null || discount.signum() == 0) {
      return gross;
    }
    if (mode == ReadMode.STRICT || net == null) {
      throw new IllegalArgumentException("Line " + lineId + ": priceDiscount (BT-147) " + discount.toPlainString() + " requires grossPrice (BT-148); the document states a price discount without the gross price" + (net == null ?
                                                                                                                                                                                                                       " and without a net price" :
                                                                                                                                                                                                                       ", so it cannot be derived in strict reading"));
    }
    BigDecimal derived = net.add(discount);
    warnings.add(new ReadWarning(ReadWarning.GROSS_PRICE,
                                 lineId,
                                 "Line " + lineId + ": price discount (BT-147) " + discount.toPlainString() + " without gross price (BT-148); gross price derived as net price " + net.toPlainString() + " + discount = " + derived.toPlainString()));
    return derived;
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
    // Also a stated amount that the rounding of the net price still explains differs from
    // quantity x net price; the deviation shows what the document states and what the price gives.
    deviate("BT-131",
            line.id(),
            statedNetAmount,
            result.calculatedNetAmount() != null ?
            result.calculatedNetAmount() :
            result.netAmount());
    deviate("BT-129",
            line.id(),
            line.quantity(),
            result.quantity());
    return result;
  }

  private InvoiceLine keepNetAmount(InvoiceLine line,
                                    BigDecimal statedNetAmount) {
    try {
      return line.withNetAmount(statedNetAmount);
    } catch (IllegalArgumentException e) {
      // A line without net price takes its amount from its sub-lines; there is no price to recalculate.
      if (mode == ReadMode.LENIENT_KEEP_LINES) {
        throw new IllegalArgumentException(e.getMessage() + "; ReadMode.LENIENT_KEEP_LINES does not change the net price or the quantity of a line");
      }
      if (mode == ReadMode.STRICT ||
          line.netUnitPrice() == null ||
          line.quantity() == null ||
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
      // Only EXTENDED allows a negative price (BR-27); elsewhere the stated amount is what differs.
      if (price.signum() < 0 && !negativePrices) {
        return negatedQuantity(line,
                               stated,
                               e);
      }
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
   * A stated amount with the opposite sign of quantity x net price, e.g. a return stated with a
   * positive quantity and price, would need a negative net price, which the profile forbids (BR-27).
   * The sign belongs to the quantity then, as credit lines with a negative quantity show: the line
   * keeps the price and takes the negated quantity if that explains the stated amount.
   */
  private InvoiceLine negatedQuantity(InvoiceLine line,
                                      BigDecimal stated,
                                      IllegalArgumentException original) {
    try {
      var result = line.toBuilder()
                       .quantity(line.quantity()
                                     .negate())
                       .build()
                       .withNetAmount(stated);
      warnings.add(new ReadWarning(ReadWarning.LINE_QUANTITY,
                                   line.id(),
                                   "Line " +
                                   line.id() +
                                   ": stated net amount " +
                                   stated.toPlainString() +
                                   " has the opposite sign of quantity " +
                                   line.quantity()
                                       .toPlainString() +
                                   " x net price " +
                                   line.netUnitPrice()
                                       .toPlainString() +
                                   "; quantity (BT-129) read as " +
                                   result.quantity()
                                         .toPlainString()));
      return result;
    } catch (IllegalArgumentException ignored) {
      throw original;
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
    checkAllowanceChargeTotal("allowances",
                              "BT-107",
                              stated.allowanceTotal(),
                              totals.allowanceTotal());
    checkAllowanceChargeTotal("charges",
                              "BT-108",
                              stated.chargeTotal(),
                              totals.chargeTotal());
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

  /**
   * Reports a stated total of allowances or charges that the document-level allowances and charges
   * read do not explain, in both modes: it changes no stated amount of the model by itself, but
   * the totals that follow from it differ from the document.
   */
  private void checkAllowanceChargeTotal(String name,
                                         String businessTerm,
                                         BigDecimal stated,
                                         BigDecimal calculated) {
    if (stated == null || stated.compareTo(calculated) == 0) {
      return;
    }
    warnings.add(new ReadWarning(ReadWarning.ALLOWANCE_CHARGE_TOTAL,
                                 null,
                                 "The document states a total of " +
                                 name +
                                 " (" +
                                 businessTerm +
                                 ") of " +
                                 stated.toPlainString() +
                                 ", but the " +
                                 name +
                                 " it lists amount to " +
                                 calculated.toPlainString() +
                                 "; the model keeps the listed ones, so the totals of the invoice differ from the document"));
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
