# Library Comparison

Choosing the right Java library for electronic invoicing depends on your target formats, required Java version, dependency constraints, and whether you need PDF/A-3 handling or official schema/Schematron validation.

## Overview & Key Differentiators

**Paniolo** is designed for modern Java applications (**Java 21+**) with a strict modular architecture:

* **Zero Core Dependencies:** The core domain model (`paniolo-core`) uses immutable records and requires no third-party runtime dependencies.
* **Modular PDF Stack:** PDF/A-3 XML embedding/extraction (`paniolo-facturx`) and visual PDF generation (`paniolo-pdf-layout`) are completely self-contained in optional modules, keeping XML-only projects lightweight.
* **Beyond Invoices:** Unlike most invoicing-only libraries, Paniolo natively supports **Order-X** (orders) and **Despatch Advices** (delivery notes) alongside EN 16931 invoices.
* **Bundled Official Validation:** Official validation rules from **KoSIT** (XRechnung) and **FeRD** (Factur-X / ZUGFeRD) are bundled and ready to use out of the box.
* **Smooth Migration:** Teams using *Mustangproject* can migrate incrementally using the `paniolo-mustang` mapper module.

---

## Feature Matrix

| Feature | **Paniolo** | **Mustangproject** | **Kaltblut** | **Imixs E-Invoice** | **Invoice4j** | **Konik** | **QR Invoice Library** |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **License** | Apache-2.0 | Apache-2.0 | Apache-2.0 | MIT | MIT / Apache-2.0 | AGPL-3.0 | Apache-2.0 |
| **Min. Java Version** | Java 21 | Java 11 / 17 | Java 11+ | Java 11+ | Java 17+ | Java 8+ | Java 8+ |
| **XRechnung (UBL & CII)** | Yes | Yes | Detection & Extraction | Partial (CII / UBL BIS 3) | Yes | No (ZUGFeRD 1.0 only) | No |
| **ZUGFeRD / Factur-X** | Yes (incl. 1.0 migration) | Yes (Core focus) | Yes (Tier 1–3) | Yes (CII) | Yes | Yes (Legacy / v1.0) | No |
| **Additional Documents** | Order-X, Despatch Advice | No | No | KSeF (Poland) | PEPPOL | No | Swiss QR-Bill |
| **PDF/A-3 Embedding & Extraction** | Native (`paniolo-facturx`) | Native | Extraction only | No (XML only) | No (XML only) | Native | Payment slip PDF |
| **PDF Layout Rendering** | Native (`paniolo-pdf-layout`) | Yes (HTML/XSLT) | No | No | No | No | Payment slip only |
| **Official Validation** | Bundled (KoSIT & FeRD) | Bundled (KoSIT) | Metadata/Structure only | Basic | Yes (XSD / Schematron) | Basic | QR consistency |
| **HTML Visualization** | Native (`paniolo-visualizer`) | Yes | No | No | No | No | No |
| **Core Dependencies** | **None** | Heavy (PDFBox, Saxon, etc.) | Minimal (`phax-commons`) | Minimal | Minimal | Heavy | Minimal |
| **CLI Tool** | Yes (`paniolo-cli`) | Yes | No | No | No | No | Yes |

---

## When to Choose Which Library?

### Paniolo vs. Mustangproject
* **Mustangproject** is a mature, battle-tested solution widely used for ZUGFeRD and XRechnung processing. However, it brings a large dependency tree into your project by default.
* **Choose Paniolo** if you are building on **Java 21+**, prefer immutable domain models with zero core dependencies, need **Order-X** or **Despatch Advice** support, or want to separate XML processing from PDF generation cleanly. If you already have existing Mustangproject code, `paniolo-mustang` lets you map models and transition step by step.

### Paniolo vs. Kaltblut
* **Kaltblut** excels at safely inspecting, detecting, and extracting XML payloads from untrusted hybrid PDFs (with built-in size and memory guards).
* **Choose Paniolo** when you need full lifecycle support—creating, validating against official KoSIT/FeRD rules, rendering HTML/PDF previews, and embedding XML into PDF/A-3.

### Paniolo vs. Imixs E-Invoice & Invoice4j
* Both **Imixs E-Invoice** and **Invoice4j** focus on clean, lightweight XML generation without PDF overhead.
* **Choose Paniolo** if you additionally need native PDF/A-3 embedding, PDF layout rendering, bundled KoSIT/FeRD validation, a CLI tool, or supply-chain documents (Order-X / Despatch Advice) within a single cohesive ecosystem.

### Paniolo vs. Konik & QR Invoice Library
* **Konik** targets legacy ZUGFeRD 1.0 workflows and uses the restrictive AGPL-3.0 license.
* **QR Invoice Library** is not an EN 16931 / XRechnung library; it is specifically built for generating **Swiss QR-Bills**. Use it alongside Paniolo if your application also needs to issue Swiss payment slips.
