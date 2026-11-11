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
package io.github.nalukit.paniolo.pdfa;

import org.verapdf.pdfa.validation.validators.JavaScriptEvaluator;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the JavaScript caches veraPDF keeps for the <em>calling</em> thread, for tests only.
 */
final class ScriptCaches {
  private static final List<String> FIELDS = List.of("ruleScripts",
                                                     "variableScripts",
                                                     "argumentScripts");

  private ScriptCaches() {
  }

  /**
   * The compiled scripts cached for the calling thread and their distinct class loaders.
   *
   * @param scripts number of cached scripts
   * @param loaders number of distinct class loaders the scripts reference
   */
  record Counts(int scripts, int loaders) {
    boolean empty() {
      return scripts == 0 && loaders == 0;
    }
  }

  static Counts ofCurrentThread() {
    int                   scripts = 0;
    Set<ClassLoader>      loaders = Collections.newSetFromMap(new IdentityHashMap<>());
    for (String name : FIELDS) {
      try {
        var field = JavaScriptEvaluator.class.getDeclaredField(name);
        field.setAccessible(true);
        Object value = ((ThreadLocal<?>) field.get(null)).get();
        if (value instanceof Map<?, ?> map) {
          scripts += map.size();
          for (Object script : map.values()) {
            loaders.add(script.getClass()
                              .getClassLoader());
          }
        }
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("veraPDF JavaScriptEvaluator changed: " + name,
                                        e);
      }
    }
    return new Counts(scripts,
                      loaders.size());
  }
}
