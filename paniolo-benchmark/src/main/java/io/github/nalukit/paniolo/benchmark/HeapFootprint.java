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
import io.github.nalukit.paniolo.config.InvoiceSyntax;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;

/**
 * Retained heap of a Paniolo instance with compiled rules, and the bytes allocated per call.
 * JMH measures time and, with {@code -prof gc}, allocation rates, but not retained memory.
 *
 * <pre>{@code
 * java -cp paniolo-benchmark/target/benchmarks.jar io.github.nalukit.paniolo.benchmark.HeapFootprint
 * }</pre>
 */
public final class HeapFootprint {
  private static final int CALLS = 50;

  private HeapFootprint() {
  }

  /**
   * Prints the retained heap of the compiled rules and the bytes allocated per warm call.
   *
   * @param args not used
   */
  public static void main(String[] args) {
    var  invoice = BenchmarkData.invoice(100);
    long base    = usedHeap();

    var paniolo = Paniolo.create();
    byte[] ubl = paniolo.toXml(invoice,
                               InvoiceSyntax.UBL)
                        .getBytes(StandardCharsets.UTF_8);
    byte[] cii = paniolo.toXml(invoice,
                               InvoiceSyntax.CII)
                        .getBytes(StandardCharsets.UTF_8);
    byte[] pdf = paniolo.toFacturX(invoice,
                                   BenchmarkData.pdfA2b(),
                                   FacturXProfile.XRECHNUNG);
    long created = usedHeap();

    BenchmarkData.requireValid(paniolo.validate(ubl),
                               "UBL invoice");
    long withUbl = usedHeap();
    BenchmarkData.requireValid(paniolo.validate(cii),
                               "CII invoice");
    long withCii = usedHeap();

    System.out.printf("Instance and test documents:        %8.1f MB%n",
                      mb(created - base));
    System.out.printf("Compiled UBL Invoice rules:         %8.1f MB%n",
                      mb(withUbl - created));
    System.out.printf("Compiled CII rules:                 %8.1f MB%n",
                      mb(withCii - withUbl));
    System.out.printf("Retained by the instance in total:  %8.1f MB%n",
                      mb(withCii - base));
    System.out.println();
    System.out.printf("Allocated per call (100 lines, average of %d warm calls):%n",
                      CALLS);
    report("toXml UBL",
           () -> paniolo.toXml(invoice,
                               InvoiceSyntax.UBL));
    report("read UBL",
           () -> paniolo.read(ubl));
    report("validate UBL",
           () -> paniolo.validate(ubl));
    report("validate CII",
           () -> paniolo.validate(cii));
    report("read Factur-X",
           () -> paniolo.read(pdf));
    report("validate Factur-X",
           () -> paniolo.validate(pdf));
    // Keeps the instance reachable until all measurements are done.
    System.out.println(paniolo.getClass()
                              .getSimpleName() + " done");
  }

  private static void report(String name,
                             Runnable call) {
    var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    for (int i = 0; i < CALLS; i++) {
      call.run();
    }
    long before = threads.getCurrentThreadAllocatedBytes();
    for (int i = 0; i < CALLS; i++) {
      call.run();
    }
    long allocated = (threads.getCurrentThreadAllocatedBytes() - before) / CALLS;
    System.out.printf("  %-20s %8.2f MB%n",
                      name,
                      mb(allocated));
  }

  private static long usedHeap() {
    var runtime = Runtime.getRuntime();
    for (int i = 0; i < 3; i++) {
      System.gc();
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread()
              .interrupt();
      }
    }
    return runtime.totalMemory() - runtime.freeMemory();
  }

  private static double mb(long bytes) {
    return bytes / (1024.0 * 1024.0);
  }
}
