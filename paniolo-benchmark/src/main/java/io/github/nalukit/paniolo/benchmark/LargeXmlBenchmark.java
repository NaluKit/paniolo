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
import io.github.nalukit.paniolo.xml.ImportResult;
import io.github.nalukit.paniolo.xml.ReadMode;
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
 * Warm paths with large invoices whose lines each state an invoicing period (BG-26): reading and
 * importing the XML, and validating it as bytes and as string, with and without the calculation
 * check. Covers the per-line dates and the copies of large documents that {@link XmlBenchmark}
 * does not reach.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class LargeXmlBenchmark {
  /**
   * The number of invoice lines, each with its own invoicing period.
   */
  @Param({ "100",
           "1000",
           "5000" })
  int lines;

  /**
   * The syntax that is read and validated.
   */
  @Param({ "UBL",
           "CII" })
  InvoiceSyntax syntax;

  private Paniolo paniolo;
  private Paniolo calculating;
  private String  xmlString;
  private byte[]  xml;

  /**
   * Creates the benchmark state; called by JMH.
   */
  public LargeXmlBenchmark() {
  }

  /**
   * Creates the instances and writes the invoice outside the measurement; validating it there
   * compiles the rules and makes sure no call ends early on an invalid document.
   */
  @Setup(Level.Trial)
  public void setUp() {
    paniolo     = Paniolo.create();
    calculating = Paniolo.builder()
                         .calculationCheck(true)
                         .build();
    xmlString   = paniolo.toXml(BenchmarkData.invoice(lines,
                                                      true),
                                syntax);
    xml         = xmlString.getBytes(StandardCharsets.UTF_8);
    String what = syntax + " invoice with " + lines + " lines and line periods";
    BenchmarkData.requireValid(paniolo.validate(xml),
                               what);
    BenchmarkData.requireValid(paniolo.validate(xmlString),
                               what + " as string");
    BenchmarkData.requireValid(calculating.validate(xml),
                               what + " with calculation check");
  }

  /**
   * Reads the invoice from bytes.
   *
   * @return the invoice
   */
  @Benchmark
  public Invoice readBytes() {
    return paniolo.read(xml);
  }

  /**
   * Reads the invoice from a string.
   *
   * @return the invoice
   */
  @Benchmark
  public Invoice readString() {
    return paniolo.read(xmlString);
  }

  /**
   * Imports the invoice, keeping the original XML and the stated amounts.
   *
   * @return the import result
   */
  @Benchmark
  public ImportResult importBytes() {
    return paniolo.importInvoice(xml,
                                 ReadMode.STRICT);
  }

  /**
   * Validates the XML bytes with the compiled rules.
   *
   * @return the validation result
   */
  @Benchmark
  public ValidationResult validateBytes() {
    return paniolo.validate(xml);
  }

  /**
   * Validates the XML string with the compiled rules.
   *
   * @return the validation result
   */
  @Benchmark
  public ValidationResult validateString() {
    return paniolo.validate(xmlString);
  }

  /**
   * Validates the XML bytes and checks the stated amounts against the calculated ones.
   *
   * @return the validation result
   */
  @Benchmark
  public ValidationResult validateBytesWithCalculationCheck() {
    return calculating.validate(xml);
  }
}
