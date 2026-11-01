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
package io.github.nalukit.paniolo.model.base;

import io.github.nalukit.paniolo.model.util.PanioloStopWatch;

/**
 * TRACE logging helpers for operation names and stopwatch durations.
 * Messages are emitted through {@link System.Logger}; invoice data is not inspected. The static
 * methods keep no mutable per-call state. A stopwatch passed to this class remains owned by its
 * caller and must not be modified concurrently.
 *
 * @see PanioloBase
 */
public class PanioloLogFactory {

  private static final System.Logger LOGGER = System.getLogger(PanioloLogFactory.class.getName());

  /**
   * Creates the helper; all methods are static.
   */
  public PanioloLogFactory() {
  }

  /**
   * Emits the start message of an operation at TRACE level.
   *
   * @param caller operation name to include in the message
   */
  public static void createLogStartMessage(String caller) {
    LOGGER.log(System.Logger.Level.TRACE,
               () -> caller + " called");
  }

  /**
   * Emits the stopwatch identifier and duration of its last completed task at TRACE level.
   * This method does not stop a running task.
   *
   * @param watch stopwatch with at least one completed task
   * @throws IllegalStateException if no task has completed
   */
  public static void createLogStopMessage(PanioloStopWatch watch) {
    StringBuilder sb = new StringBuilder();
    sb.append(watch.getId())
      .append(" called ==> beende Methode --> Verweildauer: ")
      .append(watch.getLastTaskTimeMillis())
      .append(" ms");
    LOGGER.log(System.Logger.Level.TRACE,
               () -> sb.toString());
  }

}
