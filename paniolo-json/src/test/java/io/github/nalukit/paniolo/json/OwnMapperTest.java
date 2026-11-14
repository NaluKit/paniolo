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
package io.github.nalukit.paniolo.json;

import io.github.nalukit.paniolo.model.Invoice;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration example: an application with its own Jackson 3 mapper and its own module, e.g. a
 * Spring Boot 4 application, applies the Paniolo settings to its builder and stores a Paniolo
 * invoice inside its own message.
 */
class OwnMapperTest {
  enum Priority {
    HIGH,
    LOW
  }

  record Message(String queue,
                 Priority priority,
                 Invoice invoice) {
  }

  /**
   * An own module of the application: priorities in lower case.
   */
  static final class ApplicationModule
      extends SimpleModule {
    ApplicationModule() {
      super("application");
      addSerializer(Priority.class,
                    new ValueSerializer<>() {
                      @Override
                      public void serialize(Priority value,
                                            JsonGenerator generator,
                                            SerializationContext context) {
                        generator.writeString(value.name()
                                                   .toLowerCase(Locale.ROOT));
                      }
                    });
      addDeserializer(Priority.class,
                      new ValueDeserializer<>() {
                        @Override
                        public Priority deserialize(JsonParser parser,
                                                    DeserializationContext context) {
                          return Priority.valueOf(parser.getString()
                                                        .toUpperCase(Locale.ROOT));
                        }
                      });
    }
  }

  @Test
  void storesAnInvoiceInAnOwnMessage()
      throws IOException {
    JsonMapper mapper = PanioloJson.configure(JsonMapper.builder())
                                   .addModule(new ApplicationModule())
                                   .build();
    String stored;
    try (InputStream input = OwnMapperTest.class.getResourceAsStream("/json/invoice.json")) {
      stored = new String(input.readAllBytes(),
                          StandardCharsets.UTF_8);
    }
    String json = "{\"queue\":\"drafts\",\"priority\":\"high\",\"invoice\":" + stored + "}";
    Message message = mapper.readValue(json,
                                       Message.class);
    assertEquals(PanioloJsonTest.invoice(),
                 message.invoice());
    assertEquals(Priority.HIGH,
                 message.priority());
    // Same JSON as PanioloJson writes; the own module applies to the own types.
    assertEquals(json,
                 mapper.writeValueAsString(message));
    // The Paniolo settings apply to the own mapper, too; Jackson 3 exceptions are unchecked.
    var unknown = assertThrows(JacksonException.class,
                               () -> mapper.readValue(json.replace("\"queue\"",
                                                                   "\"topic\""),
                                                      Message.class));
    assertTrue(unknown.getMessage()
                      .contains("topic"),
               unknown::getMessage);
  }
}
