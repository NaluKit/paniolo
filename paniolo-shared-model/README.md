# Paniolo Shared Model

`paniolo-shared-model` provides the immutable model shared by all Paniolo modules: invoices and
credit notes (EN 16931, XRechnung, Factur-X EXTENDED), orders (Order-X), and despatch advices, with
builders, code lists, and the calculation of all amounts. It requires Java 21 and has no runtime
dependencies.

## Dependency

After installing Paniolo from the repository root with `mvn install`, add:

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-shared-model</artifactId>
  <version>HEAD-SNAPSHOT</version>
</dependency>
```

The XML writer modules already include this dependency transitively.

## Invoice model

The main types are:

| Type                                                                            | Purpose                                                                                                                                                                                           |
|---------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Invoice`                                                                       | Immutable invoice with a builder and syntax-independent fields                                                                                                                                    |
| `InvoiceLine`, `LineAdjustment`, `ItemClassification`, `ItemAttribute`          | Line with price details, line allowances and charges, period, references, and item information (BG-25 to BG-32)                                                                                   |
| `Party`, `Address`, `Contact`, `ElectronicAddress`                              | Seller and buyer information                                                                                                                                                                      |
| `Payee`, `SellerTaxRepresentativeParty`                                         | Separate payee and seller tax representative                                                                                                                                                      |
| `InvoiceNote`, `References`, `PrecedingInvoiceReference`                        | Notes with subject codes and document references                                                                                                                                                  |
| `InvoicePeriod`, `DeliveryInformation`                                          | Invoicing period, delivery date, place, and address                                                                                                                                               |
| `TaxCategory`                                                                   | VAT category, rate, and optional exemption information                                                                                                                                            |
| `DocumentAdjustment`                                                            | Document allowance or charge with optional base amount, percentage, and reason code                                                                                                               |
| `PaymentInstruction`, `CreditTransfer`, `PaymentCardInformation`, `DirectDebit` | Payment means                                                                                                                                                                                     |
| `CashDiscount`                                                                  | Cash discount (Skonto) in the XRechnung payment terms format (BT-20)                                                                                                                              |
| `ThirdPartyPayment`                                                             | Third party payment of the XRechnung extension (BG-DEX-09)                                                                                                                                        |
| `AdditionalSupportingDocument`, `Attachment`                                    | Referenced or embedded supporting documents                                                                                                                                                       |
| `ProcessControl`                                                                | Business process and specification identifiers                                                                                                                                                    |
| `InvoiceTotals`, `VatBreakdown`                                                 | Calculated monetary totals and VAT breakdowns                                                                                                                                                     |
| `DocumentTotals`                                                                | Stated totals and VAT breakdown of an invoice without lines (Factur-X MINIMUM and BASIC WL)                                                                                                       |
| `Order`, `OrderLine`, `OrderTotals` in `model.order`                            | Order-X orders, order changes, and order responses                                                                                                                                                |
| `DespatchAdvice`, `DespatchLine`, `DespatchItemInstance` in `model.despatch`    | Despatch advices (delivery notes) after Peppol BIS Despatch Advice 3, written as UBL or CIDA                                                                                                      |
| `ExtendedInformation`, `LineExtension`, `PartyExtension`, and `model.extended`  | Header and line information of the Factur-X profile EXTENDED: further parties, delivery terms, references, payment terms, logistics service charges, advance payments, sub-lines, product details, several allowances and charges on the gross price (`GrossPriceAdjustment`) |

Model types are in `io.github.nalukit.paniolo.model` and its packages `common`, `extended`
(Factur-X EXTENDED), `order` (Order-X), and `despatch` (despatch advices).
See the [project README](../README.md#generating-an-invoice) for a complete construction example.

```java
import io.github.nalukit.paniolo.model.Invoice;

import java.math.BigDecimal;

// Given an Invoice built with Invoice.create():
BigDecimal payableAmount = invoice.totals()
                                  .payableAmount();
BigDecimal taxAmount = invoice.totals()
                              .taxAmount();
```

Amounts use `BigDecimal`. Line net amounts and tax amounts per VAT group are rounded to
two decimal places with `HALF_UP`. Totals include document allowances, charges, and
prepayments, and for the profile EXTENDED logistics service charges; EXTENDED subtotal and
information lines do not count. The VAT breakdown has one entry per category and rate; a French
multi-vendor invoice (EXTENDED-CTC-FR, `ProcessControl.isFrenchMultiVendor()`) has one per
sub-invoice, category, and rate (`TaxCategory.subInvoiceNumber()`). Lists are copied when an
invoice is built.

An invoice without lines, as in the Factur-X profiles MINIMUM and BASIC WL, sets
`statedTotals(DocumentTotals)` instead; `totals()` then returns these amounts and derives the
amount with VAT and the amount due. Seller and buyer always need a name. Invoices with lines,
orders, and despatch advices require complete addresses (street, city, post code, country) for
their parties, and so does the item seller of an EXTENDED line, except in a French multi-vendor
invoice, which names it partially. Only MINIMUM uses an address with the country alone
(`Address.ofCountry`) for the seller and a buyer without address.

Regular lines need quantity, unit, net price, and VAT category. EXTENDED subtotal (`GROUP`) and
information lines may leave out the VAT category and the net price, and without net price also
quantity and unit. A payment card next to credit transfer accounts in one payment instruction is
accepted only in an EXTENDED-CTC-FR invoice.

Defaults are EUR, document type `380`, and XRechnung 3 process identifiers. `InvoiceTypeCode`
holds the 62 document types permitted by EN 16931 with their UN/CEFACT names; `isCreditNote()`
marks the credit note types (e.g. `381`). The model performs structural checks; XML business-rule
validation is provided by [paniolo-validation](../paniolo-validation/README.md).

## Thread safety

`Invoice` and all model records are immutable and can be shared between threads; their
constructors copy lists and attachment content. Builders are mutable and belong to one
thread. Static factories (`create()`, `of(...)`) and `LocalizationFactory` hold no mutable
state; the locale is always passed as a parameter. See
[thread safety](../README.md#thread-safety-and-multi-user-use) for the whole library.

## Localized code lists

All nine enums in `io.github.nalukit.paniolo.model.common.value` obtain their display texts
through `LocalizationFactory` in `io.github.nalukit.paniolo.model.common.value.localization`.
English and German translations are included; `UnitCode` translates only the units common in
invoices into German and falls back to English for the rest.

```java
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;

import java.util.Locale;

String payment = PaymentMeansCode.CODE_58.getDescription(Locale.GERMAN); // SEPA-Überweisung
String country = Country.DE.getText(Locale.ENGLISH);                   // Germany
```

The enums support explicit locales for their display getters and `toString(Locale)`.
Methods without a locale preserve their original output: country texts are German and
other display texts are English. `Country.toString()` returns the country code.
Missing translations fall back to English independently of the JVM default locale.

To add a language, create `<EnumName>_<language>.properties` under
`src/main/resources/io/github/nalukit/paniolo/model/common/value/localization/`.
Use keys such as `CODE_58.description`, `DE.text`, or `CODE_95.name`.
The [localization documentation](../README.md#localized-enum-texts) describes the conventions.

## Build and tests

Run from the repository root using JDK 21 or newer and Maven 3.9:

```sh
mvn -pl paniolo-shared-model -am verify
```

Tests cover totals, rounding, model immutability, code lookup, localization, and fallback behavior.
The model covers the business terms of EN 16931 and most of the Factur-X profile EXTENDED; see
the [project scope](../README.md#scope) for limitations.
