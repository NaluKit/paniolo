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

import io.github.nalukit.paniolo.model.common.Address;

/**
 * Party information of the Factur-X profile EXTENDED and of Order-X.
 *
 * @param roleCode         UNTDID 3035 party role, or null
 * @param legalAddress     address of the legal organization, or null
 * @param contact          contact with department, type, or fax; replaces {@code Party.contact}, or null
 * @param globalId         global identifier written as {@code ram:GlobalID} in addition to the party
 *                         identifier {@code ram:ID}, e.g. a GLN; or null
 * @param globalIdSchemeId ISO 6523 scheme of the global identifier, required with it
 */
public record PartyExtension(String roleCode, Address legalAddress, ExtendedContact contact, String globalId, String globalIdSchemeId) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public PartyExtension {
    if ((globalId == null) != (globalIdSchemeId == null)) {
      throw new IllegalArgumentException("A global identifier and its scheme must be set together");
    }
    // Readers map a contact with EN 16931 fields only to Party.contact.
    if (contact != null && contact.isPlain()) {
      throw new IllegalArgumentException("Use Party.contact for a contact without department, type, or fax");
    }
  }

  /**
   * Party information without global identifier.
   *
   * @param roleCode     UNTDID 3035 party role, or null
   * @param legalAddress address of the legal organization, or null
   * @param contact      contact with department, type, or fax, or null
   * @throws IllegalArgumentException if the contact uses only EN 16931 fields
   */
  public PartyExtension(String roleCode,
                        Address legalAddress,
                        ExtendedContact contact) {
    this(roleCode,
         legalAddress,
         contact,
         null,
         null);
  }

  /**
   * Party information with the role only.
   *
   * @param roleCode UNTDID 3035 party role
   * @return the party information
   */
  public static PartyExtension of(String roleCode) {
    return new PartyExtension(roleCode,
                              null,
                              null);
  }

  /**
   * Returns true if no information is set.
   *
   * @return true if role, legal address, contact, and global identifier are null
   */
  public boolean isEmpty() {
    return roleCode == null && legalAddress == null && contact == null && globalId == null;
  }

  /**
   * Returns true if the only information is a contact whose contact point is a department instead
   * of a person. CII states it as {@code ram:DepartmentName} of the contact point (BT-41 / BT-56)
   * in every profile, so this is no information of the profile EXTENDED. A person and a department
   * together are two contact points, which CII-SR-465 / CII-SR-466 reject outside EXTENDED.
   *
   * @return true for a contact with department, without person, fax, or type code, and nothing else
   */
  public boolean isDepartmentContactOnly() {
    return roleCode == null && legalAddress == null && globalId == null && contact != null && contact.name() == null && contact.typeCode() == null && contact.fax() == null;
  }
}
