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
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Throughput of one shared instance under concurrent validation. Compare with {@code -t 1} to see
 * how it scales; the thread count can be changed with {@code -t}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@Threads(4)
public class ConcurrentValidateBenchmark {
  private Paniolo paniolo;
  private byte[]  ubl;
  private byte[]  cii;

  /**
   * Creates the benchmark state; called by JMH.
   */
  public ConcurrentValidateBenchmark() {
  }

  /**
   * Creates the shared instance, writes an invoice of 100 lines as UBL and CII, and compiles the
   * rules by validating both outside the measurement.
   */
  @Setup(Level.Trial)
  public void setUp() {
    paniolo = Paniolo.create();
    var invoice = BenchmarkData.invoice(100);
    ubl = paniolo.toXml(invoice,
                        InvoiceSyntax.UBL)
                 .getBytes(StandardCharsets.UTF_8);
    cii = paniolo.toXml(invoice,
                        InvoiceSyntax.CII)
                 .getBytes(StandardCharsets.UTF_8);
    BenchmarkData.requireValid(paniolo.validate(ubl),
                               "UBL invoice");
    BenchmarkData.requireValid(paniolo.validate(cii),
                               "CII invoice");
  }

  /**
   * Validates the UBL invoice with the shared instance.
   *
   * @return the validation result, which must be valid
   */
  @Benchmark
  public ValidationResult validateUbl() {
    var result = paniolo.validate(ubl);
    BenchmarkData.requireValid(result, "UBL invoice under load");
    return result;
  }

  /**
   * Validates the CII invoice with the shared instance.
   *
   * @return the validation result, which must be valid
   */
  @Benchmark
  public ValidationResult validateCii() {
    var result = paniolo.validate(cii);
    BenchmarkData.requireValid(result, "CII invoice under load");
    return result;
  }
}
