# Paniolo PDF/A

`paniolo-pdfa` adds PDF/A conformance checks to the validation of PDFs by the
[`Paniolo`](../paniolo/README.md) facade: `validate(pdf)` for Factur-X / ZUGFeRD invoices,
`validateOrder(pdf)` for Order-X PDFs, and `validateDespatchAdvice(pdf)` for PDF delivery notes.
It is based on [veraPDF](https://verapdf.org), the reference PDF/A validator, and is found through
`java.util.ServiceLoader`. It requires Java 21. Reading the PDFs needs
[`paniolo-facturx`](../paniolo-facturx/README.md) as well.

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-pdfa</artifactId>
  <version>1.0.0-rc01</version>
</dependency>
```

```java
ValidationResult result = paniolo.validate(facturXPdf);
List<ValidationMessage> pdfa = result.messages(ValidationMessage.Stage.PDF_A);
```

With the module on the class path, these methods report the PDF/A findings in stage `PDF_A` next
to the container (`HYBRID`) and XML findings; without it the PDF/A stage contains the information
`PDFA-NOT-CHECKED`.

## Behavior

- The document is validated against the PDF/A flavour its XMP metadata declares (`pdfaid`).
  Factur-X requires part 3; other parts raise `PDFA-PART`, a missing declaration raises
  `PDFA-NOT-DECLARED` and the document is checked as PDF/A-3b.
- Each failed veraPDF rule becomes one finding, e.g. `PDFA-3B-6.2.4.3-2`
  (flavour, ISO 19005 clause, test number); repeated failures are counted in the message.
- Encrypted and unreadable documents raise `PDFA-ENCRYPTED` and `PDFA-PARSE`.
- With `Paniolo.builder().ruleStatistics(true)` the result also carries a `PdfaStatistics`: the
  flavour checked, the veraPDF profile, compliance, the number of checks, and how often each rule
  failed (the SPI method `PdfaSupport.check(pdf, statistics)`).
- `VeraPdfaSupport` is stateless and thread-safe.

## Dependencies and license

veraPDF (`org.verapdf:validation-model`) adds about 10 MB of libraries, including Rhino, Jackson,
and JAXB, which is why this module is optional. veraPDF is dual licensed under GPLv3+ and
MPL-2.0; Paniolo uses it under the MPL-2.0 option, which is file-level copyleft and allows the use
from Apache-2.0 licensed code. Its transitive dependencies include Rhino (MPL-2.0), the JAXB and
JavaBeans Activation libraries (CDDL-1.1 or GPL-2.0 with Classpath Exception, used under CDDL-1.1;
the JAXB 2.3.2 runtime under EDL-1.0), and xmp-core (BSD-3-Clause). The executable jar of
[`paniolo-cli`](../paniolo-cli/README.md) lists all of them with their source locations in
`META-INF/THIRD-PARTY.txt`. This summary is not legal advice.

## Build and tests

```sh
mvn -pl paniolo-pdfa -am verify
```

Tests validate generated Factur-X documents of all writable profiles across all stages and cover
PDF/A-2 input, documents without PDF/A declaration, unreadable files, and the rule statistics of
Schematron and PDF/A checks (`RuleStatisticsTest`).
