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

import io.github.nalukit.paniolo.spi.PdfaSupport;
import io.github.nalukit.paniolo.validation.PdfaStatistics;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import io.github.nalukit.paniolo.validation.ValidationStatistics;
import org.verapdf.core.EncryptedPdfException;
import org.verapdf.core.ModelParsingException;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PDF/A validation with veraPDF, registered for {@link java.util.ServiceLoader}. The document is
 * validated against the PDF/A flavour its XMP metadata declares; Factur-X requires part 3.
 * Stateless and thread-safe.
 */
public final class VeraPdfaSupport
    implements PdfaSupport {
  private static final System.Logger LOGGER = System.getLogger(VeraPdfaSupport.class.getName());

  static {
    VeraGreenfieldFoundryProvider.initialise();
  }

  /**
   * Creates the implementation; {@link java.util.ServiceLoader} calls it.
   */
  public VeraPdfaSupport() {
  }

  private static PDFAFlavour declaredFlavour(byte[] pdf)
      throws Exception {
    try (var parser = Foundries.defaultInstance()
                               .createParser(new ByteArrayInputStream(pdf))) {
      // getFlavour() fails when no flavour is declared; the list is empty then.
      var flavours = parser.getFlavours();
      return flavours == null || flavours.isEmpty() ?
             PDFAFlavour.NO_FLAVOUR :
             flavours.get(0);
    }
  }

  /**
   * One finding per failed rule; repeated failures of the same rule are counted.
   */
  private static List<ValidationMessage> failures(List<TestAssertion> assertions,
                                                  PDFAFlavour flavour) {
    Map<String, TestAssertion> first = new LinkedHashMap<>();
    Map<String, Integer>       count = new LinkedHashMap<>();
    for (TestAssertion assertion : assertions) {
      if (assertion.getStatus() != TestAssertion.Status.FAILED) {
        continue;
      }
      String rule = "PDFA-" +
                    flavour.getId()
                           .toUpperCase(java.util.Locale.ROOT) +
                    "-" +
                    assertion.getRuleId()
                             .getClause() +
                    "-" +
                    assertion.getRuleId()
                             .getTestNumber();
      first.putIfAbsent(rule,
                        assertion);
      count.merge(rule,
                  1,
                  Integer::sum);
    }
    var result = new ArrayList<ValidationMessage>();
    first.forEach((rule, assertion) -> {
      String text = assertion.getErrorMessage() != null &&
                    !assertion.getErrorMessage()
                              .isBlank() ?
                    assertion.getErrorMessage() :
                    assertion.getMessage();
      int times = count.get(rule);
      result.add(message(rule,
                         Severity.ERROR,
                         times > 1 ?
                         text + " (" + times + " occurrences)" :
                         text,
                         assertion.getLocation() == null ?
                         assertion.getLocationContext() :
                         assertion.getLocation()
                                  .getContext()));
    });
    return result;
  }

  private static ValidationMessage message(String rule,
                                           Severity severity,
                                           String text,
                                           String location) {
    return new ValidationMessage(rule,
                                 severity,
                                 text,
                                 location,
                                 ValidationMessage.Stage.PDF_A);
  }

  /**
   * Statistics of a veraPDF result: checks, failed checks, and failures per rule.
   */
  private static PdfaStatistics statistics(org.verapdf.pdfa.results.ValidationResult result,
                                           PDFAFlavour flavour) {
    var failedRules = new LinkedHashMap<String, Integer>();
    int failed      = 0;
    for (var entry : result.getFailedChecks()
                           .entrySet()) {
      failedRules.merge(entry.getKey()
                             .getClause() +
                        "-" +
                        entry.getKey()
                             .getTestNumber(),
                        entry.getValue(),
                        Integer::sum);
      failed += entry.getValue();
    }
    var details = result.getProfileDetails();
    return new PdfaStatistics(flavour.getId(),
                              details == null ?
                              null :
                              details.getName(),
                              result.isCompliant(),
                              result.getTotalAssertions(),
                              failed,
                              failedRules);
  }

  @Override
  public List<ValidationMessage> validate(byte[] pdf) {
    return check(pdf,
                 false).messages();
  }

  @Override
  public ValidationResult check(byte[] pdf,
                                boolean statistics) {
    Objects.requireNonNull(pdf,
                           "pdf");
    long           start    = System.nanoTime();
    var            findings = new ArrayList<ValidationMessage>();
    PdfaStatistics checked  = null;
    try {
      PDFAFlavour declared = declaredFlavour(pdf);
      PDFAFlavour flavour  = declared;
      Integer part = declared == PDFAFlavour.NO_FLAVOUR || declared.getPart() == null ?
                     null :
                     declared.getPart()
                             .getPartNumber();
      if (part == null || part == 0) {
        findings.add(message("PDFA-NOT-DECLARED",
                             Severity.ERROR,
                             "The document does not declare PDF/A conformance in its XMP metadata (pdfaid); checked as PDF/A-3b",
                             "XMP pdfaid"));
        flavour = PDFAFlavour.PDFA_3_B;
      } else if (part != 3) {
        findings.add(message("PDFA-PART",
                             Severity.ERROR,
                             "Factur-X / ZUGFeRD requires PDF/A-3, but the document declares " + declared.getId(),
                             "XMP pdfaid:part"));
      }
      try (var parser = Foundries.defaultInstance()
                                 .createParser(new ByteArrayInputStream(pdf),
                                               flavour)) {
        var result = Foundries.defaultInstance()
                              .createValidator(flavour,
                                               false)
                              .validate(parser);
        findings.addAll(failures(result.getTestAssertions(),
                                 flavour));
        if (statistics) {
          checked = statistics(result,
                               flavour);
        }
      }
    } catch (EncryptedPdfException e) {
      findings.add(message("PDFA-ENCRYPTED",
                           Severity.FATAL,
                           "Encrypted documents cannot be PDF/A",
                           "/Encrypt"));
    } catch (ModelParsingException e) {
      findings.add(message("PDFA-PARSE",
                           Severity.FATAL,
                           "The PDF cannot be parsed: " + e.getMessage(),
                           ""));
    } catch (Exception e) {
      throw new IllegalStateException("PDF/A validation failed",
                                      e);
    }
    LOGGER.log(System.Logger.Level.DEBUG,
               "PDF/A validation finished with {0} findings in {1} ms",
               findings.size(),
               (System.nanoTime() - start) / 1_000_000);
    return new ValidationResult("PDF/A",
                                findings,
                                null,
                                statistics ?
                                new ValidationStatistics(List.of(),
                                                         checked == null ?
                                                         List.of() :
                                                         List.of(checked)) :
                                null);
  }
}
