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
package io.github.nalukit.paniolo.rules.facturx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacturXRulesTest {
  @Test
  void exposesPinnedRelease() {
    assertEquals("1.09.2",
                 FacturXRules.VERSION);
    assertEquals("2.5.2",
                 FacturXRules.ZUGFERD);
    assertFalse(FacturXRules.RELEASE.contains("${"));
    assertEquals(64,
                 FacturXRules.SHA256.length());
  }

  @Test
  void extractsAllProfilesOncePerJvm() {
    Path bundle = FacturXRules.bundle();
    assertSame(bundle,
               FacturXRules.bundle());
    for (var profile : new String[][] { { "0_Factur-X_1.09.2_MINIMUM",
                                          "MINIMUM",
                                          "MINIMUM" },
                                        { "1_Factur-X_1.09.2_BASICWL",
                                          "BASICWL",
                                          "BASIC-WL" },
                                        { "2_Factur-X_1.09.2_BASIC",
                                          "BASIC",
                                          "BASIC" },
                                        { "3_Factur-X_1.09.2_EN16931",
                                          "EN16931",
                                          "EN16931" },
                                        { "4_Factur-X_1.09.2_EXTENDED",
                                          "EXTENDED",
                                          "EXTENDED" } }) {
      Path directory = bundle.resolve(profile[0]);
      assertTrue(Files.isRegularFile(directory.resolve("Factur-X_1.09.2_" + profile[1] + ".xsd")),
                 profile[0]);
      assertTrue(Files.isRegularFile(directory.resolve("_XSLT_" + profile[2] + "/FACTUR-X_" + profile[2] + ".xslt")),
                 profile[0]);
      assertTrue(Files.isRegularFile(directory.resolve("_XSLT_" + profile[2] + "/FACTUR-X_" + profile[2] + "_codedb.xml")),
                 profile[0]);
    }
  }

  @Test
  void holdsTheFilesAsPublished()
      throws Exception {
    Path bundle = FacturXRules.bundle();
    var  lines  = Files.readAllLines(bundle.resolve("SHA256SUMS"));
    assertEquals(40,
                 lines.size());
    for (String line : lines) {
      String expected = line.substring(0,
                                       64);
      // The checksums list the paths of the release package, below its folder "Schema".
      Path file = bundle.resolve(line.substring(66)
                                     .replaceFirst("^Schema/",
                                                   ""));
      String actual = HexFormat.of()
                               .formatHex(MessageDigest.getInstance("SHA-256")
                                                       .digest(Files.readAllBytes(file)));
      assertEquals(expected,
                   actual,
                   line);
    }
  }

  @Test
  void extractsZugferd1OncePerJvm()
      throws Exception {
    Path bundle = FacturXRules.zugferd1();
    assertSame(bundle,
               FacturXRules.zugferd1());
    assertTrue(Files.isRegularFile(bundle.resolve("schema/ZUGFeRD1p0.xsd")));
    assertTrue(Files.isRegularFile(bundle.resolve("schematron/ZUGFeRD_1p0.sch")));
    assertTrue(Files.isRegularFile(bundle.resolve("schematron/ZUGFeRD_1p0.xslt")));
    for (String line : Files.readAllLines(bundle.resolve("SHA256SUMS"))) {
      assertEquals(line.substring(0,
                                  64),
                   HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256")
                                                    .digest(Files.readAllBytes(bundle.resolve(line.substring(66))))),
                   line);
    }
  }

  @Test
  void extractsOrderXOncePerJvm()
      throws Exception {
    Path bundle = FacturXRules.orderX();
    assertSame(bundle,
               FacturXRules.orderX());
    for (String level : new String[] { "Basic",
                                       "Comfort",
                                       "Extended" }) {
      String upper = level.toUpperCase(java.util.Locale.ROOT);
      assertTrue(Files.isRegularFile(bundle.resolve(level + "/XSD/SCRDMCCBDACIOMessageStructure_100pD20B.xsd")),
                 level);
      assertTrue(Files.isRegularFile(bundle.resolve(level + "/Schematron/SCRDMCCBDACIOMessageStructure_100pD20B_" + upper + ".sch")),
                 level);
      assertTrue(Files.isRegularFile(bundle.resolve(level + "/Schematron/SCRDMCCBDACIOMessageStructure_100pD20B_" + upper + ".xslt")),
                 level);
    }
    assertTrue(Files.isRegularFile(bundle.resolve("Nutzungsrechte.txt")));
    assertTrue(Files.isRegularFile(bundle.resolve("licenses/MIT-ISO-Schematron.txt")));
    // Each line: checksum, path, and origin (official, compiled, or license).
    for (String line : Files.readAllLines(bundle.resolve("SHA256SUMS"))) {
      String[] parts = line.split("  ");
      assertEquals(parts[0],
                   HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256")
                                                    .digest(Files.readAllBytes(bundle.resolve(parts[1])))),
                   line);
      assertEquals(parts[1].startsWith("licenses/") ?
                   "license" :
                   parts[1].endsWith(".xslt") ?
                   "compiled" :
                   "official",
                   parts[2],
                   line);
    }
  }

  @Test
  void extractsTheFrenchRulesOncePerJvm()
      throws Exception {
    assertEquals("1.4.0.04",
                 FacturXRules.FRANCE_VERSION);
    assertFalse(FacturXRules.FRANCE_RELEASE.contains("${"));
    Path bundle = FacturXRules.france();
    assertSame(bundle,
               FacturXRules.france());
    assertTrue(Files.isRegularFile(bundle.resolve(FacturXRules.FRANCE_EXTENDED_CTC_FR)));
    assertTrue(Files.isRegularFile(bundle.resolve(FacturXRules.FRANCE_BR_FR)));
    assertTrue(Files.isRegularFile(bundle.resolve("LICENSE")));
    // The checksums list the paths of the release, which the archive keeps.
    var lines = Files.readAllLines(bundle.resolve("SHA256SUMS"));
    assertEquals(6,
                 lines.size());
    for (String line : lines) {
      assertEquals(line.substring(0,
                                  64),
                   HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256")
                                                    .digest(Files.readAllBytes(bundle.resolve(line.substring(66))))),
                   line);
    }
  }

  @Test
  void reusesPersistentDirectoryWithSameRules(
      @TempDir Path temp)
      throws Exception {
    Path target = temp.resolve("rules");
    assertEquals(target,
                 FacturXRules.bundle(target));
    Path sums     = target.resolve("SHA256SUMS");
    var  modified = Files.getLastModifiedTime(sums);
    assertEquals(target,
                 FacturXRules.bundle(target));
    assertEquals(modified,
                 Files.getLastModifiedTime(sums));
  }

  @Test
  void rejectsForeignDirectory(
      @TempDir Path temp)
      throws Exception {
    Files.writeString(temp.resolve("other.txt"),
                      "x");
    assertThrows(IllegalStateException.class,
                 () -> FacturXRules.bundle(temp));
    assertTrue(Files.exists(temp.resolve("other.txt")));
  }

  @Test
  void shipsLicenseNotices() {
    for (var name : new String[] { "/META-INF/NOTICE",
                                   "/META-INF/licenses/Apache-2.0.txt",
                                   "/META-INF/licenses/MIT-ISO-Schematron.txt" }) {
      assertNotNull(FacturXRules.class.getResource(name),
                    name);
    }
  }
}
