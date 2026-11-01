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
package io.github.nalukit.paniolo.validation;

import io.github.nalukit.paniolo.xml.util.XmlDocuments;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Confines artifact imports to the chosen local bundle, including symlink resolution.
 */
final class LocalResources {
  private final Path root;

  LocalResources(Path root)
      throws IOException {
    this.root = root.toRealPath();
  }

  Path resolve(String reference,
               String base)
      throws IOException {
    try {
      URI uri = (base == null ?
                 root.toUri() :
                 URI.create(base)).resolve(reference);
      if (!"file".equalsIgnoreCase(uri.getScheme())) {
        throw new IOException("Only local rule resources are permitted: " + uri);
      }
      Path path = Path.of(uri)
                      .toRealPath();
      if (!path.startsWith(root) || !Files.isRegularFile(path)) {
        throw new IOException("Rule resource is outside the bundle: " + path);
      }
      return path;
    } catch (IllegalArgumentException e) {
      throw new IOException("Invalid rule resource URI",
                            e);
    }
  }

  Source source(Path path)
      throws Exception {
    Path safe = resolve(path.toAbsolutePath()
                            .toUri()
                            .toString(),
                        null);
    try (var input = Files.newInputStream(safe)) {
      return new DOMSource(XmlDocuments.parse(input),
                           safe.toUri()
                               .toString());
    }
  }
}
