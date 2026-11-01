# Paniolo JSON

`paniolo-json` stores invoices, orders, and despatch advices of the
[Paniolo model](../paniolo-shared-model/README.md) as JSON and loads them again, for example for
drafts, message queues, or REST interfaces. It depends only on `paniolo-shared-model` and Jackson
Databind (Apache License 2.0), which is why it is optional. It requires Java 21.

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-json</artifactId>
  <version>1.0.0-rc01</version>
</dependency>
```

## Usage

```java
PanioloJson json = PanioloJson.create();                 // thread-safe, create once

String text = json.toJson(invoice);                      // or toPrettyJson, toBytes
Invoice loaded = json.read(text, Invoice.class);         // also Order, DespatchAdvice, ...
```

To use an own `ObjectMapper`, for example in Spring, apply the same settings to its builder:

```java
JsonMapper mapper = PanioloJson.configure(JsonMapper.builder()).build();
```

## Format

The JSON mirrors the record components of the model; property names are the component names.
This complete model example can be loaded with `json.read(text, Invoice.class)`; it is not a
complete XRechnung example (business-rule validation is separate):

```json
{
  "number": "R-2026-1",
  "issueDate": "2026-10-27",
  "typeCode": "380",
  "currencyCode": "EUR",
  "processControl": { "specificationId": "urn:cen.eu:en16931:2017" },
  "paidAmount": 0,
  "buyer": { "name": "Kunde GmbH", "address": { "street": "Kundenweg 2", "city": "Hamburg", "postalCode": "20095", "country": "DE" } },
  "seller": { "name": "Muster GmbH", "address": { "street": "Musterstraße 1", "city": "Berlin", "postalCode": "10115", "country": "DE" } },
  "lines": [ { "id": "1", "name": "Beratung", "quantity": 2, "unitCode": "HUR", "netUnitPrice": 100.00, "priceBaseQuantity": 1, "tax": { "code": "S", "rate": 19 } } ]
}
```

- Code lists are written as their code (`"380"`, `"HUR"`, `"58"`, `"S"`), not as the enum constant (`CODE_380`); countries,
  currencies, and the other enums use their constant names, which are their
  codes. Dates are ISO 8601.
- Amounts are JSON numbers with their scale (`100.00`) and without exponent; on loading, numbers
  and strings (`"100.00"`) are read exactly as `BigDecimal`.
- Attachments are Base64. `null` values are left out; on loading, missing lists are empty.
  Components added to the model later, such as the payee identifier schemes
  (`identifierSchemeId`, `legalRegistrationSchemeId`), are therefore simply absent in older JSON
  and load as `null`.
- Calculated values, such as totals and the VAT breakdown, are not stored; they are calculated
  again from the loaded object.

## Behavior

- Loading invokes the record constructors, not the builders. Builder defaults are not applied:
  required values such as `typeCode`, `currencyCode`, `processControl`, and `paidAmount` must be
  supplied explicitly. Missing lists are empty as described above.
- Loading runs the checks of the model, like the builders: an incomplete address or inconsistent
  stated totals is rejected with the message of the model, e.g.
  `postalCode must not be blank (Address)`.
- Unknown properties, unknown codes, malformed JSON, and trailing content raise an
  `IllegalArgumentException` that names the property or position.
- The JSON is a storage format of Paniolo, not a standard for electronic invoices: exchange
  invoices as XRechnung or Factur-X. The property names follow the model and change with it.
- `PanioloJson` is immutable and thread-safe. Register `PanioloJsonModule` once when building a
  mapper and do not change it afterwards.

## Build and tests

```sh
mvn -pl paniolo-json -am verify
```

Tests cover the format, all codes of all code lists, hand-written JSON, and invalid documents.
`JsonRoundTripTest` in `paniolo-facturx` stores and loads the test invoices of all Factur-X
profiles (including EXTENDED with sub-lines, MINIMUM and BASIC WL, and read ZUGFeRD 1.0
documents), all Order-X documents, and a despatch advice.
