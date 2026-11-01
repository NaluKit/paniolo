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
package io.github.nalukit.paniolo.rules.xrechnung;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XrechnungRulesTest {
  @Test
  void exposesPinnedRelease() {
    assertEquals("3.0.2",
                 XrechnungRules.VERSION);
    assertFalse(XrechnungRules.RELEASE.contains("${"));
    assertEquals(64,
                 XrechnungRules.SHA256.length());
  }

  @Test
  void extractsOncePerJvm() {
    Path bundle = XrechnungRules.bundle();
    assertSame(bundle,
               XrechnungRules.bundle());
    assertTrue(Files.isRegularFile(bundle.resolve("scenarios.xml")));
    assertTrue(Files.isRegularFile(bundle.resolve("EN16931-UBL-validation.xsl")));
    assertTrue(Files.isRegularFile(bundle.resolve("resources/cii/16b/xsd/CrossIndustryInvoice_100pD16B.xsd")));
  }

  @Test
  void extractsTheOlderReleasesOncePerJvm() {
    assertEquals(java.util.List.of("1.2",
                                   "2.0",
                                   "2.1",
                                   "2.2",
                                   "2.3"),
                 XrechnungRules.LEGACY_VERSIONS);
    assertEquals("2.3.1",
                 XrechnungRules.legacyVersion("2.3"));
    for (String version : XrechnungRules.LEGACY_VERSIONS) {
      Path bundle = XrechnungRules.legacyBundle(version);
      assertSame(bundle,
                 XrechnungRules.legacyBundle(version));
      assertTrue(Files.isRegularFile(bundle.resolve("scenarios.xml")),
                 version);
    }
    assertThrows(IllegalArgumentException.class,
                 () -> XrechnungRules.legacyBundle("3.0"));
  }

  @Test
  void reusesPersistentDirectoryWithSameRules(
      @TempDir Path temp)
      throws Exception {
    Path target = temp.resolve("rules");
    assertEquals(target,
                 XrechnungRules.bundle(target));
    Path scenarios = target.resolve("scenarios.xml");
    var  modified  = Files.getLastModifiedTime(scenarios);
    assertEquals(target,
                 XrechnungRules.bundle(target));
    assertEquals(modified,
                 Files.getLastModifiedTime(scenarios));
  }

  @Test
  void rejectsForeignDirectory(
      @TempDir Path temp)
      throws Exception {
    Files.writeString(temp.resolve("other.txt"),
                      "x");
    assertThrows(IllegalStateException.class,
                 () -> XrechnungRules.bundle(temp));
    assertTrue(Files.exists(temp.resolve("other.txt")));
  }

  @Test
  void shipsLicenseNotices() {
    for (var name : new String[] { "/META-INF/NOTICE",
                                   "/META-INF/licenses/Apache-2.0.txt",
                                   "/META-INF/licenses/EUPL-1.2.txt",
                                   "/META-INF/licenses/OASIS-NOTICE.txt",
                                   "/META-INF/licenses/UNCEFACT-NOTICE.txt" }) {
      assertNotNull(XrechnungRules.class.getResource(name),
                    name);
    }
  }
}
