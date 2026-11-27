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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SoftLazyTest {

  @Test
  void createsOnceWhileHeld() {
    var lazy    = new SoftLazy<Object>();
    var created = new AtomicInteger();
    var first   = lazy.get(() -> {
      created.incrementAndGet();
      return new Object();
    });
    var second = lazy.get(() -> {
      created.incrementAndGet();
      return new Object();
    });
    assertSame(first,
               second);
    assertEquals(1,
                 created.get());
  }

  @Test
  void createsAgainAfterReclaim() {
    var lazy    = new SoftLazy<Object>();
    var created = new AtomicInteger();
    var first   = lazy.get(() -> {
      created.incrementAndGet();
      return new Object();
    });
    lazy.evict();
    var second = lazy.get(() -> {
      created.incrementAndGet();
      return new Object();
    });
    assertNotSame(first,
                  second);
    assertEquals(2,
                 created.get());
  }

  @Test
  void failedCreationIsRetried() {
    var lazy = new SoftLazy<Object>();
    assertThrows(IllegalStateException.class,
                 () -> lazy.get(() -> {
                   throw new IllegalStateException("no");
                 }));
    var value = new Object();
    assertSame(value,
               lazy.get(() -> value));
  }

  @Test
  @Timeout(30)
  void concurrentCallsCreateOnce() throws Exception {
    var lazy    = new SoftLazy<Object>();
    var created = new AtomicInteger();
    var start   = new CountDownLatch(1);
    var pool    = Executors.newFixedThreadPool(8);
    try {
      var results = new ArrayList<java.util.concurrent.Future<Object>>();
      for (int i = 0; i < 8; i++) {
        results.add(pool.submit(() -> {
          start.await();
          return lazy.get(() -> {
            created.incrementAndGet();
            try {
              Thread.sleep(50);
            } catch (InterruptedException e) {
              Thread.currentThread()
                    .interrupt();
            }
            return new Object();
          });
        }));
      }
      start.countDown();
      var first = results.get(0)
                         .get();
      for (var result : results) {
        assertSame(first,
                   result.get());
      }
      assertEquals(1,
                   created.get());
    } finally {
      pool.shutdownNow();
      pool.awaitTermination(5,
                            TimeUnit.SECONDS);
    }
  }

}
