# Jackson version of Paniolo

Status: decided 2026-11-14. Trigger: Mustang issue 1346 asks for Jackson 3. That issue is an
upgrade request; it does not show a defect in Paniolo.

## Decision

1. **Paniolo uses Jackson 3 (`tools.jackson`) from 1.0.0 on**, managed by
   `tools.jackson:jackson-bom`. The switch is made before the first final release, while the API is
   still a release candidate (1.0.0-rc01 used Jackson 2), so the 1.x line does not need a later
   major version for it.
2. **Version policy:** a Paniolo release is built on an open Jackson 3 LTS branch, currently 3.1
   (3.1.8). Newer non-LTS branches (3.3) are taken only for a needed fix.
3. **No Jackson 2 API.** `PanioloJson.configure` and `PanioloJsonModule` exist only for Jackson 3.
   Paniolo does not ship a second Jackson 2 variant.
4. **The JSON storage format does not change.** JSON written by 1.0.0-rc01 loads unchanged and is
   written again byte for byte.

## Reasons

- **The API break costs nothing now and much later.** `configure(JsonMapper.Builder)` and
  `PanioloJsonModule extends SimpleModule` name the Jackson major. Before 1.0.0 the change is
  allowed; after it, it would need Paniolo 2.0.
- **Jackson 3 is the current line.** Spring Boot 4 uses it by default; new applications build their
  mappers with Jackson 3 and can now pass their builder to `configure`.
- **Applications still on Jackson 2 can use Paniolo.** Both majors have different packages and group
  ids and can be on one classpath; they share `jackson-annotations` (2.x). Such an application
  uses `PanioloJson.create()`, which keeps its own Jackson 3 mapper.
- **Small code base.** Jackson is used in two classes of `paniolo-json` and two classes of
  `paniolo-cli`.

## Compatibility matrix

| Part                                          | 1.0.0-rc01 (Jackson 2)                                                             | 1.0.0 (Jackson 3)                                                                                                 | Effect on users                                          |
|-----------------------------------------------|------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------|
| Parent POM                                    | `jackson.version` 2.22.4                                                           | `jackson.version` 3.1.8, `tools.jackson:jackson-bom` imported                                                     | none                                                     |
| `jackson-annotations`                         | 2.22, transitive                                                                   | stays `com.fasterxml.jackson.core:jackson-annotations`, package `com.fasterxml.jackson.annotation`, from the BOM  | none                                                     |
| `paniolo-json` dependency                     | `com.fasterxml.jackson.core:jackson-databind`                                      | `tools.jackson.core:jackson-databind`                                                                             | transitive dependency changes                            |
| `PanioloJson.configure(JsonMapper.Builder)`   | `com.fasterxml.jackson.databind.json.JsonMapper.Builder`                           | `tools.jackson.databind.json.JsonMapper.Builder`                                                                  | **source and binary change** for callers                 |
| `PanioloJsonModule`                           | `extends com.fasterxml.jackson.databind.module.SimpleModule`                       | `extends tools.jackson.databind.module.SimpleModule`                                                              | **change** for callers registering it                    |
| Serializers/deserializers in the module       | `JsonSerializer`, `JsonDeserializer`, `SerializerProvider`, `getText()`            | `ValueSerializer`, `ValueDeserializer`, `SerializationContext`, `getString()`                                     | internal                                                 |
| Exception translation                         | `IOException`, `JsonProcessingException`, `JsonMappingException`                   | `JacksonException` (unchecked); `JacksonIOException` for a failing stream                                         | contract unchanged (see below)                           |
| Pretty mapper                                 | `copy()`                                                                           | `rebuild()…build()`                                                                                               | internal                                                 |
| Builder settings                              | `serializationInclusion`, `visibility`, `setSetterInfo`                            | `changeDefaultPropertyInclusion`, `changeDefaultVisibility`, `setNullHandling`                                    | internal                                                 |
| `paniolo-cli` dependency                      | `jackson-databind` 2.x (reports, inventory, via `paniolo-json`)                    | `tools.jackson.core:jackson-databind` 3.x                                                                         | none (the CLI API is the command line)                   |
| `paniolo-pdfa` (veraPDF)                      | veraPDF's `jackson-databind` 2.22.3, overridden by Paniolo's 2.22.4                | veraPDF's `jackson-databind` excluded (see below)                                                                 | no Jackson 2 from Paniolo on the classpath               |
| Executable JAR (`paniolo-cli-…-all.jar`)      | Jackson 2 only                                                                     | Jackson 3 plus `jackson-annotations` 2.x; no Jackson 2 databind or core                                           | none                                                     |
| JSON storage format                           | as documented in `paniolo-json/README.md`                                          | identical, checked byte for byte against stored rc01 JSON                                                         | none                                                     |

### Jackson 2 of veraPDF

`org.verapdf:core` 1.30.4 depends on `com.fasterxml.jackson.core:jackson-databind` 2.22.3. veraPDF
uses it only for its own report output (`org.verapdf.processor`: `JsonHandler`,
`FeaturesNodeSerializer`, `VeraPDFExceptionSerializer`); `paniolo-pdfa` uses the foundry, the
flavours, the test assertions, and the JavaScript evaluator of the validation, not the processor.
`paniolo-pdfa` therefore excludes that dependency, so Paniolo and its executable JAR carry one
Jackson major only, and the Jackson 3 BOM's `jackson-annotations` 2.21 does not meet a Jackson 2.22
databind.

Risk: a later veraPDF version may need Jackson on a path Paniolo uses; a missing class would show
as `NoClassDefFoundError` for `com.fasterxml.jackson.*`. Every veraPDF update is therefore checked
with the PDF/A tests and the invoice corpus (`mvn verify -P invoice-corpus`). An application that
uses the veraPDF processor itself adds `jackson-databind` 2.x on its own.

### Changed Jackson 3 defaults

Source: the official guide "Migrating to Jackson 3" (`FasterXML/jackson`,
`jackson3/MIGRATING_TO_JACKSON_3.md`). `PanioloJson.configure` sets every default that matters for
the format explicitly:

| Setting                                    | Jackson 3 default | Paniolo                | Reason                                                                                     |
|--------------------------------------------|-------------------|------------------------|--------------------------------------------------------------------------------------------|
| `FAIL_ON_UNKNOWN_PROPERTIES`               | off               | on                     | unknown properties are errors                                                              |
| `FAIL_ON_TRAILING_TOKENS`                  | on                | on                     | trailing content is an error                                                               |
| `SORT_PROPERTIES_ALPHABETICALLY`           | on                | off                    | order of the record components, as in stored JSON                                          |
| `WRITE_/READ_ENUMS_USING_TO_STRING`        | on                | off                    | enums without own serializer, e.g. countries, by constant name                             |
| `FAIL_ON_NULL_FOR_PRIMITIVES`              | on                | off                    | as in rc01, `"charge": null` or `"days": null` is read as `false` / `0`                    |
| `INCLUDE_SOURCE_IN_LOCATION`               | off               | on                     | line and column in error messages                                                          |
| `AUTO_CLOSE_SOURCE`                        | on                | off for streams        | `read(InputStream, …)` does not close the caller's stream                                  |
| `WRITE_DATES_AS_TIMESTAMPS`, time zone UTC | off / UTC         | no effect              | `LocalDate` and `LocalTime` are written by `PanioloJsonModule`; the model has no zoned type |

### Error contract

`PanioloJson.read` still throws `IllegalArgumentException` for unknown codes, unknown properties,
malformed JSON, trailing content, and model violations. The message starts with
`Invalid Paniolo JSON:` and names the property path (`…["lines"]->…["unitCode"]`) or line and
column; a model violation keeps the message of the model, e.g.
`postalCode must not be blank (Address)`. A failing input stream raises `UncheckedIOException` with
the original `IOException`. An own mapper built with `configure` throws Jackson's unchecked
`JacksonException` instead, as Jackson 3 does.

## Checks

State on 2026-11-14 (based on commit `4e5f070`):

- Before the switch, Paniolo on Jackson 2.22.4 wrote the reference JSON
  `paniolo-json/src/test/resources/json/invoice.json` and
  `paniolo-facturx/src/test/resources/json/*.json`: EXTENDED invoices with sub-lines and line
  references, allowances and charges with details, party and currency, direct debit, MINIMUM,
  ZUGFeRD 1.0 COMFORT, an Order-X order with times, and a despatch advice.
- `JsonFixturesTest` (`paniolo-facturx`) and `PanioloJsonTest` load these files with Jackson 3 to
  equal objects and write them byte for byte again; `PanioloJsonTest` also covers codes, decimals
  with scale, Base64, null and missing lists, null primitives, error messages with location, and
  failing streams.
- `OwnMapperTest` is the integration example: an own `JsonMapper` with `configure` and an own
  module stores an invoice inside an own message.
- `JsonRoundTripTest` and `PanioloCliTest` pass.
- `paniolo-cli` resolves `tools.jackson.core:jackson-databind` and `jackson-core` 3.1.8 and
  `com.fasterxml.jackson.core:jackson-annotations` 2.21 only; the executable JAR contains no
  `com/fasterxml/jackson/databind` or `com/fasterxml/jackson/core` classes. Without the exclusion
  in `paniolo-pdfa` it would have bundled veraPDF's Jackson 2.22.3 as well.
- The full build including the invoice corpus (`mvn verify -P invoice-corpus`) passes with veraPDF's
  Jackson 2 excluded.
