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
package io.github.nalukit.paniolo.validation;

/**
 * Original Schematron flags are mapped conservatively; unknown flags are errors.
 */
public enum Severity {
  /**
   * Information; does not make the document invalid.
   */
  INFO,
  /**
   * Warning; does not make the document invalid.
   */
  WARNING,
  /**
   * Error; the document is invalid.
   */
  ERROR,
  /**
   * Fatal error; the document is invalid.
   */
  FATAL;

  /**
   * Maps a Schematron flag, ignoring case: {@code info} and {@code information} to INFO,
   * {@code warn} and {@code warning} to WARNING, {@code fatal} to FATAL, all others to ERROR.
   *
   * @param flag the flag of the failed assertion or report
   * @return the severity
   */
  public static Severity fromFlag(String flag) {
    return switch (flag.toLowerCase(java.util.Locale.ROOT)) {
      case "info", "information" -> INFO;
      case "warn", "warning" -> WARNING;
      case "fatal" -> FATAL;
      default -> ERROR;
    };
  }
}
