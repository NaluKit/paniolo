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
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * The first validation in a fresh JVM: extracting the rules, compiling XSD and Schematron, and
 * running an unwarmed JIT. Each fork measures one call.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(5)
public class ColdStartBenchmark {
  /**
   * The syntax of the validated invoice.
   */
  @Param({ "UBL",
           "CII" })
  InvoiceSyntax syntax;

  private byte[] xml;

  /**
   * Creates the benchmark state; called by JMH.
   */
  public ColdStartBenchmark() {
  }

  /**
   * Writes the invoice of ten lines that the measured call validates.
   */
  @Setup
  public void setUp() {
    // Written with a separate instance; its writer does not compile any rules.
    xml = Paniolo.create()
                 .toXml(BenchmarkData.invoice(10),
                        syntax)
                 .getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Validates with a new instance, so the rules are extracted and compiled in the measurement.
   *
   * @return the validation result, which must be valid
   */
  @Benchmark
  public ValidationResult firstValidate() {
    var result = Paniolo.create().validate(xml);
    BenchmarkData.requireValid(result, syntax + " cold-start invoice");
    return result;
  }
}
