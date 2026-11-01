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
package io.github.nalukit.paniolo;

import io.github.nalukit.paniolo.validation.AcceptancePolicy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * G13: the acceptance of the calculation corpus under different policies. The official result stays
 * the same; only the named rules decide what a receiving process additionally rejects.
 */
class AcceptanceCorpusTest {
  private static final Paniolo PANIOLO = Paniolo.builder()
                                                .calculationCheck(true)
                                                .build();

  private static byte[] resource(String name) {
    try (InputStream input = AcceptanceCorpusTest.class.getResourceAsStream("/calculation-corpus/" + name)) {
      return input.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The corpus files the policy rejects although they are valid.
   */
  private static Set<String> rejectedByPolicy(AcceptancePolicy policy) {
    var rejected = new TreeSet<String>();
    for (String name : CalculationCorpus.CASES.keySet()) {
      for (String target : CalculationCorpus.TARGETS) {
        String file = name + "." + target + ".xml";
        var result = CalculationCorpus.validate(PANIOLO,
                                                resource(file),
                                                target);
        var decision = policy.evaluate(result);
        if (decision.rejectedByPolicy()) {
          rejected.add(file);
        }
        // Invalid documents are never accepted.
        if (!result.isValid()) {
          assertFalse(decision.accepted(),
                      file);
        }
      }
    }
    return rejected;
  }

  @Test
  void officialRulesAcceptEveryValidFile() {
    assertEquals(Set.of(),
                 rejectedByPolicy(AcceptancePolicy.officialRules()));
  }

  @Test
  void rejectingR120BlocksTheXrechnungLineAmountCases() {
    // KoSIT XRechnung 3.0.2 rates PEPPOL-EN16931-R120 as warning in UBL and CII; the policy decides.
    assertEquals(Set.of("line-net-amount.xrechnung-cii.xml",
                        "line-net-amount.xrechnung-ubl.xml"),
                 rejectedByPolicy(AcceptancePolicy.officialRules()
                                                  .rejectOn("PEPPOL-EN16931-R120")));
  }

  @Test
  void theCalculationCheckExtendsThePolicyToFacturX() {
    // Factur-X EN 16931 has no R120; the calculation check reports the same relationship.
    assertEquals(Set.of("line-net-amount.xrechnung-cii.xml",
                        "line-net-amount.xrechnung-ubl.xml",
                        "line-net-amount.facturx-en16931.xml"),
                 rejectedByPolicy(AcceptancePolicy.officialRules()
                                                  .rejectOn("PEPPOL-EN16931-R120",
                                                            "PANIOLO-CALC-LINE-NET-AMOUNT")));
  }

  @Test
  void rejectingAllWarningsGoesFurther() {
    assertEquals(Set.of("line-net-amount.facturx-en16931.xml",
                        "line-net-amount.xrechnung-cii.xml",
                        "line-net-amount.xrechnung-ubl.xml",
                        "net-price.facturx-en16931.xml",
                        "allowance-percentage.facturx-en16931.xml"),
                 rejectedByPolicy(AcceptancePolicy.officialRules()
                                                  .rejectOnWarnings()));
  }
}
