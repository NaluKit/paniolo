# Performance comparison: Paniolo 1.0.1 and Mustangproject 2.26.0

Measured on **27 November 2026** (Europe/Berlin) with the official release binaries
`paniolo-cli-1.0.1-all.jar` and `Mustang-CLI-2.26.0.jar`. The study calls the Java libraries
directly; it does not start a CLI process per invoice. It complements the
[feature comparison](../Comparsion.md), which does not contain performance figures.

This page summarizes the study. The raw data of the main series are in
[`performance-comparison/`](performance-comparison/): [`jmh-results.csv`](performance-comparison/jmh-results.csv),
[`load-results.csv`](performance-comparison/load-results.csv),
[`large-invoices.csv`](performance-comparison/large-invoices.csv) and
[`corpus-2gib.csv`](performance-comparison/corpus-2gib.csv).

## Summary

- In **14 of 15** measured task/size combinations Paniolo needs less time, and in **15 of 15** it
  allocates less heap. With 100 lines, CII import takes 1.086 ms (Paniolo) against 20.831 ms
  (Mustang), XML validation including the XML report 10.470 ms against 73.962 ms.
- The one exception is XML extraction from a one-line PDF (0.163 ms against 0.156 ms).
- Under parallel load (24 real CII invoices, validate, or read, write and validate again) Paniolo
  reaches 3.5 to 4.7 times the throughput of Mustang at the same heap size.
- Paniolo has **memory limits at 512 MiB**: eight separate instances with 512 MiB each fail while
  compiling the rules, and six documents of the full corpus run ran out of memory at 512 MiB
  (all six passed in a fresh 512 MiB JVM; with 2 GiB there were none). Speed is not the same as a
  lower maximum memory requirement.
- Two very large UBL invoices (24.5 and 58.3 MiB) were validated, read, written and validated again
  by Paniolo; Mustang exceeded the five-minute limit of this setup on both.
- Speed advantages of single tasks are not a general speed guarantee. The figures apply to the
  tasks, sizes and configuration described below.

## Setup

| | |
| --- | --- |
| Hardware | Apple M4 Max, 16 cores, 128 GiB, macOS 27.2.1 (arm64) |
| Java | Temurin 21.0.12.1, JMH 1.37, UTF-8, headless AWT |
| Heap | `-Xms512m -Xmx512m` for corpus, microbenchmark and load runs; additional 2 GiB runs for both libraries; 12 GiB for the two very large invoices |
| Microbenchmarks | 5 functions x 3 sizes (1, 100, 1,000 lines) x 2 libraries x 2 independent JVMs; 3 warm-up and 5 measurement intervals of 1 s; heap allocation per operation from the GC profiler |
| Load | 24 CII invoices (2.2 to 69.3 KiB), 1, 4 and 8 workers, 2 JVM repeats, 5 s warm-up, 20 s measurement |
| Corpus | 2,423 distinct XML and PDF candidates (444.7 MiB, 1,815 XML files and 608 PDFs) from public corpora, older originals, local regression inputs and attachments of known Mustang issues |
| Validation | Each library with its own packaged rules; the optional additional arithmetic check is off for both in the timing runs |

The validators are not reconfigured to an identical rule package. The figures compare the native
API task with the rules and pre-checks each library ships, not the XSLT engine alone. The
workstation was not isolated (one-minute load 2.99 to 13.43, median 4.88); the results are
repeatable workstation measurements with the stated spread, not a hardware benchmark.

## Time and heap allocation

Smaller is better. **Factor M/P = Mustang time / Paniolo time**; above 1 favours Paniolo. KiB/op is
newly allocated heap, not retained memory.

| Task | Lines | Paniolo ms/op | Mustang ms/op | Factor M/P | Paniolo KiB/op | Mustang KiB/op |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| CII import | 1 | 0.099 | 1.488 | 15.01 | 310.5 | 16,093.7 |
| CII import | 100 | 1.086 | 20.831 | 19.17 | 3,214.1 | 28,860.5 |
| CII import | 1,000 | 10.908 | 202.106 | 18.53 | 29,631.1 | 153,413.2 |
| CII generation | 1 | 0.049 | 0.071 | 1.46 | 183.1 | 205.7 |
| CII generation | 100 | 0.547 | 0.952 | 1.74 | 2,144.9 | 3,590.6 |
| CII generation | 1,000 | 5.088 | 9.354 | 1.84 | 18,910.8 | 35,339.7 |
| XML validation + XML report | 1 | 0.580 | 6.815 | 11.75 | 1,450.8 | 14,350.6 |
| XML validation + XML report | 100 | 10.470 | 73.962 | 7.06 | 19,149.8 | 187,711.3 |
| XML validation + XML report | 1,000 | 108.393 | 867.785 | 8.01 | 176,405.4 | 1,820,556.2 |
| XML from PDF | 1 | 0.163 | 0.156 | 0.96 | 370.0 | 529.6 |
| XML from PDF | 100 | 0.238 | 0.726 | 3.04 | 1,318.1 | 2,804.2 |
| XML from PDF | 1,000 | 0.927 | 5.924 | 6.39 | 9,028.1 | 21,640.9 |
| XML + PDF embedding | 1 | 0.282 | 0.491 | 1.74 | 832.7 | 1,404.1 |
| XML + PDF embedding | 100 | 1.280 | 1.879 | 1.47 | 3,239.7 | 5,128.6 |
| XML + PDF embedding | 1,000 | 10.330 | 14.668 | 1.42 | 24,040.6 | 39,943.1 |

The PDF tasks use the same prepared PDF/A templates (1, 4 and 31 pages) for both libraries.
"XML + PDF embedding" covers XML creation and embedding into an existing template, without layout
rendering or file I/O. The report of Paniolo's validation result is serialized to XML inside the
measurement; the scope of both reports is similar but not identical. Quotients of very short
operations are sensitive to workstation load and JIT effects.

## Parallel load

24 original CII invoices, 512 MiB heap. A: validate. B: read strictly, write CII, validate the
written CII. Throughput in successful operations per second.

| Flow | Workers | Paniolo ops/s | Mustang ops/s | Paniolo P95 ms | Mustang P95 ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| A: validate | 1 | 550.7 | 136.2 | 7.44 | 18.50 |
| A: validate | 4 | 1,731.9 | 493.4 | 10.21 | 20.42 |
| A: validate | 8 | not completed | 828.4 | n/a | 23.46 |
| B: read, write, validate | 1 | 456.8 | 103.7 | 8.89 | 22.11 |
| B: read, write, validate | 4 | 1,465.2 | 346.1 | 11.05 | 26.42 |
| B: read, write, validate | 8 | not completed | 603.8 | n/a | 30.35 |

With one Paniolo instance **per worker**, both runs with eight workers stopped in the warm-up
while compiling the rules (`OutOfMemoryError`); this is a real limit of this instance and heap
combination. Other results, each with separately stated heap and instance setup:

| Setup | Flow | Paniolo ops/s | Mustang ops/s |
| --- | --- | ---: | ---: |
| 8 workers, 2 GiB, one instance per worker | A: validate | 3,519.9 | 819.0 |
| 8 workers, 2 GiB, one instance per worker | B: read, write, validate | 2,970.4 | 626.4 |
| 8 workers, 512 MiB, **one shared Paniolo facade** | A: validate | 3,786.6 | n/a |
| 8 workers, 512 MiB, **one shared Paniolo facade** | B: read, write, validate | 3,274.2 | n/a |

The facade is documented as thread-safe and intended to be reused as a singleton so that compiled
validators are shared; that is the recommended operation mode. CPU per success, peak RSS and the
spread of both repeats are in [`load-results.csv`](performance-comparison/load-results.csv). This
is a time-limited parallel run, not a soak test.

## Corpus runs

| | Paniolo 1.0.1 | Mustang 2.26.0 |
| --- | ---: | ---: |
| PDFs where extraction returns XML (of 608) | 409 | 432 |
| of these, invoice root document | 409 | 408 |
| of these, other documents (Order-X) | 0 | 24 |
| Invoice XML candidates validated | 2,040 | 2,051 |
| valid / invalid by each library's own rules | 969 / 1,071 | 987 / 1,064 |
| Strict import succeeded | 1,399 | 1,681 |
| Lenient import succeeded | 1,911 | 2,034 |
| CII output written | 1,652 | 2,024 |
| Written CII valid by the library's own validator | 1,243 | 615 |
| Aborted documents | 0 | 2 |

The 24 additional Mustang extractions are Order-X documents, which Paniolo's invoice API rejects
with a pointer to the order API; they are not additionally processed invoices. A higher number of
accepted documents does not prove higher correctness: rule generations, legacy profiles and the
handling of warnings differ. This study does not arbitrate every individual difference; all
differing verdicts are in the raw data of the study.

Run time of the full normal corpus (up to 8 MiB, 512 MiB heap): Paniolo 42.7 s, Mustang 69.5 s.
The runs contain different successful and rejected inputs, file I/O and report writing, so no
speed factor is derived from them. Repeated with a 2 GiB heap for both libraries, neither library
ran out of memory ([`corpus-2gib.csv`](performance-comparison/corpus-2gib.csv)).

## Very large invoices

Two public UBL examples, each library with 12 GiB heap and a five-minute limit per document.

| Size / lines | Library | Validation s | Strict read s | CII write s | Validate written s | Total s | Status |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 58.29 MiB / 63,404 | Paniolo | 63.14 | 0.76 | 0.65 | 208.44 | 274.1 | valid round trip |
| 58.29 MiB | Mustang | - | - | - | - | 300.1 | timeout during validation |
| 24.50 MiB / 26,813 | Paniolo | 12.49 | 0.38 | 0.32 | 35.41 | 49.1 | valid round trip |
| 24.50 MiB / 26,813 | Mustang | 163.40 | 6.24 | 0.52 | - | 300.5 | timeout in the validation of the written CII |

A timeout describes this configuration and is not a statement that the document cannot be
processed with other limits. Peak RSS of Paniolo was 5.5 GiB and 2.6 GiB respectively.

## Known Mustang issues

The open and closed Mustang issues (783 at the review day, 147 open) were indexed, and 37 targeted
Paniolo probes for the issues #1354 to #1359 (UBL party names, VAT and tax identifiers, payment
means, payment text, attachment description, multiple seller identifiers) all passed against
Paniolo 1.0.1. The probes show how Paniolo behaves for these documents; they do not claim a
separate new reproduction run for each Mustang report. Mustang's CII-to-UBL conversion of a valid
single-reference credit note (#1362) produced a document that an independent validation did not accept,
whereas Paniolo writes UBL credit notes directly. An index entry is not an individually reproduced
or fixed defect.

One finding applies to Paniolo itself and to Mustang: a UBL credit note with only the structured
`PaymentTerms/PaymentDueDate` was read without a due date and without a warning. It is fixed in the
development branch after 1.0.1 (see the [changelog](../CHANGELOG.md)).

Documented limits, unchanged: Paniolo does not implement VAT category B (Italian split payment),
does not yet select the language of official Schematron messages, and does not claim support for
EN 16931:2026 or XRechnung 4.0.

## Releasing and CI

For the reviewed workflows, Paniolo's release run covers verify on Java 21 and 25, the invoice
corpus, browser tests, security scan (build breaks at CVSS 7, data at most 14 days old), release
dry run, deployment to Maven Central, SBOMs, checksums and test evidence. The checked-in Mustang
workflow builds and tests with one Java version and has no comparable public release job. A
workflow shows traceable steps; it does not prove fewer production defects. Release immutability
is off for both releases.

## Limits of the results

- The figures describe the stated tasks, input sizes, heap sizes and the workstation. They are no
  universal factor and no statement about every profile or document.
- Different rule releases, report scope and warning handling mean that validity counts are not a
  correctness ranking. Whether a document is "readable" is neither "valid" nor "lossless".
- For capacity planning, repeat the measurements with the document sizes and profiles of the target
  application. Large documents and unexplained corpus differences deserve their own acceptance
  criteria.
- For the choice between the libraries by functions, Java baseline and migration effort, see the
  [feature comparison](../Comparsion.md).
