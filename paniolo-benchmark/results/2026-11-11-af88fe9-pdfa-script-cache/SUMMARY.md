# veraPDF script caches: REUSE, RELEASE_PER_ROUND, CLEAR_AFTER_CHECK

Load test `PdfaScriptCacheLoad` (Mustang issue 1158, veraPDF issue 1616), measured on the working
tree of `af88fe9` with the `PdfaScriptCache` implementation; environment in `environment.txt`, the
round by round output in `java<version>-<strategy>.txt`. Every strategy ran in its own JVM.

## Setup

| Item              | Value                                                                                                       |
|-------------------|-------------------------------------------------------------------------------------------------------------|
| Threads           | 4 long-lived workers of a fixed pool                                                                        |
| Input             | 6 documents: hybrid XRechnung PDF/A-3b with 1 and 100 lines, PDF/A-2b, PDF/A-1b and PDF/A-3u declared (same base document, violating 1b), broken PDF |
| Round             | every worker validates the 6 documents 4 times with `Paniolo.validate(pdf)` (96 checks per round); then 2 × `System.gc()` and measurement |
| Warmup            | 3 rounds, not counted; the XML rules are compiled on the main thread before                                 |
| Measured          | 12 rounds                                                                                                   |
| Strategies        | `REUSE` (default), `RELEASE_PER_ROUND` (`REUSE` + `Paniolo.releasePdfaScriptCache()` on every worker after each round), `CLEAR_AFTER_CHECK` |
| Java              | 21.0.12.1 and 25.0.4.1, default options, `-Djava.awt.headless=true`                                         |

All six runs printed the same findings fingerprint `67521b5e`: validity, rule identifiers,
severities and messages are equal for all strategies.

## Results

`ms/check` is the wall time of a round divided by the checks of one worker (24), i.e. the
latency of a check with 4 parallel workers. Metaspace is measured after the GC at the end of
round 4 (first measured), round 10 and round 15 (last).

| Java | Strategy            | Round ms | ms/check | Metaspace MB r4 → r10 → r15 | Loaded classes (last) | Unloaded classes per round | Cached scripts (4 workers) |
|------|---------------------|---------:|---------:|-----------------------------|----------------------:|---------------------------:|---------------------------:|
| 21   | `REUSE`             |      195 |      8.1 | 40.5 → 41.5 → 41.7          |                  6793 |                         80 |                        356 |
| 21   | `RELEASE_PER_ROUND` |      184 |      7.7 | 37.2 → 37.8 → 37.9          |                  6418 |                        436 |                          0 |
| 21   | `CLEAR_AFTER_CHECK` |      530 |     22.1 | 37.2 → 37.8 → 37.9          |                  6439 |                       4336 |                          0 |
| 25   | `REUSE`             |      161 |      6.7 | 39.1 → 40.0 → 40.1          |                  6714 |                         80 |                        356 |
| 25   | `RELEASE_PER_ROUND` |      172 |      7.2 | 35.8 → 36.4 → 36.5          |                  6389 |                        436 |                          0 |
| 25   | `CLEAR_AFTER_CHECK` |      494 |     20.6 | 35.8 → 36.4 → 36.4          |                  6358 |                       4336 |                          0 |

## Interpretation

**Bound memory.** With `REUSE` every worker keeps 89 compiled scripts, each with its own class
loader (356 for 4 workers, constant over all rounds). They bind about 3.6 to 3.8 MB Metaspace and
about 350 to 375 loaded classes more than the strategies that release, i.e. roughly 1 MB per
worker for the flavours of this input. This is the binding the review of Mustang issue 1158
reported; it scales with the number of threads that check PDF/A, not with the number of checks.

**No continuous growth from the caches.** The number of cached scripts and loaded classes stays
constant with `REUSE`. Metaspace rises by 0.6 to 1.2 MB over the 12 measured rounds with *every*
strategy, also with `CLEAR_AFTER_CHECK`, where no compiled rule survives a check, and flattens in
the last rounds (r10 → r15: at most +0.2 MB). This rise therefore does not come from the script
cache (JIT and other JVM metadata); a continuously growing Metaspace caused by the cache was not
observed.

**Runtime cost.** `CLEAR_AFTER_CHECK` compiles the rules of every check again: 45 classes per
check are created and unloaded (4336 per round), and a check takes 2.7 times (Java 21) and 3.1
times (Java 25) as long as with `REUSE`, about +14 ms per check here. Releasing once per round
of 24 checks per worker (`RELEASE_PER_ROUND`) costs no measurable time: the difference to `REUSE`
(−11 ms and +11 ms per round) is within the variation between rounds.

**Recommendation.** Keep `REUSE` for a fixed pool of workers; release with
`Paniolo.releasePdfaScriptCache()` at the end of a batch if the memory must be returned between
batches; use `CLEAR_AFTER_CHECK` only if no compiled rule may stay bound between two checks, e.g.
with many long-lived threads that check PDF/A rarely. The numbers depend on the checked flavours
and documents; they are no general target for cache sizes.

## Reproduce

```bash
mvn -Pbenchmark package -pl paniolo-benchmark -am -DskipTests
CP="paniolo-benchmark/target/benchmarks.jar:paniolo-benchmark/target/pdfa-lib/*"
for s in REUSE RELEASE_PER_ROUND CLEAR_AFTER_CHECK; do
  java -Djava.awt.headless=true -cp "$CP" io.github.nalukit.paniolo.benchmark.PdfaScriptCacheLoad $s 4 3 12 4
done
```
