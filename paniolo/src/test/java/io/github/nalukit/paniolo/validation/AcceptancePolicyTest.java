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
package io.github.nalukit.paniolo.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcceptancePolicyTest {
  private static ValidationMessage finding(String rule,
                                           Severity severity) {
    return new ValidationMessage(rule,
                                 severity,
                                 rule + " text",
                                 "/",
                                 ValidationMessage.Stage.SCHEMATRON);
  }

  private static ValidationResult result(ValidationMessage... messages) {
    return new ValidationResult("test",
                                List.of(messages));
  }

  @Test
  void officialRulesAcceptExactlyTheValidDocuments() {
    var policy = AcceptancePolicy.officialRules();
    var warning = policy.evaluate(result(finding("PEPPOL-EN16931-R120",
                                                 Severity.WARNING)));
    assertTrue(warning.accepted());
    assertTrue(warning.valid());
    assertEquals(List.of(),
                 warning.blockingFindings());
    var error = policy.evaluate(result(finding("BR-CO-10",
                                               Severity.FATAL)));
    assertFalse(error.accepted());
    assertFalse(error.rejectedByPolicy());
    assertEquals(List.of("BR-CO-10"),
                 error.blockingRules());
  }

  @Test
  void namedRulesBlockAValidDocument() {
    var policy = AcceptancePolicy.officialRules()
                                 .rejectOn("PEPPOL-EN16931-R120");
    var r120 = result(finding("PEPPOL-EN16931-R120",
                              Severity.WARNING),
                      finding("BR-DE-21",
                              Severity.WARNING));
    var decision = policy.evaluate(r120);
    assertTrue(decision.valid());
    assertFalse(decision.accepted());
    assertTrue(decision.rejectedByPolicy());
    assertEquals(List.of("PEPPOL-EN16931-R120"),
                 decision.blockingRules());
    // Another warning does not block.
    assertTrue(policy.evaluate(result(finding("BR-DE-21",
                                              Severity.WARNING)))
                     .accepted());
    // A named rule blocks at any severity, e.g. a calculation finding lowered to information.
    assertFalse(AcceptancePolicy.officialRules()
                                .rejectOn("PANIOLO-CALC-LINE-NET-AMOUNT")
                                .evaluate(result(finding("PANIOLO-CALC-LINE-NET-AMOUNT",
                                                         Severity.INFO)))
                                .accepted());
    // The official result is not changed.
    assertTrue(r120.isValid());
  }

  @Test
  void rejectingAllWarningsIsTheStrictestPolicy() {
    var policy = AcceptancePolicy.officialRules()
                                 .rejectOnWarnings();
    assertFalse(policy.evaluate(result(finding("BR-DE-21",
                                               Severity.WARNING)))
                      .accepted());
    assertTrue(policy.evaluate(result(finding("FX-PRODUCER",
                                              Severity.INFO)))
                     .accepted());
    assertTrue(policy.rejectsWarnings());
  }

  @Test
  void policiesAreImmutable() {
    var official = AcceptancePolicy.officialRules();
    var strict = official.rejectOn("PEPPOL-EN16931-R120",
                                   " PEPPOL-EN16931-R046 ");
    assertSame(official,
               AcceptancePolicy.officialRules());
    assertEquals(java.util.Set.of(),
                 official.rejectedRules());
    assertEquals(java.util.Set.of("PEPPOL-EN16931-R120",
                                  "PEPPOL-EN16931-R046"),
                 strict.rejectedRules());
    assertThrows(IllegalArgumentException.class,
                 () -> official.rejectOn(" "));
    assertThrows(NullPointerException.class,
                 () -> official.rejectOn((String) null));
  }
}
