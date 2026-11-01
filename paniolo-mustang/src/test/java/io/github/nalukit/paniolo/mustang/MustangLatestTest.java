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
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import org.mustangproject.Invoice;
import org.mustangproject.Product;
import org.mustangproject.ZUGFeRD.IExportableTransaction;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRD2PullProvider;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static io.github.nalukit.paniolo.mustang.MustangInvoiceAdapterTest.date;
import static io.github.nalukit.paniolo.mustang.MustangInvoiceAdapterTest.mustangInvoice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The newer data of the real classes of a Mustang version after 2.26.0, compared with Mustang's
 * own export. Uses reflection, so it compiles against 2.26.0; with 2.26.0 the tests are skipped.
 * Run the module tests with {@code -Dmustang.version=...} to check a newer version (see the module
 * README).
 */
class MustangLatestTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final ZoneId  ZONE    = ZoneId.of("Europe/Berlin");
  private static final String  BT19    = "CURRENT-MUSTANG-BT19-20261030";

  /**
   * The Mustang version on the class path, from the Maven metadata of the library.
   */
  static String version() {
    try (InputStream input = Invoice.class.getResourceAsStream("/META-INF/maven/org.mustangproject/library/pom.properties")) {
      if (input != null) {
        var properties = new Properties();
        properties.load(input);
        return properties.getProperty("version",
                                      "unknown");
      }
    } catch (IOException e) {
      // The version is information only.
    }
    return newer() ?
           "after " + MustangGetters.RELEASE :
           MustangGetters.RELEASE;
  }

  /**
   * Returns true if the Mustang version on the class path has getters the release does not have.
   */
  static boolean newer() {
    for (Class<?> type : NewerMustangVersionTest.EXPORT_INTERFACES) {
      Set<String> runtime = new TreeSet<>(NewerMustangVersionTest.getters(type));
      runtime.removeAll(MustangGetters.RELEASE_GETTERS.get(type.getSimpleName()));
      if (!runtime.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static Method setter(Class<?> type,
                               String name,
                               Class<?> parameter) {
    try {
      return type.getMethod(name,
                            parameter);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private static void call(Method setter,
                           Object target,
                           Object value) {
    try {
      setter.invoke(target,
                    value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] mustangXml(IExportableTransaction invoice,
                                   String profile) {
    var provider = new ZUGFeRD2PullProvider();
    provider.setProfile(Profiles.getByName(profile));
    provider.generateXML(invoice);
    return provider.getXML();
  }

  private static void assertValid(ValidationResult result) {
    assertTrue(result.messages()
                     .stream()
                     .noneMatch(message -> message.severity() != Severity.INFO),
               result::toString);
  }

  @Test
  void keepsTheBuyerAccountingReference() {
    Method setter = setter(Invoice.class,
                           "setAccountingReference",
                           String.class);
    assumeTrue(setter != null,
               "Mustang " + version() + " has no BT-19 (Invoice.setAccountingReference)");
    Invoice invoice = mustangInvoice();
    call(setter,
         invoice,
         BT19);

    byte[] mustang = mustangXml(invoice,
                                "XRechnung");
    assertTrue(new String(mustang,
                          StandardCharsets.UTF_8).contains(BT19));
    assertEquals(BT19,
                 PANIOLO.read(mustang)
                        .references()
                        .buyerAccountingReference());

    var mapping = MustangInvoiceAdapter.builder()
                                       .zone(ZONE)
                                       .build()
                                       .map(invoice);
    assertTrue(mapping.isLossless(),
               mapping.losses()::toString);
    assertEquals(BT19,
                 mapping.invoice()
                        .references()
                        .buyerAccountingReference());
    byte[] adapted = PANIOLO.toBytes(mapping.invoice(),
                                     InvoiceSyntax.CII);
    assertTrue(new String(adapted,
                          StandardCharsets.UTF_8).contains(BT19));
    assertValid(PANIOLO.validate(adapted));
    assertEquals(mapping.invoice(),
                 PANIOLO.read(adapted));
  }

  @Test
  void keepsTheVatPointDate() {
    Method setter = setter(Invoice.class,
                           "setTaxPointDate",
                           Date.class);
    assumeTrue(setter != null,
               "Mustang " + version() + " has no BT-7 (Invoice.setTaxPointDate)");
    Invoice invoice = mustangInvoice();
    call(setter,
         invoice,
         date(2026,
              10,
              26));
    var mapping = MustangInvoiceAdapter.builder()
                                       .zone(ZONE)
                                       .build()
                                       .map(invoice);
    assertTrue(mapping.isLossless(),
               mapping.losses()::toString);
    assertEquals(LocalDate.of(2026,
                              10,
                              26),
                 mapping.invoice()
                        .taxPointDate());
    assertEquals(PANIOLO.read(mustangXml(invoice,
                                         "XRechnung"))
                        .taxPointDate(),
                 mapping.invoice()
                        .taxPointDate());
    assertValid(PANIOLO.validate(PANIOLO.toBytes(mapping.invoice(),
                                                 InvoiceSyntax.UBL)));
  }

  @Test
  void keepsTheIndustryItemIdentifierInExtended() {
    Method setter = setter(Product.class,
                           "setIndustryAssignedID",
                           String.class);
    assumeTrue(setter != null,
               "Mustang " + version() + " has no Product.setIndustryAssignedID");
    Invoice invoice = mustangInvoice();
    call(setter,
         invoice.getZFItems()[0].getProduct(),
         "IND-4711");
    var extended = MustangInvoiceAdapter.builder()
                                        .processControl(ProcessControl.facturXExtended())
                                        .zone(ZONE)
                                        .build();
    var mapped = extended.toInvoice(invoice);
    assertEquals("IND-4711",
                 mapped.lines()
                       .getFirst()
                       .extended()
                       .industryItemId());
    assertEquals(PANIOLO.read(mustangXml(invoice,
                                         "EXTENDED"))
                        .lines()
                        .getFirst()
                        .extended()
                        .industryItemId(),
                 "IND-4711");
    assertValid(PANIOLO.validate(PANIOLO.toBytes(mapped,
                                                 InvoiceSyntax.CII),
                                 FacturXProfile.EXTENDED));
    // Outside EXTENDED the identifier is reported, not dropped.
    assertTrue(MustangInvoiceAdapter.builder()
                                    .zone(ZONE)
                                    .build()
                                    .map(invoice)
                                    .losses()
                                    .stream()
                                    .anyMatch(loss -> loss.getter()
                                                          .equals("IZUGFeRDExportableProduct.getIndustryAssignedID")));
  }
}
