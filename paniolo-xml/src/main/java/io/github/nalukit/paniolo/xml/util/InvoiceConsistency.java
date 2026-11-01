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

/**
 * Internal helpers for readers: restoring exemption details; see {@link ReadContext} for the totals.
 */
public final class InvoiceConsistency {
  private InvoiceConsistency() {
  }

  /**
   * Lines and allowances carry only category and rate; the VAT breakdown carries the exemption
   * reason. Returns a lookup that completes a category from the matching breakdown.
   *
   * @param breakdownCategories the categories of the VAT breakdown (BG-23)
   * @return the categories by code and rate, for {@link #complete(TaxCategory, Map)}
   */
  public static Map<String, TaxCategory> byCategoryAndRate(List<TaxCategory> breakdownCategories) {
    var result = new HashMap<String, TaxCategory>();
    breakdownCategories.forEach(category -> {
      result.put(key(category),
                 category);
      // A French multi-vendor invoice has a breakdown per sub-invoice of the same category and rate.
      if (category.subInvoiceNumber() != null) {
        result.put(subInvoiceKey(category),
                   category);
      }
    });
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
    // EXTENDED subtotal and information lines may state no VAT category (BR-FXEXT-CO-04).
    if (category == null) {
      return null;
    }
    // EXTENDED lines state their exemption reason, of a French multi-vendor invoice starting with
    // the sub-invoice number (BR-FR-MV-08), which selects the breakdown of that sub-invoice.
    if (category.subInvoiceNumber() != null && breakdowns.containsKey(subInvoiceKey(category))) {
      return breakdowns.get(subInvoiceKey(category));
    }
    return breakdowns.getOrDefault(key(category),
                                   category);
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
