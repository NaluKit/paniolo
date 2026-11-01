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

import io.github.nalukit.paniolo.config.PdfAttachment;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Input and output of the commands: files, {@code -} for standard input and output.
 */
final class Io {
  /**
   * Path that stands for standard input or standard output.
   */
  static final String STANDARD_STREAM = "-";

  private final InputStream in;
  private final PrintStream out;

  Io(InputStream in,
     PrintStream out) {
    this.in  = in;
    this.out = out;
  }

  /**
   * Returns true if the content looks like JSON, i.e. starts with an object after white space and
   * an optional byte order mark.
   */
  static boolean isJson(byte[] content) {
    for (int i = 0; i < content.length; i++) {
      int b = content[i] & 0xFF;
      if (i < 3 && (b == 0xEF || b == 0xBB || b == 0xBF)) {
        continue;
      }
      if (!Character.isWhitespace(b)) {
        return b == '{';
      }
    }
    return false;
  }

  private static boolean isStandardStream(Path path) {
    return STANDARD_STREAM.equals(path.toString());
  }

  private static String mimeType(Path file) {
    try {
      String probed = Files.probeContentType(file);
      if (probed != null) {
        return probed;
      }
    } catch (IOException e) {
      // fall back to the file extension
    }
    String name = file.getFileName()
                      .toString()
                      .toLowerCase(Locale.ROOT);
    if (name.endsWith(".pdf")) {
      return "application/pdf";
    }
    if (name.endsWith(".xml")) {
      return "text/xml";
    }
    if (name.endsWith(".csv")) {
      return "text/csv";
    }
    if (name.endsWith(".txt")) {
      return "text/plain";
    }
    if (name.endsWith(".png")) {
      return "image/png";
    }
    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
      return "image/jpeg";
    }
    if (name.endsWith(".xlsx")) {
      return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }
    return "application/octet-stream";
  }

  /**
   * Reads a file, or standard input for {@code -}.
   */
  byte[] read(Path path) {
    try {
      if (isStandardStream(path)) {
        return in.readAllBytes();
      }
      return Files.readAllBytes(path);
    } catch (NoSuchFileException e) {
      throw new UncheckedIOException("File not found: " + path,
                                     e);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + path + ": " + e.getMessage(),
                                     e);
    }
  }

  /**
   * Writes to a file, or to standard output for {@code null} and {@code -}.
   */
  void write(Path path,
             byte[] content) {
    try {
      if (path == null || isStandardStream(path)) {
        out.write(content);
        out.flush();
        return;
      }
      Path parent = path.toAbsolutePath()
                        .getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.write(path,
                  content);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write " + path + ": " + e.getMessage(),
                                     e);
    }
  }

  /**
   * Writes text as UTF-8, followed by a line break if it does not end with one.
   */
  void write(Path path,
             String text) {
    write(path,
          (text.endsWith("\n") ?
           text :
           text + "\n").getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Reads files to embed into a PDF; the MIME type is guessed from the file name.
   */
  List<PdfAttachment> attachments(List<Path> files) {
    var attachments = new ArrayList<PdfAttachment>();
    for (Path file : files) {
      attachments.add(PdfAttachment.of(file.getFileName()
                                           .toString(),
                                       read(file),
                                       mimeType(file)));
    }
    return attachments;
  }
}
