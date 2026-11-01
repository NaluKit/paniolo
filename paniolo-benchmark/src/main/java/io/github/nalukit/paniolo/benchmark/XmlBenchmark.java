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
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
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
 * Warm XML paths with compiled rules: writing, reading and validating invoices of different size.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class XmlBenchmark {
  /**
   * The number of invoice lines.
   */
  @Param({ "1",
           "100",
           "1000" })
  int lines;

  /**
   * The syntax that is written, read and validated.
   */
  @Param({ "UBL",
           "CII" })
  InvoiceSyntax syntax;

  private Paniolo paniolo;
  private Invoice invoice;
  private byte[]  xml;

  /**
   * Creates the benchmark state; called by JMH.
   */
  public XmlBenchmark() {
  }

  /**
   * Creates the instance and the invoice, writes it, and compiles the rules by validating it
   * outside the measurement.
   */
  @Setup(Level.Trial)
  public void setUp() {
    paniolo = Paniolo.create();
    invoice = BenchmarkData.invoice(lines);
    xml     = paniolo.toXml(invoice,
                            syntax)
                     .getBytes(StandardCharsets.UTF_8);
    // Compiles the rules outside the measurement.
    BenchmarkData.requireValid(paniolo.validate(xml),
                               syntax + " invoice with " + lines + " lines");
  }

  /**
   * Writes the invoice in the syntax of this run.
   *
   * @return the invoice XML
   */
  @Benchmark
  public String toXml() {
    return paniolo.toXml(invoice,
                         syntax);
  }

  /**
   * Reads the written invoice XML.
   *
   * @return the invoice
   */
  @Benchmark
  public Invoice read() {
    return paniolo.read(xml);
  }

  /**
   * Validates the written invoice XML with the compiled rules.
   *
   * @return the validation result
   */
  @Benchmark
  public ValidationResult validate() {
    return paniolo.validate(xml);
  }
}
