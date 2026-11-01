# paniolo-benchmark

JMH benchmarks for Paniolo. The module is built with the Maven profile `benchmark` only and is
never installed or deployed.

```bash
mvn -Pbenchmark package -pl paniolo-benchmark -am -DskipTests
java -jar paniolo-benchmark/target/benchmarks.jar
```

| Benchmark                     | Measures                                                                            |
|-------------------------------|-------------------------------------------------------------------------------------|
| `XmlBenchmark`                | `toXml`, `read`, `validate` for UBL and CII with 1, 100, 1000 lines (warm)          |
| `FacturXBenchmark`            | `toFacturX`, `extractXml`, `read`, `validate` of hybrid PDFs (warm, no PDF/A check) |
| `ColdStartBenchmark`          | the first `validate` in a fresh JVM: rule extraction and compilation                |
| `ConcurrentValidateBenchmark` | throughput of one shared instance with 4 threads (`-t` to change)                   |
| `HeapFootprint` (main class)  | retained heap of the compiled rules and bytes allocated per call                    |

Useful options:

```bash
java -jar paniolo-benchmark/target/benchmarks.jar XmlBenchmark.validate -p lines=100   # a single benchmark
java -jar paniolo-benchmark/target/benchmarks.jar XmlBenchmark -prof gc                # allocation per operation
java -jar paniolo-benchmark/target/benchmarks.jar ConcurrentValidateBenchmark -t 1     # single thread baseline
java -jar paniolo-benchmark/target/benchmarks.jar -rf json -rff results.json           # machine readable results
java -cp paniolo-benchmark/target/benchmarks.jar io.github.nalukit.paniolo.benchmark.HeapFootprint
```

`ColdStartBenchmark` relies on its own fork and warmup settings; do not override `-wi` or `-f 1`
for it, or the compilation moves into the warmup and the result shows a warm call.

The load and cold-start benchmarks abort if any validation result is invalid.
