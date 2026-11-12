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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

/**
 * Official KoSIT XRechnung validator configuration packaged in this artifact. The archive is
 * extracted to a directory on first use, which can be passed to
 * {@code ValidationProfile.xrechnung(Path, Syntax)} of paniolo.
 *
 * <p>Besides the current release the artifact holds the last KoSIT release of each older XRechnung
 * version ({@link #LEGACY_VERSIONS}), for documents that state such a version.
 */
public final class XrechnungRules {
  private static final System.Logger LOGGER     = System.getLogger(XrechnungRules.class.getName());
  private static final String        BASE       = "/io/github/nalukit/paniolo/rules/xrechnung/";
  private static final String        MARKER     = ".paniolo-xrechnung-rules";
  private static final Properties    PROPERTIES = load();

  /**
   * XRechnung version of the packaged rules, e.g. {@code 3.0.2}.
   */
  public static final String       VERSION         = PROPERTIES.getProperty("version");
  /**
   * KoSIT release date of the packaged validator configuration.
   */
  public static final String       RELEASE         = PROPERTIES.getProperty("release");
  /**
   * SHA-256 of the packaged archive as published by KoSIT.
   */
  public static final String       SHA256          = PROPERTIES.getProperty("sha256");
  /**
   * Older XRechnung versions with a packaged release, as major and minor version, e.g.
   * {@code 2.3}; see {@link #legacyBundle(String)}.
   */
  public static final List<String> LEGACY_VERSIONS = Arrays.stream(PROPERTIES.getProperty("legacy")
                                                                             .split(","))
                                                           .map(String::strip)
                                                           .toList();

  private static final Map<String, Path> LEGACY_BUNDLES = new HashMap<>();
  private static       Path              temporaryBundle;

  private XrechnungRules() {
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
        Path directory = Files.createTempDirectory("paniolo-xrechnung-" + VERSION + "-");
        extract(directory,
                PROPERTIES.getProperty("archive"),
                SHA256);
        Runtime.getRuntime()
               .addShutdownHook(new Thread(() -> deleteQuietly(directory)));
        temporaryBundle = directory;
      } catch (IOException e) {
        throw new UncheckedIOException("Cannot extract XRechnung rules",
                                       e);
      }
    }
    return temporaryBundle;
  }

  /**
   * Returns the full XRechnung version of the packaged release of an older version, e.g.
   * {@code 2.3.1} for {@code 2.3}.
   *
   * @param version major and minor version, one of {@link #LEGACY_VERSIONS}
   * @return the full version
   * @throws IllegalArgumentException if no release of that version is packaged
   */
  public static String legacyVersion(String version) {
    return legacy(version,
                  "version");
  }

  /**
   * Returns the packaged release of an older XRechnung version extracted to a private temporary
   * directory, created once per JVM and version and deleted on exit.
   *
   * @param version major and minor version, one of {@link #LEGACY_VERSIONS}
   * @return the directory with the rules
   * @throws IllegalArgumentException     if no release of that version is packaged
   * @throws java.io.UncheckedIOException if the rules cannot be extracted
   */
  public static synchronized Path legacyBundle(String version) {
    String archive = legacy(version,
                            "archive");
    Path bundle = LEGACY_BUNDLES.get(version);
    if (bundle == null) {
      try {
        Path directory = Files.createTempDirectory("paniolo-xrechnung-" + legacyVersion(version) + "-");
        extract(directory,
                archive,
                legacy(version,
                       "sha256"));
        Runtime.getRuntime()
               .addShutdownHook(new Thread(() -> deleteQuietly(directory)));
        LEGACY_BUNDLES.put(version,
                           directory);
        bundle = directory;
      } catch (IOException e) {
        throw new UncheckedIOException("Cannot extract XRechnung rules " + version,
                                       e);
      }
    }
    return bundle;
  }

  private static String legacy(String version,
                               String key) {
    if (!LEGACY_VERSIONS.contains(version)) {
      throw new IllegalArgumentException("No packaged release of XRechnung " + version + "; packaged are " + LEGACY_VERSIONS);
    }
    return PROPERTIES.getProperty("legacy." + version + "." + key);
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
                   "Reusing XRechnung rules {0} ({1}) in {2}",
                   VERSION,
                   RELEASE,
                   target);
        return target;
      }
      if (Files.exists(target)) {
        if (!Files.isDirectory(target) || !isEmpty(target)) {
          throw new IllegalStateException("Directory exists and does not contain XRechnung rules " + VERSION + " (" + RELEASE + "): " + target);
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
          throw new IllegalStateException("Directory exists and does not contain XRechnung rules " + VERSION + " (" + RELEASE + "): " + target,
                                          e);
        }
      } finally {
        deleteQuietly(staging);
      }
      return target;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot extract XRechnung rules to " + directory,
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
      throw new IOException("Packaged XRechnung rules are corrupt: SHA-256 " + actual + ", expected " + sha256);
    }
    Files.writeString(directory.resolve(MARKER),
                      sha256);
    LOGGER.log(System.Logger.Level.DEBUG,
               "Extracted XRechnung rules {0} to {1} in {2} ms",
               name,
               directory,
               (System.nanoTime() - start) / 1_000_000);
  }

  private static InputStream resource(String name)
      throws IOException {
    InputStream stream = XrechnungRules.class.getResourceAsStream(BASE + name);
    if (stream == null) {
      // The archive is downloaded by the Maven build; IDE builds only copy it once it exists.
      throw new IOException("Packaged resource missing: " +
                            BASE +
                            name +
                            ". The XRechnung rule archive is " +
                            "downloaded by the Maven build of paniolo-rules. Run 'mvn -pl paniolo-rules " +
                            "generate-resources' once (or any Maven build of the module) and reload the Maven project in the IDE.");
    }
    return stream;
  }

  private static Properties load() {
    try (InputStream stream = resource("xrechnung-rules.properties")) {
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
