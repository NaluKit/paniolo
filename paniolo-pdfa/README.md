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
- `VeraPdfaSupport` is thread-safe; checks run in parallel without a shared lock.

## Compiled rules per thread

veraPDF compiles the JavaScript test of each PDF/A rule to a class with its own class loader and
caches it per thread, keyed by the rule text (`JavaScriptEvaluator`, see
[veraPDF #1616](https://github.com/veraPDF/veraPDF-library/issues/1616) and
[Mustang #1158](https://github.com/ZUGFeRD/mustangproject/issues/1158)). The cache belongs to the
thread that ran the check and is bounded by the rules of the flavours it checked: a second check
of the same flavour on the same thread compiles nothing new. The compiled classes stay in Metaspace
as long as the thread lives, once per thread. `Paniolo.builder().pdfaScriptCache(...)` selects what
happens with them:

| Setting                       | Behavior                                                                                                                    | Fits                                                                              |
|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| `REUSE` (default)             | The compiled rules stay with the thread for its next checks                                                                 | A small, fixed pool of long-lived worker threads                                  |
| `CLEAR_AFTER_CHECK`           | Released on the validating thread after every PDF/A check, also when it fails; every check compiles its rules again         | Many or changing long-lived threads, memory that must not stay bound between checks |
| `REUSE` + `releasePdfaScriptCache()` | The worker calls `Paniolo.releasePdfaScriptCache()` at the end of a batch or before it is returned to its pool       | Batches on a pool, memory released between batches                               |

```java
Paniolo paniolo = Paniolo.builder()
                         .pdfaScriptCache(PdfaScriptCache.CLEAR_AFTER_CHECK)
                         .build();

// or: keep REUSE and release at the end of a batch, on the worker thread
try {
  batch.forEach(pdf -> results.add(paniolo.validate(pdf)));
} finally {
  Paniolo.releasePdfaScriptCache();
}
```

- The release acts on the **calling thread only**, because the caches are thread-local. A
  separate cleanup thread or a call on another thread releases nothing of the workers.
- `releasePdfaScriptCache()` is safe with empty caches and without `paniolo-pdfa`; later checks
  compile the rules again. It also clears rules compiled by other veraPDF validations of the
  application on the same thread, which then compile them again.
- With a new thread per check, e.g. virtual threads, the cache is never reused and released with
  the thread anyway; `CLEAR_AFTER_CHECK` releases it already after the check.
- veraPDF also keeps the Rhino `Context` of the last check per thread; it holds no compiled rules
  and is not released by these settings.
- The setting never changes findings, validity or statistics. The SPI offers the same as
  `PdfaSupport.check(pdf, statistics, PdfaScriptCache)` and `PdfaSupport.releaseThreadCaches()`.

Measured with 4 worker threads and 6 PDFs of several flavours (load test `PdfaScriptCacheLoad`,
[results](../paniolo-benchmark/results/2026-11-11-af88fe9-pdfa-script-cache/SUMMARY.md)): with
`REUSE` each worker keeps about 90 compiled rules and about 1 MB Metaspace, constant over all
rounds; no continuous growth was observed. `CLEAR_AFTER_CHECK` returns this memory but makes a
check about 2.7 to 3.1 times as slow (+14 ms per check), because the rules are compiled again;
releasing once per batch costs no measurable time.

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
Schematron and PDF/A checks (`RuleStatisticsTest`). `PdfaScriptCacheTest` checks the script
caches of every strategy on the validating thread, with four worker threads alive at the same time,
after successful, invalid, unreadable and unexpectedly failing checks, and that all strategies give
the same findings for PDF/A-1b, -2b, -3b and -3u, conform and violating documents, and broken PDFs.
