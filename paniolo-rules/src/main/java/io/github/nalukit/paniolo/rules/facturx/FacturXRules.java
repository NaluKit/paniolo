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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

/**
 * Official Factur-X / ZUGFeRD schemas and Schematrons of FeRD packaged in this artifact. The
 * archive is extracted to a directory on first use, which can be passed to
 * {@code ValidationProfile.facturX(Path, String)} of paniolo.
 *
 * <p>The artifact also holds the schema and Schematron of ZUGFeRD 1.0 ({@link #zugferd1()}) and
 * the rules of the French profile EXTENDED-CTC-FR of FNFE-MPE ({@link #france()}).
 */
public final class FacturXRules {
  /**
   * Path of the compiled Schematron of the profile EXTENDED-CTC-FR in {@link #france()}.
   */
  public static final String FRANCE_EXTENDED_CTC_FR = "FNFE_RFE_INVOICE/CII/EXTENDED-CTC-FR/2xslt/EXTENDED-CTC-FR-CII.xslt";
  /**
   * Path of the compiled Schematron of the French rules BR-FR for invoices exchanged between French
   * parties (flux 2) in {@link #france()}.
   */
  public static final String FRANCE_BR_FR           = "FNFE_RFE_INVOICE/CII/EXTENDED-CTC-FR/2xslt/BR-FR-Flux2-Schematron-CII.xslt";
  private static final System.Logger LOGGER     = System.getLogger(FacturXRules.class.getName());
  private static final String        BASE       = "/io/github/nalukit/paniolo/rules/facturx/";
  private static final String        MARKER     = ".paniolo-facturx-rules";
  private static final Properties    PROPERTIES = load();
  /**
   * Factur-X version of the packaged rules, e.g. {@code 1.09.2}.
   */
  public static final String VERSION                = PROPERTIES.getProperty("version");
  /**
   * ZUGFeRD version of the same release, e.g. {@code 2.5.2}.
   */
  public static final String ZUGFERD                = PROPERTIES.getProperty("zugferd");
  /**
   * Release date of the packaged rules.
   */
  public static final String RELEASE                = PROPERTIES.getProperty("release");
  /**
   * SHA-256 of the packaged archive, which holds the files of the FeRD release unmodified.
   */
  public static final String SHA256                 = PROPERTIES.getProperty("sha256");
  /**
   * Version of the packaged French rules of FNFE-MPE, e.g. {@code 1.4.0.04}: the first two digits
   * name the version of XP Z12-012.
   */
  public static final String FRANCE_VERSION         = PROPERTIES.getProperty("france.version");
  /**
   * Release date of the packaged French rules.
   */
  public static final String FRANCE_RELEASE         = PROPERTIES.getProperty("france.release");
  private static Path temporaryBundle;
  private static Path zugferd1Bundle;
  private static Path franceBundle;
  private static Path orderXBundle;

  private FacturXRules() {
  }

  /**
   * Returns the rules extracted to a private temporary directory, created once per JVM and
   * deleted on exit.
   *
   * @return the directory with the rules
   * @throws java.io.UncheckedIOException if the rules cannot be extracted
   */
  public static synchronized Path bundle() {
    if (temporaryBundle == null) {
      try {
        Path directory = Files.createTempDirectory("paniolo-facturx-" + VERSION + "-");
        extract(directory,
                PROPERTIES.getProperty("archive"),
                SHA256);
        Runtime.getRuntime()
               .addShutdownHook(new Thread(() -> deleteQuietly(directory)));
        temporaryBundle = directory;
      } catch (IOException e) {
        throw new UncheckedIOException("Cannot extract Factur-X rules",
                                       e);
      }
    }
    return temporaryBundle;
  }

  /**
   * Returns the schema and compiled Schematron of ZUGFeRD 1.0 (FeRD, 2014-06-25) extracted to a
   * private temporary directory, created once per JVM and deleted on exit. FeRD no longer publishes
   * them; the files are taken from the Mustang project, see {@code META-INF/NOTICE}.
   *
   * @return the directory with the rules
   * @throws java.io.UncheckedIOException if the rules cannot be extracted
   */
  public static synchronized Path zugferd1() {
    if (zugferd1Bundle == null) {
      try {
        Path directory = Files.createTempDirectory("paniolo-zugferd-1.0-");
        extract(directory,
                PROPERTIES.getProperty("zugferd1.archive"),
                PROPERTIES.getProperty("zugferd1.sha256"));
        Runtime.getRuntime()
               .addShutdownHook(new Thread(() -> deleteQuietly(directory)));
        zugferd1Bundle = directory;
      } catch (IOException e) {
        throw new UncheckedIOException("Cannot extract ZUGFeRD 1.0 rules",
                                       e);
      }
    }
    return zugferd1Bundle;
  }

  /**
   * Returns the Order-X 1.0 rules of FNFE-MPE and FeRD (2021-04-13) extracted to a private temporary
   * directory, created once per JVM and deleted on exit: per profile (BASIC, COMFORT, EXTENDED) the
   * official XSDs and Schematron, and the Schematron compiled to SVRL by
   * {@code scripts/package-orderx-rules.sh}; for {@code ValidationProfile.orderX(Path, String)}.
   *
   * @return the directory with the rules
   * @throws java.io.UncheckedIOException if the rules cannot be extracted
   */
  public static synchronized Path orderX() {
    if (orderXBundle == null) {
      try {
        Path directory = Files.createTempDirectory("paniolo-order-x-1.0-");
        extract(directory,
                PROPERTIES.getProperty("orderx.archive"),
                PROPERTIES.getProperty("orderx.sha256"));
        Runtime.getRuntime()
               .addShutdownHook(new Thread(() -> deleteQuietly(directory)));
        orderXBundle = directory;
      } catch (IOException e) {
        throw new UncheckedIOException("Cannot extract Order-X rules",
                                       e);
      }
    }
    return orderXBundle;
  }

  /**
   * Returns the CII rules of the French profile EXTENDED-CTC-FR (Schematron and compiled XSLT of
   * EXTENDED-CTC-FR and of the rules BR-FR, flux 2) of the FNFE-MPE release {@link #FRANCE_VERSION},
   * extracted to a private temporary directory, created once per JVM and deleted on exit. The paths
   * of the compiled stylesheets are {@link #FRANCE_EXTENDED_CTC_FR} and {@link #FRANCE_BR_FR}.
   *
   * @return the directory with the rules
   * @throws java.io.UncheckedIOException if the rules cannot be extracted
   */
  public static synchronized Path france() {
    if (franceBundle == null) {
      try {
        Path directory = Files.createTempDirectory("paniolo-france-" + FRANCE_VERSION + "-");
        extract(directory,
                PROPERTIES.getProperty("france.archive"),
                PROPERTIES.getProperty("france.sha256"));
        Runtime.getRuntime()
               .addShutdownHook(new Thread(() -> deleteQuietly(directory)));
        franceBundle = directory;
      } catch (IOException e) {
        throw new UncheckedIOException("Cannot extract the French EXTENDED-CTC-FR rules",
                                       e);
      }
    }
    return franceBundle;
  }

  /**
   * Extracts the rules to a persistent directory and returns it. An existing directory with the
   * same rules is reused; any other existing content is rejected. The caller is responsible for
   * protecting the directory against modification by others.
   *
   * @param directory the target directory; created if it does not exist
   * @return the absolute directory with the rules
   * @throws IllegalStateException        if the directory exists with other content
   * @throws java.io.UncheckedIOException if the rules cannot be extracted
   */
  public static Path bundle(Path directory) {
    try {
      Path target = directory.toAbsolutePath()
                             .normalize();
      if (isCurrent(target)) {
        LOGGER.log(System.Logger.Level.DEBUG,
                   "Reusing Factur-X rules {0} ({1}) in {2}",
                   VERSION,
                   RELEASE,
                   target);
        return target;
      }
      if (Files.exists(target)) {
        if (!Files.isDirectory(target) || !isEmpty(target)) {
          throw new IllegalStateException("Directory exists and does not contain Factur-X rules " + VERSION + " (" + RELEASE + "): " + target);
        }
        Files.delete(target);
      }
      Files.createDirectories(target.getParent());
      Path staging = Files.createTempDirectory(target.getParent(),
                                               target.getFileName() + ".tmp-");
      try {
        extract(staging,
                PROPERTIES.getProperty("archive"),
                SHA256);
        Files.move(staging,
                   target,
                   StandardCopyOption.ATOMIC_MOVE);
      } catch (FileAlreadyExistsException | DirectoryNotEmptyException e) {
        // Another process may have extracted the same rules concurrently.
        if (!isCurrent(target)) {
          throw new IllegalStateException("Directory exists and does not contain Factur-X rules " + VERSION + " (" + RELEASE + "): " + target,
                                          e);
        }
      } finally {
        deleteQuietly(staging);
      }
      return target;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot extract Factur-X rules to " + directory,
                                     e);
    }
  }

  private static boolean isEmpty(Path directory)
      throws IOException {
    try (Stream<Path> entries = Files.list(directory)) {
      return entries.findAny()
                    .isEmpty();
    }
  }

  private static boolean isCurrent(Path directory)
      throws IOException {
    Path marker = directory.resolve(MARKER);
    return Files.isRegularFile(marker) &&
           Files.readString(marker)
                .strip()
                .equals(SHA256);
  }

  private static void extract(Path directory,
                              String name,
                              String sha256)
      throws IOException {
    long          start = System.nanoTime();
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    try (InputStream archive = resource(name); var digested = new DigestInputStream(archive,
                                                                                    digest); var zip = new ZipInputStream(digested)) {
      for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        Path path = directory.resolve(entry.getName())
                             .normalize();
        if (!path.startsWith(directory) || path.equals(directory)) {
          throw new IOException("Archive entry outside target directory: " + entry.getName());
        }
        if (entry.isDirectory()) {
          Files.createDirectories(path);
        } else {
          Files.createDirectories(path.getParent());
          Files.copy(zip,
                     path);
        }
      }
      // Consume the central directory so the digest covers the complete archive.
      digested.transferTo(OutputStream.nullOutputStream());
    }
    String actual = HexFormat.of()
                             .formatHex(digest.digest());
    if (!actual.equals(sha256)) {
      throw new IOException("Packaged Factur-X rules are corrupt: SHA-256 " + actual + ", expected " + sha256);
    }
    Files.writeString(directory.resolve(MARKER),
                      sha256);
    LOGGER.log(System.Logger.Level.DEBUG,
               "Extracted Factur-X rules {0} to {1} in {2} ms",
               name,
               directory,
               (System.nanoTime() - start) / 1_000_000);
  }

  private static InputStream resource(String name)
      throws IOException {
    InputStream stream = FacturXRules.class.getResourceAsStream(BASE + name);
    if (stream == null) {
      throw new IOException("Packaged resource missing: " + BASE + name);
    }
    return stream;
  }

  private static Properties load() {
    try (InputStream stream = resource("facturx-rules.properties")) {
      var properties = new Properties();
      properties.load(stream);
      return properties;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void deleteQuietly(Path directory) {
    if (!Files.exists(directory)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(directory)) {
      paths.sorted(Comparator.reverseOrder())
           .forEach(path -> path.toFile()
                                .delete());
    } catch (IOException e) {
      // Best effort cleanup of a temporary directory.
      LOGGER.log(System.Logger.Level.DEBUG,
                 "Cannot delete " + directory,
                 e);
    }
  }
}
