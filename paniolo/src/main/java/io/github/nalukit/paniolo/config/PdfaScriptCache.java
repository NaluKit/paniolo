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
package io.github.nalukit.paniolo.config;

/**
 * What the PDF/A check of {@code paniolo-pdfa} does with the JavaScript rules veraPDF compiles. Set
 * it with {@code Paniolo.builder().pdfaScriptCache(...)}.
 *
 * <p>veraPDF compiles the test of each PDF/A rule to a class with its own class loader and keeps it
 * in a cache of the thread that ran the check, keyed by the rule text. The cache is bounded by the
 * rules of the checked PDF/A flavours: a second check of the same flavour on the same thread
 * compiles nothing new. But the compiled classes stay in memory (Metaspace) as long as the thread
 * lives, once per thread. Which choice fits depends on the threads that validate:
 * <ul>
 *   <li>A small, fixed pool of long-lived worker threads: {@link #REUSE} (default). Each worker
 *       compiles the rules once and checks faster afterwards; the memory is bound once per worker.</li>
 *   <li>Many or changing long-lived threads, e.g. a large or growing pool, or memory that must be
 *       released between batches: {@link #CLEAR_AFTER_CHECK}, or {@link #REUSE} with
 *       {@code Paniolo.releasePdfaScriptCache()} called by each worker at the end of a batch.</li>
 *   <li>A new thread per check, e.g. virtual threads: the cache is never reused anyway and is
 *       released with the thread; {@link #CLEAR_AFTER_CHECK} releases it already after the check.</li>
 * </ul>
 * The choice never changes the findings of a check.
 */
public enum PdfaScriptCache {
  /**
   * Default: the compiled rules stay in the cache of the thread for later checks on that thread.
   * Fastest for a fixed pool of worker threads; the cache is bound to each thread until it ends or
   * calls {@code Paniolo.releasePdfaScriptCache()}.
   */
  REUSE,
  /**
   * The cache of the thread that ran the check is cleared after every check, also when the check
   * fails. Each check compiles its rules again, so it takes longer; no compiled rules stay bound to
   * the thread between checks.
   */
  CLEAR_AFTER_CHECK
}
