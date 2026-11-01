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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.nalukit.paniolo.model.common.value.AllowanceChargeIdentification;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.DateTimeQualifier;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.common.value.TextSubjectQualifier;
import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.model.order.OrderTypeCode;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.function.Function;

/**
 * Jackson module for the Paniolo model: code lists are written as their code, e.g. {@code "H87"}
 * for a unit or {@code "380"} for an invoice type, instead of the enum constant; dates are written
 * as ISO 8601 ({@code "2026-10-28"}). Countries and the other enums keep their constant names,
 * which are their codes. Register it in an own {@code ObjectMapper} together with the settings of
 * {@link PanioloJson#configure(com.fasterxml.jackson.databind.json.JsonMapper.Builder)}.
 */
public final class PanioloJsonModule
    extends SimpleModule {

  /**
   * Creates the module with the serializers and deserializers of the code lists.
   */
  public PanioloJsonModule() {
    super("paniolo");
    code(UnitCode.class,
         UnitCode::getCode,
         UnitCode::of,
         "unit code");
    code(CurrencyCode.class,
         CurrencyCode::getCode,
         CurrencyCode::of,
         "currency code");
    code(InvoiceTypeCode.class,
         InvoiceTypeCode::getCode,
         InvoiceTypeCode::of,
         "invoice type code");
    code(PaymentMeansCode.class,
         PaymentMeansCode::getCode,
         PaymentMeansCode::of,
         "payment means code");
    code(DutyTaxFeeCategory.class,
         DutyTaxFeeCategory::getCode,
         DutyTaxFeeCategory::of,
         "VAT category code");
    code(TextSubjectQualifier.class,
         TextSubjectQualifier::getCode,
         TextSubjectQualifier::of,
         "text subject code");
    code(DateTimeQualifier.class,
         DateTimeQualifier::getCode,
         DateTimeQualifier::of,
         "date qualifier");
    code(AllowanceChargeIdentification.class,
         AllowanceChargeIdentification::getCode,
         AllowanceChargeIdentification::of,
         "allowance or charge reason code");
    code(OrderTypeCode.class,
         OrderTypeCode::getCode,
         OrderTypeCode::of,
         "order type code");
    code(LocalDate.class,
         LocalDate::toString,
         PanioloJsonModule::date,
         "date");
    code(LocalTime.class,
         LocalTime::toString,
         PanioloJsonModule::time,
         "time");
  }

  private static LocalDate date(String text) {
    try {
      return LocalDate.parse(text);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static LocalTime time(String text) {
    try {
      return LocalTime.parse(text);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private <T> void code(Class<T> type,
                        Function<T, String> code,
                        Function<String, T> lookup,
                        String kind) {
    addSerializer(type,
                  new JsonSerializer<>() {
                    @Override
                    public void serialize(T value,
                                          JsonGenerator generator,
                                          SerializerProvider provider)
                        throws IOException {
                      generator.writeString(code.apply(value));
                    }
                  });
    addDeserializer(type,
                    new JsonDeserializer<>() {
                      @Override
                      public T deserialize(JsonParser parser,
                                           DeserializationContext context)
                          throws IOException {
                        if (parser.currentToken() != JsonToken.VALUE_STRING) {
                          return context.reportInputMismatch(type,
                                                             "A %s must be a string",
                                                             kind);
                        }
                        String text  = parser.getText();
                        T      value = lookup.apply(text);
                        if (value == null) {
                          return context.reportInputMismatch(type,
                                                             "Unknown %s: %s",
                                                             kind,
                                                             text);
                        }
                        return value;
                      }
                    });
  }
}
