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
 * Base class for operations that emit TRACE messages with their execution time.
 * Subclasses start a per-call {@link PanioloStopWatch} with {@link #logStart(String)} and finish it
 * with {@link #logStop(PanioloStopWatch)}. No timing state is kept in the base instance, so each
 * concurrent operation must use its own stopwatch. Logging uses {@link System.Logger} and does
 * not require a logging framework.
 */
public abstract class PanioloBase {

  /**
   * Creates a base instance without starting a timing task.
   */
  public PanioloBase() {
  }

  /**
   * Logs the start of an unnamed operation and starts its stopwatch.
   *
   * @return a new, running stopwatch owned by this call
   */
  protected PanioloStopWatch logStart() {
    return this.logStart("");
  }

  /**
   * Logs the start of an operation and starts a stopwatch carrying its name.
   *
   * @param caller operation name included in TRACE messages
   * @return a new, running stopwatch owned by this call
   */
  protected PanioloStopWatch logStart(String caller) {
    PanioloLogFactory.createLogStartMessage(caller);
    return PanioloStopWatch.start(caller);
  }

  /**
   * Stops a running stopwatch, then logs its last completed task duration.
   *
   * @param stopWatch stopwatch of the operation; must have a running or completed task
   * @throws IllegalStateException if the stopwatch has no task to report
   */
  protected void logStop(PanioloStopWatch stopWatch) {
    if (stopWatch.isRunning()) {
      stopWatch.stop();
    }
    PanioloLogFactory.createLogStopMessage(stopWatch);
  }

}
