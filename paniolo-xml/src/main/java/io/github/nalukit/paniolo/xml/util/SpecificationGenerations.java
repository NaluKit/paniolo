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

/**
 * Internal helper for readers, writers, and validation: the generation of EN 16931 a document
 * states in its specification identifier (BT-24). Paniolo supports EN 16931:2017 with XRechnung
 * up to 3.0; EN 16931:2026 with XRechnung 4.0 follows once the official validation artefacts are
 * published (expected spring 2027).
 */
public final class SpecificationGenerations {
  /**
   * Start of the specification identifiers of EN 16931:2026, e.g. of XRechnung 4.0
   * ({@code urn:cen.eu:en16931:2026#compliant#urn:xeinkauf.de:kosit:xrechnung_4.0}).
   */
  public static final String EN16931_2026 = "urn:cen.eu:en16931:2026";

  /**
   * Why a document of EN 16931:2026 cannot be validated or written yet.
   */
  public static final String NOT_SUPPORTED = "EN 16931:2026 (e.g. XRechnung 4.0) is not supported yet; its official validation artefacts are not published";

  private SpecificationGenerations() {
  }

  /**
   * Returns true if the specification identifier states EN 16931:2026.
   *
   * @param specificationId the specification identifier (BT-24), or null
   * @return true for EN 16931:2026
   */
  public static boolean isEn16931Of2026(String specificationId) {
    return specificationId != null && specificationId.startsWith(EN16931_2026);
  }
}
