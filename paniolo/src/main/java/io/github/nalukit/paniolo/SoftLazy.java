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
package io.github.nalukit.paniolo;

import java.lang.ref.SoftReference;
import java.util.function.Supplier;

/**
 * Holds one lazily created, expensive value that the garbage collector may reclaim when memory is
 * short. A reclaimed value is created again on the next use, so heap pressure costs time instead
 * of causing an {@link OutOfMemoryError}. Creation is locked per instance, so threads using other
 * instances are not blocked.
 *
 * @param <T> the type of the value
 */
final class SoftLazy<T> {

  private volatile SoftReference<T> reference;

  /**
   * Returns the value, creating it if it was never created or has been reclaimed. A failed
   * creation is not remembered.
   *
   * @param creator creates the value
   * @return the value
   */
  T get(Supplier<T> creator) {
    T result = peek();
    if (result == null) {
      synchronized (this) {
        result = peek();
        if (result == null) {
          result    = creator.get();
          reference = new SoftReference<>(result);
        }
      }
    }
    return result;
  }

  /**
   * Drops the value as the garbage collector would under memory pressure; for tests.
   */
  void evict() {
    reference = null;
  }

  private T peek() {
    var current = reference;
    return current == null ?
           null :
           current.get();
  }

}
