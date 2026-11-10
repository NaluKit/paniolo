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
package io.github.nalukit.paniolo.xml.util;

import io.github.nalukit.paniolo.model.common.TaxCategory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Internal helpers for readers: restoring exemption details; see {@link ReadContext} for the totals.
 */
public final class InvoiceConsistency {
  private InvoiceConsistency() {
  }

  /**
   * Lines and allowances carry only category and rate; the VAT breakdown carries the exemption
   * reason. Returns a lookup that completes a category from the matching breakdown. If several
   * breakdowns share category and rate (and sub-invoice), the first one is used.
   *
   * @param breakdownCategories the categories of the VAT breakdown (BG-23)
   * @return the categories by code and rate, for {@link #complete(TaxCategory, Map)}
   */
  public static Map<String, TaxCategory> byCategoryAndRate(List<TaxCategory> breakdownCategories) {
    return byCategoryAndRate(breakdownCategories,
                             null);
  }

  /**
   * Like {@link #byCategoryAndRate(List)}, and reports breakdowns that repeat category and rate (and
   * sub-invoice) of an earlier one with another exemption reason (BT-120) or code (BT-121): the model
   * keeps one breakdown per category and rate, so their texts cannot both be kept.
   *
   * @param breakdownCategories the categories of the VAT breakdown (BG-23) in document order
   * @param conflicts           the read that reports conflicts, or null where the profile states no
   *                            exemption reason outside the breakdown and nothing is checked
   * @return the categories by code and rate, for {@link #complete(TaxCategory, Map, String, String, ReadContext)}
   * @throws IllegalArgumentException if a conflict is found in strict reading
   */
  public static Map<String, TaxCategory> byCategoryAndRate(List<TaxCategory> breakdownCategories,
                                                           ReadContext conflicts) {
    var result = new HashMap<String, TaxCategory>();
    var owners = new HashMap<String, TaxCategory>();
    var first  = new HashMap<String, Integer>();
    for (int index = 0; index < breakdownCategories.size(); index++) {
      var category = breakdownCategories.get(index);
      if (category == null) {
        continue;
      }
      // A French multi-vendor invoice has a breakdown per sub-invoice of the same category and rate.
      String own = category.subInvoiceNumber() == null ?
                   key(category) :
                   subInvoiceKey(category);
      var kept = owners.get(own);
      if (kept != null && conflicts != null && differs(category,
                                                       kept)) {
        conflicts.taxExemption(null,
                               "VAT breakdown " + (index + 1) + " (" + describe(category) + ") repeats category and rate of VAT breakdown " + first.get(own) + " (" + describe(kept) +
                               "); the model keeps one breakdown per category and rate",
                               "the text of VAT breakdown " + first.get(own) + " is kept and the amounts are merged");
      }
      result.putIfAbsent(key(category),
                         category);
      if (category.subInvoiceNumber() != null) {
        result.putIfAbsent(subInvoiceKey(category),
                           category);
      }
      owners.putIfAbsent(own,
                         category);
      first.putIfAbsent(own,
                        index + 1);
    }
    return result;
  }

  /**
   * Completes a category of a line or allowance with the exemption details of the matching VAT
   * breakdown.
   *
   * @param category   category and rate of a line or allowance
   * @param breakdowns lookup from {@link #byCategoryAndRate(List)}
   * @return the matching breakdown category, or {@code category} if there is none
   */
  public static TaxCategory complete(TaxCategory category,
                                     Map<String, TaxCategory> breakdowns) {
    return complete(category,
                    breakdowns,
                    null,
                    null,
                    null);
  }

  /**
   * Like {@link #complete(TaxCategory, Map)}, and reports an exemption reason (BT-X-96, BT-X-97) or
   * code stated by the line or allowance that differs from the matching breakdown, which replaces it.
   * A missing reason or code is completed without report.
   *
   * @param category   category and rate of a line or allowance, as stated
   * @param breakdowns lookup from {@link #byCategoryAndRate(List, ReadContext)}
   * @param lineId     the line, or null for the document level
   * @param location   where the category is stated, e.g. {@code Line 2}
   * @param conflicts  the read that reports conflicts, or null where the profile states no exemption
   *                   reason outside the breakdown and nothing is checked
   * @return the matching breakdown category, or {@code category} if there is none
   * @throws IllegalArgumentException if a conflict is found in strict reading
   */
  public static TaxCategory complete(TaxCategory category,
                                     Map<String, TaxCategory> breakdowns,
                                     String lineId,
                                     String location,
                                     ReadContext conflicts) {
    // EXTENDED subtotal and information lines may state no VAT category (BR-FXEXT-CO-04).
    if (category == null) {
      return null;
    }
    // EXTENDED lines state their exemption reason, of a French multi-vendor invoice starting with
    // the sub-invoice number (BR-FR-MV-08), which selects the breakdown of that sub-invoice.
    TaxCategory result = category.subInvoiceNumber() != null && breakdowns.containsKey(subInvoiceKey(category)) ?
                         breakdowns.get(subInvoiceKey(category)) :
                         breakdowns.getOrDefault(key(category),
                                                 category);
    if (conflicts != null && result != category && (category.exemptionReason() != null && !category.exemptionReason()
                                                                                                    .equals(result.exemptionReason()) ||
                                                    category.exemptionReasonCode() != null && !category.exemptionReasonCode()
                                                                                                       .equals(result.exemptionReasonCode()))) {
      conflicts.taxExemption(lineId,
                             location + ": VAT " + describe(category) + " differs from the VAT breakdown (" + describe(result) + ")",
                             "the text of the breakdown is kept");
    }
    return result;
  }

  private static boolean differs(TaxCategory one,
                                 TaxCategory other) {
    return !Objects.equals(one.exemptionReason(),
                           other.exemptionReason()) || !Objects.equals(one.exemptionReasonCode(),
                                                                        other.exemptionReasonCode());
  }

  private static String describe(TaxCategory category) {
    return category.code()
                   .getCode() + " " + (category.rate() == null ?
                                       "-" :
                                       category.rate()
                                               .toPlainString()) + " %, exemption reason " + (category.exemptionReason() == null ?
                                                                                              "-" :
                                                                                              "'" + category.exemptionReason() + "'") + ", code " + (category.exemptionReasonCode() == null ?
                                                                                                                                                      "-" :
                                                                                                                                                      category.exemptionReasonCode());
  }

  private static String subInvoiceKey(TaxCategory category) {
    return key(category) + "#" + category.subInvoiceNumber();
  }

  private static String key(TaxCategory category) {
    return category.code()
                   .getCode() +
           ":" +
           (category.rate() == null ?
            "" :
            category.rate()
                    .toPlainString());
  }
}
