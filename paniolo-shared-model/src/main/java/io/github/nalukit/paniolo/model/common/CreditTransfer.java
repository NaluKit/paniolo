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

/**
 * Credit transfer account (BG-17).
 *
 * <p>The identifier (BT-84) is an IBAN or, with {@link AccountIdType#PROPRIETARY}, another account
 * number. The component keeps its name {@code iban} for compatibility; read {@link #idType()} to
 * learn what it holds. The constructor without kind and {@link Builder#iban(String)} create an IBAN
 * account, {@link #proprietary(String, String, String)} and {@link Builder#proprietaryId(String)} a
 * proprietary one.
 *
 * @param iban        payment account identifier (BT-84), an IBAN or, by {@code idType}, another
 *                    account number; must not be blank
 * @param accountName payment account name (BT-85), or null
 * @param bic         payment service provider identifier (BT-86), usually a BIC; or null
 * @param idType      kind of the identifier; null means {@link AccountIdType#IBAN}, which is also
 *                    the value of stored documents without a kind
 */
public record CreditTransfer(String iban, String accountName, String bic, AccountIdType idType) {
  /**
   * Applies the checks of the model; a missing kind becomes {@link AccountIdType#IBAN}.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public CreditTransfer {
    ModelChecks.required(iban,
                         "iban");
    if (idType == null) {
      idType = AccountIdType.IBAN;
    }
  }

  /**
   * Creates an account with an IBAN, the same as with kind {@link AccountIdType#IBAN}.
   *
   * @param iban        payment account identifier (BT-84); must not be blank
   * @param accountName payment account name (BT-85), or null
   * @param bic         payment service provider identifier (BT-86), or null
   * @throws IllegalArgumentException if the identifier is blank
   */
  public CreditTransfer(String iban,
                        String accountName,
                        String bic) {
    this(iban,
         accountName,
         bic,
         AccountIdType.IBAN);
  }

  /**
   * Creates an account with a proprietary account number, which is no IBAN and written as
   * {@code ram:ProprietaryID} in CII.
   *
   * @param accountId   payment account identifier (BT-84), e.g. {@code EXAMPLE-ACCOUNT}; must not
   *                    be blank
   * @param accountName payment account name (BT-85), or null
   * @param bic         payment service provider identifier (BT-86), or null
   * @return the account with kind {@link AccountIdType#PROPRIETARY}
   * @throws IllegalArgumentException if the identifier is blank
   */
  public static CreditTransfer proprietary(String accountId,
                                           String accountName,
                                           String bic) {
    return new CreditTransfer(accountId,
                              accountName,
                              bic,
                              AccountIdType.PROPRIETARY);
  }

  /**
   * Whether the identifier is a proprietary account number and no IBAN.
   *
   * @return true for kind {@link AccountIdType#PROPRIETARY}
   */
  public boolean isProprietary() {
    return idType == AccountIdType.PROPRIETARY;
  }

  /**
   * Starts named construction of a {@link CreditTransfer} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable credit transfer account.
   *
   * <p>Mutable construction state for {@link CreditTransfer}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String iban;
    private String proprietaryId;
    private String accountName;
    private String bic;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link CreditTransfer#create()}.
     */
    public Builder() {
    }

    /**
     * Payment account identifier (BT-84) that is an IBAN. Use either this or
     * {@link #proprietaryId(String)}.
     *
     * @param iban payment account identifier (BT-84), an IBAN; must not be blank
     * @return this builder
     */
    public Builder iban(String iban) {
      this.iban = iban;
      return this;
    }

    /**
     * Payment account identifier (BT-84) that is another account number and no IBAN, e.g.
     * {@code EXAMPLE-ACCOUNT}. Use either this or {@link #iban(String)}.
     *
     * @param proprietaryId proprietary account number; must not be blank
     * @return this builder
     */
    public Builder proprietaryId(String proprietaryId) {
      this.proprietaryId = proprietaryId;
      return this;
    }

    /**
     * Sets {@link CreditTransfer#accountName() accountName}.
     *
     * @param accountName payment account name (BT-85), or null
     * @return this builder
     */
    public Builder accountName(String accountName) {
      this.accountName = accountName;
      return this;
    }

    /**
     * Sets {@link CreditTransfer#bic() bic}.
     *
     * @param bic payment service provider identifier (BT-86), usually a BIC; or null
     * @return this builder
     */
    public Builder bic(String bic) {
      this.bic = bic;
      return this;
    }

    /**
     * Constructs a new {@link CreditTransfer} from the current values.
     *
     * @return the constructed value after the model checks succeed
     * @throws IllegalArgumentException if both an IBAN and a proprietary identifier are set, or
     *                                  neither
     */
    public CreditTransfer build() {
      if (iban != null && proprietaryId != null) {
        throw new IllegalArgumentException("A credit transfer account has either an iban or a proprietaryId (BT-84), not both");
      }
      return proprietaryId == null ?
             new CreditTransfer(iban,
                                accountName,
                                bic,
                                AccountIdType.IBAN) :
             new CreditTransfer(proprietaryId,
                                accountName,
                                bic,
                                AccountIdType.PROPRIETARY);
    }
  }
}
