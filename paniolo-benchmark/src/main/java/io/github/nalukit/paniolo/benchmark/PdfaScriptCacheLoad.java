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
package io.github.nalukit.paniolo.benchmark;

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.PdfaScriptCache;
import io.github.nalukit.paniolo.validation.ValidationMessage;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Load test of the veraPDF script caches (Mustang issue 1158): a fixed pool of long-lived worker
 * threads validates a corpus of PDFs with the PDF/A check in rounds, and after every round the
 * test reports the time, the Metaspace, the loaded and unloaded classes, and the scripts cached
 * by the workers. Rounds after the warmup show whether memory stays bound at one level or keeps
 * growing.
 *
 * <p>Run every strategy in its own JVM, because Metaspace and class counts are JVM-wide:
 *
 * <pre>{@code
 * CP="paniolo-benchmark/target/benchmarks.jar:paniolo-benchmark/target/pdfa-lib/*"
 * java -cp "$CP" io.github.nalukit.paniolo.benchmark.PdfaScriptCacheLoad REUSE 4 3 12 4
 * java -cp "$CP" io.github.nalukit.paniolo.benchmark.PdfaScriptCacheLoad CLEAR_AFTER_CHECK 4 3 12 4
 * java -cp "$CP" io.github.nalukit.paniolo.benchmark.PdfaScriptCacheLoad RELEASE_PER_ROUND 4 3 12 4
 * }</pre>
 *
 * <p>Arguments: strategy ({@code REUSE}, {@code CLEAR_AFTER_CHECK}, or {@code RELEASE_PER_ROUND},
 * which reuses the caches and releases them on every worker at the end of each round), threads,
 * warmup rounds, measured rounds, passes over the corpus per worker and round. Every strategy
 * checks the same documents in the same order and prints a fingerprint of all findings, which must
 * be equal for all strategies.
 */
public final class PdfaScriptCacheLoad {
  private PdfaScriptCacheLoad() {
  }

  /**
   * Runs the load test and prints one line per round and a summary.
   *
   * @param args strategy, threads, warmup rounds, measured rounds, passes per round
   * @throws Exception if a worker fails
   */
  public static void main(String[] args)
      throws Exception {
    String strategy = args.length > 0 ?
                      args[0] :
                      "REUSE";
    int    threads  = intArg(args,
                             1,
                             4);
    int    warmup   = intArg(args,
                             2,
                             3);
    int    rounds   = intArg(args,
                             3,
                             12);
    int    passes   = intArg(args,
                             4,
                             4);
    var cache = strategy.equals("CLEAR_AFTER_CHECK") ?
                PdfaScriptCache.CLEAR_AFTER_CHECK :
                PdfaScriptCache.REUSE;
    boolean releasePerRound = strategy.equals("RELEASE_PER_ROUND");
    if (!List.of("REUSE",
                 "CLEAR_AFTER_CHECK",
                 "RELEASE_PER_ROUND")
             .contains(strategy)) {
      throw new IllegalArgumentException("Unknown strategy " + strategy);
    }
    var paniolo = Paniolo.builder()
                         .pdfaScriptCache(cache)
                         .build();
    var corpus = corpus(paniolo);
    // Compiles the XML rules on the main thread, so the rounds measure the PDF/A check.
    for (var pdf : corpus.values()) {
      BenchmarkData.requirePdfaChecked(paniolo.validate(pdf),
                                       true,
                                       "load corpus");
    }
    Paniolo.releasePdfaScriptCache();

    System.out.printf(java.util.Locale.ROOT, "strategy=%s threads=%d warmup=%d rounds=%d passes=%d documents=%d checksPerRound=%d java=%s%n",
                      strategy,
                      threads,
                      warmup,
                      rounds,
                      passes,
                      corpus.size(),
                      threads * passes * corpus.size(),
                      System.getProperty("java.version"));
    System.out.println("documents: " + String.join(" | ",
                                                   corpus.keySet()));
    System.out.println("phase  round  wall_ms  ms_per_check  metaspace_mb  loaded_classes  unloaded_classes  cached_scripts  script_loaders");
    var    findings = new ArrayList<String>();
    var    measured = new ArrayList<long[]>();
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      for (int round = 1; round <= warmup + rounds; round++) {
        var  jobs  = new ArrayList<Future<long[]>>();
        long start = System.nanoTime();
        for (int t = 0; t < threads; t++) {
          boolean first = round == 1 && t == 0;
          jobs.add(pool.submit(worker(paniolo,
                                      corpus,
                                      passes,
                                      releasePerRound,
                                      first ?
                                      findings :
                                      null)));
        }
        long scripts = 0;
        long loaders = 0;
        for (var job : jobs) {
          long[] counts = job.get();
          scripts += counts[0];
          loaders += counts[1];
        }
        long wall = (System.nanoTime() - start) / 1_000_000;
        System.gc();
        System.gc();
        var    classes   = ManagementFactory.getClassLoadingMXBean();
        long   metaspace = metaspaceBytes();
        String phase     = round <= warmup ?
                           "warmup" :
                           "measure";
        System.out.printf(java.util.Locale.ROOT, "%-6s %6d %8d %13.2f %13.1f %15d %17d %15d %14d%n",
                          phase,
                          round,
                          wall,
                          (double) wall * threads / (threads * passes * corpus.size()),
                          metaspace / 1048576.0,
                          classes.getLoadedClassCount(),
                          classes.getUnloadedClassCount(),
                          scripts,
                          loaders);
        if (round > warmup) {
          measured.add(new long[] { wall,
                                    metaspace,
                                    classes.getLoadedClassCount(),
                                    scripts });
        }
      }
    }
    var first = measured.get(0);
    var last  = measured.get(measured.size() - 1);
    long total = measured.stream()
                         .mapToLong(values -> values[0])
                         .sum();
    System.out.printf(java.util.Locale.ROOT, "summary: mean_wall_ms=%.1f metaspace_first_mb=%.1f metaspace_last_mb=%.1f metaspace_delta_mb=%.2f loaded_first=%d loaded_last=%d loaded_delta=%d cached_scripts_last=%d%n",
                      (double) total / measured.size(),
                      first[1] / 1048576.0,
                      last[1] / 1048576.0,
                      (last[1] - first[1]) / 1048576.0,
                      first[2],
                      last[2],
                      last[2] - first[2],
                      last[3]);
    System.out.println("findings fingerprint: " + Integer.toHexString(findings.hashCode()) + " (" + findings.size() + " documents)");
  }

  /**
   * One worker's round: validates the corpus, optionally releases its caches, and reports the
   * scripts cached for its own thread.
   */
  private static Callable<long[]> worker(Paniolo paniolo,
                                         Map<String, byte[]> corpus,
                                         int passes,
                                         boolean release,
                                         List<String> findings) {
    return () -> {
      for (int pass = 0; pass < passes; pass++) {
        for (var entry : corpus.entrySet()) {
          var result = paniolo.validate(entry.getValue());
          if (findings != null && pass == 0) {
            findings.add(entry.getKey() + " " + result.isValid() + " " + result.messages()
                                                                               .stream()
                                                                               .map(PdfaScriptCacheLoad::finding)
                                                                               .toList());
          }
        }
      }
      if (release) {
        Paniolo.releasePdfaScriptCache();
      }
      return cachedScripts();
    };
  }

  private static String finding(ValidationMessage message) {
    return message.stage() + "/" + message.ruleId() + "/" + message.severity() + "/" + message.message();
  }

  /**
   * Hybrid XRechnung invoices of 1 and 100 lines on PDF/A-3b, the same base document declaring PDF/A-1b,
   * 2b and 3u, the base document without Factur-X data, and a broken PDF.
   */
  private static Map<String, byte[]> corpus(Paniolo paniolo) {
    var    corpus = new LinkedHashMap<String, byte[]>();
    byte[] base   = BenchmarkData.pdfA2b();
    corpus.put("XRechnung PDF 1 line",
               paniolo.toFacturX(BenchmarkData.invoice(1),
                                 base,
                                 FacturXProfile.XRECHNUNG));
    corpus.put("XRechnung PDF 100 lines",
               paniolo.toFacturX(BenchmarkData.invoice(100),
                                 base,
                                 FacturXProfile.XRECHNUNG));
    corpus.put("PDF/A-2b without invoice",
               base);
    corpus.put("PDF/A-1b declared without invoice",
               declare(base,
                       "1",
                       "B"));
    corpus.put("PDF/A-3u declared without invoice",
               declare(base,
                       "3",
                       "U"));
    corpus.put("broken PDF",
               "%PDF-1.7 broken".getBytes(StandardCharsets.US_ASCII));
    return corpus;
  }

  /**
   * Replaces part and conformance in the uncompressed XMP of the base document; the lengths stay
   * equal, so the cross-reference table stays valid.
   */
  private static byte[] declare(byte[] pdf,
                                String part,
                                String conformance) {
    String text     = new String(pdf,
                                 StandardCharsets.ISO_8859_1);
    String replaced = text.replace("<pdfaid:part>2</pdfaid:part>",
                                   "<pdfaid:part>" + part + "</pdfaid:part>")
                          .replace("<pdfaid:conformance>B</pdfaid:conformance>",
                                   "<pdfaid:conformance>" + conformance + "</pdfaid:conformance>");
    if (replaced.equals(text) && !(part.equals("2") && conformance.equals("B"))) {
      throw new IllegalStateException("The XMP of the base document is not readable as text");
    }
    return replaced.getBytes(StandardCharsets.ISO_8859_1);
  }

  /**
   * The scripts veraPDF cached for the calling thread and their distinct class loaders, read by
   * reflection because veraPDF keeps them private.
   */
  private static long[] cachedScripts()
      throws ReflectiveOperationException {
    var              evaluator = Class.forName("org.verapdf.pdfa.validation.validators.JavaScriptEvaluator");
    long             scripts   = 0;
    Set<ClassLoader> loaders   = Collections.newSetFromMap(new IdentityHashMap<>());
    for (String name : List.of("ruleScripts",
                               "variableScripts",
                               "argumentScripts")) {
      var field = evaluator.getDeclaredField(name);
      field.setAccessible(true);
      if (((ThreadLocal<?>) field.get(null)).get() instanceof Map<?, ?> map) {
        scripts += map.size();
        for (Object script : map.values()) {
          loaders.add(script.getClass()
                            .getClassLoader());
        }
      }
    }
    return new long[] { scripts,
                        loaders.size() };
  }

  private static long metaspaceBytes() {
    return ManagementFactory.getMemoryPoolMXBeans()
                            .stream()
                            .filter(pool -> pool.getType() == MemoryType.NON_HEAP && pool.getName()
                                                                                         .equals("Metaspace"))
                            .mapToLong(pool -> pool.getUsage()
                                                   .getUsed())
                            .sum();
  }

  private static int intArg(String[] args,
                            int index,
                            int fallback) {
    return args.length > index ?
           Integer.parseInt(args[index]) :
           fallback;
  }
}
