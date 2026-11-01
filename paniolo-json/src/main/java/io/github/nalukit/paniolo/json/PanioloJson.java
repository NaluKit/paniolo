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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.despatch.DespatchAdvice;
import io.github.nalukit.paniolo.model.order.Order;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Stores invoices, orders, and despatch advices of the Paniolo model as JSON and loads them again,
 * e.g. for drafts, queues, or REST interfaces. The JSON mirrors the record components of the model;
 * loading runs the same checks as the builders, so an invalid document is rejected with the
 * message of the model. Calculated values such as totals are not stored.
 *
 * <pre>{@code
 * PanioloJson json = PanioloJson.create();
 * String text = json.toJson(invoice);
 * Invoice loaded = json.read(text, Invoice.class);
 * }</pre>
 *
 * <p>Instances are immutable and thread-safe; create one and share it.
 */
public final class PanioloJson {
  private final ObjectMapper mapper;
  private final ObjectMapper pretty;

  private PanioloJson() {
    mapper = configure(JsonMapper.builder()).build();
    pretty = mapper.copy()
                   .enable(SerializationFeature.INDENT_OUTPUT);
  }

  /**
   * Creates an instance with the settings of {@link #configure(JsonMapper.Builder)}.
   *
   * @return the instance
   */
  public static PanioloJson create() {
    return new PanioloJson();
  }

  /**
   * Applies the settings Paniolo JSON uses to a mapper builder: the {@link PanioloJsonModule},
   * record components as the only properties, no {@code null} values, missing lists as empty
   * lists, exact decimals without exponent, and rejection of unknown properties.
   *
   * @param builder the mapper builder
   * @return the configured builder
   */
  public static JsonMapper.Builder configure(JsonMapper.Builder builder) {
    return builder.addModule(new PanioloJsonModule())
                  // Only the record components are stored, not derived methods like isCountryOnly().
                  .visibility(PropertyAccessor.ALL,
                              JsonAutoDetect.Visibility.NONE)
                  .visibility(PropertyAccessor.FIELD,
                              JsonAutoDetect.Visibility.ANY)
                  .serializationInclusion(JsonInclude.Include.NON_NULL)
                  // Lists may be left out or null; they are empty then, as in the builders.
                  .withConfigOverride(List.class,
                                      override -> override.setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.AS_EMPTY)))
                  .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                  .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                  .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                  .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION);
  }

  private static String write(ObjectMapper mapper,
                              Object value) {
    Objects.requireNonNull(value,
                           "value");
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot write " +
                                         value.getClass()
                                              .getSimpleName() +
                                         " as JSON: " +
                                         e.getOriginalMessage(),
                                         e);
    }
  }

  /**
   * Model checks fail inside the record constructors; their message is the useful one.
   */
  private static RuntimeException failure(IOException e) {
    if (e instanceof ValueInstantiationException instantiation && instantiation.getCause() instanceof IllegalArgumentException cause) {
      return new IllegalArgumentException(cause.getMessage() +
                                          " (" +
                                          instantiation.getType()
                                                       .getRawClass()
                                                       .getSimpleName() +
                                          ")",
                                          cause);
    }
    if (e instanceof JsonProcessingException processing) {
      return new IllegalArgumentException("Invalid Paniolo JSON: " + processing.getOriginalMessage() + " at " + path(processing),
                                          e);
    }
    return new UncheckedIOException(e);
  }

  private static String path(JsonProcessingException e) {
    if (e instanceof com.fasterxml.jackson.databind.JsonMappingException mapping &&
        !mapping.getPath()
                .isEmpty()) {
      return mapping.getPathReference();
    }
    return e.getLocation() == null ?
           "unknown location" :
           "line " +
           e.getLocation()
            .getLineNr() +
           ", column " +
           e.getLocation()
            .getColumnNr();
  }

  /**
   * Writes a model object, e.g. an {@link Invoice}, {@link Order}, or {@link DespatchAdvice}, as
   * compact JSON.
   *
   * @param value the model object
   * @return the JSON
   * @throws IllegalArgumentException if the object cannot be written as JSON
   */
  public String toJson(Object value) {
    return write(mapper,
                 value);
  }

  /**
   * Writes a model object as indented JSON.
   *
   * @param value the model object
   * @return the JSON
   * @throws IllegalArgumentException if the object cannot be written as JSON
   */
  public String toPrettyJson(Object value) {
    return write(pretty,
                 value);
  }

  /**
   * Writes a model object as UTF-8 JSON.
   *
   * @param value the model object
   * @return the UTF-8 encoded JSON
   * @throws IllegalArgumentException if the object cannot be written as JSON
   */
  public byte[] toBytes(Object value) {
    return toJson(value).getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Loads a model object.
   *
   * @param <T>  the model type
   * @param json the JSON
   * @param type the model class, e.g. {@code Invoice.class}
   * @return the model object
   * @throws IllegalArgumentException if the JSON is malformed, has unknown properties, or violates
   *                                  a check of the model
   */
  public <T> T read(String json,
                    Class<T> type) {
    Objects.requireNonNull(json,
                           "json");
    Objects.requireNonNull(type,
                           "type");
    try {
      return mapper.readValue(json,
                              type);
    } catch (IOException e) {
      throw failure(e);
    }
  }

  /**
   * Loads a model object from UTF-8 JSON.
   *
   * @param <T>  the model type
   * @param json the JSON
   * @param type the model class, e.g. {@code Invoice.class}
   * @return the model object
   * @throws IllegalArgumentException if the JSON is malformed, has unknown properties, or violates
   *                                  a check of the model
   */
  public <T> T read(byte[] json,
                    Class<T> type) {
    Objects.requireNonNull(json,
                           "json");
    Objects.requireNonNull(type,
                           "type");
    try {
      return mapper.readValue(json,
                              type);
    } catch (IOException e) {
      throw failure(e);
    }
  }

  /**
   * Loads a model object from a stream, which is not closed.
   *
   * @param <T>  the model type
   * @param json the JSON
   * @param type the model class, e.g. {@code Invoice.class}
   * @return the model object
   * @throws IllegalArgumentException if the JSON is malformed, has unknown properties, or violates
   *                                  a check of the model
   */
  public <T> T read(InputStream json,
                    Class<T> type) {
    Objects.requireNonNull(json,
                           "json");
    Objects.requireNonNull(type,
                           "type");
    try {
      return mapper.readerFor(type)
                   .without(com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE)
                   .readValue(json);
    } catch (IOException e) {
      throw failure(e);
    }
  }
}
