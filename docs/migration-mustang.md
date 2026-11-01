# Migrating from Mustang export interfaces to Paniolo

This guide is for applications that create invoices with Mustang (`org.mustangproject`, library 2.x) today by providing their own objects through `IExportableTransaction` and the related `IZUGFeRDExportable…` interfaces. Paniolo works with its own immutable invoice model (`Invoice` and builders) instead. The migration therefore consists of an explicit mapping of the existing getters to the model. In the calling code, the creation of XML and PDF changes.

The guide is based on a runnable sample integration in the test sources of `paniolo`:

- [`MustangInterfaces`](../paniolo/src/test/java/io/github/nalukit/paniolo/migration/MustangInterfaces.java) reproduces the Mustang interfaces used, with Mustang's method names (as of library 2.24). In a real migration these are Mustang's own interfaces; Paniolo does not depend on Mustang.
- [`SampleIntegration`](../paniolo/src/test/java/io/github/nalukit/paniolo/migration/SampleIntegration.java) is the existing application: two invoices of an ERP connector with invoice data, parties, payments, cash discount, attachment, allowances on price, line, and document level, and EXTENDED content.
- [`MustangMigration`](../paniolo/src/test/java/io/github/nalukit/paniolo/migration/MustangMigration.java) is the mapping code. It is meant to be copied and adapted and contains the field table `FIELDS`, from which the table below is taken.
- [`MustangMigrationTest`](../paniolo/src/test/java/io/github/nalukit/paniolo/migration/MustangMigrationTest.java) shows that every getter used has an entry (mapped, lost, or decision), that the migrated invoices are valid in Factur-X EXTENDED, Factur-X EN 16931, and XRechnung (UBL and CII), and that values that cannot be carried over are reported by name. The test also compares this guide with `FIELDS`.

## Adapter `paniolo-mustang`

To keep the Mustang objects for now, use the optional module [`paniolo-mustang`](../paniolo-mustang/README.md). `MustangInvoiceAdapter` maps a real `org.mustangproject.Invoice` (any `IExportableTransaction`, tested with Mustang 2.26.0) to the Paniolo `Invoice` and reports every value it cannot carry over as `MappingLoss`; `toInvoice` refuses such invoices. Amounts are calculated as in Mustang's export; the test compares line amounts and totals with Mustang's own XRechnung XML. `MustangInvoiceAdapter.FIELDS` contains the mapping of all getters of the current export interfaces. The Paniolo core stays free of Mustang: only the module depends on it, with scope `provided`.

```java
MustangMapping mapping = MustangInvoiceAdapter.builder()
                                              .processControl(ProcessControl.facturXExtended())
                                              .zone(ZoneId.of("Europe/Berlin"))
                                              .build()
                                              .map(mustangInvoice);
byte[] pdf = Paniolo.create().toFacturX(mapping.invoice(), pdfA, FacturXProfile.EXTENDED);
```

The rest of this guide describes the explicit mapping without the adapter; its field table is based on the reproduced interfaces as of 2.24.

## Procedure

1. **Choose the target profile.** The mapping depends on the profile. Many Mustang fields (invoicee, delivery note reference, logistics service charges, fax number, document name, sub-lines) exist only in Factur-X EXTENDED. For XRechnung or EN 16931 they have to be dropped or mapped differently; these are the entries with status *decision*.
2. **Write the mapping.** For every getter the application implements, the table gives the Paniolo field and the conversion needed. Getters the application does not override return default values in Mustang and need no mapping.
3. **Make losses visible.** Do not drop values without counterpart silently; report them (in the sample integration `Result.lost()`), so that the business department and operations can decide.
4. **Switch the creation.** Replace `ZUGFeRDExporter…` with the Paniolo facade and check the result:

```java
Paniolo paniolo = Paniolo.create();
Invoice invoice = MustangMigration.migrate(transaction, ProcessControl.facturXExtended(), ZoneId.of("Europe/Berlin")).invoice();
byte[] pdf = paniolo.toFacturX(invoice, pdfA, FacturXProfile.EXTENDED);   // before: exporter.setTransaction(...).export(...)
ValidationResult result = paniolo.validate(pdf);                          // container, PDF/A, XML
```

For XML output only, `paniolo.toXml(invoice, InvoiceSyntax.CII)` or `InvoiceSyntax.UBL` is enough. If the application already has finished XML, `paniolo.embedXml(pdfA, xml, profile)` embeds it unchanged (Mustang's `setXML`).

## Mapping principles

- **Amounts are calculated.** Paniolo calculates line amounts and totals from quantity, price, allowances, and charges. A stated line amount (`getLineTotalAmount`) is taken over but must agree up to the rounding of the net price. Totals are not stated.
- **Prices.** Mustang's `Item.getPrice()` is the gross price. Allowances from `Product.getAllowances()` reduce it to the net price (BT-146 = BT-148 − BT-147). Allowances and charges of the line come from `Item.getItemAllowances()` and `getItemCharges()`.
- **Dates.** Mustang returns `java.util.Date`, Paniolo `LocalDate`. The conversion needs a time zone; it is a decision of the application (in the example `Europe/Berlin`).
- **Codes.** Mustang returns codes as strings, Paniolo expects code lists (`InvoiceTypeCode.of`, `CurrencyCode.of`, `Country.of`, `DutyTaxFeeCategory.of`, `PaymentMeansCode.of`). Invalid codes are therefore noticed during the mapping already.
- **One identifier, one payment means.** Paniolo keeps one identifier per party (BT-29/BT-46) and one payment means code per invoice. If the application provides more, decide which one applies.
- **No adapter in the core.** The Paniolo core gets no Mustang dependency. The reusable adapter is the optional module `paniolo-mustang`; the copied mapping code remains the alternative if the application wants to take its own decisions.

## Examples

### Invoice data and parties

```java
var invoice = Invoice.create()
                     .number(t.getNumber())
                     .issueDate(date(t.getIssueDate()))
                     .typeCode(InvoiceTypeCode.of(t.getDocumentCode()))
                     .currencyCode(CurrencyCode.of(t.getCurrency()))
                     .processControl(ProcessControl.xrechnung())          // BT-23/BT-24 of the target profile
                     .buyerReference(t.getReferenceNumber())              // Leitweg-ID
                     .seller(Party.create()
                                  .name(sender.getName())
                                  .address(Address.create()
                                                  .street(sender.getStreet())
                                                  .postalCode(sender.getZIP())
                                                  .city(sender.getLocation())
                                                  .country(Country.of(sender.getCountry()))
                                                  .build())
                                  .vatId(sender.getVATID())
                                  .identifier(sender.getGlobalID(), sender.getGlobalIDScheme())
                                  .legalRegistrationId(legal.getSchemedID().getID(), legal.getSchemedID().getScheme())
                                  .contact(new Contact(contact.getName(), contact.getPhone(), contact.getEMail()))
                                  .electronicAddress(new ElectronicAddress(sender.getUriUniversalCommunicationID(),
                                                                           sender.getUriUniversalCommunicationIDScheme()))
                                  .build())
                     .buyer(/* likewise */);
```

### Lines, price allowances, allowances and charges

```java
BigDecimal discount = sum(product.getAllowances());                       // per unit
InvoiceLine.create()
           .id(item.getId())
           .name(product.getName())
           .quantity(item.getQuantity())
           .unitCode(product.getUnit())
           .grossPrice(item.getPrice())                                   // BT-148
           .priceDiscount(discount)                                       // BT-147
           .netUnitPrice(item.getPrice().subtract(discount))              // BT-146
           .priceBaseQuantity(item.getBasisQuantity())
           .tax(new TaxCategory(DutyTaxFeeCategory.of(product.getTaxCategoryCode()), product.getVATPercent(),
                                product.getTaxExemptionReason(), product.getTaxExemptionReasonCode()))
           .addAdjustment(new LineAdjustment(false, a.getTotalAmount(), a.getBasisAmount(), a.getPercent(),
                                             a.getReason(), a.getReasonCode()))   // per getItemAllowances()
           .build();

invoice.addAdjustment(DocumentAdjustment.createAllowance()                // per getZFAllowances()
                                        .amount(a.getTotalAmount())
                                        .reason(a.getReason())
                                        .reasonCode(a.getReasonCode())
                                        .tax(TaxCategory.standard(a.getTaxPercent()))
                                        .build());
```

### Payments and cash discount

```java
invoice.paymentInstruction(PaymentInstruction.create()
                                             .meansCode(PaymentMeansCode.of(p.getPaymentMeansCode()))   // 58
                                             .meansText(p.getPaymentMeansInformation())
                                             .remittanceInformation(t.getPaymentReference())
                                             .addCreditTransfer(new CreditTransfer(p.getOwnIBAN(), p.getAccountName(), p.getOwnBIC()))
                                             .build())
       .paymentTerms(t.getPaymentTermDescription())
       .addCashDiscount(CashDiscount.of(discount.getBasePeriodMeasure(), discount.getCalculationPercentage()));

// SEPA direct debit from getTradeSettlement():
PaymentInstruction.create()
                  .meansCode(PaymentMeansCode.CODE_59)
                  .directDebit(new DirectDebit(debit.getMandate(), t.getCreditorReferenceID(), debit.getIBAN()))
                  .build();

// Cash discount available as XRechnung text only (IZUGFeRDCashDiscount.getAsXRechnung):
invoice.cashDiscounts(CashDiscount.parse(cashDiscount.getAsXRechnung()).discounts());
```

### Attachments

```java
invoice.addSupportingDocument(AdditionalSupportingDocument.create()
                                                          .reference(file.getFilename())
                                                          .description(file.getDescription())
                                                          .attachment(Attachment.create()
                                                                                .content(file.getData())
                                                                                .mimeCode(file.getMimetype())   // PDF, PNG, JPEG, CSV, XLSX, ODS
                                                                                .filename(file.getFilename())
                                                                                .build())
                                                          .build());
// With relationship (getRelation) or another file type, as a file in the PDF instead:
paniolo.toFacturX(invoice, pdfA, profile, List.of(PdfAttachment.of(file.getFilename(), file.getData(), file.getMimetype())));
```

### PDF compression

`ZUGFeRDExporterFromPDFA.setEnablePDFCompression(...)` and `setEnablePDFAttachmentCompression(...)`
have no counterpart and need none. Paniolo always compresses the embedded files and saves the
PDF with object streams (PDFBox `CompressParameters.DEFAULT_COMPRESSION`), which PDF/A-3 allows.
The streams of the given PDF are taken as they are: a compressed PDF stays compressed, an
uncompressed one is not compressed afterwards. Pass a compressed PDF to get a small file.

### EXTENDED fields

```java
invoice.processControl(ProcessControl.facturXExtended())
       .extended(ExtendedInformation.create()
                                    .documentName(t.getDocumentName())
                                    .invoicee(party(t.getInvoicee()))
                                    .deliveryNoteReference(new DocumentReference(t.getDeliveryNoteReferencedDocumentID(), null,
                                                                                 date(t.getDeliveryNoteReferencedDocumentDate())))
                                    .addLogisticsServiceCharge(new LogisticsServiceCharge(c.getReason(), c.getTotalAmount(),
                                                                                          TaxCategory.standard(c.getTaxPercent())))
                                    .build());
// Fax number: the contact as EXTENDED contact instead of Party.contact
Party.create().extended(new PartyExtension(null, null,
                                           new ExtendedContact(c.getName(), null, null, c.getPhone(), c.getFax(), c.getEMail()),
                                           null, null));
```

## Result of the sample integration

| Target | Check | Reported losses |
| --- | --- | --- |
| Factur-X EXTENDED | valid; reading returns the same model | date of the purchase order reference, project name, relationship of the attachment in the XML |
| Factur-X EN 16931 | valid; logistics service charges as document charge, same amount due | additionally document name, invoicee, delivery note reference, fax number |
| XRechnung UBL and CII (direct debit) | valid; mandate, creditor ID, and cash discount carried over | fax number |

Building the sample integration revealed two requirements that Mustang applications often do not meet and that should be checked during the migration: XRechnung requires a business process identifier (BT-23, PEPPOL-EN16931-R001), and EN 16931 allows only PDF, PNG, JPEG, CSV, XLSX, and ODS as embedded attachments.

## Field table

Status: *mapped* – direct counterpart, possibly with conversion; *decision* – can be mapped depending on the profile or a business decision; *lost* – no counterpart in EN 16931 and EXTENDED. The table covers the getters of the sample integration; further Mustang getters are to be added following the same pattern.

### Document

| Mustang getter | Status | Paniolo | Note |
| --- | --- | --- | --- |
| `ExportableTransaction.getDocumentCode` | mapped | BT-3 Invoice.typeCode | InvoiceTypeCode.of(code) |
| `ExportableTransaction.getDocumentName` | decision | ExtendedInformation.documentName | EXTENDED only; other profiles derive the name from the type code |
| `ExportableTransaction.getTestIndicator` | decision | ExtendedInformation.testIndicator | EXTENDED only |
| `ExportableTransaction.getNumber` | mapped | BT-1 Invoice.number |  |
| `ExportableTransaction.getIssueDate` | mapped | BT-2 Invoice.issueDate | java.util.Date to LocalDate in the time zone of the application |
| `ExportableTransaction.getDueDate` | mapped | BT-9 Invoice.dueDate | PaymentTerms.getDueDate if not set |
| `ExportableTransaction.getDeliveryDate` | mapped | BT-72 DeliveryInformation.actualDeliveryDate |  |
| `ExportableTransaction.getCurrency` | mapped | BT-5 Invoice.currencyCode | CurrencyCode.of(code) |
| `ExportableTransaction.getBusinessProcessId` | mapped | BT-23 ProcessControl.businessProcessId | if null, the one of the target profile (XRechnung requires BT-23); BT-24 comes from the target profile |
| `ExportableTransaction.getReferenceNumber` | mapped | BT-10 Invoice.buyerReference | Leitweg-ID for XRechnung |

### Parties

| Mustang getter | Status | Paniolo | Note |
| --- | --- | --- | --- |
| `ExportableTransaction.getSender` | mapped | BG-4 Invoice.seller | see TradeParty |
| `ExportableTransaction.getRecipient` | mapped | BG-7 Invoice.buyer | see TradeParty |
| `ExportableTransaction.getPayee` | mapped | BG-10 Invoice.payee | name, identifier and legal registration only; EN 16931 has no payee address or contact |
| `ExportableTransaction.getDeliveryAddress` | mapped | BG-13 DeliveryInformation | name, location identifier and address; tax and contact data of the ship-to party are lost |
| `ExportableTransaction.getInvoicee` | decision | ExtendedInformation.invoicee | EXTENDED only |
| `TradeParty.getName` | mapped | BT-27 / BT-44 Party.name |  |
| `TradeParty.getStreet` | mapped | BT-35 / BT-50 Address.street |  |
| `TradeParty.getAdditionalAddress` | mapped | BT-36 / BT-51 Address.additionalStreet |  |
| `TradeParty.getZIP` | mapped | BT-38 / BT-53 Address.postalCode |  |
| `TradeParty.getLocation` | mapped | BT-37 / BT-52 Address.city |  |
| `TradeParty.getCountry` | mapped | BT-40 / BT-55 Address.country | Country.of(code) |
| `TradeParty.getVATID` | mapped | BT-31 / BT-48 Party.vatId |  |
| `TradeParty.getTaxID` | mapped | BT-32 Party.taxId | seller only; a buyer tax number is lost |
| `TradeParty.getID` | decision | BT-29 / BT-46 Party.identifier | Paniolo has one party identifier: the global identifier wins, a second ID is lost |
| `TradeParty.getGlobalID` | mapped | BT-29 / BT-46 Party.identifier | with scheme |
| `TradeParty.getGlobalIDScheme` | mapped | BT-29-1 / BT-46-1 Party.identifierSchemeId |  |
| `TradeParty.getUriUniversalCommunicationID` | mapped | BT-34 / BT-49 ElectronicAddress.value |  |
| `TradeParty.getUriUniversalCommunicationIDScheme` | mapped | BT-34-1 / BT-49-1 ElectronicAddress.schemeId |  |
| `TradeParty.getContact` | mapped | BG-6 / BG-9 Party.contact | see Contact |
| `TradeParty.getLegalOrganisation` | mapped | BT-30 / BT-47, BT-28 / BT-45 | see LegalOrganisation |
| `TradeParty.getDescription` | mapped | BT-33 Party.additionalLegalInformation | seller only; a buyer description is lost |
| `Contact.getName` | mapped | BT-41 / BT-56 Contact.name |  |
| `Contact.getPhone` | mapped | BT-42 / BT-57 Contact.telephone |  |
| `Contact.getEMail` | mapped | BT-43 / BT-58 Contact.email |  |
| `Contact.getFax` | decision | PartyExtension.contact (ExtendedContact.fax) | EXTENDED only, which then carries the whole contact |
| `LegalOrganisation.getSchemedID` | mapped | BT-30 / BT-47 Party.legalRegistrationId | see SchemedID |
| `LegalOrganisation.getTradingBusinessName` | mapped | BT-28 / BT-45 Party.tradingName |  |
| `SchemedID.getScheme` | mapped | BT-30-1 / BT-47-1 Party.legalRegistrationSchemeId |  |
| `SchemedID.getID` | mapped | BT-30 / BT-47 Party.legalRegistrationId |  |

### Lines

| Mustang getter | Status | Paniolo | Note |
| --- | --- | --- | --- |
| `ExportableTransaction.getZFItems` | mapped | BG-25 Invoice.lines | see Item |
| `Item.getId` | mapped | BT-126 InvoiceLine.id | Mustang numbers lines itself if null; Paniolo needs the ID |
| `Item.getProduct` | mapped | BG-31 item information | see Product |
| `Item.getPrice` | mapped | BT-148 InvoiceLine.grossPrice, BT-146 netUnitPrice | Mustang's price is the gross price; net price = price - product allowances |
| `Item.getQuantity` | mapped | BT-129 InvoiceLine.quantity |  |
| `Item.getBasisQuantity` | mapped | BT-149 InvoiceLine.priceBaseQuantity |  |
| `Item.getItemAllowances` | mapped | BG-27 InvoiceLine.adjustments | line level allowances (LineAdjustment) |
| `Item.getItemCharges` | mapped | BG-28 InvoiceLine.adjustments | line level charges (LineAdjustment) |
| `Item.getNotes` | decision | BT-127 InvoiceLine.note | EN 16931 has one line note: several notes are joined with line breaks |
| `Item.getDetailedDeliveryPeriodFrom` | mapped | BT-134 InvoiceLine.period |  |
| `Item.getDetailedDeliveryPeriodTo` | mapped | BT-135 InvoiceLine.period |  |
| `Item.getBuyerOrderReferencedDocumentLineID` | mapped | BT-132 InvoiceLine.orderLineReference |  |
| `Item.getAccountingReference` | mapped | BT-133 InvoiceLine.buyerAccountingReference |  |
| `Item.getLineTotalAmount` | decision | BT-131 InvoiceLine.netAmount | Paniolo calculates the amount; a stated amount must agree up to the rounding of the net price, see ImportResult (G01) for received documents |
| `Item.getParentLineID` | decision | LineExtension.parentLineId | EXTENDED only; XRechnung sub-lines are nested lines (InvoiceLine.addSubLine) instead |
| `Product.getName` | mapped | BT-153 InvoiceLine.name |  |
| `Product.getDescription` | mapped | BT-154 InvoiceLine.description |  |
| `Product.getUnit` | mapped | BT-130 InvoiceLine.unitCode | UN/ECE Rec 20 code |
| `Product.getVATPercent` | mapped | BT-152 TaxCategory.rate | no rate for category O |
| `Product.getTaxCategoryCode` | mapped | BT-151 TaxCategory.code | DutyTaxFeeCategory.of(code) |
| `Product.getTaxExemptionReason` | mapped | BT-120 TaxCategory.exemptionReason |  |
| `Product.getTaxExemptionReasonCode` | mapped | BT-121 TaxCategory.exemptionReasonCode |  |
| `Product.getSellerAssignedID` | mapped | BT-155 InvoiceLine.sellerItemId |  |
| `Product.getBuyerAssignedID` | mapped | BT-156 InvoiceLine.buyerItemId |  |
| `Product.getGlobalID` | mapped | BT-157 InvoiceLine.standardItemId |  |
| `Product.getGlobalIDScheme` | mapped | BT-157-1 InvoiceLine.standardItemSchemeId |  |
| `Product.getCountryOfOrigin` | mapped | BT-159 InvoiceLine.originCountry |  |
| `Product.getAttributes` | mapped | BG-32 InvoiceLine.attributes |  |
| `Product.getAllowances` | mapped | BT-147 InvoiceLine.priceDiscount; EXTENDED: LineExtension.priceAdjustments | sum of the amounts per unit; in EXTENDED each allowance with reason as `GrossPriceAdjustment`, percentages are lost |
| `Product.getCharges` | decision | BT-146 InvoiceLine.netUnitPrice; EXTENDED: LineExtension.priceAdjustments | EN 16931 has no price level charge (PEPPOL-EN16931-R044): added to the net price; in EXTENDED a `GrossPriceAdjustment.charge`, which reduces BT-147 |

### Allowances and charges

| Mustang getter | Status | Paniolo | Note |
| --- | --- | --- | --- |
| `ExportableTransaction.getZFAllowances` | mapped | BG-20 Invoice.adjustments | DocumentAdjustment.createAllowance() |
| `ExportableTransaction.getZFCharges` | mapped | BG-21 Invoice.adjustments | DocumentAdjustment.createCharge() |
| `ExportableTransaction.getZFLogisticsServiceCharges` | decision | ExtendedInformation.logisticsServiceCharges | EXTENDED; other profiles carry them as document charges |
| `AllowanceCharge.getTotalAmount` | mapped | BT-92 / BT-99 / BT-136 / BT-141 amount |  |
| `AllowanceCharge.getPercent` | mapped | BT-94 / BT-101 / BT-138 / BT-143 percentage |  |
| `AllowanceCharge.getBasisAmount` | mapped | BT-93 / BT-100 / BT-137 / BT-142 baseAmount |  |
| `AllowanceCharge.getReason` | mapped | BT-97 / BT-104 / BT-139 / BT-144 reason |  |
| `AllowanceCharge.getReasonCode` | mapped | BT-98 / BT-105 / BT-140 / BT-145 reasonCode | UNTDID 5189 for allowances, 7161 for charges |
| `AllowanceCharge.getTaxPercent` | mapped | BT-96 / BT-103 TaxCategory.rate | document level only; a line adjustment takes the VAT of its line |
| `AllowanceCharge.getCategoryCode` | mapped | BT-95 / BT-102 TaxCategory.code | document level only |
| `AllowanceCharge.isCharge` | mapped | DocumentAdjustment.charge / LineAdjustment.charge |  |

### Payment

| Mustang getter | Status | Paniolo | Note |
| --- | --- | --- | --- |
| `ExportableTransaction.getTradeSettlementPayment` | mapped | BG-16 PaymentInstruction, BG-17 CreditTransfer | one payment means per invoice; every account becomes a CreditTransfer |
| `ExportableTransaction.getTradeSettlement` | decision | BG-19 DirectDebit | used if there is no credit transfer; EN 16931 has one payment means code per invoice |
| `ExportableTransaction.getCreditorReferenceID` | mapped | BT-90 DirectDebit.creditorId | only together with a direct debit |
| `ExportableTransaction.getPaymentReference` | mapped | BT-83 PaymentInstruction.remittanceInformation |  |
| `ExportableTransaction.getPaymentTermDescription` | mapped | BT-20 Invoice.paymentTerms |  |
| `ExportableTransaction.getPaymentTerms` | mapped | BT-20, BT-9, Invoice.cashDiscounts | see PaymentTerms |
| `ExportableTransaction.getCashDiscounts` | mapped | Invoice.cashDiscounts | parsed from the XRechnung text with CashDiscount.parse |
| `ExportableTransaction.getTotalPrepaidAmount` | mapped | BT-113 Invoice.paidAmount |  |
| `ExportableTransaction.getRoundingAmount` | mapped | BT-114 Invoice.roundingAmount |  |
| `TradeSettlementPayment.getPaymentMeansCode` | mapped | BT-81 PaymentInstruction.meansCode | PaymentMeansCode.of(code) |
| `TradeSettlementPayment.getPaymentMeansInformation` | mapped | BT-82 PaymentInstruction.meansText |  |
| `TradeSettlementPayment.getOwnIBAN` | mapped | BT-84 CreditTransfer.iban |  |
| `TradeSettlementPayment.getOwnBIC` | mapped | BT-86 CreditTransfer.bic |  |
| `TradeSettlementPayment.getAccountName` | mapped | BT-85 CreditTransfer.accountName |  |
| `TradeSettlementPayment.getOwnPaymentInfoText` | decision | BT-82 PaymentInstruction.meansText | same business term as getPaymentMeansInformation: used only if that is empty |
| `TradeSettlementDebit.getPaymentMeansCode` | mapped | BT-81 PaymentInstruction.meansCode |  |
| `TradeSettlementDebit.getIBAN` | mapped | BT-91 DirectDebit.debitedAccountId |  |
| `TradeSettlementDebit.getMandate` | mapped | BT-89 DirectDebit.mandateReference |  |
| `PaymentTerms.getDescription` | mapped | BT-20 Invoice.paymentTerms | if getPaymentTermDescription is empty |
| `PaymentTerms.getDueDate` | mapped | BT-9 Invoice.dueDate | if getDueDate is empty |
| `PaymentTerms.getDiscountTerms` | mapped | Invoice.cashDiscounts | see PaymentDiscountTerms |
| `PaymentDiscountTerms.getCalculationPercentage` | mapped | CashDiscount.percent |  |
| `PaymentDiscountTerms.getBaseDate` | decision | - | the discount period counts from the issue date (XRechnung #SKONTO#); another base date is lost |
| `PaymentDiscountTerms.getBasePeriodMeasure` | mapped | CashDiscount.days |  |
| `PaymentDiscountTerms.getBasePeriodUnitCode` | decision | CashDiscount.days | only days (DAY); other units are lost |
| `CashDiscount.getAsXRechnung` | mapped | Invoice.cashDiscounts | CashDiscount.parse |
| `CashDiscount.getAsCII` | lost | - | the same discount as text; not needed |

### References

| Mustang getter | Status | Paniolo | Note |
| --- | --- | --- | --- |
| `ExportableTransaction.getBuyerOrderReferencedDocumentID` | mapped | BT-13 References.purchaseOrderReference |  |
| `ExportableTransaction.getBuyerOrderReferencedDocumentIssueDateTime` | lost | - | EN 16931 has no date for BT-13 |
| `ExportableTransaction.getSellerOrderReferencedDocumentID` | mapped | BT-14 References.salesOrderReference |  |
| `ExportableTransaction.getContractReferencedDocument` | mapped | BT-12 References.contractReference |  |
| `ExportableTransaction.getSpecifiedProcuringProjectID` | mapped | BT-11 References.projectReference |  |
| `ExportableTransaction.getSpecifiedProcuringProjectName` | lost | - | EN 16931 has no project name; CII writes a placeholder |
| `ExportableTransaction.getDespatchAdviceReferencedDocumentID` | mapped | BT-16 References.despatchAdviceReference |  |
| `ExportableTransaction.getDeliveryNoteReferencedDocumentID` | decision | ExtendedInformation.deliveryNoteReference | EXTENDED only |
| `ExportableTransaction.getDeliveryNoteReferencedDocumentDate` | decision | ExtendedInformation.deliveryNoteReference | EXTENDED only |
| `ExportableTransaction.getInvoiceReferencedDocumentID` | mapped | BT-25 PrecedingInvoiceReference.number |  |
| `ExportableTransaction.getInvoiceReferencedIssueDate` | mapped | BT-26 PrecedingInvoiceReference.issueDate |  |
| `ExportableTransaction.getDetailedDeliveryPeriodFrom` | mapped | BT-73 Invoice.invoicePeriod |  |
| `ExportableTransaction.getDetailedDeliveryPeriodTo` | mapped | BT-74 Invoice.invoicePeriod |  |
| `ExportableTransaction.getVATDueDateTypeCode` | mapped | BT-8 Invoice.taxPointDateCode | CII code 5, 29, 72 to UNTDID 2005 code 3, 35, 432 |

### Notes and attachments

| Mustang getter | Status | Paniolo | Note |
| --- | --- | --- | --- |
| `ExportableTransaction.getNotes` | mapped | BG-1 Invoice.notes | without subject code |
| `ExportableTransaction.getNotesWithSubjectCode` | mapped | BG-1 InvoiceNote | BT-21 subject code |
| `ExportableTransaction.getSubjectNote` | mapped | BG-1 Invoice.notes |  |
| `ExportableTransaction.getOwnOrganisationFullPlaintextInfo` | mapped | BG-1 InvoiceNote with subject code REG | as Mustang writes it |
| `ExportableTransaction.getAdditionalReferencedDocuments` | mapped | BG-24 Invoice.supportingDocuments | see FileAttachment; alternatively PdfAttachment next to the XML in the PDF |
| `IncludedNote.getSubjectCode` | mapped | BT-21 InvoiceNote.subjectCode |  |
| `IncludedNote.getContent` | mapped | BT-22 InvoiceNote.text |  |
| `FileAttachment.getFilename` | mapped | BT-122 reference, BT-125-2 Attachment.filename | the file name is the reference |
| `FileAttachment.getMimetype` | mapped | BT-125-1 Attachment.mimeCode | EN 16931 allows PDF, PNG, JPEG, CSV, XLSX and ODS only; other files only as PdfAttachment |
| `FileAttachment.getRelation` | decision | PdfAttachment.relationship | embedded in the XML (BG-24) the relationship is lost; as PDF attachment it is kept |
| `FileAttachment.getData` | mapped | BT-125 Attachment.content |  |
| `FileAttachment.getDescription` | mapped | BT-123 AdditionalSupportingDocument.description |  |
