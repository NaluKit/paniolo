# Paniolo JSON

`paniolo-json` stores invoices, orders, quotations, and despatch advices of the
[Paniolo model](../paniolo-shared-model/README.md) as JSON and loads them again, for example for
drafts, message queues, or REST interfaces. It depends only on `paniolo-shared-model` and Jackson 3
Databind (`tools.jackson.core:jackson-databind`, Apache License 2.0), which is why it is optional.
It requires Java 21.

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-json</artifactId>
  <version>1.1.0</version>
</dependency>
```

## Usage

```java
PanioloJson json = PanioloJson.create();                 // thread-safe, create once

String text = json.toJson(invoice);                      // or toPrettyJson, toBytes
Invoice loaded = json.read(text, Invoice.class);         // also Order, Quotation, DespatchAdvice, ...
```

To use an own Jackson 3 mapper, for example in Spring Boot 4, apply the same settings to its
builder:

```java
import tools.jackson.databind.json.JsonMapper;

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
- Payment accounts store `idType: "PROPRIETARY"` for a non-IBAN account; IBAN accounts omit
  `idType`. JSON without it is read as an IBAN account for compatibility. The component `iban`
  holds the account identifier in both cases; inspect `CreditTransfer.idType()` for its kind.
  EXTENDED insurance taxes are stored in `ExtendedInformation.otherTaxes` and
  `LineExtension.otherTaxes`; absent lists in older JSON are empty.
- Derived totals and VAT breakdowns are calculated again from the loaded object. Explicit
  model components such as `statedTotals`, `statedNetAmount`, and `vatAmounts` are stored, including
  a stated VAT breakdown for an invoice without lines.

## Behavior

- Loading invokes the record constructors, not the builders. Builder defaults are not applied:
  required values such as `typeCode`, `currencyCode`, `processControl`, and `paidAmount` must be
  supplied explicitly. Missing lists are empty as described above.
- Loading runs the checks of the model, like the builders: a blank address value or inconsistent
  stated totals is rejected with the message of the model, e.g.
  `postalCode must not be blank (Address)`. The model requires only the country for an invoice
  address; city, post code, and other profile requirements are checked by XML validation.
- Unknown properties, unknown codes, malformed JSON, and trailing content raise an
  `IllegalArgumentException` that names the property or position.
- The JSON is a storage format of Paniolo, not a standard for electronic invoices: exchange
  invoices as XRechnung or Factur-X. The property names follow the model and change with it.
- `PanioloJson` is immutable and thread-safe. Register `PanioloJsonModule` once when building a
  mapper and do not change it afterwards.

## Jackson version

Since 1.0.0-rc02, `paniolo-json` uses Jackson 3 (`tools.jackson`); 1.0.0-rc01 used Jackson 2.
The JSON format is unchanged: JSON stored with 1.0.0-rc01 loads as before. Migrating from 1.0.0-rc01:

- `PanioloJson.configure` takes and returns `tools.jackson.databind.json.JsonMapper.Builder`, and
  `PanioloJsonModule` is a Jackson 3 module; replace the `com.fasterxml.jackson.databind` and
  `com.fasterxml.jackson.core` imports by `tools.jackson.databind` and `tools.jackson.core`.
  Annotations stay in `com.fasterxml.jackson.annotation`.
- A mapper built with `configure` throws Jackson 3's unchecked `JacksonException`.
  `PanioloJson.read` still throws `IllegalArgumentException` as described above.
- Applications still on Jackson 2 can use `PanioloJson.create()`; both Jackson majors can share
  one classpath.

The changed defaults of Jackson 3 and the checks of the format are described in
[Jackson version](../docs/jackson.md).

## Build and tests

```sh
mvn -pl paniolo-json -am verify
```

Tests cover the format, all codes of all code lists, JSON stored with 1.0.0-rc01, hand-written
JSON, invalid documents, and an own mapper with an own module (`OwnMapperTest`).
`JsonFixturesTest` in `paniolo-facturx` loads and writes JSON of further documents stored with
1.0.0-rc01 byte for byte.
`JsonRoundTripTest` in `paniolo-facturx` stores and loads the test invoices of all Factur-X
profiles (including EXTENDED with sub-lines, MINIMUM and BASIC WL, and read ZUGFeRD 1.0
documents), all Order-X documents, and a despatch advice.
`QuotationJsonTest` stores and loads a quotation with optional lines and checks the model rules on load.
