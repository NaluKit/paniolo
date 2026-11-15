# Legacy hybrid PDFs

Regression files for the generation detection of the XMP metadata (`LegacyXmpTest`).

| File                                                           | Origin                     | SHA-256                                                            |
|----------------------------------------------------------------|----------------------------|--------------------------------------------------------------------|
| `attributeBasedXMP_zugferd_2p0_EN16931_Einfach.pdf`            | Mustang project, unchanged | `29842457f57143aba5da78fdf390aba773c965e60b1caab93dfff3e9d4c802f4` |
| `attributeBasedXMP_zugferd_2p0_EN16931_Einfach-version-2p0.pdf` | modified copy, see below   | `d7b24adaebdc620bdbc5cb5909232d3a26a23eeebe331a993eb16941dee79f91` |
| `validXRechnung.pdf`                                           | Mustang project, unchanged | `a0046f55cab4b1ec2909e83a4b6970a37a5905e9a8ce469b4e75e2ab1cabc59e` |
| `validXRechnung-xrechnung-xml.pdf`                             | modified copy, see below   | `b4dedee2f42a49b3ae77d0220e1e2a48df381fcd873db9bf90549b52623adbef` |

The unchanged files come from the Mustang project at commit
`32e59732f2846b7f2a22c08b515605c40938e95e`, `validator/src/test/resources/`:

- <https://raw.githubusercontent.com/ZUGFeRD/mustangproject/32e59732f2846b7f2a22c08b515605c40938e95e/validator/src/test/resources/attributeBasedXMP_zugferd_2p0_EN16931_Einfach.pdf>
- <https://raw.githubusercontent.com/ZUGFeRD/mustangproject/32e59732f2846b7f2a22c08b515605c40938e95e/validator/src/test/resources/validXRechnung.pdf>

The Mustang project is licensed under the Apache License 2.0; see `mustang-LICENSE.txt` and
`mustang-NOTICE.txt` (SHA-256 `ba98dd76…a21b` and `9225d07c…c6cf`, unchanged copies of the files at
the same commit). The test files are not part of any published artifact.

The modified copies are controls, not upstream files:

- `…-version-2p0.pdf`: only the XMP property `Version` is changed from `1.0` to `2p0`, the value the
  ZUGFeRD 2.0 metadata specify. Namespace, file name, the other properties, and the embedded
  `zugferd-invoice.xml` (SHA-256 `5600027528d5ffaf89fb0be911c73ec84125a98411525e7299c7ca3a0ab073f7`)
  are those of the original.
- `validXRechnung-xrechnung-xml.pdf`: the embedded file is renamed from `factur-x.xml` to
  `xrechnung.xml` consistently in the `EmbeddedFiles` name tree, `/F`, `/UF`, and the XMP
  `DocumentFileName`, as the profile XRECHNUNG requires. The embedded XML (SHA-256
  `17bf68a45c15dfac5efc47efa5c732de408fde114b0f5371626b96419475a754`) is unchanged.
