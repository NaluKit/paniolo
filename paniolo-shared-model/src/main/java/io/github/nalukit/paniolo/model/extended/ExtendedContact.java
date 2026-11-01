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

/**
 * Contact of a party in the Factur-X profile EXTENDED, with department, contact type, and fax.
 *
 * @param name       contact person, or null
 * @param department department, or null
 * @param typeCode   UNTDID 3139 contact type, or null
 * @param telephone  telephone number, or null
 * @param fax        fax number, or null
 * @param email      e-mail address, or null
 */
public record ExtendedContact(String name, String department, String typeCode, String telephone, String fax, String email) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public ExtendedContact {
    if (name == null && department == null && telephone == null && fax == null && email == null) {
      throw new IllegalArgumentException("A contact requires a name, department, telephone, fax, or e-mail");
    }
  }

  /**
   * Returns true if the contact uses only the EN 16931 fields name, telephone, and e-mail.
   *
   * @return true if only name, telephone, and e-mail are set
   */
  public boolean isPlain() {
    return department == null && typeCode == null && fax == null;
  }
}
