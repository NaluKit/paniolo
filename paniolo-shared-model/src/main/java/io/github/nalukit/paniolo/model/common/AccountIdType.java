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

/**
 * Kind of the payment account identifier (BT-84) of a {@link CreditTransfer}.
 *
 * <p>EN 16931 has one business term for the identifier. Only the CII syntax distinguishes the kind:
 * an IBAN is written as {@code ram:IBANID}, any other account number as {@code ram:ProprietaryID}.
 * UBL has {@code cbc:ID} for both and does not carry the kind.
 */
public enum AccountIdType {
  /**
   * An IBAN; written as {@code ram:IBANID} in CII. This is the default.
   */
  IBAN,
  /**
   * Another, proprietary account number, e.g. of a bank outside the IBAN countries; written as
   * {@code ram:ProprietaryID} in CII.
   */
  PROPRIETARY
}
