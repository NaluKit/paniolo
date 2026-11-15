# Test case: two VAT rates with document level allowances and charges

Test: [`MixedVatAdjustmentsTest`](../../paniolo/src/test/java/io/github/nalukit/paniolo/MixedVatAdjustmentsTest.java)

An invoice with one line at 19 % and one line at 7 % VAT, calculated by Paniolo in four
variants. Every variant is written as XRechnung in UBL and CII, validated (XML schema, EN 16931,
XRechnung rules of KoSIT and the Paniolo calculation check, all without findings) and read back
with the same totals.

## Invoice

Invoice type 380 (commercial invoice), currency EUR, seller Muster GmbH (Berlin), buyer
Kunde GmbH (Hamburg), payment by SEPA credit transfer (58).

| Line | Item                 | Quantity | Unit | Net unit price | Net amount (BT-131) | VAT  |
|------|----------------------|---------:|------|---------------:|--------------------:|------|
| 1    | Beratung (consulting)|        2 | HUR  |         100.00 |              200.00 | 19 % |
| 2    | Fachbuch (book)      |        1 | H87  |         100.00 |              100.00 |  7 % |
|      | **Line total (BT-106)** |       |      |                |          **300.00** |      |

## How the allowances and charges are stated

A document level allowance (BG-20) or charge (BG-21) has exactly one VAT category
(BT-95 / BT-102). A percentage on the whole invoice is therefore stated once per VAT rate, with
the line net sum of that rate as base amount (BT-93 / BT-100):

| Variant | 19 % group (base 200.00)                  | 7 % group (base 100.00)                   |
|---------|-------------------------------------------|-------------------------------------------|
| A       | –                                         | –                                         |
| B       | charge 10 % = 20.00                       | charge 10 % = 10.00                       |
| C       | allowance 10 % = 20.00                    | allowance 10 % = 10.00                    |
| D       | charge 20 % = 40.00, allowance 15 % = 30.00 | charge 20 % = 20.00, allowance 15 % = 15.00 |

In variant D, charge and allowance both refer to the line net sum. They are not applied one
after the other (that would be a 15 % allowance on 240.00 = 36.00 in the 19 % group and needs
the base amount 240.00 in BT-93).

## Results

All amounts in EUR.

| Value                          | A: none | B: +10 % | C: −10 % | D: +20 % / −15 % |
|--------------------------------|--------:|---------:|---------:|-----------------:|
| Line total (BT-106)            |  300.00 |   300.00 |   300.00 |           300.00 |
| Allowances (BT-107)            |    0.00 |     0.00 |    30.00 |            45.00 |
| Charges (BT-108)               |    0.00 |    30.00 |     0.00 |            60.00 |
| 19 % taxable amount (BT-116)   |  200.00 |   220.00 |   180.00 |           210.00 |
| 19 % VAT (BT-117)              |   38.00 |    41.80 |    34.20 |            39.90 |
| 7 % taxable amount (BT-116)    |  100.00 |   110.00 |    90.00 |           105.00 |
| 7 % VAT (BT-117)               |    7.00 |     7.70 |     6.30 |             7.35 |
| **Net (BT-109)**               | **300.00** | **330.00** | **270.00** |     **315.00** |
| **VAT (BT-110)**               |  **45.00** |  **49.50** |  **40.50** |      **47.25** |
| **Gross (BT-112)**             | **345.00** | **379.50** | **310.50** |     **362.25** |
| Amount due (BT-115)            |  345.00 |   379.50 |   310.50 |           362.25 |

Calculation (EN 16931 BR-CO-10 to BR-CO-17):

- Net = line total − allowances + charges
- Taxable amount per rate = line net sum of the rate − its allowances + its charges
- VAT per rate = taxable amount × rate, rounded to two decimals; VAT = sum of the rates
- Gross = net + VAT; amount due = gross (no prepayment, no rounding amount)
