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
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.despatch.DespatchAdvice;
import io.github.nalukit.paniolo.model.order.Order;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Stores invoices, orders, and despatch advices of the Paniolo model as JSON and loads them again,
 * e.g. for drafts, queues, or REST interfaces. The JSON mirrors the record components of the model;
 * loading runs the same structural checks as the builders and rejects values that violate them
 * with the message of the model. XML schema and business rule validation are separate operations.
 * Calculated values such as totals are not stored; explicit stated amounts remain part of the
 * model and are stored.
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
    pretty = mapper.rebuild()
                   .enable(SerializationFeature.INDENT_OUTPUT)
                   .build();
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
   * record components as the only properties in the order of the record, no {@code null} values,
   * missing lists as empty lists, exact decimals without exponent, and rejection of unknown
   * properties and trailing content. Settings whose default changed between Jackson 2 and 3 are
   * set explicitly, so the JSON stays the same whatever the defaults of the builder are.
   *
   * @param builder the mapper builder
   * @return the configured builder
   */
  public static JsonMapper.Builder configure(JsonMapper.Builder builder) {
    return builder.addModule(new PanioloJsonModule())
                  // Only the record components are stored, not derived methods like isCountryOnly().
                  .changeDefaultVisibility(visibility -> visibility.withVisibility(PropertyAccessor.ALL,
                                                                                   JsonAutoDetect.Visibility.NONE)
                                                                   .withVisibility(PropertyAccessor.FIELD,
                                                                                   JsonAutoDetect.Visibility.ANY))
                  .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                  // Lists may be left out or null; they are empty then, as in the builders.
                  .withConfigOverride(List.class,
                                      override -> override.setNullHandling(JsonSetter.Value.forValueNulls(Nulls.AS_EMPTY)))
                  .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                  .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                  .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                  .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                  // Properties in the order of the record components, as in the stored JSON.
                  .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                  // Enums without own serializer, e.g. countries, by their constant name.
                  .disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING,
                           EnumFeature.READ_ENUMS_USING_TO_STRING)
                  // A null primitive, e.g. "charge": null, is read as its default value.
                  .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
  }

  private static String write(ObjectMapper mapper,
                              Object value) {
    Objects.requireNonNull(value,
                           "value");
    try {
      return mapper.writeValueAsString(value);
    } catch (JacksonException e) {
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
  private static RuntimeException failure(JacksonException e) {
    if (e instanceof ValueInstantiationException instantiation && instantiation.getCause() instanceof IllegalArgumentException cause) {
      return new IllegalArgumentException(cause.getMessage() +
                                          " (" +
                                          instantiation.getType()
                                                       .getRawClass()
                                                       .getSimpleName() +
                                          ")",
                                          cause);
    }
    // Only a failing stream is an I/O problem; everything else is an invalid document.
    if (e instanceof JacksonIOException io && io.getCause() instanceof IOException cause) {
      return new UncheckedIOException(cause);
    }
    return new IllegalArgumentException("Invalid Paniolo JSON: " + e.getOriginalMessage() + " at " + path(e),
                                        e);
  }

  private static String path(JacksonException e) {
    if (!e.getPath()
          .isEmpty()) {
      return e.getPathReference();
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
    } catch (JacksonException e) {
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
    } catch (JacksonException e) {
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
                   .without(StreamReadFeature.AUTO_CLOSE_SOURCE)
                   .readValue(json);
    } catch (JacksonException e) {
      throw failure(e);
    }
  }
}
