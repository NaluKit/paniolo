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
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.validation.Severity;
import org.junit.jupiter.api.Test;
import org.mustangproject.BankDetails;
import org.mustangproject.Contact;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.IExportableTransaction;
import org.mustangproject.ZUGFeRD.IZUGFeRDAllowanceCharge;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableContact;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableItem;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableProduct;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableTradeParty;
import org.mustangproject.ZUGFeRD.IZUGFeRDLegalOrganisation;
import org.mustangproject.ZUGFeRD.IZUGFeRDLogisticsServiceCharge;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentDiscountTerms;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentTerms;
import org.mustangproject.ZUGFeRD.IZUGFeRDTradeSettlementDebit;
import org.mustangproject.ZUGFeRD.IZUGFeRDTradeSettlementPayment;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mustang versions after 2.26.0, reproduced with sub-interfaces: the getters the adapter knows are
 * mapped (BT-19 {@code getAccountingReference}, BT-7 {@code getTaxPointDate},
 * {@code getIndustryAssignedID}), every other new getter with a value is a {@link MappingLoss}.
 */
class NewerMustangVersionTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final ZoneId  ZONE    = ZoneId.of("Europe/Berlin");
  private static final String  BT19    = "CURRENT-MUSTANG-BT19-20261030";

  /**
   * The getters a newer Mustang adds to IExportableTransaction, and one the adapter does not know.
   */
  interface NewerTransaction
      extends IExportableTransaction {
    default String getAccountingReference() {
      return null;
    }

    default Date getTaxPointDate() {
      return null;
    }

    default String getFutureField() {
      return null;
    }
  }

  interface NewerProductInterface
      extends IZUGFeRDExportableProduct {
    default String getIndustryAssignedID() {
      return null;
    }
  }

  static final class NewerInvoice
      extends Invoice
      implements NewerTransaction {
    String accountingReference;
    Date   taxPointDate;
    String futureField;

    @Override
    public String getAccountingReference() {
      return accountingReference;
    }

    @Override
    public Date getTaxPointDate() {
      return taxPointDate;
    }

    @Override
    public String getFutureField() {
      return futureField;
    }
  }

  static final class NewerProduct
      extends Product
      implements NewerProductInterface {
    String industryId;

    NewerProduct(String name,
                 String unit,
                 BigDecimal vat) {
      super(name,
            null,
            unit,
            vat);
    }

    @Override
    public String getIndustryAssignedID() {
      return industryId;
    }
  }

  private static Date date(int year,
                           int month,
                           int day) {
    return Date.from(LocalDate.of(year,
                                  month,
                                  day)
                              .atStartOfDay(ZONE)
                              .toInstant());
  }

  private static NewerInvoice invoice(NewerProduct product) {
    var invoice = new NewerInvoice();
    invoice.setNumber("R-2026-0816")
           .setIssueDate(date(2026,
                              10,
                              30))
           .setDueDate(date(2026,
                            11,
                            13))
           .setDeliveryDate(date(2026,
                                 10,
                                 29))
           .setReferenceNumber("04011000-12345-03")
           .setSender(new TradeParty("Muster GmbH",
                                     "Musterstraße 1",
                                     "10115",
                                     "Berlin",
                                     "DE").setVATID("DE123456788")
                                          .setEmail("rechnung@example.org")
                                          .setContact(new Contact("Erika Muster",
                                                                  "+49 30 123456",
                                                                  "rechnung@example.org"))
                                          .addBankDetails(new BankDetails("DE89370400440532013000",
                                                                          "COBADEFFXXX")))
           .setRecipient(new TradeParty("Kunde GmbH",
                                        "Kundenweg 2",
                                        "20095",
                                        "Hamburg",
                                        "DE").setEmail("einkauf@example.org"))
           .addItem(new Item(product,
                             new BigDecimal("100.00"),
                             new BigDecimal("2")));
    return invoice;
  }

  private static NewerProduct product() {
    return new NewerProduct("Beratung",
                            "HUR",
                            new BigDecimal("19"));
  }

  private static MustangInvoiceAdapter adapter() {
    return MustangInvoiceAdapter.builder()
                                .zone(ZONE)
                                .build();
  }

  @Test
  void keepsTheBuyerAccountingReferenceOfNewerMustangVersions() {
    var invoice = invoice(product());
    invoice.accountingReference = BT19;
    var mapping = adapter().map(invoice);
    assertTrue(mapping.isLossless(),
               mapping.losses()::toString);
    assertEquals(BT19,
                 mapping.invoice()
                        .references()
                        .buyerAccountingReference());
    for (InvoiceSyntax syntax : InvoiceSyntax.values()) {
      byte[] xml = PANIOLO.toBytes(mapping.invoice(),
                                   syntax);
      assertTrue(new String(xml,
                            StandardCharsets.UTF_8).contains(BT19),
                 syntax::name);
      var result = PANIOLO.validate(xml);
      assertTrue(result.messages()
                       .stream()
                       .noneMatch(message -> message.severity() != Severity.INFO),
                 result::toString);
      assertEquals(BT19,
                   PANIOLO.read(xml)
                          .references()
                          .buyerAccountingReference());
    }
  }

  @Test
  void reportsGettersTheAdapterDoesNotKnow() {
    var invoice = invoice(product());
    // An unknown getter without value loses nothing.
    assertTrue(adapter().map(invoice)
                        .isLossless());
    invoice.futureField = "a value of a newer Mustang version";
    var mapping = adapter().map(invoice);
    assertFalse(mapping.isLossless());
    assertEquals(List.of("IExportableTransaction.getFutureField"),
                 mapping.losses()
                        .stream()
                        .map(MappingLoss::getter)
                        .toList());
    assertTrue(mapping.losses()
                      .getFirst()
                      .reason()
                      .contains("built for Mustang 2.26.0"),
               mapping.losses()::toString);
    var refused = assertThrows(IllegalArgumentException.class,
                               () -> adapter().toInvoice(invoice));
    assertTrue(refused.getMessage()
                      .contains("getFutureField"),
               refused::getMessage);
  }

  @Test
  void mapsTheVatPointDateUnlessItsCodeIsSet() {
    var invoice = invoice(product());
    invoice.taxPointDate = date(2026,
                                10,
                                29);
    var mapped = adapter().toInvoice(invoice);
    assertEquals(LocalDate.of(2026,
                              10,
                              29),
                 mapped.taxPointDate());
    // BT-7 and BT-8 exclude each other (BR-CO-3): the code is kept, the date reported.
    invoice.setVATDueDateTypeCode("5");
    var mapping = adapter().map(invoice);
    assertNull(mapping.invoice()
                      .taxPointDate());
    assertEquals(List.of("IExportableTransaction.getTaxPointDate"),
                 mapping.losses()
                        .stream()
                        .map(MappingLoss::getter)
                        .toList());
  }

  @Test
  void mapsTheIndustryItemIdentifierInExtended() {
    var product = product();
    product.industryId = "IND-4711";
    var invoice = invoice(product);
    assertEquals(List.of("IZUGFeRDExportableProduct.getIndustryAssignedID"),
                 adapter().map(invoice)
                          .losses()
                          .stream()
                          .map(MappingLoss::getter)
                          .toList());
    var extended = MustangInvoiceAdapter.builder()
                                        .processControl(ProcessControl.facturXExtended())
                                        .zone(ZONE)
                                        .build()
                                        .toInvoice(invoice);
    assertEquals("IND-4711",
                 extended.lines()
                         .getFirst()
                         .extended()
                         .industryItemId());
  }

  /**
   * The export interfaces whose getters the adapter checks.
   */
  static final List<Class<?>> EXPORT_INTERFACES = List.of(IExportableTransaction.class,
                                                          IZUGFeRDExportableTradeParty.class,
                                                          IZUGFeRDExportableContact.class,
                                                          IZUGFeRDLegalOrganisation.class,
                                                          IZUGFeRDExportableItem.class,
                                                          IZUGFeRDExportableProduct.class,
                                                          IZUGFeRDAllowanceCharge.class,
                                                          IZUGFeRDLogisticsServiceCharge.class,
                                                          IZUGFeRDTradeSettlementPayment.class,
                                                          IZUGFeRDTradeSettlementDebit.class,
                                                          IZUGFeRDPaymentTerms.class,
                                                          IZUGFeRDPaymentDiscountTerms.class);

  /**
   * Getters of newer Mustang versions that were reviewed and are deliberately not mapped; a set
   * value is reported as loss by the adapter. Empty: every new getter found so far is mapped.
   */
  static final Set<String> REVIEWED_UNMAPPED = Set.of();

  static Set<String> getters(Class<?> type) {
    Set<String> getters = new TreeSet<>();
    for (Method method : type.getMethods()) {
      String name = method.getName();
      if (method.getParameterCount() == 0 && method.getReturnType() != void.class && (name.startsWith("get") || name.startsWith("is") || name.equals("rebateAgreementExists"))) {
        getters.add(name);
      }
    }
    return getters;
  }

  /**
   * Runs against every Mustang version of the test matrix (see the module README): with the
   * release the adapter is built for, the getters are exactly the known ones; with a newer version,
   * no known getter is gone, and every new one is mapped or reviewed.
   */
  @Test
  void knowsTheGettersOfTheMustangVersionAtRuntime() {
    var problems = new java.util.ArrayList<String>();
    for (Class<?> type : EXPORT_INTERFACES) {
      Set<String> runtime = getters(type);
      Set<String> release = new TreeSet<>(MustangGetters.RELEASE_GETTERS.get(type.getSimpleName()));
      Set<String> newer   = MustangGetters.NEWER_GETTERS.getOrDefault(type.getSimpleName(),
                                                                      Set.of());
      // A removed getter would fail the adapter at runtime (NoSuchMethodError).
      for (String getter : release) {
        if (!runtime.contains(getter)) {
          problems.add("removed: " + type.getSimpleName() + "." + getter);
        }
      }
      for (String getter : runtime) {
        String qualified = type.getSimpleName() + "." + getter;
        if (!release.contains(getter) && !newer.contains(getter) && !REVIEWED_UNMAPPED.contains(qualified)) {
          problems.add("new and not reviewed: " + qualified);
        }
      }
    }
    assertEquals(List.of(),
                 problems,
                 "Mustang " + MustangLatestTest.version() + ": map the new getters (MustangGetters.NEWER_GETTERS) or review them (REVIEWED_UNMAPPED)");
    // Every getter of a newer version that the adapter maps is documented.
    MustangGetters.NEWER_GETTERS.forEach((type, names) -> names.forEach(getter -> assertTrue(MustangInvoiceAdapter.FIELDS.stream()
                                                                                                                   .anyMatch(field -> field.getter()
                                                                                                                                           .equals(type + "." + getter)),
                                                                                                type + "." + getter)));
  }

  @Test
  void theKnownGettersAreThoseOfTheRelease() {
    // Meaningful with the release only; a newer version has more getters.
    org.junit.jupiter.api.Assumptions.assumeFalse(MustangLatestTest.newer(),
                                                  "runs with Mustang " + MustangGetters.RELEASE + " only");
    for (Class<?> type : EXPORT_INTERFACES) {
      assertEquals(getters(type),
                   new TreeSet<>(MustangGetters.RELEASE_GETTERS.get(type.getSimpleName())),
                   type::getSimpleName);
    }
  }
}
