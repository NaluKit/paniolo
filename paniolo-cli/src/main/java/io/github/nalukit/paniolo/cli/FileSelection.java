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

import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Selection of the files of directory arguments, shared by the validation commands and
 * {@code inventory}: directories are expanded into the files they contain, with
 * {@code --recursive} including sub-directories, filtered by {@code --include} and
 * {@code --exclude}.
 */
final class FileSelection {
  @Option(names = { "-r",
                    "--recursive" }, description = "Also process the files in sub-directories of a directory argument.")
  boolean recursive;

  @Option(names = "--include", paramLabel = "GLOB", description = "Process only files of a directory argument whose path relative to it matches, e.g. '**.xml'; repeatable. Default: *.xml and *.pdf files.")
  List<String> includes;

  @Option(names = "--exclude", paramLabel = "GLOB", description = "Skip files of a directory argument whose path relative to it matches, e.g. 'archive/**'; repeatable.")
  List<String> excludes;

  private static List<PathMatcher> matchers(List<String> globs) {
    var result = new ArrayList<PathMatcher>();
    if (globs != null) {
      for (String glob : globs) {
        result.add(FileSystems.getDefault()
                              .getPathMatcher("glob:" + glob));
      }
    }
    return result;
  }

  private static boolean matches(List<PathMatcher> matchers,
                                 Path relative) {
    for (var matcher : matchers) {
      // A pattern without directory, e.g. *.xml, applies to the file name at any depth.
      if (matcher.matches(relative) || matcher.matches(relative.getFileName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * The files to process: files as given, directories expanded in sorted order. A directory that
   * cannot be listed becomes an entry with its error.
   */
  List<Input> inputs(List<Path> paths) {
    var include = matchers(includes == null || includes.isEmpty() ?
                           List.of("*.xml",
                                   "*.pdf",
                                   "*.XML",
                                   "*.PDF") :
                           includes);
    var exclude = matchers(excludes);
    var result  = new ArrayList<Input>();
    for (Path path : paths) {
      if (!Files.isDirectory(path)) {
        result.add(new Input(path,
                             null));
        continue;
      }
      var found = new ArrayList<Input>();
      try {
        Files.walkFileTree(path,
                           new SimpleFileVisitor<>() {
                             @Override
                             public FileVisitResult preVisitDirectory(Path directory,
                                                                      BasicFileAttributes attributes) {
                               if (directory.equals(path)) {
                                 return FileVisitResult.CONTINUE;
                               }
                               return !recursive || matches(exclude,
                                                            path.relativize(directory)) ?
                                      FileVisitResult.SKIP_SUBTREE :
                                      FileVisitResult.CONTINUE;
                             }

                             @Override
                             public FileVisitResult visitFile(Path file,
                                                              BasicFileAttributes attributes) {
                               Path relative = path.relativize(file);
                               if (attributes.isRegularFile() && matches(include,
                                                                         relative) && !matches(exclude,
                                                                                               relative)) {
                                 found.add(new Input(file,
                                                     null));
                               }
                               return FileVisitResult.CONTINUE;
                             }

                             @Override
                             public FileVisitResult visitFileFailed(Path file,
                                                                    IOException e) {
                               found.add(new Input(file,
                                                   "Cannot read " + file + ": " + e.getMessage()));
                               return FileVisitResult.CONTINUE;
                             }
                           });
      } catch (IOException e) {
        found.add(new Input(path,
                            "Cannot read " + path + ": " + e.getMessage()));
      }
      found.sort((a, b) -> a.path()
                            .compareTo(b.path()));
      result.addAll(found);
    }
    return result;
  }

  /**
   * A file to process, or a path that could not be listed with its error.
   */
  record Input(Path path, String error) {
  }
}
