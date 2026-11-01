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

import java.util.concurrent.TimeUnit;

/**
 * Warm Factur-X paths (PDFBox): embedding, extracting, reading and validating hybrid invoices.
 * PDF/A conformance is not checked, because paniolo-pdfa is not on the class path.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class FacturXBenchmark {
  /**
   * The number of invoice lines.
   */
  @Param({ "1",
           "100" })
  int lines;

  private Paniolo paniolo;
  private Invoice invoice;
  private byte[]  pdfA;
  private byte[]  facturX;

  /**
   * Creates the benchmark state; called by JMH.
   */
  public FacturXBenchmark() {
  }

  /**
   * Creates the instance, the invoice and the PDF/A-2b document, and the Factur-X document that
   * the reading and validating calls use; the validation outside the measurement compiles the
   * rules.
   */
  @Setup(Level.Trial)
  public void setUp() {
    paniolo = Paniolo.create();
    invoice = BenchmarkData.invoice(lines);
    pdfA    = BenchmarkData.pdfA2b();
    facturX = paniolo.toFacturX(invoice,
                                pdfA,
                                FacturXProfile.XRECHNUNG);
    BenchmarkData.requireValid(paniolo.validate(facturX),
                               "Factur-X invoice with " + lines + " lines");
  }

  /**
   * Embeds the invoice into the PDF/A document as profile XRECHNUNG.
   *
   * @return the Factur-X document
   */
  @Benchmark
  public byte[] toFacturX() {
    return paniolo.toFacturX(invoice,
                             pdfA,
                             FacturXProfile.XRECHNUNG);
  }

  /**
   * Extracts the embedded invoice XML.
   *
   * @return the invoice XML
   */
  @Benchmark
  public byte[] extractXml() {
    return paniolo.extractXml(facturX);
  }

  /**
   * Reads the invoice from the Factur-X document.
   *
   * @return the invoice
   */
  @Benchmark
  public Invoice read() {
    return paniolo.read(facturX);
  }

  /**
   * Validates the Factur-X document: container and embedded XML, without PDF/A.
   *
   * @return the validation result
   */
  @Benchmark
  public ValidationResult validate() {
    return paniolo.validate(facturX);
  }
}
