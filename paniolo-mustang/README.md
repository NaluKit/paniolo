# paniolo-mustang

Optional adapter for migrations from [Mustangproject](https://www.mustangproject.org):
`MustangInvoiceAdapter` maps a Mustang invoice (`org.mustangproject.Invoice`, or any other
implementation of `IExportableTransaction`) to the Paniolo `Invoice`. An existing Mustang
integration keeps building its Mustang objects and lets Paniolo write, validate, and embed the
invoice. The Paniolo core does not depend on Mustang; only this module does.

## Dependency

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-mustang</artifactId>
  <version>1.0.0-rc01</version>
</dependency>
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo</artifactId>            <!-- to write and validate the mapped invoice -->
  <version>1.0.0-rc01</version>
</dependency>
```

The Mustang library is a `provided` dependency: the application brings its own
`org.mustangproject:library`. The adapter is compiled and tested against **Mustang 2.26.0**, the
default test baseline. Newer Mustang versions are handled explicitly: see
[newer Mustang versions](#newer-mustang-versions) and the [test matrix](#test-matrix).

## Usage

```java
MustangInvoiceAdapter adapter = MustangInvoiceAdapter.builder()
                                                     .processControl(ProcessControl.xrechnung())   // default
                                                     .zone(ZoneId.of("Europe/Berlin"))              // default: JVM
                                                     .build();
MustangMapping mapping = adapter.map(mustangInvoice);
mapping.losses().forEach(loss -> log.warn("not carried over: {}", loss));

Paniolo paniolo = Paniolo.create();
byte[] xml = paniolo.toBytes(mapping.invoice(), InvoiceSyntax.UBL);
ValidationResult result = paniolo.validate(xml);

Invoice invoice = adapter.toInvoice(mustangInvoice);   // throws if any value would be lost
```

- **Target profile.** `processControl(...)` sets BT-24 of the mapped invoices, e.g.
  `ProcessControl.en16931()` or `ProcessControl.facturXExtended()`; the business process (BT-23)
  is Mustang's `getBusinessProcessId()`, otherwise the target profile's. Content of the Factur-X
  profile EXTENDED (fax numbers, invoicer, invoicee, ultimate ship-to party, delivery note
  references, line hierarchy, several price allowances and charges with reasons, product
  characteristics and instances, sequence numbers of allowances, the conversion rate with its
  date, a second identifier of other parties than the seller) is carried over only for EXTENDED.
  The seller keeps an identifier next to its global identifier in every profile (BT-29 is
  repeatable).
- **Dates.** Mustang works with `java.util.Date`; `zone(...)` is the time zone those dates refer
  to (Mustang itself uses the JVM default).
- **No silent losses.** Every value without counterpart in the target profile is a
  `MappingLoss` in `MustangMapping.losses()`: its getter, the reason, the `subject` it belongs to
  (e.g. `the contact Erika Muster of the buyer Kunde GmbH`, `the price allowance 2 of line 2`),
  and the original `value` (dates as `LocalDate` in the configured zone). Each value is reported
  on its own, so a migration can store the originals with their context.
  `toInvoice` refuses such invoices and lists the losses in the exception. Values that EN 16931
  cannot express separately are kept where possible: several price allowances are summed up into
  the price discount (BT-147), a price charge becomes part of the net price, logistics service
  charges become document charges. Each such change that loses information is reported.
- **Amounts as Mustang calculates them.** Percentage allowances and charges, price allowances, and
  net prices are calculated with Mustang's `LineCalculator` and `TransactionCalculator`, so Paniolo
  writes the same amounts as Mustang. Line net amounts and totals are calculated by the Paniolo
  model; a stated `getLineTotalAmount()` must agree.
- **Payment terms as in Mustang's export.** The first entry of `getExtendedPaymentTerms` is
  `getPaymentTerms`: its description and due date become BT-20 and BT-9 and win over
  `getPaymentTermDescription` and `getDueDate`, whose differing values are reported. The first
  entry is compared by its values (description, due date, cash discount), so implementations that
  return new objects on every call lose nothing. Every further entry becomes an EXTENDED
  `PaymentTerm` with description, due date, and cash discount (base date, days, the grand total
  as base amount, percentage); outside EXTENDED each further entry is reported as loss. The cash
  discount of the first entry counts from the issue date: a period from another base date is
  recounted to the same end and its base date reported; a period in another unit than days is
  reported and not converted.
- **Field reference.** `MustangInvoiceAdapter.FIELDS` lists every getter of Mustang's export
  interfaces with its EN 16931 business term, Paniolo field, and status: `MAPPED`, `EXTENDED`
  (EXTENDED only), `DECISION` (changed representation), or `LOST`.

## Newer Mustang versions

Mustang adds getters to its export interfaces from time to time. The adapter is built for 2.26.0
and handles newer versions explicitly:

- Getters of newer versions that it maps are read by reflection: `getAccountingReference` (BT-19
  buyer accounting reference), `getTaxPointDate` (BT-7 VAT point date; reported as loss if BT-8 is
  set as well), and `IZUGFeRDExportableProduct.getIndustryAssignedID` (EXTENDED industry item
  identifier). With 2.26.0 these getters do not exist and nothing changes.
- Every other getter of the export interfaces at runtime that the adapter does not know is checked
  as well: if it carries a value, the mapping reports it as `MappingLoss` ("the adapter, built for
  Mustang 2.26.0, does not know this getter ...") and `toInvoice` refuses the invoice. A newer
  Mustang version can therefore not lose a value silently.

## Not mapped

- EN 16931 terms without Mustang counterpart are not invented.
- The percentage of a price allowance or charge is reported as loss also in EXTENDED, whose gross
  price adjustments carry the amount only; a single allowance without reason is the plain price
  discount (BT-147), as when reading.
- Settlements other than credit transfers and direct debits are reported as loss; EN 16931 has
  one payment means per invoice, so a second one is reported as well.
- Mustang's XML fragments (`getSettlementXML`, `getPaymentXML`, `getAsCII`) are not used.

## Tests

`MustangInvoiceAdapterTest` builds invoices with the real Mustang classes and
- validates the mapped invoice as XRechnung UBL and CII and, for the EXTENDED target, as Factur-X
  EXTENDED;
- compares line net amounts, net prices, and totals with Mustang's own XRechnung export
  (`ZUGFeRD2PullProvider`) read by Paniolo;
- checks that lost values are reported and refused by `toInvoice`, that EXTENDED content is kept
  for the EXTENDED target, and that `FIELDS` documents every getter of the export interfaces.

`PaymentTermsTest` checks the payment terms against Mustang's own EXTENDED export,
`NewerMustangVersionTest` reproduces newer Mustang versions with sub-interfaces (mapped newer
getters, an unknown getter with value is reported), and `MustangLatestTest` checks the newer data
on the real classes of a newer Mustang version against Mustang's own export (skipped with 2.26.0).

### Test matrix

The tests run against Mustang 2.26.0 by default. To check another Mustang version, for example
before an application upgrades Mustang, run them against it:

```sh
mvn install -DskipTests                                    # the Paniolo modules
scripts/mustang-matrix.sh 2.27.0                           # 2.26.0 and a version from Maven Central
scripts/mustang-matrix.sh --mustang-checkout ../mustangproject   # 2.26.0 and a local checkout
mvn test -pl paniolo-mustang -Dmustang.version=2.27.0      # one version only
```

`--mustang-checkout` builds the library of the checkout in a temporary copy and installs it into
the local Maven repository under its version (e.g. `HEAD-SNAPSHOT`); the checkout is not changed.
With a newer version, `NewerMustangVersionTest.knowsTheGettersOfTheMustangVersionAtRuntime` fails
if a getter known from 2.26.0 is gone or a new getter is neither mapped
(`MustangGetters.NEWER_GETTERS`) nor reviewed (`REVIEWED_UNMAPPED`). Such a getter would not lose
data silently, because the adapter reports set values of unknown getters, but it needs a decision.

Checked so far: 2.26.0 and the Mustang `HEAD-SNAPSHOT` of 30 October 2026 (commit `ec9945ca`).
