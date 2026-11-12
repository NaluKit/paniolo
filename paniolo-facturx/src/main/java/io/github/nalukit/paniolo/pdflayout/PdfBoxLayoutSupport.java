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
package io.github.nalukit.paniolo.pdflayout;

import io.github.nalukit.paniolo.config.PdfLayout;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.despatch.DespatchAdvice;
import io.github.nalukit.paniolo.model.order.Order;
import io.github.nalukit.paniolo.spi.PdfLayoutSupport;

import java.util.Objects;

/**
 * Renders the visual PDF/A-3b document of an invoice, order, or despatch advice with Apache
 * PDFBox, registered for {@link java.util.ServiceLoader}. Use it through {@code Paniolo#toPdf},
 * {@code Paniolo#toFacturX(Invoice, FacturXProfile)}, {@code Paniolo#toOrderXPdf(Order)}, and
 * {@code Paniolo#toDespatchAdvicePdf(DespatchAdvice)}. Stateless and thread-safe.
 */
public final class PdfBoxLayoutSupport
    implements PdfLayoutSupport {
  private static final System.Logger LOGGER = System.getLogger(PdfBoxLayoutSupport.class.getName());

  /**
   * Creates the implementation; {@link java.util.ServiceLoader} calls it.
   */
  public PdfBoxLayoutSupport() {
  }

  @Override
  public byte[] render(Invoice invoice,
                       PdfLayout layout) {
    Objects.requireNonNull(invoice,
                           "invoice");
    Objects.requireNonNull(layout,
                           "layout");
    byte[] pdf = new InvoiceRenderer(invoice,
                                     layout).render();
    LOGGER.log(System.Logger.Level.DEBUG,
               "Rendered invoice with {0} lines as PDF/A-3b ({1} bytes)",
               invoice.lines()
                      .size(),
               pdf.length);
    return pdf;
  }

  @Override
  public byte[] render(Order order,
                       PdfLayout layout) {
    Objects.requireNonNull(order,
                           "order");
    Objects.requireNonNull(layout,
                           "layout");
    return new OrderRenderer(order,
                             layout).render();
  }

  @Override
  public byte[] render(DespatchAdvice advice,
                       PdfLayout layout) {
    Objects.requireNonNull(advice,
                           "advice");
    Objects.requireNonNull(layout,
                           "layout");
    return new DespatchAdviceRenderer(advice,
                                      layout).render();
  }

  @Override
  public byte[] render(Report report,
                       PdfLayout layout) {
    Objects.requireNonNull(report,
                           "report");
    Objects.requireNonNull(layout,
                           "layout");
    byte[] pdf = new ValidationReportRenderer(report,
                                              layout).render();
    LOGGER.log(System.Logger.Level.DEBUG,
               "Rendered validation report with {0} findings as PDF/A-3b ({1} bytes)",
               report.result()
                     .messages()
                     .size(),
               pdf.length);
    return pdf;
  }
}
