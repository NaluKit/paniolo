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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Checks how current the vulnerability data of a dependency-check data directory is. The age is
 * taken from the metadata that dependency-check writes into its H2 database after a successful NVD
 * update, not from file times:
 * <ul>
 *   <li>{@code nvd.api.last.modified}: the newest modification time of the NVD data in the
 *       database; this is the age that is checked</li>
 *   <li>{@code nvd.api.last.checked}: the time of the last successful update; logged</li>
 * </ul>
 * Missing or unreadable metadata and times in the future fail the check.
 * <p>
 * Usage: {@code java -cp h2.jar OwaspDatabaseAge.java <data directory> <maximum age in days>
 * [<now as ISO instant>]}; the third argument is for tests. Exit status 0 if the data is current
 * enough, 1 otherwise, 2 for a usage error.
 */
public class OwaspDatabaseAge {

  /** File name of the dependency-check database ({@code data.file_name}). */
  private static final String   DATABASE      = "odc.mv.db";
  /** Default credentials of dependency-check ({@code data.user} and {@code data.password}). */
  private static final String   USER          = "dcuser";
  private static final String   PASSWORD      = "DC-Pass1337!";
  private static final String   LAST_MODIFIED = "nvd.api.last.modified";
  private static final String   LAST_CHECKED  = "nvd.api.last.checked";
  /** Allowed clock difference between the runner and the time of the data. */
  private static final Duration CLOCK_SKEW    = Duration.ofHours(1);
  /** Format of dependency-check ({@code DatabaseProperties.save(String, ZonedDateTime)}). */
  private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssX");

  public static void main(String[] args) throws Exception {
    if (args.length < 2 || args.length > 3) {
      System.err.println("usage: OwaspDatabaseAge <data directory> <maximum age in days> [<now>]");
      System.exit(2);
    }
    Path    directory = Path.of(args[0]);
    long    maxDays   = Long.parseLong(args[1]);
    Instant now       = args.length == 3 ? Instant.parse(args[2]) : Instant.now();
    try {
      System.out.println(check(directory, maxDays, now));
    } catch (IllegalStateException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }

  static String check(Path directory,
                      long maxDays,
                      Instant now) throws Exception {
    Path database = directory.resolve(DATABASE);
    if (!Files.isRegularFile(database)) {
      throw new IllegalStateException("No vulnerability database " + database);
    }
    // open a copy, so that the database used by the scan is not touched
    Path copy = Files.createTempDirectory("owasp-db-age");
    try {
      Files.copy(database, copy.resolve(DATABASE), StandardCopyOption.REPLACE_EXISTING);
      String url = "jdbc:h2:file:" + copy.resolve("odc")
                                         .toAbsolutePath() + ";IFEXISTS=TRUE";
      try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD)) {
        Instant lastModified = timestamp(connection, LAST_MODIFIED);
        Instant lastChecked  = timestamp(connection, LAST_CHECKED);
        for (Instant time : new Instant[] { lastModified, lastChecked }) {
          if (time.isAfter(now.plus(CLOCK_SKEW))) {
            throw new IllegalStateException("The vulnerability database has a time in the future: " + time + " (now " + now + ")");
          }
        }
        long days = Duration.between(lastModified, now)
                            .toDays();
        String summary = "Vulnerability database " + database + ": NVD data modified until " + lastModified + " (" + LAST_MODIFIED + "), last successful update " + lastChecked + " (" + LAST_CHECKED + "), " + days + " days old, limit " + maxDays + " days";
        if (days > maxDays) {
          throw new IllegalStateException(summary + ": the data is too old");
        }
        return summary;
      }
    } finally {
      try (var files = Files.list(copy)) {
        for (Path file : files.toList()) {
          Files.deleteIfExists(file);
        }
      }
      Files.deleteIfExists(copy);
    }
  }

  private static Instant timestamp(Connection connection,
                                   String key) throws Exception {
    Optional<String> value = Optional.empty();
    try (PreparedStatement statement = connection.prepareStatement("SELECT `value` FROM properties WHERE id = ?")) {
      statement.setString(1, key);
      try (ResultSet result = statement.executeQuery()) {
        if (result.next()) {
          value = Optional.ofNullable(result.getString(1));
        }
      }
    }
    String text = value.map(String::trim)
                       .filter(s -> !s.isEmpty())
                       .orElseThrow(() -> new IllegalStateException("The vulnerability database has no " + key + "; it was never updated successfully"));
    try {
      if (text.matches("[0-9]{1,12}")) {
        // older versions stored epoch seconds
        return Instant.ofEpochSecond(Long.parseLong(text));
      }
      return OffsetDateTime.parse(text, FORMAT)
                           .toInstant();
    } catch (DateTimeParseException | NumberFormatException e) {
      throw new IllegalStateException("The vulnerability database has an invalid " + key + ": " + text);
    }
  }
}
