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
package io.github.nalukit.paniolo.facturx;

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument;
import io.github.nalukit.paniolo.model.common.Attachment;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentFacturXTest {
  @ParameterizedTest
  @EnumSource(value = FacturXProfile.class, names = {"EN16931", "XRECHNUNG"})
  @Timeout(120)
  void sharedFacadeDoesNotMixPdfInvoicesOrAttachments(FacturXProfile profile) throws Exception {
    var shared = Paniolo.create();
    byte[] originalPdf = TestPdfs.pdfA2b();
    byte[] unchanged = originalPdf.clone();
    var start = new CountDownLatch(1);
    var ready = new CountDownLatch(4);
    var pool = Executors.newFixedThreadPool(4);
    try {
      var jobs = new ArrayList<java.util.concurrent.Future<Boolean>>();
      for (int worker = 0; worker < 4; worker++) {
        final int tenant = worker;
        jobs.add(pool.submit(() -> {
          byte[] payload = new byte[8192 + tenant];
          Arrays.fill(payload, (byte) (tenant + 1));
          var invoice = FacturXInvoices.invoice(profile, InvoiceTypeCode.CODE_380)
              .number("PDF-TENANT-" + tenant)
              .addSupportingDocument(new AdditionalSupportingDocument("A-" + tenant, "Data", null,
                  new Attachment(payload, "text/csv", "tenant-" + tenant + ".csv")))
              .build();
          ready.countDown();
          assertTrue(start.await(20, TimeUnit.SECONDS));
          for (int i = 0; i < 6; i++) {
            byte[] pdf = shared.toFacturX(invoice, originalPdf, profile);
            var imported = shared.read(pdf);
            assertEquals(invoice, imported);
            assertArrayEquals(payload, imported.supportingDocuments().get(0).attachment().content());
            assertEquals("tenant-" + tenant + ".csv", imported.supportingDocuments().get(0).attachment().filename());
            assertTrue(shared.validate(pdf).isValid());
          }
          return true;
        }));
      }
      assertTrue(ready.await(20, TimeUnit.SECONDS));
      start.countDown();
      for (var job : jobs) assertTrue(job.get(90, TimeUnit.SECONDS));
      assertArrayEquals(unchanged, originalPdf);
    } finally {
      start.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
    }
  }
}
