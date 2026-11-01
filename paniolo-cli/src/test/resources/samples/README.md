# CLI samples

All files are written for the Paniolo CLI tests, with the example data of the Paniolo
documentation (Muster GmbH, Kunde GmbH, `DE123456788`, example IBAN):

| File                                | Content                                                                                  |
|-------------------------------------|------------------------------------------------------------------------------------------|
| `invoice.json`, `order.json`, `despatch-advice.json` | Paniolo JSON of an invoice, an Order-X order, and a despatch advice     |
| `xrechnung.xml`                     | XRechnung CII written with Paniolo                                                       |
| `calculation-line-net-amount.xml`   | EN 16931 CII whose line net amount differs from quantity x net price, for `--calculation-check` |
| `line-net-amount.xrechnung-ubl.xml` | XRechnung UBL with the same deviation, for `--reject-rule`                               |
| `visual.pdf`                        | Minimal PDF/A-2b page created for the tests (no producer, fixed timestamps), used as the visual part of hybrid PDFs |

The data is fictitious.
