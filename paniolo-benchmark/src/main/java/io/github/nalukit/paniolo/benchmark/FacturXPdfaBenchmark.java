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

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Warm validation of Factur-X documents along the complete path of a received PDF: container,
 * PDF/A conformance with veraPDF and the embedded XML, with and without the calculation check.
 *
 * <p>Needs paniolo-pdfa on the class path, which {@code benchmarks.jar} leaves out so that
 * {@link FacturXBenchmark} stays without PDF/A; the setup fails if PDF/A is not checked. Run it with
 * the jars copied to {@code target/pdfa-lib}, see README.md.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class FacturXPdfaBenchmark {
  /**
   * The number of invoice lines.
   */
  @Param({ "1",
           "100",
           "1000" })
  int lines;

  /**
   * The size in kilobytes of a further, incompressible attachment; 0 for none.
   */
  @Param({ "0",
           "2048" })
  int attachmentKb;

  private Paniolo paniolo;
  private Paniolo calculating;
  private byte[]  facturX;

  /**
   * Creates the benchmark state; called by JMH.
   */
  public FacturXPdfaBenchmark() {
  }

  /**
   * Creates the instances and the Factur-X document; validating it outside the measurement compiles
   * the rules and makes sure PDF/A is checked and the document is valid.
   */
  @Setup(Level.Trial)
  public void setUp() {
    paniolo     = Paniolo.create();
    calculating = Paniolo.builder()
                         .calculationCheck(true)
                         .build();
    facturX     = paniolo.toFacturX(BenchmarkData.invoice(lines),
                                    BenchmarkData.pdfA2b(),
                                    FacturXProfile.XRECHNUNG,
                                    attachmentKb == 0 ?
                                    List.of() :
                                    List.of(BenchmarkData.attachment(attachmentKb)));
    String what   = "Factur-X invoice with " + lines + " lines and " + attachmentKb + " KB attachment";
    var    result = paniolo.validate(facturX);
    BenchmarkData.requirePdfaChecked(result,
                                     true,
                                     what);
    BenchmarkData.requireValid(result,
                               what);
    BenchmarkData.requireValid(calculating.validate(facturX),
                               what + " with calculation check");
  }

  /**
   * Validates the Factur-X document: container, PDF/A and embedded XML.
   *
   * @return the validation result
   */
  @Benchmark
  public ValidationResult validate() {
    return paniolo.validate(facturX);
  }

  /**
   * Validates the Factur-X document including PDF/A and checks the stated amounts of the embedded
   * invoice against the calculated ones.
   *
   * @return the validation result
   */
  @Benchmark
  public ValidationResult validateWithCalculationCheck() {
    return calculating.validate(facturX);
  }
}
