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
package io.github.nalukit.paniolo.cli;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the license notices of the packaged executable JAR ({@code *-all.jar}).
 * Runs after {@code package} through maven-failsafe, so it sees the shaded artifact
 * exactly as it is distributed.
 */
class LicenseNoticesIT {

  private static final String THIRD_PARTY = "META-INF/THIRD-PARTY.txt";
  private static final String STAX_UTILS_LICENSE = "META-INF/licenses/stax-utils-BSD-3-Clause.txt";
  private static final String XMP_LICENSE = "META-INF/licenses/verapdf-xmp-core-BSD-3-Clause.txt";

  private static Path jar;

  @BeforeAll
  static void locateJar() {
    String path = System.getProperty("paniolo.cli.shadedJar");
    assertNotNull(path, "system property paniolo.cli.shadedJar is not set");
    jar = Path.of(path);
    assertTrue(Files.isRegularFile(jar), "shaded JAR not found: " + jar);
  }

  @Test
  void bundledStaxUtilsComesWithItsBsdNotices() throws IOException {
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      if (zip.stream().noneMatch(e -> e.getName().startsWith("javanet/staxutils/"))) {
        return;
      }
      String license = read(zip, STAX_UTILS_LICENSE);
      for (String notice : List.of("Copyright (c) 2004, Christian Niles, unit12.net",
                                   "Copyright (c) 2004, Christian Niles, Unit12",
                                   "Copyright (c) 2004, Sun Microsystems, Inc.",
                                   "Copyright (c) 2006, John Kristian",
                                   "Neither the name of Christian Niles, Unit12, nor the names of its",
                                   "Neither the name of Sun Microsystems, Inc. nor the names of its",
                                   "Neither the name of StAX-Utils nor the names of its contributors")) {
        assertTrue(license.contains(notice), STAX_UTILS_LICENSE + " lacks: " + notice);
      }
      assertEquals(3, count(license, "Redistributions in binary form must reproduce the above"),
                   "binary redistribution condition of each copyright holder");
      assertEquals(3, count(license, "THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS"),
                   "disclaimer of each copyright holder");
      String thirdParty = read(zip, THIRD_PARTY);
      assertTrue(thirdParty.contains("net.java.dev.stax-utils:stax-utils 20070216"),
                 THIRD_PARTY + " does not list stax-utils");
      assertTrue(thirdParty.contains(STAX_UTILS_LICENSE), THIRD_PARTY + " does not refer to " + STAX_UTILS_LICENSE);
    }
  }

  @Test
  void bundledVeraPdfXmpComesWithAdobeAndVeraPdfNotices() throws IOException {
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      if (zip.stream().noneMatch(e -> e.getName().startsWith("org/verapdf/xmp/"))) {
        return;
      }
      String license = read(zip, XMP_LICENSE);
      for (String notice : List.of("Copyright (c) 2009, Adobe Systems Incorporated",
                                   "Copyright 2001 Adobe Systems Incorporated",
                                   "Copyright 2006 Adobe Systems Incorporated",
                                   "Copyright 2006-2007 Adobe Systems Incorporated",
                                   "Copyright (c) 2015-2026, veraPDF Consortium.",
                                   "Redistributions in binary form must reproduce the above copyright notice",
                                   "Neither the name of Adobe Systems Incorporated, nor the names of its contributors",
                                   "THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\"")) {
        assertTrue(license.contains(notice), XMP_LICENSE + " lacks: " + notice);
      }
      assertFalse(license.contains("${"), XMP_LICENSE + " contains unresolved placeholders");
      String thirdParty = read(zip, THIRD_PARTY);
      assertTrue(thirdParty.contains("org.verapdf:verapdf-xmp-core"), THIRD_PARTY + " does not list verapdf-xmp-core");
      assertTrue(thirdParty.contains(XMP_LICENSE), THIRD_PARTY + " does not refer to " + XMP_LICENSE);
    }
  }

  @Test
  void saxonSourceReferenceMatchesBundledVersion() throws Exception {
    String bundled = bundledSaxonVersion();
    String thirdParty;
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      thirdParty = read(zip, THIRD_PARTY);
    }
    assertFalse(thirdParty.contains("${"), THIRD_PARTY + " contains unresolved placeholders");
    assertTrue(thirdParty.contains("Saxon-HE " + bundled + ","), THIRD_PARTY + " names another Saxon version than " + bundled);
    assertTrue(thirdParty.contains("/net/sf/saxon/Saxon-HE/" + bundled + "/Saxon-HE-" + bundled + "-sources.jar"),
               THIRD_PARTY + " has no source reference for Saxon-HE " + bundled);

    List<String> foreign = new ArrayList<>();
    Matcher m = Pattern.compile("Saxon-HE[ /-](\\d+(?:\\.\\d+)*)|saxon(\\d+)-").matcher(thirdParty);
    while (m.find()) {
      String version = m.group(1) != null ? m.group(1) : m.group(2);
      if (!bundled.equals(version) && !bundled.startsWith(version + ".")) {
        foreign.add(m.group());
      }
    }
    assertTrue(foreign.isEmpty(), THIRD_PARTY + " still refers to other Saxon versions: " + foreign);
  }

  /** Reads the product version from the Saxon classes inside the shaded JAR itself. */
  private static String bundledSaxonVersion() throws Exception {
    try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()},
                                                     ClassLoader.getPlatformClassLoader())) {
      Class<?> version = Class.forName("net.sf.saxon.Version", true, loader);
      return (String) version.getMethod("getProductVersion").invoke(null);
    }
  }

  private static String read(ZipFile zip, String name) throws IOException {
    ZipEntry entry = zip.getEntry(name);
    assertNotNull(entry, name + " is missing in " + jar.getFileName());
    try (InputStream in = zip.getInputStream(entry)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static int count(String text, String part) {
    int n = 0;
    for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
      n++;
    }
    return n;
  }
}
