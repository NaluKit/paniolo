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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Decides whether a validated document is accepted, e.g. for booking or for passing it on. The
 * official result ({@link ValidationResult#isValid()}) stays as the rules define it; the policy adds
 * the findings that the receiving process treats as blocking, named by rule identifier.
 *
 * <p>A document is accepted if it is valid (no {@code ERROR} or {@code FATAL}) and has no finding of
 * a rule the policy rejects on. Errors always reject; a policy cannot accept an invalid document.
 *
 * <pre>{@code
 * AcceptancePolicy policy = AcceptancePolicy.officialRules()
 *                                           .rejectOn("PEPPOL-EN16931-R120");   // line net amount
 * AcceptanceDecision decision = policy.evaluate(paniolo.validate(xml));
 * if (!decision.accepted()) {
 *   decision.blockingFindings().forEach(f -> log.warn("{}: {}", f.ruleId(), f.message()));
 * }
 * }</pre>
 *
 * <p>Why per rule? The KoSIT XRechnung configuration rates some rules, e.g. the Peppol rule
 * {@code PEPPOL-EN16931-R120} for line net amounts, as warnings, while other validators or
 * recipients reject such documents. Which warnings block is a decision of the receiving process;
 * {@link #rejectOnWarnings()} is the strictest choice and rejects on every warning.
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class AcceptancePolicy {
  private static final AcceptancePolicy OFFICIAL = new AcceptancePolicy(Set.of(),
                                                                        false);

  private final Set<String> rejectedRules;
  private final boolean     rejectWarnings;

  private AcceptancePolicy(Set<String> rejectedRules,
                           boolean rejectWarnings) {
    this.rejectedRules  = Set.copyOf(rejectedRules);
    this.rejectWarnings = rejectWarnings;
  }

  /**
   * Accepts exactly the valid documents, as {@link ValidationResult#isValid()} does.
   *
   * @return the policy of the official rules
   */
  public static AcceptancePolicy officialRules() {
    return OFFICIAL;
  }

  /**
   * Returns a policy that additionally rejects documents with a finding of one of the given rules,
   * whatever its severity. This includes the {@code PANIOLO-CALC-} findings of the optional
   * calculation check, which may be reported as information when an official rule already reports
   * the same.
   *
   * @param ruleIds rule identifiers as reported, e.g. {@code PEPPOL-EN16931-R120}
   * @return a new policy
   * @throws NullPointerException     if a rule identifier is null
   * @throws IllegalArgumentException if a rule identifier is blank
   */
  public AcceptancePolicy rejectOn(String... ruleIds) {
    var rules = new LinkedHashSet<>(rejectedRules);
    for (String ruleId : Objects.requireNonNull(ruleIds,
                                                "ruleIds")) {
      if (Objects.requireNonNull(ruleId,
                                 "ruleId")
                 .isBlank()) {
        throw new IllegalArgumentException("Rule identifier must not be blank");
      }
      rules.add(ruleId.strip());
    }
    return new AcceptancePolicy(rules,
                                rejectWarnings);
  }

  /**
   * Returns a policy that additionally rejects documents with any warning. This goes further than
   * naming the blocking rules and may reject documents the recipient would accept.
   *
   * @return a new policy
   */
  public AcceptancePolicy rejectOnWarnings() {
    return new AcceptancePolicy(rejectedRules,
                                true);
  }

  /**
   * Returns the rules this policy rejects on in addition to errors.
   *
   * @return the rule identifiers
   */
  public Set<String> rejectedRules() {
    return rejectedRules;
  }

  /**
   * Whether this policy rejects on every warning.
   *
   * @return true if every warning blocks
   */
  public boolean rejectsWarnings() {
    return rejectWarnings;
  }

  /**
   * Evaluates a validation result.
   *
   * @param result the validation result
   * @return whether the document is accepted, and the findings that block it
   * @throws NullPointerException if result is null
   */
  public AcceptanceDecision evaluate(ValidationResult result) {
    Objects.requireNonNull(result,
                           "result");
    var blocking = new ArrayList<ValidationMessage>();
    for (var message : result.messages()) {
      boolean error = message.severity() == Severity.ERROR || message.severity() == Severity.FATAL;
      if (error || rejectWarnings && message.severity() == Severity.WARNING || message.ruleId() != null && rejectedRules.contains(message.ruleId())) {
        blocking.add(message);
      }
    }
    return new AcceptanceDecision(blocking.isEmpty(),
                                  result.isValid(),
                                  blocking);
  }

  @Override
  public String toString() {
    var parts = new ArrayList<String>();
    parts.add("errors");
    if (rejectWarnings) {
      parts.add("all warnings");
    }
    var rules = new ArrayList<>(rejectedRules);
    rules.sort(null);
    parts.addAll(rules);
    return "AcceptancePolicy[" + String.join(", ",
                                             parts) + "]";
  }

  /**
   * The decision about one document.
   *
   * @param accepted         whether the document is accepted
   * @param valid            the official result, {@link ValidationResult#isValid()}
   * @param blockingFindings the findings that reject the document: errors and the findings of the
   *                         rules the policy rejects on, in reporting order
   */
  public record AcceptanceDecision(boolean accepted, boolean valid, List<ValidationMessage> blockingFindings) {
    /**
     * Creates the decision and copies the findings.
     */
    public AcceptanceDecision {
      blockingFindings = List.copyOf(blockingFindings);
    }

    /**
     * Whether the policy rejects a document the official rules accept.
     *
     * @return true if the document is valid but not accepted
     */
    public boolean rejectedByPolicy() {
      return valid && !accepted;
    }

    /**
     * The rule identifiers of the blocking findings, without repetition.
     *
     * @return the rule identifiers in reporting order
     */
    public List<String> blockingRules() {
      return blockingFindings.stream()
                             .map(ValidationMessage::ruleId)
                             .distinct()
                             .toList();
    }
  }
}
