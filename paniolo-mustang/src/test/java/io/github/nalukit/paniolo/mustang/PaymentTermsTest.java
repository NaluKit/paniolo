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
package io.github.nalukit.paniolo.mustang;

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.extended.PaymentTerm;
import io.github.nalukit.paniolo.validation.Severity;
import org.junit.jupiter.api.Test;
import org.mustangproject.Invoice;
import org.mustangproject.PaymentTerms;
import org.mustangproject.ZUGFeRD.IExportableTransaction;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentDiscountTerms;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentTerms;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRD2PullProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.function.UnaryOperator;

import static io.github.nalukit.paniolo.mustang.MustangInvoiceAdapterTest.date;
import static io.github.nalukit.paniolo.mustang.MustangInvoiceAdapterTest.mustangInvoice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mustang's extended payment terms: the first is the payment terms of the invoice (BT-20, BT-9),
 * every further one an additional EXTENDED payment term.
 */
class PaymentTermsTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final ZoneId  ZONE    = ZoneId.of("Europe/Berlin");

  /**
   * 2 % cash discount within 10 days from 27 October 2026.
   */
  private static IZUGFeRDPaymentDiscountTerms discount() {
    return new IZUGFeRDPaymentDiscountTerms() {
      @Override
      public BigDecimal getCalculationPercentage() {
        return new BigDecimal("2");
      }

      @Override
      public Date getBaseDate() {
        return date(2026,
                    10,
                    27);
      }

      @Override
      public int getBasePeriodMeasure() {
        return 10;
      }

      @Override
      public String getBasePeriodUnitCode() {
        return "DAY";
      }
    };
  }

  /**
   * The invoice of {@link MustangInvoiceAdapterTest} with three payment terms: the regular one, a
   * second instalment, and a cash discount.
   */
  private static Invoice withTerms() {
    Invoice invoice = mustangInvoice();
    invoice.addPaymentTerms(new PaymentTerms("Zahlbar innerhalb von 14 Tagen",
                                             date(2026,
                                                  11,
                                                  10)))
           .addPaymentTerms(new PaymentTerms("Zweite Rate",
                                             date(2026,
                                                  12,
                                                  10)))
           .addPaymentTerms(new PaymentTerms("Skonto",
                                             null,
                                             discount()));
    return invoice;
  }

  private static MustangInvoiceAdapter extended() {
    return MustangInvoiceAdapter.builder()
                                .processControl(ProcessControl.facturXExtended())
                                .zone(ZONE)
                                .build();
  }

  @Test
  void mapsTheAdditionalPaymentTermsInExtended() {
    var mapping = extended().map(withTerms());
    assertTrue(mapping.isLossless(),
               mapping.losses()::toString);
    var invoice = mapping.invoice();
    assertEquals("Zahlbar innerhalb von 14 Tagen",
                 invoice.paymentTerms());
    assertEquals(LocalDate.of(2026,
                              11,
                              10),
                 invoice.dueDate());
    List<PaymentTerm> terms = invoice.extended()
                                     .paymentTerms();
    assertEquals(2,
                 terms.size(),
                 terms::toString);
    assertEquals("Zweite Rate",
                 terms.getFirst()
                      .description());
    assertEquals(LocalDate.of(2026,
                              12,
                              10),
                 terms.getFirst()
                      .dueDate());
    var discount = terms.get(1)
                        .discount();
    assertEquals(LocalDate.of(2026,
                              10,
                              27),
                 discount.basisDate());
    assertEquals(10,
                 discount.periodDays());
    assertEquals(0,
                 new BigDecimal("2").compareTo(discount.percent()));
    // Mustang states the grand total as base amount of the discount.
    assertEquals(0,
                 invoice.totals()
                        .taxInclusiveAmount()
                        .compareTo(discount.basisAmount()));

    byte[] xml = PANIOLO.toBytes(invoice,
                                 InvoiceSyntax.CII);
    var result = PANIOLO.validate(xml,
                                  FacturXProfile.EXTENDED);
    assertTrue(result.messages()
                     .stream()
                     .noneMatch(message -> message.severity() != Severity.INFO),
               result::toString);
    assertEquals(invoice,
                 PANIOLO.read(xml));
  }

  @Test
  void readsTheSameTermsFromMustangsOwnExport() {
    var provider = new ZUGFeRD2PullProvider();
    provider.setProfile(Profiles.getByName("EXTENDED"));
    provider.generateXML(withTerms());
    var exported = PANIOLO.read(provider.getXML());
    // Mustang writes dates in the JVM default zone, so the adapter has to use the same zone here.
    var mapped = MustangInvoiceAdapter.builder()
                                      .processControl(ProcessControl.facturXExtended())
                                      .zone(ZoneId.systemDefault())
                                      .build()
                                      .toInvoice(withTerms());
    assertEquals(mapped.paymentTerms(),
                 exported.paymentTerms());
    assertEquals(mapped.dueDate(),
                 exported.dueDate());
    // The further plain term is no longer dropped when reading; the discount term is identical.
    assertEquals(mapped.extended()
                       .paymentTerms(),
                 exported.extended()
                         .paymentTerms());
  }

  @Test
  void reportsEachAdditionalTermOutsideExtended() {
    var mapping = MustangInvoiceAdapter.builder()
                                       .zone(ZONE)
                                       .build()
                                       .map(withTerms());
    // The first extended term is the regular payment terms; only the two further ones are lost.
    assertEquals(List.of("IExportableTransaction.getExtendedPaymentTerms",
                         "IExportableTransaction.getExtendedPaymentTerms"),
                 mapping.losses()
                        .stream()
                        .map(MappingLoss::getter)
                        .toList());
    assertTrue(mapping.losses()
                      .getFirst()
                      .reason()
                      .contains("Zweite Rate"),
               mapping.losses()::toString);
    assertFalse(mapping.isLossless());
    assertNull(mapping.invoice()
                      .extended());
  }

  @Test
  void reportsADescriptionThatMustangWouldNotWrite() {
    Invoice invoice = mustangInvoice();
    invoice.addPaymentTerms(new PaymentTerms("Zahlbar sofort",
                                             date(2026,
                                                  11,
                                                  10)));
    var mapping = extended().map(invoice);
    assertEquals("Zahlbar sofort",
                 mapping.invoice()
                        .paymentTerms());
    assertEquals(List.of("IExportableTransaction.getPaymentTermDescription"),
                 mapping.losses()
                        .stream()
                        .map(MappingLoss::getter)
                        .toList());
  }

  /**
   * A cash discount of {@code percent} % within 10 days without base date, as a new object.
   */
  private static IZUGFeRDPaymentDiscountTerms discount(String percent) {
    return discount(percent,
                    10,
                    "DAY");
  }

  private static IZUGFeRDPaymentDiscountTerms discount(String percent,
                                                       int period,
                                                       String unit) {
    return new IZUGFeRDPaymentDiscountTerms() {
      @Override
      public BigDecimal getCalculationPercentage() {
        return new BigDecimal(percent);
      }

      @Override
      public Date getBaseDate() {
        return null;
      }

      @Override
      public int getBasePeriodMeasure() {
        return period;
      }

      @Override
      public String getBasePeriodUnitCode() {
        return unit;
      }
    };
  }

  /**
   * A copy with the same values, as a new object.
   */
  private static IZUGFeRDPaymentTerms copy(IZUGFeRDPaymentTerms term) {
    var discount = term.getDiscountTerms();
    return new PaymentTerms(term.getDescription(),
                            term.getDueDate() == null ?
                            null :
                            new Date(term.getDueDate()
                                         .getTime()),
                            discount == null ?
                            null :
                            copy(discount));
  }

  private static IZUGFeRDPaymentDiscountTerms copy(IZUGFeRDPaymentDiscountTerms discount) {
    BigDecimal percent = discount.getCalculationPercentage();
    Date       base    = discount.getBaseDate() == null ?
                         null :
                         new Date(discount.getBaseDate()
                                          .getTime());
    int        measure = discount.getBasePeriodMeasure();
    String     unit    = discount.getBasePeriodUnitCode();
    return new IZUGFeRDPaymentDiscountTerms() {
      @Override
      public BigDecimal getCalculationPercentage() {
        return percent;
      }

      @Override
      public Date getBaseDate() {
        return base;
      }

      @Override
      public int getBasePeriodMeasure() {
        return measure;
      }

      @Override
      public String getBasePeriodUnitCode() {
        return unit;
      }
    };
  }

  /**
   * An own implementation of the export interface around a Mustang invoice that, like DTO based
   * integrations, returns new payment terms objects on every call. {@code first} changes the copy
   * of the first extended payment term.
   */
  private static IExportableTransaction copying(Invoice invoice,
                                                UnaryOperator<IZUGFeRDPaymentTerms> first) {
    return (IExportableTransaction) Proxy.newProxyInstance(PaymentTermsTest.class.getClassLoader(),
                                                           new Class<?>[] { IExportableTransaction.class },
                                                           (proxy, method, arguments) -> {
                                                             switch (method.getName()) {
                                                               case "getPaymentTerms" -> {
                                                                 return invoice.getPaymentTerms() == null ?
                                                                        null :
                                                                        copy(invoice.getPaymentTerms());
                                                               }
                                                               case "getExtendedPaymentTerms" -> {
                                                                 var terms = invoice.getExtendedPaymentTerms();
                                                                 var result = new IZUGFeRDPaymentTerms[terms.length];
                                                                 for (int i = 0; i < terms.length; i++) {
                                                                   result[i] = i == 0 ?
                                                                               first.apply(copy(terms[i])) :
                                                                               copy(terms[i]);
                                                                 }
                                                                 return result;
                                                               }
                                                               default -> {
                                                                 try {
                                                                   return method.invoke(invoice,
                                                                                        arguments);
                                                                 } catch (InvocationTargetException e) {
                                                                   throw e.getCause();
                                                                 }
                                                               }
                                                             }
                                                           });
  }

  /**
   * The invoice with a first payment term carrying a cash discount and a further instalment.
   */
  private static Invoice withDiscountTerms() {
    Invoice invoice = mustangInvoice();
    invoice.addPaymentTerms(new PaymentTerms("Zahlbar innerhalb von 14 Tagen",
                                             date(2026,
                                                  11,
                                                  10),
                                             discount("3")))
           .addPaymentTerms(new PaymentTerms("Zweite Rate",
                                             date(2026,
                                                  12,
                                                  10)));
    return invoice;
  }

  /**
   * Equal values in other instances are no loss.
   */
  @Test
  void comparesTheFirstExtendedTermByItsValues() {
    for (Invoice invoice : List.of(withTerms(),
                                   withDiscountTerms())) {
      var expected = extended().map(invoice);
      assertTrue(expected.isLossless(),
                 expected.losses()::toString);
      var copies = copying(invoice,
                           UnaryOperator.identity());
      assertTrue(copies.getPaymentTerms() != copies.getExtendedPaymentTerms()[0]);
      var mapping = extended().map(copies);
      assertTrue(mapping.isLossless(),
                 mapping.losses()::toString);
      assertEquals(expected.invoice(),
                   mapping.invoice());
      assertEquals(mapping.invoice(),
                   extended().toInvoice(copies));
    }
  }

  @Test
  void stillReportsAFirstExtendedTermWithOtherValues() {
    var invoice = withDiscountTerms();
    for (UnaryOperator<IZUGFeRDPaymentTerms> change : List.<UnaryOperator<IZUGFeRDPaymentTerms>>of(term -> new PaymentTerms("Zahlbar sofort",
                                                                                                                             term.getDueDate(),
                                                                                                                             term.getDiscountTerms()),
                                                                                                      term -> new PaymentTerms(term.getDescription(),
                                                                                                                               date(2026,
                                                                                                                                    11,
                                                                                                                                    11),
                                                                                                                               term.getDiscountTerms()),
                                                                                                      term -> new PaymentTerms(term.getDescription(),
                                                                                                                               term.getDueDate(),
                                                                                                                               discount("2")),
                                                                                                      term -> new PaymentTerms(term.getDescription(),
                                                                                                                               term.getDueDate()))) {
      var mapping = extended().map(copying(invoice,
                                           change));
      assertEquals(List.of("IExportableTransaction.getExtendedPaymentTerms"),
                   mapping.losses()
                          .stream()
                          .map(MappingLoss::getter)
                          .toList());
    }
    // 3.0 and 3 are the same percentage.
    assertTrue(extended().map(copying(invoice,
                                      term -> new PaymentTerms(term.getDescription(),
                                                               term.getDueDate(),
                                                               discount("3.0"))))
                         .isLossless());
  }
}
