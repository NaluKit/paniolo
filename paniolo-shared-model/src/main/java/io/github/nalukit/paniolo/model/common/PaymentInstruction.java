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

import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Payment instructions (BG-16). Credit transfer (30/58) requires accounts, payment card
 * (48/54/55) requires card information and direct debit (59) requires a mandate. Only an
 * EXTENDED-CTC-FR invoice may name a card next to the credit transfer accounts.
 *
 * @param meansCode             payment means code (BT-81); required
 * @param meansText             payment means text (BT-82), or null
 * @param remittanceInformation remittance information (BT-83), or null
 * @param creditTransfers       credit transfer accounts (BG-17); required for payment means 30 and 58
 * @param paymentCard           payment card information (BG-18); required for payment means 48, 54 and 55;
 *                              next to credit transfer accounts (30/58) only in an EXTENDED-CTC-FR invoice
 * @param directDebit           direct debit information (BG-19); required for payment means 59
 */
public record PaymentInstruction(PaymentMeansCode meansCode, String meansText, String remittanceInformation, List<CreditTransfer> creditTransfers, PaymentCardInformation paymentCard, DirectDebit directDebit) {
  /**
   * Applies the checks of the model and copies the lists.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if the accounts, card and mandate do not fit the payment means
   */
  public PaymentInstruction {
    Objects.requireNonNull(meansCode,
                           "meansCode");
    creditTransfers = List.copyOf(creditTransfers);
    switch (meansCode) {
      // A card next to the account is allowed by the French profile EXTENDED-CTC-FR only, which
      // the invoice checks (see hasCardWithCreditTransfer()).
      case CODE_30, CODE_58 -> requireOnly(!creditTransfers.isEmpty(),
                                           true,
                                           directDebit == null,
                                           "Credit transfer requires at least one account and no direct debit");
      case CODE_48, CODE_54, CODE_55 -> requireOnly(paymentCard != null,
                                                    creditTransfers.isEmpty(),
                                                    directDebit == null,
                                                    "Payment card requires card information and no account or direct debit");
      case CODE_59 -> requireOnly(directDebit != null,
                                  creditTransfers.isEmpty(),
                                  paymentCard == null,
                                  "SEPA direct debit requires a mandate and no account or card");
      default -> requireOnly(true,
                             creditTransfers.isEmpty(),
                             paymentCard == null && directDebit == null,
                             "Payment means " + meansCode.getCode() + " does not take accounts, cards or mandates");
    }
  }

  /**
   * Returns true for a credit transfer (30/58) that names a payment card as well, as the French
   * profile EXTENDED-CTC-FR allows in one payment means block (e.g. its official example UC10);
   * other profiles reject it.
   *
   * @return true if a credit transfer carries payment card information
   */
  public boolean hasCardWithCreditTransfer() {
    return paymentCard != null && !creditTransfers.isEmpty();
  }

  private static void requireOnly(boolean required,
                                  boolean otherAbsent,
                                  boolean thirdAbsent,
                                  String message) {
    if (!required || !otherAbsent || !thirdAbsent) {
      throw new IllegalArgumentException(message);
    }
  }

  /**
   * Starts named construction of a {@link PaymentInstruction} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable payment instruction.
   *
   * <p>Mutable construction state for {@link PaymentInstruction}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private PaymentMeansCode       meansCode;
    private String                 meansText;
    private String                 remittanceInformation;
    private List<CreditTransfer>   creditTransfers = new ArrayList<>();
    private PaymentCardInformation paymentCard;
    private DirectDebit            directDebit;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link PaymentInstruction#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link PaymentInstruction#meansCode() meansCode}.
     *
     * @param meansCode payment means code (BT-81); required
     * @return this builder
     */
    public Builder meansCode(PaymentMeansCode meansCode) {
      this.meansCode = meansCode;
      return this;
    }

    /**
     * Payment means text (BT-82).
     *
     * @param meansText payment means text (BT-82), or null
     * @return this builder
     */
    public Builder meansText(String meansText) {
      this.meansText = meansText;
      return this;
    }

    /**
     * Remittance information (BT-83), e.g. the payment reference.
     *
     * @param remittanceInformation remittance information (BT-83), or null
     * @return this builder
     */
    public Builder remittanceInformation(String remittanceInformation) {
      this.remittanceInformation = remittanceInformation;
      return this;
    }

    /**
     * Sets {@link PaymentInstruction#creditTransfers() creditTransfers}.
     *
     * @param creditTransfers credit transfer accounts (BG-17); required for payment means 30 and 58
     * @return this builder
     */
    public Builder creditTransfers(List<CreditTransfer> creditTransfers) {
      this.creditTransfers = new ArrayList<>(creditTransfers);
      return this;
    }

    /**
     * Adds a value to {@link PaymentInstruction#creditTransfers() creditTransfers}.
     *
     * @param value the credit transfer to add
     * @return this builder
     */
    public Builder addCreditTransfer(CreditTransfer value) {
      this.creditTransfers.add(value);
      return this;
    }

    /**
     * Sets {@link PaymentInstruction#paymentCard() paymentCard}.
     *
     * @param paymentCard payment card information (BG-18); required for payment means 48, 54 and 55;
     *                    next to credit transfer accounts (30/58) only in an EXTENDED-CTC-FR invoice
     * @return this builder
     */
    public Builder paymentCard(PaymentCardInformation paymentCard) {
      this.paymentCard = paymentCard;
      return this;
    }

    /**
     * Sets {@link PaymentInstruction#directDebit() directDebit}.
     *
     * @param directDebit direct debit information (BG-19); required for payment means 59
     * @return this builder
     */
    public Builder directDebit(DirectDebit directDebit) {
      this.directDebit = directDebit;
      return this;
    }

    /**
     * Constructs a new {@link PaymentInstruction} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public PaymentInstruction build() {
      return new PaymentInstruction(meansCode,
                                    meansText,
                                    remittanceInformation,
                                    creditTransfers,
                                    paymentCard,
                                    directDebit);
    }
  }
}
