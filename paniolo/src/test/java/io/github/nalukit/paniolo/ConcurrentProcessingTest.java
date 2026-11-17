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
package io.github.nalukit.paniolo;

import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument;
import io.github.nalukit.paniolo.model.common.Attachment;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentProcessingTest {
  private record Request(Invoice invoice, InvoiceSyntax syntax, byte[] xml,
                         List<?> findings, boolean valid) {}

  @Test
  @Timeout(120)
  void coldSharedFacadeKeepsFindingsAndAttachmentsSeparate() throws Exception {
    var baseline = Paniolo.create();
    var requests = new ArrayList<Request>();
    for (int i = 0; i < 12; i++) {
      byte[] bytes = new byte[8192 + i];
      Arrays.fill(bytes, (byte) (i + 1));
      var type = i % 3 == 0 ? InvoiceTypeCode.CODE_381 : InvoiceTypeCode.CODE_380;
      var invoice = TestInvoices.builder(type, "TENANT-" + i)
          .buyerReference(i % 4 == 0 ? null : "REFERENCE-" + i)
          .addSupportingDocument(new AdditionalSupportingDocument("DOC-" + i, "Attachment", null,
              new Attachment(bytes, "text/csv", "tenant-" + i + ".csv")))
          .build();
      var syntax = InvoiceSyntax.values()[i % 2];
      byte[] xml = baseline.toBytes(invoice, syntax);
      var validation = baseline.validate(xml);
      assertEquals(i % 4 != 0, validation.isValid(), validation.messages().toString());
      requests.add(new Request(invoice, syntax, xml, validation.messages(), validation.isValid()));
      Arrays.fill(bytes, (byte) 0);
    }
    var shared = Paniolo.create();
    var ready = new CountDownLatch(requests.size());
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(requests.size());
    try {
      var jobs = requests.stream().map(request -> executor.submit(() -> {
        ready.countDown();
        assertTrue(start.await(20, TimeUnit.SECONDS));
        for (int i = 0; i < 8; i++) {
          var result = shared.validate(request.xml());
          assertEquals(request.valid(), result.isValid());
          assertEquals(request.findings(), result.messages());
          assertEquals(request.invoice(), shared.read(request.xml(), ReadMode.STRICT).invoice());
          assertArrayEquals(request.xml(), shared.toBytes(request.invoice(), request.syntax()));
          byte[] copy = request.invoice().supportingDocuments().get(0).attachment().content();
          Arrays.fill(copy, (byte) 0);
        }
        return true;
      })).toList();
      assertTrue(ready.await(20, TimeUnit.SECONDS));
      start.countDown();
      for (var job : jobs) assertTrue(job.get(90, TimeUnit.SECONDS));
    } finally {
      start.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
    }
  }

  @Test
  @Timeout(60)
  void failedRequestsDoNotPoisonReadersOrWriters() throws Exception {
    var shared = Paniolo.create();
    var invoice = TestInvoices.invoice(InvoiceTypeCode.CODE_380, "AFTER-ERROR");
    var ready = new CountDownLatch(4);
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(4);
    try {
      var jobs = new ArrayList<java.util.concurrent.Future<Boolean>>();
      for (int worker = 0; worker < 4; worker++) {
        final var syntax = InvoiceSyntax.values()[worker % 2];
        jobs.add(executor.submit(() -> {
          byte[] xml = shared.toBytes(invoice, syntax);
          ready.countDown();
          assertTrue(start.await(20, TimeUnit.SECONDS));
          for (int i = 0; i < 30; i++) {
            assertThrows(IllegalArgumentException.class, () -> shared.read("<broken/>"));
            assertEquals(invoice, shared.read(xml));
            assertArrayEquals(xml, shared.toBytes(invoice, syntax));
          }
          return true;
        }));
      }
      assertTrue(ready.await(20, TimeUnit.SECONDS));
      start.countDown();
      for (var job : jobs) assertTrue(job.get(30, TimeUnit.SECONDS));
    } finally {
      start.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
    }
  }

  @Test
  @Timeout(120)
  void repeatedReadAndWriteFitsInABoundedChildHeap(@TempDir Path temp) throws Exception {
    Path log = temp.resolve("bounded-heap.log");
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-Xmx128m", "-Djava.awt.headless=true", "-cp", classpath, HeapProbe.class.getName())
        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
    try {
      assertTrue(process.waitFor(100, TimeUnit.SECONDS), "bounded-heap child timed out");
      assertEquals(0, process.exitValue(), () -> {
        try { return Files.readString(log); } catch (Exception e) { return e.toString(); }
      });
      assertTrue(Files.readString(log).contains("100 roundtrips completed"));
    } finally {
      if (process.isAlive()) process.destroyForcibly().waitFor();
    }
  }

  public static final class HeapProbe {
    public static void main(String[] args) throws Exception {
      var api = Paniolo.create();
      byte[] content = new byte[256 * 1024];
      for (int i = 0; i < content.length; i++) content[i] = (byte) (i * 17);
      var line = TestInvoices.invoice(InvoiceTypeCode.CODE_380, "HEAP").lines().get(0);
      var lines = new ArrayList<io.github.nalukit.paniolo.model.common.InvoiceLine>();
      for (int i = 0; i < 500; i++) lines.add(line.toBuilder().id(Integer.toString(i + 1)).build());
      var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380, "HEAP")
          .lines(lines).addSupportingDocument(new AdditionalSupportingDocument("A", "Data", null,
              new Attachment(content, "text/csv", "heap.csv"))).build();
      var pool = Executors.newFixedThreadPool(4);
      try {
        var jobs = new ArrayList<java.util.concurrent.Future<Boolean>>();
        for (int worker = 0; worker < 4; worker++) {
          final int index = worker;
          jobs.add(pool.submit(() -> {
            for (int i = 0; i < 25; i++) {
              var syntax = InvoiceSyntax.values()[(index + i) % 2];
              byte[] xml = api.toBytes(invoice, syntax);
              if (!invoice.equals(api.read(xml))) throw new AssertionError("roundtrip changed invoice " + i);
            }
            return true;
          }));
        }
        for (var job : jobs) if (!job.get(60, TimeUnit.SECONDS)) throw new AssertionError("worker failed");
      } finally {
        pool.shutdownNow();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) throw new AssertionError("workers did not stop");
      }
      System.out.println("100 roundtrips completed");
    }
  }
}
