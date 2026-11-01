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
package io.github.nalukit.paniolo.model.order;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Period of an Order-X event, e.g. a requested delivery between two dates, each with an optional
 * time of day. A date without time is written in format 102, a date with time in format 203 (or 204
 * with seconds).
 *
 * @param startDate first day, or null
 * @param startTime time of day of the start, requires startDate; or null
 * @param endDate   last day, or null
 * @param endTime   time of day of the end, requires endDate; or null
 */
public record OrderPeriod(LocalDate startDate, LocalTime startTime, LocalDate endDate, LocalTime endTime) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public OrderPeriod {
    if (startDate == null && endDate == null) {
      throw new IllegalArgumentException("A period needs a start or an end date");
    }
    if (startTime != null && startDate == null || endTime != null && endDate == null) {
      throw new IllegalArgumentException("A time of day requires its date");
    }
    if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("A period must not end before it starts");
    }
  }
}
