# LargeXmlBenchmark: 720acb2 compared with 5bfe146

Effect of the changes since 720acb2 on large invoices whose lines each state an invoicing period:
date locations computed only for errors, XML lookups stopping at the first match, one copy of the
XML per import, and one UTF-8 conversion per validated string.

| | Before | After |
|---|---|---|
| Results | `2026-11-11-720acb2-large-xml-baseline/` | `2026-11-11-5bfe146-large-xml-current/` |
| Source | 720acb2 with the benchmark module of 5bfe146 (the benchmarks did not exist yet) | 5bfe146, clean working tree |

Same machine, Java and arguments for both runs, see `environment.txt`: Temurin 21.0.12.1, Apple
M4 Max, macOS, `LargeXmlBenchmark` with its defaults (1 fork, 3 × 2 s warmup, 5 × 2 s
measurement) and `-prof gc`. Times are averages with the 99.9 % error JMH reports; allocation is
`gc.alloc.rate.norm`.

| Benchmark | Syntax | Lines | Before ms/op | After ms/op | Time | Before MB/op | After MB/op | Allocation |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| `importBytes` | CII | 100 | 1.85 ± 0.04 | 1.42 ± 0.04 | -23 % | 5.3 | 3.7 | -30 % |
| `importBytes` | CII | 1,000 | 29.63 ± 2.73 | 12.62 ± 0.69 | -57 % | 50.4 | 34.9 | -31 % |
| `importBytes` | CII | 5,000 | 209.19 ± 13.21 | 69.35 ± 1.81 | -67 % | 244.3 | 172.2 | -30 % |
| `importBytes` | UBL | 100 | 1.28 ± 0.04 | 0.91 ± 0.01 | -29 % | 3.2 | 2.4 | -27 % |
| `importBytes` | UBL | 1,000 | 23.49 ± 0.36 | 8.10 ± 0.34 | -66 % | 29.9 | 21.3 | -29 % |
| `importBytes` | UBL | 5,000 | 272.48 ± 127.01 | 40.90 ± 1.28 | -85 % | 150.8 | 105.7 | -30 % |
| `readBytes` | CII | 100 | 1.85 ± 0.02 | 1.43 ± 0.05 | -23 % | 4.9 | 3.5 | -28 % |
| `readBytes` | CII | 1,000 | 29.56 ± 2.09 | 12.53 ± 0.38 | -58 % | 46.7 | 33.0 | -29 % |
| `readBytes` | CII | 5,000 | 398.81 ± 17.27 | 62.08 ± 1.52 | -84 % | 227.3 | 162.9 | -28 % |
| `readBytes` | UBL | 100 | 1.27 ± 0.03 | 0.88 ± 0.01 | -31 % | 3.1 | 2.3 | -25 % |
| `readBytes` | UBL | 1,000 | 23.62 ± 0.17 | 8.08 ± 0.20 | -66 % | 28.0 | 20.4 | -27 % |
| `readBytes` | UBL | 5,000 | 227.43 ± 19.04 | 40.29 ± 1.60 | -82 % | 138.2 | 101.0 | -27 % |
| `readString` | CII | 100 | 1.79 ± 0.08 | 1.44 ± 0.02 | -20 % | 5.8 | 4.1 | -29 % |
| `readString` | CII | 1,000 | 31.27 ± 0.76 | 12.88 ± 0.34 | -59 % | 52.3 | 38.6 | -26 % |
| `readString` | CII | 5,000 | 409.41 ± 7.95 | 61.83 ± 1.06 | -85 % | 254.5 | 190.8 | -25 % |
| `readString` | UBL | 100 | 1.30 ± 0.04 | 0.94 ± 0.02 | -28 % | 3.3 | 2.6 | -23 % |
| `readString` | UBL | 1,000 | 24.63 ± 0.62 | 8.31 ± 0.19 | -66 % | 30.8 | 23.2 | -25 % |
| `readString` | UBL | 5,000 | 248.82 ± 152.26 | 41.73 ± 0.95 | -83 % | 152.0 | 114.9 | -24 % |
| `validateBytes` | CII | 100 | 14.05 ± 0.55 | 13.26 ± 0.12 | -6 % | 23.5 | 23.5 | -0 % |
| `validateBytes` | CII | 1,000 | 161.42 ± 2.04 | 151.23 ± 1.32 | -6 % | 220.2 | 220.1 | -0 % |
| `validateBytes` | CII | 5,000 | 1431.53 ± 58.89 | 1403.81 ± 85.63 | -2 % | 1111.4 | 1108.4 | -0 % |
| `validateBytes` | UBL | 100 | 15.00 ± 0.29 | 14.25 ± 0.22 | -5 % | 19.0 | 19.0 | -0 % |
| `validateBytes` | UBL | 1,000 | 147.64 ± 1.10 | 143.45 ± 1.00 | -3 % | 179.4 | 179.1 | -0 % |
| `validateBytes` | UBL | 5,000 | 905.86 ± 29.33 | 891.87 ± 8.17 | -2 % | 892.1 | 892.1 | -0 % |
| `validateBytesWithCalculationCheck` | CII | 100 | 16.28 ± 0.58 | 14.83 ± 0.28 | -9 % | 28.9 | 27.0 | -6 % |
| `validateBytesWithCalculationCheck` | CII | 1,000 | 188.80 ± 3.44 | 168.98 ± 1.50 | -10 % | 270.9 | 253.5 | -6 % |
| `validateBytesWithCalculationCheck` | CII | 5,000 | 1842.29 ± 66.29 | 1523.82 ± 46.23 | -17 % | 1356.8 | 1272.7 | -6 % |
| `validateBytesWithCalculationCheck` | UBL | 100 | 16.54 ± 0.38 | 15.04 ± 0.15 | -9 % | 22.5 | 21.4 | -5 % |
| `validateBytesWithCalculationCheck` | UBL | 1,000 | 170.13 ± 4.12 | 151.27 ± 2.81 | -11 % | 209.3 | 199.9 | -5 % |
| `validateBytesWithCalculationCheck` | UBL | 5,000 | 1114.44 ± 19.20 | 959.89 ± 22.82 | -14 % | 1043.5 | 994.8 | -5 % |
| `validateString` | CII | 100 | 14.30 ± 0.39 | 14.19 ± 0.43 | -1 % | 25.2 | 24.0 | -5 % |
| `validateString` | CII | 1,000 | 158.02 ± 1.73 | 159.83 ± 2.36 | +1 % | 237.5 | 225.7 | -5 % |
| `validateString` | CII | 5,000 | 1437.32 ± 18.86 | 1449.35 ± 35.77 | +1 % | 1195.4 | 1139.4 | -5 % |
| `validateString` | UBL | 100 | 15.09 ± 0.28 | 15.22 ± 0.51 | +1 % | 19.9 | 19.3 | -3 % |
| `validateString` | UBL | 1,000 | 148.13 ± 2.98 | 146.88 ± 1.72 | -1 % | 187.4 | 181.9 | -3 % |
| `validateString` | UBL | 5,000 | 901.28 ± 28.29 | 902.08 ± 22.83 | +0 % | 933.5 | 905.9 | -3 % |

- Reading and importing no longer grow quadratically with the lines: 5,000 lines take 6 to 7
  times less time, 1,000 lines 2 to 3 times less; allocation drops by about 25 to 30 %.
- Validation is dominated by the rules: without the calculation check the time stays within the
  error and allocation is unchanged for bytes and 3 to 5 % lower for strings. With the
  calculation check, which reads the invoice, it is 9 to 17 % faster.
- One fork only; the before runs of UBL with 5,000 lines vary widely (± 127 ms and ± 152 ms), so
  use the after values and the CII rows for these sizes.
