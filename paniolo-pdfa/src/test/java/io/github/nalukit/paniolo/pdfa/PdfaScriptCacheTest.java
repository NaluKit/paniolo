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

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.PdfaScriptCache;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The caches of compiled PDF/A rules veraPDF keeps per thread (Mustang issue 1158, veraPDF issue
 * 1616): reused by default, released after every check or on demand, always on the thread that
 * ran the check, and never changing the findings.
 */
class PdfaScriptCacheTest {
  private static final VeraPdfaSupport     PDFA  = new VeraPdfaSupport();
  private static final Map<String, byte[]> INPUT = inputs();

  /**
   * Several PDF/A flavours, valid and invalid documents, and documents veraPDF cannot parse.
   */
  private static Map<String, byte[]> inputs() {
    var paniolo = Paniolo.create();
    var inputs  = new LinkedHashMap<String, byte[]>();
    inputs.put("Factur-X EN 16931, PDF/A-3b, conform",
               paniolo.toFacturX(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                         InvoiceTypeCode.CODE_380)
                                                .build(),
                                 TestPdfs.pdfA2b(),
                                 FacturXProfile.EN16931));
    inputs.put("PDF/A-2b, conform but not part 3",
               TestPdfs.pdfA2b());
    inputs.put("PDF/A-1b declared, violates part 1",
               TestPdfs.pdfA(1,
                             "B"));
    inputs.put("PDF/A-3u, conform",
               TestPdfs.pdfA(3,
                             "U"));
    inputs.put("no PDF/A declaration, checked as PDF/A-3b",
               TestPdfs.plainPdf());
    inputs.put("encrypted, violates part 2",
               TestPdfs.encryptedPdf());
    inputs.put("broken PDF",
               "%PDF-1.7 broken".getBytes(StandardCharsets.US_ASCII));
    return inputs;
  }

  static List<String> inputNames() {
    return List.copyOf(INPUT.keySet());
  }

  /**
   * Runs the task on a new thread whose caches start empty and ends with it.
   */
  private static <T> T onNewThread(Callable<T> task)
      throws Exception {
    try (var executor = Executors.newSingleThreadExecutor()) {
      return executor.submit(task)
                     .get(2,
                          TimeUnit.MINUTES);
    }
  }

  private static ValidationResult check(byte[] pdf,
                                        PdfaScriptCache cache) {
    return PDFA.check(pdf,
                      true,
                      cache);
  }

  /**
   * Fills the caches of the calling thread with the rules of PDF/A-3b and PDF/A-2b.
   */
  private static ScriptCaches.Counts warm() {
    check(INPUT.get("Factur-X EN 16931, PDF/A-3b, conform"),
          PdfaScriptCache.REUSE);
    check(TestPdfs.pdfA2b(),
          PdfaScriptCache.REUSE);
    var counts = ScriptCaches.ofCurrentThread();
    assertTrue(counts.scripts() > 0 && counts.loaders() > 0,
               counts::toString);
    return counts;
  }

  @Test
  void reusesTheCachesOfTheThreadByDefault()
      throws Exception {
    onNewThread(() -> {
      assertTrue(ScriptCaches.ofCurrentThread()
                             .empty());
      byte[] pdf   = INPUT.get("Factur-X EN 16931, PDF/A-3b, conform");
      var    first = check(pdf,
                           PdfaScriptCache.REUSE);
      var    once  = ScriptCaches.ofCurrentThread();
      assertTrue(once.scripts() > 0,
                 once::toString);
      // Repeated checks keep working and compile nothing new.
      for (int i = 0; i < 3; i++) {
        assertEquals(first.messages(),
                     check(pdf,
                           PdfaScriptCache.REUSE).messages());
        assertEquals(once,
                     ScriptCaches.ofCurrentThread());
      }
      // The facade's default is REUSE as well.
      Paniolo.create()
             .validate(pdf);
      assertEquals(once,
                   ScriptCaches.ofCurrentThread());
      return null;
    });
  }

  @ParameterizedTest
  @MethodSource("inputNames")
  void clearsTheCachesAfterEveryCheck(String input)
      throws Exception {
    onNewThread(() -> {
      warm();
      check(INPUT.get(input),
            PdfaScriptCache.CLEAR_AFTER_CHECK);
      assertTrue(ScriptCaches.ofCurrentThread()
                             .empty(),
                 () -> ScriptCaches.ofCurrentThread()
                                   .toString());
      return null;
    });
  }

  /**
   * An unexpected error after veraPDF compiled the rules still releases them.
   */
  @Test
  void clearsTheCachesWhenTheCheckFailsUnexpectedly()
      throws Exception {
    var failing = new VeraPdfaSupport(flavour -> {
      assertFalse(ScriptCaches.ofCurrentThread()
                              .empty());
      throw new IllegalArgumentException("unexpected");
    });
    onNewThread(() -> {
      warm();
      var error = assertThrows(IllegalStateException.class,
                               () -> failing.check(INPUT.get("Factur-X EN 16931, PDF/A-3b, conform"),
                                                   false,
                                                   PdfaScriptCache.CLEAR_AFTER_CHECK));
      assertInstanceOf(IllegalArgumentException.class,
                       error.getCause());
      assertTrue(ScriptCaches.ofCurrentThread()
                             .empty());
      // With REUSE the same failure leaves the caches in place.
      assertThrows(IllegalStateException.class,
                   () -> failing.check(INPUT.get("Factur-X EN 16931, PDF/A-3b, conform"),
                                       false,
                                       PdfaScriptCache.REUSE));
      assertFalse(ScriptCaches.ofCurrentThread()
                              .empty());
      return null;
    });
  }

  @Test
  void releasesTheCachesOfTheCallingThreadOnDemand()
      throws Exception {
    onNewThread(() -> {
      // Safe with empty caches, also repeatedly.
      Paniolo.releasePdfaScriptCache();
      Paniolo.releasePdfaScriptCache();
      PDFA.releaseThreadCaches();
      assertTrue(ScriptCaches.ofCurrentThread()
                             .empty());
      var warm = warm();
      Paniolo.releasePdfaScriptCache();
      assertTrue(ScriptCaches.ofCurrentThread()
                             .empty());
      // Built again when needed.
      assertEquals(warm,
                   warm());
      return null;
    });
  }

  /**
   * The setting of the builder reaches the PDF/A check of the facade.
   */
  @Test
  void theFacadeAppliesTheConfiguredStrategy()
      throws Exception {
    byte[] pdf = INPUT.get("Factur-X EN 16931, PDF/A-3b, conform");
    var clearing = Paniolo.builder()
                          .pdfaScriptCache(PdfaScriptCache.CLEAR_AFTER_CHECK)
                          .build();
    onNewThread(() -> {
      warm();
      var result = clearing.validate(pdf);
      assertTrue(result.isValid(),
                 () -> result.messages()
                             .toString());
      assertTrue(ScriptCaches.ofCurrentThread()
                             .empty());
      Paniolo.create()
             .validate(pdf);
      assertFalse(ScriptCaches.ofCurrentThread()
                              .empty());
      return null;
    });
  }

  /**
   * Four worker threads alive at the same time: each releases its own caches, and releasing on one
   * worker leaves the others untouched.
   */
  @Test
  void releasesOnEveryWorkerAndOnlyThere()
      throws Exception {
    int    workers  = 4;
    byte[] pdf      = INPUT.get("Factur-X EN 16931, PDF/A-3b, conform");
    var    clearing = Paniolo.builder()
                             .pdfaScriptCache(PdfaScriptCache.CLEAR_AFTER_CHECK)
                             .build();
    var    reusing  = Paniolo.create();
    var    barrier  = new CyclicBarrier(workers);
    try (var pool = Executors.newFixedThreadPool(workers)) {
      var jobs = new ArrayList<Future<List<ScriptCaches.Counts>>>();
      for (int i = 0; i < workers; i++) {
        int worker = i;
        jobs.add(pool.submit(() -> {
          var counts = new ArrayList<ScriptCaches.Counts>();
          var thread = Thread.currentThread();
          reusing.validate(pdf);
          counts.add(ScriptCaches.ofCurrentThread());                  // 0: warm
          barrier.await(1,
                        TimeUnit.MINUTES);
          if (worker == 0) {
            Paniolo.releasePdfaScriptCache();
          }
          barrier.await(1,
                        TimeUnit.MINUTES);
          counts.add(ScriptCaches.ofCurrentThread());                  // 1: after worker 0 released
          barrier.await(1,
                        TimeUnit.MINUTES);
          clearing.validate(pdf);
          counts.add(ScriptCaches.ofCurrentThread());                  // 2: after clearing check
          barrier.await(1,
                        TimeUnit.MINUTES);
          assertEquals(thread,
                       Thread.currentThread());
          return counts;
        }));
      }
      for (int i = 0; i < workers; i++) {
        var counts = jobs.get(i)
                         .get(2,
                              TimeUnit.MINUTES);
        assertFalse(counts.get(0)
                          .empty(),
                    counts::toString);
        assertEquals(i == 0,
                     counts.get(1)
                           .empty(),
                     counts::toString);
        assertTrue(counts.get(2)
                         .empty(),
                   counts::toString);
      }
    } catch (ExecutionException e) {
      throw e.getCause() instanceof Exception cause ?
            cause :
            e;
    }
  }

  /**
   * All strategies give the same validity, rule identifiers, severities and messages, and the same
   * statistics of the checks.
   */
  @ParameterizedTest
  @MethodSource("inputNames")
  void theStrategiesGiveTheSameFindings(String input)
      throws Exception {
    byte[] pdf = INPUT.get(input);
    var reused = onNewThread(() -> {
      warm();
      check(pdf,
            PdfaScriptCache.REUSE);
      return check(pdf,
                   PdfaScriptCache.REUSE);
    });
    var cleared = onNewThread(() -> {
      warm();
      return check(pdf,
                   PdfaScriptCache.CLEAR_AFTER_CHECK);
    });
    var rebuilt = onNewThread(() -> {
      warm();
      Paniolo.releasePdfaScriptCache();
      return check(pdf,
                   PdfaScriptCache.REUSE);
    });
    for (var other : List.of(cleared,
                             rebuilt)) {
      assertEquals(reused.isValid(),
                   other.isValid());
      assertEquals(reused.messages(),
                   other.messages());
      assertEquals(reused.statistics(),
                   other.statistics());
    }
  }

  /**
   * The inputs cover conform and violating documents of several flavours and unreadable ones.
   */
  @Test
  void theInputsCoverValidInvalidAndBrokenDocuments()
      throws Exception {
    var expected = new LinkedHashMap<String, String>();
    expected.put("Factur-X EN 16931, PDF/A-3b, conform",
                 "");
    expected.put("PDF/A-2b, conform but not part 3",
                 "PDFA-PART");
    expected.put("PDF/A-1b declared, violates part 1",
                 "PDFA-1B-");
    expected.put("PDF/A-3u, conform",
                 "");
    expected.put("no PDF/A declaration, checked as PDF/A-3b",
                 "PDFA-NOT-DECLARED");
    expected.put("encrypted, violates part 2",
                 "PDFA-2B-6.1.3-2");
    expected.put("broken PDF",
                 "PDFA-PARSE");
    assertEquals(expected.keySet(),
                 INPUT.keySet());
    for (var entry : expected.entrySet()) {
      var rules = onNewThread(() -> check(INPUT.get(entry.getKey()),
                                          PdfaScriptCache.REUSE)).messages()
                                                                 .stream()
                                                                 .map(ValidationMessage::ruleId)
                                                                 .toList();
      if (entry.getValue()
               .isEmpty()) {
        assertEquals(List.of(),
                     rules,
                     entry.getKey());
      } else {
        assertTrue(rules.stream()
                        .anyMatch(rule -> rule.startsWith(entry.getValue())),
                   () -> entry.getKey() + ": " + rules);
      }
    }
  }

  @ParameterizedTest
  @EnumSource(PdfaScriptCache.class)
  void theFacadeGivesTheSameResultWithEveryStrategy(PdfaScriptCache cache)
      throws Exception {
    byte[] pdf = INPUT.get("Factur-X EN 16931, PDF/A-3b, conform");
    var expected = onNewThread(() -> Paniolo.create()
                                            .validate(pdf)
                                            .messages());
    var paniolo = Paniolo.builder()
                         .pdfaScriptCache(cache)
                         .build();
    assertEquals(expected,
                 onNewThread(() -> {
                   paniolo.validate(pdf);
                   return paniolo.validate(pdf)
                                 .messages();
                 }));
  }
}
