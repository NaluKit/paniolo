# paniolo-benchmark

JMH benchmarks for Paniolo. The module is built with the Maven profile `benchmark` only and is
never installed or deployed.

```bash
mvn -Pbenchmark package -pl paniolo-benchmark -am -DskipTests
java -jar paniolo-benchmark/target/benchmarks.jar
```

| Benchmark                     | Measures                                                                                                                  |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `XmlBenchmark`                | `toXml`, `read`, `validate` for UBL and CII with 1, 100, 1000 lines (warm)                                                |
| `LargeXmlBenchmark`           | reading, importing and validating UBL and CII with 100, 1000, 5000 lines, each with its own invoicing period (BG-26); input as bytes and as string, validation with and without calculation check (warm) |
| `FacturXBenchmark`            | `toFacturX`, `extractXml`, `read`, `validate` with and without calculation check of hybrid PDFs with 1, 100, 1000 lines and with no or a 2 MB attachment (warm, **no PDF/A check**) |
| `FacturXPdfaBenchmark`        | `validate` with and without calculation check of the same PDFs **including the PDF/A check**; needs `target/pdfa-lib`, see below |
| `ColdStartBenchmark`          | the first `validate` in a fresh JVM: rule extraction and compilation                                                      |
| `ConcurrentValidateBenchmark` | throughput of one shared instance with 4 threads (`-t` to change)                                                         |
| `HeapFootprint` (main class)  | retained heap of the compiled rules and bytes allocated per call                                                          |
| `PdfaScriptCacheLoad` (main class) | load test of the veraPDF script caches per `PdfaScriptCache` strategy: time, Metaspace, loaded and unloaded classes, cached scripts per round with a fixed worker pool; needs `target/pdfa-lib` |

Useful options:

```bash
java -jar paniolo-benchmark/target/benchmarks.jar XmlBenchmark.validate -p lines=100   # a single benchmark
java -jar paniolo-benchmark/target/benchmarks.jar LargeXmlBenchmark.readBytes -p lines=5000 -p syntax=CII
java -jar paniolo-benchmark/target/benchmarks.jar XmlBenchmark -prof gc                # allocation per operation
java -jar paniolo-benchmark/target/benchmarks.jar ConcurrentValidateBenchmark -t 1     # single thread baseline
java -jar paniolo-benchmark/target/benchmarks.jar -rf json -rff results.json           # machine readable results
java -cp paniolo-benchmark/target/benchmarks.jar io.github.nalukit.paniolo.benchmark.HeapFootprint
```

`ColdStartBenchmark` relies on its own fork and warmup settings; do not override `-wi` or `-f 1`
for it, or the compilation moves into the warmup and the result shows a warm call.

The setup of every benchmark prepares its data outside the measurement and aborts if a validation
result is invalid, so a document rejected early cannot show up as a speed-up.

## PDF/A

`benchmarks.jar` does not contain paniolo-pdfa, so `FacturXBenchmark` measures the Factur-X path
without PDF/A and its results stay comparable. The package phase copies paniolo-pdfa and veraPDF to
`target/pdfa-lib`; `FacturXPdfaBenchmark` runs with these jars on the class path:

```bash
java -cp "paniolo-benchmark/target/benchmarks.jar:paniolo-benchmark/target/pdfa-lib/*" org.openjdk.jmh.Main FacturXPdfaBenchmark
```

Both benchmarks check in their setup whether PDF/A is checked and abort on the wrong class path,
so results with and without PDF/A are never mixed up.

`PdfaScriptCacheLoad` needs the same class path. Run each strategy in its own JVM, because
Metaspace and class counts are JVM-wide; arguments are strategy, threads, warmup rounds, measured
rounds and passes over the input per worker and round:

```bash
java -cp "paniolo-benchmark/target/benchmarks.jar:paniolo-benchmark/target/pdfa-lib/*" \
  io.github.nalukit.paniolo.benchmark.PdfaScriptCacheLoad CLEAR_AFTER_CHECK 4 3 12 4
```

## Recording results

`scripts/record-benchmark.sh` runs JMH with `-prof gc` and keeps the machine readable results
with the environment in `paniolo-benchmark/results/<date>-<commit>-<label>/`:

| File              | Content                                                                                       |
|-------------------|-----------------------------------------------------------------------------------------------|
| `results.json`    | JMH results: time and allocation per operation (`gc.alloc.rate.norm`), JVM and JVM options, forks, warmup and measurement settings, parameters |
| `environment.txt` | source commit and whether the working tree was modified, Java version, operating system, CPU, cores, memory, arguments |
| `output.txt`      | the console output                                                                            |

```bash
mvn -Pbenchmark package -pl paniolo-benchmark -am -DskipTests
scripts/record-benchmark.sh large-xml LargeXmlBenchmark
scripts/record-benchmark.sh pdfa --pdfa FacturXPdfaBenchmark
```

To compare a change, record the same benchmarks with the same arguments on the commit before and
after it, on the same machine and without other load, and keep both directories. The input data
is generated by `BenchmarkData` and identified by the benchmark parameters (`lines`, `syntax`,
`attachmentKb`). A difference counts only if it exceeds the error JMH reports; for small effects
use more forks (`-f 3`). Measurements need neither a release nor a deployment.
