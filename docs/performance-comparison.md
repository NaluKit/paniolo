# Performance comparison: Paniolo 1.1.0 and Mustangproject 2.26.0

Measured on **28 November 2026** (Europe/Berlin) with the official release binaries
`paniolo-cli-1.1.0-all.jar` and `Mustang-CLI-2.26.0.jar`. The study calls the Java libraries
directly; it does not start a CLI process per invoice. It repeats the study of 27 November 2026
with Paniolo 1.0.1 on identical input bytes; both libraries were run again. It complements the
[feature comparison](../Comparsion.md), which does not contain performance figures.

This page summarizes the study. The raw data of the main series are in
[`performance-comparison/`](performance-comparison/): [`jmh-results.csv`](performance-comparison/jmh-results.csv),
[`load-results.csv`](performance-comparison/load-results.csv),
[`large-invoices.csv`](performance-comparison/large-invoices.csv),
[`corpus-2gib.csv`](performance-comparison/corpus-2gib.csv) and
[`release-comparison.csv`](performance-comparison/release-comparison.csv) (1.0.1 against 1.1.0).

## Summary

- In **14 of 15** measured task/size combinations Paniolo needs less time, and in **15 of 15** it
  allocates less heap. With 100 lines, CII import takes 1.096 ms (Paniolo) against 20.352 ms
  (Mustang), XML validation including the XML report 10.025 ms against 72.626 ms.
- The one exception is XML extraction from a one-line PDF (0.163 ms against 0.157 ms).
- Under parallel load (24 real CII invoices; validate, or read, write and validate again) Paniolo
  reaches 3.6 to 4.7 times the throughput of Mustang in the configurations that completed cleanly
  with the same heap size.
- **Order-X:** both libraries extract all 24 Order-X documents of the corpus, with byte-identical
  XML. Paniolo uses `extractOrderXml(byte[])` for this; the invoice method `extractXml(byte[])`
  rejects them on purpose. This applies to 1.0.1 as well.
- **Memory:** in the full corpus run with a 512 MiB heap, 1.0.1 ran out of memory on six documents
  because it kept every compiled rule set. 1.1.0 holds them through soft references: **no
  documents with `OutOfMemoryError`**, same verdicts. See
  [memory and heap sizing](../README.md#memory-and-heap-sizing).
- **Eight separate Paniolo instances with 512 MiB each remain a critical configuration**: one of
  the 36 load phases was aborted and the completed phases show a sharp drop in throughput
  (validate: 64.8 against 843.8 successes/s for Mustang). With 2 GiB, or with one shared facade
  (the documented operation mode) and 512 MiB, eight workers reach 3,500 to 3,700 validations/s.
  Choose instance model and heap together.
- Two very large UBL invoices (24.5 and 58.3 MiB) were validated, read, written and validated again
  by Paniolo; Mustang exceeded the five-minute limit of this setup on both.
- The structured due date of UBL credit notes (`PaymentTerms/PaymentDueDate`), which neither
  library read in the previous study, is read by Paniolo 1.1.0.
- Speed advantages of single tasks are not a general speed guarantee. The figures apply to the
  tasks, sizes and configuration described below.

## Setup

| | |
| --- | --- |
| Hardware | Apple M4 Max, 16 cores, 128 GiB, macOS 27.2.1 (arm64) |
| Java | Temurin 21.0.12.1, JMH 1.37, UTF-8, headless AWT |
| Heap | `-Xms512m -Xmx512m` for corpus, microbenchmark and load runs; additional 2 GiB runs for both libraries; 12 GiB for the two very large invoices |
| Microbenchmarks | 5 functions x 3 sizes (1, 100, 1,000 lines) x 2 libraries x 2 independent JVMs; 3 warm-up and 5 measurement intervals of 1 s; heap allocation per operation from the GC profiler |
| Load | 24 CII invoices (2.2 to 69.3 KiB, unchanged selection), 1, 4 and 8 workers, 2 JVM repeats, 5 s warm-up, 20 s measurement |
| Corpus | 2,423 distinct XML and PDF candidates (444.7 MiB, 1,815 XML files and 608 PDFs, unchanged), from public corpora, older originals, local regression inputs and attachments of known Mustang issues |
| Validation | Each library with its own packaged rules; the optional additional arithmetic check is off for both in the timing runs |

The validators are not reconfigured to an identical rule package. The figures compare the native
API task with the rules and pre-checks each library ships, not the XSLT engine alone. The
workstation was not isolated (one-minute load 2.92 to 18.83, median 5.06); the results are
repeatable workstation measurements with the stated spread, not a hardware benchmark.

## Time and heap allocation

Smaller is better. **Factor M/P = Mustang time / Paniolo time**; above 1 favours Paniolo. KiB/op is
newly allocated heap, not retained memory.

| Task | Lines | Paniolo ms/op | Mustang ms/op | Factor M/P | Paniolo KiB/op | Mustang KiB/op |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| CII import | 1 | 0.096 | 1.483 | 15.39 | 310.5 | 16,093.6 |
| CII import | 100 | 1.096 | 20.352 | 18.57 | 3,234.4 | 28,859.2 |
| CII import | 1,000 | 10.575 | 193.870 | 18.33 | 29,631.1 | 153,372.6 |
| CII generation | 1 | 0.048 | 0.070 | 1.47 | 183.0 | 205.7 |
| CII generation | 100 | 0.520 | 0.939 | 1.80 | 2,145.1 | 3,590.6 |
| CII generation | 1,000 | 4.582 | 8.996 | 1.96 | 18,910.6 | 35,523.2 |
| XML validation + XML report | 1 | 0.571 | 6.088 | 10.67 | 1,451.2 | 14,350.2 |
| XML validation + XML report | 100 | 10.025 | 72.626 | 7.24 | 19,151.4 | 187,840.3 |
| XML validation + XML report | 1,000 | 104.393 | 825.137 | 7.90 | 176,404.6 | 1,772,677.1 |
| XML from PDF | 1 | 0.163 | 0.157 | 0.96 | 369.9 | 529.7 |
| XML from PDF | 100 | 0.245 | 0.742 | 3.03 | 1,318.1 | 2,804.2 |
| XML from PDF | 1,000 | 0.951 | 6.105 | 6.42 | 9,028.0 | 21,641.0 |
| XML + PDF embedding | 1 | 0.281 | 0.484 | 1.72 | 829.9 | 1,404.5 |
| XML + PDF embedding | 100 | 1.291 | 1.832 | 1.42 | 3,232.1 | 5,128.4 |
| XML + PDF embedding | 1,000 | 9.787 | 14.017 | 1.43 | 24,003.3 | 39,743.5 |

The PDF tasks use the same prepared PDF/A templates (1, 4 and 31 pages) for both libraries.
"XML + PDF embedding" covers XML creation and embedding into an existing template, without layout
rendering or file I/O. The report of Paniolo's validation result is serialized to XML inside the
measurement; the scope of both reports is similar but not identical. Quotients of very short
operations are sensitive to workstation load and JIT effects.

### Against Paniolo 1.0.1

100 lines, ms per operation. The 1.0.1 values are from the study of 27 November and were not
measured again; the Mustang values were measured again on both days.

| Task | Paniolo 1.0.1 | Paniolo 1.1.0 | Mustang, 27 Nov / 28 Nov |
| --- | ---: | ---: | ---: |
| CII import | 1.086 | 1.096 | 20.831 / 20.352 |
| CII generation | 0.547 | 0.520 | 0.952 / 0.939 |
| XML validation + XML report | 10.470 | 10.025 | 73.962 / 72.626 |
| XML from PDF | 0.238 | 0.245 | 0.726 / 0.742 |
| XML + PDF embedding | 1.280 | 1.291 | 1.879 / 1.832 |

The differences are within the range that workstation and JVM effects also produce; they are no
isolated proof of a speed change between the releases. The complete table is in
[`release-comparison.csv`](performance-comparison/release-comparison.csv).

## Parallel load

24 original CII invoices, 512 MiB heap, one engine per worker. A: validate. B: read strictly, write
CII, validate the written CII. Throughput in successful operations per second.

| Flow | Workers | Paniolo ops/s | Mustang ops/s | Paniolo P95 ms | Mustang P95 ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| A: validate | 1 | 560.1 | 140.0 | 7.29 | 18.14 |
| A: validate | 4 | 1,759.5 | 493.3 | 9.82 | 20.02 |
| A: validate | 8 | 64.8 | 843.8 | 1,050.95 | 23.00 |
| B: read, write, validate | 1 | 467.6 | 107.2 | 8.72 | 21.67 |
| B: read, write, validate | 4 | 1,504.7 | 350.3 | 10.84 | 25.90 |
| B: read, write, validate | 8 | 43.1 (1 of 2 repeats) | 615.8 | 1,359.51 | 29.75 |

With eight Paniolo instances of 512 MiB each, one measurement phase was aborted with an
`OutOfMemoryError` and the completed phases show second-long latencies. In 1.0.1 the same
configuration did not even leave the warm-up. This is a real limit of this instance and heap
combination; the study did not count how often validators were compiled again, so it shows the
operating limit, not an isolated cause analysis. Other configurations, each with separately stated
heap and instance setup:

| Setup | Flow | Paniolo ops/s | Mustang ops/s |
| --- | --- | ---: | ---: |
| 8 workers, 2 GiB, one instance per worker | A: validate | 3,551.0 | 801.5 |
| 8 workers, 2 GiB, one instance per worker | B: read, write, validate | 2,992.2 | 632.6 |
| 8 workers, 512 MiB, **one shared Paniolo facade** | A: validate | 3,682.0 | n/a |
| 8 workers, 512 MiB, **one shared Paniolo facade** | B: read, write, validate | 3,195.0 | n/a |

The facade is documented as thread-safe and intended to be reused as a singleton so that compiled
validators are shared; that is the recommended operation mode. CPU per success, peak RSS and the
spread of both repeats are in [`load-results.csv`](performance-comparison/load-results.csv). This
is a time-limited parallel run, not a soak test.

## Corpus runs

Both libraries were run again on the unchanged inputs.

| | Paniolo 1.1.0 | Mustang 2.26.0 |
| --- | ---: | ---: |
| PDFs where the matching API returns XML (of 608) | 433 | 432 |
| of these, invoice XML | 409 | 408 |
| of these, Order-X XML (separate API) | 24 | 24 |
| Invoice XML candidates validated | 2,046 | 2,051 |
| valid / invalid by each library's own rules | 975 / 1,071 | 987 / 1,064 |
| Strict import succeeded | 1,399 | 1,681 |
| Lenient import succeeded | 1,911 | 2,034 |
| CII output written | 1,652 | 2,024 |
| Written CII valid by the library's own validator | 1,243 | 615 |
| Aborted documents | 0 | 2 |

The 24 Order-X documents are eight orders, 14 order responses and two order changes. The
extraction of both libraries is byte-identical; the check covers extraction only, not the full
field fidelity of orders or Order-X performance. A higher number of accepted documents does not
prove higher correctness: rule generations, legacy profiles and the handling of warnings differ.
This study does not arbitrate every individual difference; all differing verdicts are in the raw
data of the study. Compared with 1.0.1 on identical input bytes, nine selected functional
features changed for six documents (six field changes).

Run time of the full normal corpus (up to 8 MiB):

| Heap | Paniolo | Mustang |
| --- | ---: | ---: |
| 512 MiB | 32.5 s | 67.9 s |
| 2 GiB | 25.9 s | 69.5 s |

The runs contain different successful and rejected inputs, file I/O and report writing, and the
2 GiB runs suppress diagnostic logging, so no speed factor is derived from them. In the 512 MiB
run no Paniolo document ran out of memory (1.0.1: six). With 2 GiB neither library did
([`corpus-2gib.csv`](performance-comparison/corpus-2gib.csv)).

## Very large invoices

Two public UBL examples, each library with 12 GiB heap and a five-minute limit per document.

| Size / lines | Library | Validation s | Strict read s | CII write s | Validate written s | Total s | Status |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 58.29 MiB / 63,404 | Paniolo | 62.03 | 0.70 | 0.67 | 204.32 | 269.1 | valid round trip |
| 58.29 MiB | Mustang | - | - | - | - | 300.4 | timeout during validation |
| 24.50 MiB / 26,813 | Paniolo | 12.34 | 0.37 | 0.32 | 35.32 | 49.1 | valid round trip |
| 24.50 MiB / 26,813 | Mustang | 160.54 | 6.09 | 0.40 | - | 300.2 | timeout in the validation of the written CII |

A timeout describes this configuration and is not a statement that the document cannot be
processed with other limits. Peak RSS of Paniolo was 3.6 GiB in both runs.

## Known Mustang issues

The open and closed Mustang issues (783 at the review day of 27 November, 147 open) were indexed,
and 37 targeted Paniolo probes for the issues #1354 to #1359 (UBL party names, VAT and tax
identifiers, payment means, payment text, attachment description, multiple seller identifiers) all
passed against Paniolo 1.1.0. The probes show how Paniolo behaves for these documents; they do not
claim a separate new reproduction run for each Mustang report. Mustang's CII-to-UBL conversion of a
valid single-reference credit note (#1362) produced a document that an independent validation did
not accept, whereas Paniolo writes UBL credit notes directly. An index entry is not an individually
reproduced or fixed defect.

**Structured due date (F01):** in the study of Paniolo 1.0.1 a UBL credit note with only
`PaymentTerms/PaymentDueDate` was read without a due date and without a warning, by both libraries.
Paniolo 1.1.0 reads it in all three read modes (six imports, twelve date-preserving round trips,
three conflict controls checked). Conflicting dates are rejected by strict reading and reported as
`READ-DUE-DATE` by the lenient modes; the official warning `UBL-CR-463` stays in the validation
report. Mustang 2.26.0 still accepts the document and reads no due date.

Documented limits, unchanged: Paniolo does not implement VAT category B (Italian split payment),
does not yet select the language of official Schematron messages, and does not claim support for
EN 16931:2026 or XRechnung 4.0.

## Releasing and CI

For the reviewed workflows, Paniolo's release run of 1.1.0 covers verify on Java 21 and 25, the
invoice corpus, browser tests, security scan (build breaks at CVSS 7, data at most 14 days old),
release dry run, deployment to Maven Central and GitHub Packages, SBOMs, checksums, provenance and
published test evidence (1,280 test cases per Java job, 0 failures; 14 more than in 1.0.1). The
checked-in Mustang workflow builds and tests with one Java version and has no comparable public
release job; non-public Mustang processes are not assessed. A workflow shows traceable steps; it
does not prove fewer production defects. Release immutability is off for both releases.

## Limits of the results

- The figures describe the stated tasks, input sizes, heap sizes and the workstation. They are no
  universal factor and no statement about every profile or document.
- Different rule releases, report scope and warning handling mean that validity counts are not a
  correctness ranking. Whether a document is "readable" is neither "valid" nor "lossless".
- The soft rule cache of 1.1.0 fixes the corpus run with many profiles in one instance. It does not
  remove the memory demand of active validators or of many parallel instances.
- For capacity planning, repeat the measurements with the document sizes and profiles of the target
  application. Large documents and unexplained corpus differences deserve their own acceptance
  criteria.
- For the choice between the libraries by functions, Java baseline and migration effort, see the
  [feature comparison](../Comparsion.md).
