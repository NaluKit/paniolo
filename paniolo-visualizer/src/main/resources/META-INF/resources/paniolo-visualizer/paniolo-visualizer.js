/*
 * Copyright © 2026 Frank Hossfeld
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
/*
 * Paniolo Visualizer
 *
 * A dependency-free web component that renders electronic invoices, orders, and despatch advices
 * in the browser. It accepts the document as bytes and reads it in the browser:
 *
 *   - UBL 2.1 Invoice, CreditNote, and DespatchAdvice (XRechnung, EN 16931, Peppol, 1Lieferschein)
 *   - UN/CEFACT CII (XRechnung, EN 16931, all Factur-X / ZUGFeRD 2 profiles, EXTENDED-CTC-FR)
 *   - ZUGFeRD 1.0
 *   - Order-X 1.0 (order, order change, order response)
 *   - CIDA delivery notes
 *   - PDF/A-3 with an embedded document (Factur-X / ZUGFeRD, Order-X, CIDA), incl. attachments
 *
 * Usage:
 *
 *   <script type="module" src="paniolo-visualizer.js">…
 *   <paniolo-viewer lang="de"></paniolo-viewer>
 *
 *   document.querySelector('paniolo-viewer').load(bytes);   // Uint8Array, ArrayBuffer, Blob, Response, string
 *
 * No network access, no globals, no innerHTML of document content: every value of the document is
 * escaped, attachments are offered as downloads only.
 */

const VERSION = '1.0.0';
const TAG_NAME = 'paniolo-viewer';

// ------------------------------------------------------------------------------------------------
// Errors
// ------------------------------------------------------------------------------------------------

/** Error with a code that the viewer translates. */
export class PanioloError extends Error {
    constructor(code, detail) {
        super(detail ? code + ': ' + detail : code);
        this.name = 'PanioloError';
        this.code = code;
        this.detail = detail;
    }
}

// ------------------------------------------------------------------------------------------------
// Input
// ------------------------------------------------------------------------------------------------

async function toBytes(input) {
    if (input == null) {
        throw new PanioloError('empty');
    }
    if (input instanceof Uint8Array) {
        return input;
    }
    if (input instanceof ArrayBuffer) {
        return new Uint8Array(input);
    }
    if (ArrayBuffer.isView(input)) {
        return new Uint8Array(input.buffer, input.byteOffset, input.byteLength);
    }
    if (typeof Blob !== 'undefined' && input instanceof Blob) {
        return new Uint8Array(await input.arrayBuffer());
    }
    if (typeof Response !== 'undefined' && input instanceof Response) {
        if (!input.ok) {
            throw new PanioloError('fetch', String(input.status));
        }
        return new Uint8Array(await input.arrayBuffer());
    }
    if (Array.isArray(input)) {
        // e.g. a Java byte[] serialized as numbers (signed)
        return Uint8Array.from(input, b => b & 0xff);
    }
    if (typeof input === 'string') {
        const trimmed = input.trim();
        if (trimmed.startsWith('<')) {
            return new TextEncoder().encode(trimmed);
        }
        if (/^[A-Za-z0-9+/=\s_-]+$/.test(trimmed)) {
            const bytes = base64(trimmed);
            if (bytes) {
                return bytes;
            }
        }
        throw new PanioloError('input');
    }
    throw new PanioloError('input');
}

function base64(text) {
    try {
        const clean = text.replace(/\s+/g, '').replace(/-/g, '+').replace(/_/g, '/');
        return Uint8Array.from(atob(clean), c => c.charCodeAt(0));
    } catch (e) {
        return null;
    }
}

function latin1(bytes) {
    let out = '';
    for (let i = 0; i < bytes.length; i += 0x8000) {
        out += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
    }
    return out;
}

function isPdf(bytes) {
    return latin1(bytes.subarray(0, 1024)).includes('%PDF-');
}

function decodeText(bytes) {
    if (bytes[0] === 0xfe && bytes[1] === 0xff) {
        return new TextDecoder('utf-16be').decode(bytes);
    }
    if (bytes[0] === 0xff && bytes[1] === 0xfe) {
        return new TextDecoder('utf-16le').decode(bytes);
    }
    const head = latin1(bytes.subarray(0, 256));
    const declared = /^\s*<\?xml[^>]*encoding\s*=\s*["']([A-Za-z0-9._-]+)["']/.exec(head);
    try {
        return new TextDecoder(declared ? declared[1].toLowerCase() : 'utf-8').decode(bytes);
    } catch (e) {
        return new TextDecoder('utf-8').decode(bytes);
    }
}

// ------------------------------------------------------------------------------------------------
// XML access, independent of namespace prefixes and versions
// ------------------------------------------------------------------------------------------------

function parseXml(text) {
    const doc = new DOMParser().parseFromString(text, 'application/xml');
    const root = doc.documentElement;
    const error = !root || root.localName === 'parsererror' ?
        root :
        doc.getElementsByTagName('parsererror')[0];
    if (error) {
        throw new PanioloError('xml', (error && error.textContent || '').trim().split('\n')[0]);
    }
    return doc;
}

/** Children of el with one of the local names, e.g. "ID|GlobalID". */
function children(el, names) {
    const out = [];
    if (!el) {
        return out;
    }
    const wanted = names.split('|');
    for (let c = el.firstElementChild; c; c = c.nextElementSibling) {
        if (wanted.includes(c.localName)) {
            out.push(c);
        }
    }
    return out;
}

/** All elements at a path of local names, e.g. "PostalTradeAddress/LineOne". */
function all(el, path) {
    let current = el ? [el] : [];
    for (const step of path.split('/')) {
        current = current.flatMap(e => children(e, step));
        if (!current.length) {
            break;
        }
    }
    return current;
}

function one(el, path) {
    return el ? all(el, path)[0] || null : null;
}

function txt(el, path) {
    const e = path ? one(el, path) : el;
    const value = e && e.textContent.trim();
    return value || undefined;
}

function at(el, path, name) {
    const e = path ? one(el, path) : el;
    const value = e && e.getAttribute(name);
    return value ? value.trim() : undefined;
}

function idOf(el) {
    return el && txt(el) ? {value: txt(el), scheme: at(el, null, 'schemeID')} : undefined;
}

function texts(el, path) {
    return all(el, path).map(e => txt(e)).filter(Boolean);
}

/** Removes empty values; returns undefined for an empty object. */
function clean(obj) {
    if (!obj) {
        return undefined;
    }
    const out = {};
    let any = false;
    for (const [key, value] of Object.entries(obj)) {
        if (value === undefined || value === null || value === '') {
            continue;
        }
        if (Array.isArray(value)) {
            const list = value.filter(v => v !== undefined && v !== null);
            if (!list.length) {
                continue;
            }
            out[key] = list;
        } else {
            out[key] = value;
        }
        any = true;
    }
    return any ? out : undefined;
}

function note(text) {
    // XRechnung writes the subject code into the text: "#ADU#Hinweis"
    const m = /^#([A-Z]{3})#([\s\S]*)$/.exec(text);
    return m ? {subject: m[1], text: m[2].trim()} : {text};
}

// ------------------------------------------------------------------------------------------------
// Code lists that decide the kind of a document
// ------------------------------------------------------------------------------------------------

const CREDIT_NOTE_TYPES = ['81', '83', '261', '262', '296', '308', '381', '396', '420', '458', '532'];
const ORDER_KINDS = {'220': 'order', '230': 'orderChange', '231': 'orderResponse'};

function profileLabel(id) {
    if (!id) {
        return undefined;
    }
    const s = id.toLowerCase();
    let m;
    if ((m = /xrechnung_(\d+\.\d+)/.exec(s))) {
        return 'XRechnung ' + m[1] + (s.includes('extension') ? ' Extension' : '');
    }
    if (s.includes('extended-ctc-fr')) {
        return 'EXTENDED-CTC-FR';
    }
    if ((m = /factur-x\.eu:1p0:(minimum|basicwl|basic|extended)/.exec(s))) {
        return {minimum: 'MINIMUM', basicwl: 'BASIC WL', basic: 'BASIC', extended: 'EXTENDED'}[m[1]];
    }
    if ((m = /crossindustrydocument:invoice:1p0:(\w+)/.exec(s))) {
        return 'ZUGFeRD 1.0 ' + m[1].toUpperCase();
    }
    if ((m = /order-x\.eu:1p0:(\w+)/.exec(s))) {
        return 'Order-X ' + m[1].toUpperCase();
    }
    if (s.includes('poacc:billing:3.0')) {
        return 'Peppol BIS Billing 3.0';
    }
    if (s.includes('poacc:trns:despatch_advice') || s.includes('despatch_advice:3')) {
        return 'Peppol Despatch Advice 3';
    }
    if (s.includes('1lieferschein')) {
        return '1Lieferschein';
    }
    if (s.includes('cida')) {
        return 'CIDA';
    }
    if (s === 'urn:cen.eu:en16931:2017') {
        return 'EN 16931';
    }
    if (s.startsWith('urn:cen.eu:en16931:2017')) {
        return 'EN 16931 CIUS';
    }
    return id;
}

// ------------------------------------------------------------------------------------------------
// UBL
// ------------------------------------------------------------------------------------------------

function ublAddress(a) {
    return a ? clean({
        lines: [txt(a, 'StreetName'), txt(a, 'AdditionalStreetName'), ...texts(a, 'AddressLine/Line')],
        postcode: txt(a, 'PostalZone'),
        city: txt(a, 'CityName'),
        region: txt(a, 'CountrySubentity'),
        country: txt(a, 'Country/IdentificationCode')
    }) : undefined;
}

function ublParty(p) {
    if (!p) {
        return undefined;
    }
    let vatId;
    let taxId;
    for (const scheme of all(p, 'PartyTaxScheme')) {
        if (txt(scheme, 'TaxScheme/ID') === 'VAT') {
            vatId = vatId || txt(scheme, 'CompanyID');
        } else {
            taxId = taxId || txt(scheme, 'CompanyID');
        }
    }
    const legal = one(p, 'PartyLegalEntity');
    const partyName = txt(p, 'PartyName/Name');
    const name = txt(legal, 'RegistrationName') || partyName;
    return clean({
        name,
        tradingName: partyName !== name ? partyName : undefined,
        ids: all(p, 'PartyIdentification/ID').map(idOf),
        legalId: idOf(one(legal, 'CompanyID')),
        legalInfo: txt(legal, 'CompanyLegalForm'),
        vatId,
        taxId,
        endpoint: idOf(one(p, 'EndpointID')),
        address: ublAddress(one(p, 'PostalAddress')),
        contact: clean({
            name: txt(p, 'Contact/Name'),
            phone: txt(p, 'Contact/Telephone'),
            email: txt(p, 'Contact/ElectronicMail')
        })
    });
}

function ublAllowanceCharge(e) {
    return clean({
        charge: txt(e, 'ChargeIndicator') === 'true',
        amount: txt(e, 'Amount'),
        base: txt(e, 'BaseAmount'),
        percent: txt(e, 'MultiplierFactorNumeric'),
        reason: txt(e, 'AllowanceChargeReason'),
        reasonCode: txt(e, 'AllowanceChargeReasonCode'),
        taxCategory: txt(e, 'TaxCategory/ID'),
        taxRate: txt(e, 'TaxCategory/Percent')
    });
}

function period(el, start, end) {
    return el ? clean({start: txt(el, start), end: txt(el, end)}) : undefined;
}

function ublLine(l, index) {
    const quantity = one(l, 'InvoicedQuantity|CreditedQuantity|DeliveredQuantity');
    const item = one(l, 'Item');
    const price = one(l, 'Price');
    const priceAllowance = one(price, 'AllowanceCharge');
    const baseQuantity = one(price, 'BaseQuantity');
    return clean({
        index,
        id: txt(l, 'ID'),
        note: texts(l, 'Note').join('\n'),
        name: txt(item, 'Name'),
        description: txt(item, 'Description'),
        sellerId: txt(item, 'SellersItemIdentification/ID'),
        buyerId: txt(item, 'BuyersItemIdentification/ID'),
        standardId: idOf(one(item, 'StandardItemIdentification/ID')),
        classifications: all(item, 'CommodityClassification/ItemClassificationCode')
            .map(e => ({value: txt(e), scheme: at(e, null, 'listID')})),
        origin: txt(item, 'OriginCountry/IdentificationCode'),
        attributes: all(item, 'AdditionalItemProperty').map(p => ({name: txt(p, 'Name'), value: txt(p, 'Value')})),
        instances: all(l, 'Item/ItemInstance').map(i => clean({
            batch: txt(i, 'LotIdentification/LotNumberID'),
            serial: txt(i, 'SerialID'),
            expiry: txt(i, 'LotIdentification/ExpiryDate')
        })),
        quantity: txt(quantity),
        unit: at(quantity, null, 'unitCode'),
        outstanding: txt(l, 'OutstandingQuantity'),
        outstandingReason: txt(l, 'OutstandingReason'),
        netPrice: txt(price, 'PriceAmount'),
        baseQuantity: txt(baseQuantity),
        baseUnit: at(baseQuantity, null, 'unitCode'),
        grossPrice: txt(priceAllowance, 'BaseAmount'),
        priceDiscount: txt(priceAllowance, 'Amount'),
        total: txt(l, 'LineExtensionAmount'),
        taxCategory: txt(item, 'ClassifiedTaxCategory/ID'),
        taxRate: txt(item, 'ClassifiedTaxCategory/Percent'),
        period: period(one(l, 'InvoicePeriod'), 'StartDate', 'EndDate'),
        orderLine: txt(l, 'OrderLineReference/LineID'),
        objectId: txt(l, 'DocumentReference/ID'),
        accountingCost: txt(l, 'AccountingCost'),
        allowanceCharges: all(l, 'AllowanceCharge').map(ublAllowanceCharge),
        subLines: all(l, 'SubInvoiceLine').map(s => ublLine(s))
    });
}

function ublAttachment(r) {
    const binary = one(r, 'Attachment/EmbeddedDocumentBinaryObject');
    return clean({
        source: 'xml',
        id: txt(r, 'ID'),
        description: txt(r, 'DocumentDescription'),
        typeCode: txt(r, 'DocumentTypeCode'),
        name: at(binary, null, 'filename'),
        mime: at(binary, null, 'mimeCode'),
        data: binary ? base64(binary.textContent) : undefined,
        uri: txt(r, 'Attachment/ExternalReference/URI')
    });
}

function mapUbl(root) {
    if (root.localName === 'DespatchAdvice') {
        return mapUblDespatchAdvice(root);
    }
    const credit = root.localName === 'CreditNote';
    const currency = txt(root, 'DocumentCurrencyCode');
    const taxCurrency = txt(root, 'TaxCurrencyCode');
    const taxTotals = all(root, 'TaxTotal');
    const mainTax = taxTotals.find(t => all(t, 'TaxSubtotal').length) || taxTotals[0];
    const otherTax = taxTotals.find(t => t !== mainTax);
    const total = one(root, 'LegalMonetaryTotal');
    const documents = all(root, 'AdditionalDocumentReference');
    const means = all(root, 'PaymentMeans');
    const seller = one(root, 'AccountingSupplierParty/Party');
    const delivery = one(root, 'Delivery');
    const specification = txt(root, 'CustomizationID');
    return {
        kind: credit ? 'creditNote' : 'invoice',
        syntax: 'UBL',
        specification,
        profile: profileLabel(specification),
        businessProcess: txt(root, 'ProfileID'),
        number: txt(root, 'ID'),
        typeCode: txt(root, 'InvoiceTypeCode|CreditNoteTypeCode'),
        issueDate: txt(root, 'IssueDate'),
        dueDate: txt(root, 'DueDate') || txt(root, 'PaymentMeans/PaymentDueDate'),
        taxPointDate: txt(root, 'TaxPointDate'),
        currency,
        taxCurrency,
        notes: texts(root, 'Note').map(note),
        period: period(one(root, 'InvoicePeriod'), 'StartDate', 'EndDate'),
        seller: ublParty(seller),
        buyer: ublParty(one(root, 'AccountingCustomerParty/Party')),
        payee: ublParty(one(root, 'PayeeParty')),
        taxRepresentative: ublParty(one(root, 'TaxRepresentativeParty')),
        shipTo: clean({
            name: txt(delivery, 'DeliveryParty/PartyName/Name'),
            ids: [idOf(one(delivery, 'DeliveryLocation/ID'))],
            address: ublAddress(one(delivery, 'DeliveryLocation/Address'))
        }),
        delivery: clean({date: txt(delivery, 'ActualDeliveryDate')}),
        references: clean({
            buyerReference: txt(root, 'BuyerReference'),
            order: txt(root, 'OrderReference/ID'),
            salesOrder: txt(root, 'OrderReference/SalesOrderID'),
            contract: txt(root, 'ContractDocumentReference/ID'),
            project: txt(root, 'ProjectReference/ID'),
            despatchAdvice: txt(root, 'DespatchDocumentReference/ID'),
            receivingAdvice: txt(root, 'ReceiptDocumentReference/ID'),
            tender: txt(root, 'OriginatorDocumentReference/ID'),
            accountingCost: txt(root, 'AccountingCost'),
            object: documents.filter(d => txt(d, 'DocumentTypeCode') === '130').map(d => txt(d, 'ID'))[0],
            precedingInvoices: all(root, 'BillingReference/InvoiceDocumentReference')
                .map(r => clean({id: txt(r, 'ID'), date: txt(r, 'IssueDate')}))
        }),
        payment: clean({
            means: means.map(pm => clean({
                code: txt(pm, 'PaymentMeansCode'),
                text: at(pm, 'PaymentMeansCode', 'name'),
                accounts: all(pm, 'PayeeFinancialAccount').map(a => clean({
                    iban: txt(a, 'ID'),
                    name: txt(a, 'Name'),
                    bic: txt(a, 'FinancialInstitutionBranch/ID')
                })),
                card: clean({
                    number: txt(pm, 'CardAccount/PrimaryAccountNumberID'),
                    network: txt(pm, 'CardAccount/NetworkID'),
                    holder: txt(pm, 'CardAccount/HolderName')
                }),
                debitAccount: txt(pm, 'PaymentMandate/PayerFinancialAccount/ID')
            })),
            remittance: means.map(pm => txt(pm, 'PaymentID')).find(Boolean),
            mandate: means.map(pm => txt(pm, 'PaymentMandate/ID')).find(Boolean),
            creditorId: all(seller, 'PartyIdentification/ID')
                .filter(e => at(e, null, 'schemeID') === 'SEPA')
                .map(e => txt(e))[0],
            terms: texts(root, 'PaymentTerms/Note').join('\n')
        }),
        allowanceCharges: all(root, 'AllowanceCharge').map(ublAllowanceCharge),
        taxes: all(mainTax, 'TaxSubtotal').map(t => clean({
            category: txt(t, 'TaxCategory/ID'),
            rate: txt(t, 'TaxCategory/Percent'),
            taxable: txt(t, 'TaxableAmount'),
            amount: txt(t, 'TaxAmount'),
            exemptionReason: txt(t, 'TaxCategory/TaxExemptionReason'),
            exemptionCode: txt(t, 'TaxCategory/TaxExemptionReasonCode')
        })),
        totals: clean({
            lineTotal: txt(total, 'LineExtensionAmount'),
            allowanceTotal: txt(total, 'AllowanceTotalAmount'),
            chargeTotal: txt(total, 'ChargeTotalAmount'),
            taxExclusive: txt(total, 'TaxExclusiveAmount'),
            taxTotal: txt(mainTax, 'TaxAmount'),
            taxTotalTaxCurrency: txt(otherTax, 'TaxAmount'),
            taxInclusive: txt(total, 'TaxInclusiveAmount'),
            prepaid: txt(total, 'PrepaidAmount'),
            rounding: txt(total, 'PayableRoundingAmount'),
            payable: txt(total, 'PayableAmount')
        }),
        lines: all(root, 'InvoiceLine|CreditNoteLine').map((l, i) => ublLine(l, i + 1)),
        attachments: documents.filter(d => txt(d, 'DocumentTypeCode') !== '130').map(ublAttachment)
    };
}

function mapUblDespatchAdvice(root) {
    const delivery = one(root, 'Shipment/Delivery');
    const receiver = one(root, 'DeliveryCustomerParty/Party');
    const specification = txt(root, 'CustomizationID');
    const estimated = one(delivery, 'EstimatedDeliveryPeriod');
    return {
        kind: 'despatchAdvice',
        syntax: 'UBL',
        specification,
        profile: profileLabel(specification),
        businessProcess: txt(root, 'ProfileID'),
        number: txt(root, 'ID'),
        typeCode: txt(root, 'DespatchAdviceTypeCode'),
        issueDate: txt(root, 'IssueDate'),
        notes: texts(root, 'Note').map(note),
        seller: ublParty(one(root, 'DespatchSupplierParty/Party')),
        buyer: ublParty(one(root, 'BuyerCustomerParty/Party')) || ublParty(receiver),
        sellerSupplier: ublParty(one(root, 'SellerSupplierParty/Party')),
        shipTo: clean({
            ...ublParty(receiver),
            address: ublAddress(one(delivery, 'DeliveryAddress')) || ublAddress(one(receiver, 'PostalAddress'))
        }),
        delivery: clean({
            date: txt(delivery, 'ActualDeliveryDate'),
            despatchDate: txt(delivery, 'Despatch/ActualDespatchDate'),
            estimated: period(estimated, 'StartDate', 'EndDate'),
            shipment: txt(root, 'Shipment/ID')
        }),
        references: clean({order: txt(root, 'OrderReference/ID')}),
        lines: all(root, 'DespatchLine').map((l, i) => ublLine(l, i + 1)),
        attachments: all(root, 'AdditionalDocumentReference').map(ublAttachment)
    };
}

// ------------------------------------------------------------------------------------------------
// CII family: CII D16B / Factur-X, ZUGFeRD 1.0, Order-X, CIDA
// ------------------------------------------------------------------------------------------------

function ciiDate(el) {
    const e = one(el, 'DateTimeString') || el;
    const value = txt(e);
    if (!value) {
        return undefined;
    }
    const format = at(e, null, 'format') || '102';
    const v = value.replace(/\s/g, '');
    if (format === '102' && /^\d{8}$/.test(v)) {
        return v.slice(0, 4) + '-' + v.slice(4, 6) + '-' + v.slice(6);
    }
    if (format === '610' && /^\d{6}$/.test(v)) {
        return v.slice(0, 4) + '-' + v.slice(4);
    }
    if (format === '616' && /^\d{6}$/.test(v)) {
        return v.slice(0, 4) + '-W' + v.slice(4);
    }
    if (format === '203' && /^\d{12}$/.test(v)) {
        return v.slice(0, 4) + '-' + v.slice(4, 6) + '-' + v.slice(6, 8) + 'T' + v.slice(8, 10) + ':' + v.slice(10);
    }
    return value;
}

function ciiPeriod(el) {
    return el ? clean({
        start: ciiDate(one(el, 'StartDateTime')),
        end: ciiDate(one(el, 'EndDateTime'))
    }) : undefined;
}

function ciiAddress(a) {
    return a ? clean({
        lines: [txt(a, 'LineOne'), txt(a, 'LineTwo'), txt(a, 'LineThree')],
        postcode: txt(a, 'PostcodeCode'),
        city: txt(a, 'CityName'),
        region: txt(a, 'CountrySubDivisionName'),
        country: txt(a, 'CountryID')
    }) : undefined;
}

function ciiParty(p) {
    if (!p) {
        return undefined;
    }
    const registrations = all(p, 'SpecifiedTaxRegistration/ID');
    const byScheme = scheme => registrations.filter(e => at(e, null, 'schemeID') === scheme).map(e => txt(e))[0];
    const contact = one(p, 'DefinedTradeContact');
    const legal = one(p, 'SpecifiedLegalOrganization');
    return clean({
        name: txt(p, 'Name'),
        tradingName: txt(legal, 'TradingBusinessName'),
        ids: [...all(p, 'ID'), ...all(p, 'GlobalID')].map(idOf),
        legalId: idOf(one(legal, 'ID')),
        legalInfo: txt(p, 'Description'),
        vatId: byScheme('VA'),
        taxId: byScheme('FC'),
        endpoint: idOf(one(p, 'URIUniversalCommunication/URIID')),
        address: ciiAddress(one(p, 'PostalTradeAddress')),
        contact: clean({
            name: txt(contact, 'PersonName') || txt(contact, 'DepartmentName'),
            department: txt(contact, 'PersonName') ? txt(contact, 'DepartmentName') : undefined,
            phone: txt(contact, 'TelephoneUniversalCommunication/CompleteNumber'),
            email: txt(contact, 'EmailURIUniversalCommunication/URIID')
        })
    });
}

function ciiAllowanceCharge(e) {
    return clean({
        charge: txt(e, 'ChargeIndicator/Indicator') === 'true',
        amount: txt(e, 'ActualAmount'),
        base: txt(e, 'BasisAmount'),
        percent: txt(e, 'CalculationPercent'),
        reason: txt(e, 'Reason'),
        reasonCode: txt(e, 'ReasonCode'),
        taxCategory: txt(e, 'CategoryTradeTax/CategoryCode'),
        taxRate: txt(e, 'CategoryTradeTax/RateApplicablePercent|ApplicablePercent')
    });
}

function ciiLine(l, index) {
    const lineDocument = one(l, 'AssociatedDocumentLineDocument');
    const product = one(l, 'SpecifiedTradeProduct');
    const agreement = one(l, 'SpecifiedLineTradeAgreement|SpecifiedSupplyChainTradeAgreement');
    const delivery = one(l, 'SpecifiedLineTradeDelivery|SpecifiedSupplyChainTradeDelivery');
    const settlement = one(l, 'SpecifiedLineTradeSettlement|SpecifiedSupplyChainTradeSettlement');
    const quantity = one(delivery, 'BilledQuantity|AgreedQuantity|RequestedQuantity|DespatchedQuantity');
    const gross = one(agreement, 'GrossPriceProductTradePrice');
    const net = one(agreement, 'NetPriceProductTradePrice');
    const basis = one(net, 'BasisQuantity') || one(gross, 'BasisQuantity');
    const tax = one(settlement, 'ApplicableTradeTax');
    return clean({
        index,
        id: txt(lineDocument, 'LineID'),
        parentId: txt(lineDocument, 'ParentLineID'),
        status: txt(lineDocument, 'LineStatusReasonCode') || txt(lineDocument, 'LineStatusCode'),
        note: texts(lineDocument, 'IncludedNote/Content').join('\n'),
        name: txt(product, 'Name'),
        description: txt(product, 'Description'),
        sellerId: txt(product, 'SellerAssignedID'),
        buyerId: txt(product, 'BuyerAssignedID'),
        industryId: txt(product, 'IndustryAssignedID'),
        standardId: idOf(one(product, 'GlobalID')),
        classifications: all(product, 'DesignatedProductClassification/ClassCode')
            .map(e => ({value: txt(e), scheme: at(e, null, 'listID')})),
        origin: txt(product, 'OriginTradeCountry/ID'),
        attributes: all(product, 'ApplicableProductCharacteristic')
            .map(c => ({
                name: txt(c, 'Description'),
                value: txt(c, 'Value') || [txt(c, 'ValueMeasure'), at(c, 'ValueMeasure', 'unitCode')].filter(Boolean).join(' ')
            })),
        instances: all(product, 'IndividualTradeProductInstance').map(i => clean({
            batch: txt(i, 'BatchID'),
            // CII D16B names the serial number SupplierAssignedSerialID.
            serial: txt(i, 'SupplierAssignedSerialID|SerialID'),
            expiry: ciiDate(one(i, 'BestBeforeDateTime'))
        })),
        // EXTENDED product details
        productId: txt(product, 'ID'),
        model: [txt(product, 'ModelName'), txt(product, 'ModelID')].filter(Boolean).join(' ') || undefined,
        brand: txt(product, 'BrandName'),
        batches: texts(product, 'BatchID'),
        manufacturer: txt(product, 'ManufacturerTradeParty/Name'),
        includedProducts: all(product, 'IncludedReferencedProduct').map(i => clean({
            name: txt(i, 'Name'),
            id: txt(i, 'ID'),
            quantity: txt(i, 'UnitQuantity'),
            unit: at(i, 'UnitQuantity', 'unitCode')
        })),
        quantity: txt(quantity),
        unit: at(quantity, null, 'unitCode'),
        requestedQuantity: quantity && quantity.localName !== 'RequestedQuantity' ? txt(delivery, 'RequestedQuantity') : undefined,
        netPrice: txt(net, 'ChargeAmount'),
        baseQuantity: txt(basis),
        baseUnit: at(basis, null, 'unitCode'),
        grossPrice: txt(gross, 'ChargeAmount'),
        priceDiscount: txt(gross, 'AppliedTradeAllowanceCharge/ActualAmount'),
        total: txt(settlement, 'SpecifiedTradeSettlementLineMonetarySummation|SpecifiedTradeSettlementMonetarySummation/LineTotalAmount'),
        taxCategory: txt(tax, 'CategoryCode'),
        taxRate: txt(tax, 'RateApplicablePercent|ApplicablePercent'),
        period: ciiPeriod(one(settlement, 'BillingSpecifiedPeriod')),
        orderLine: txt(agreement, 'BuyerOrderReferencedDocument/LineID'),
        deliveryDate: ciiDate(one(delivery, 'ActualDeliverySupplyChainEvent/OccurrenceDateTime')),
        deliveryNote: txt(delivery, 'DeliveryNoteReferencedDocument/IssuerAssignedID'),
        despatchAdvice: txt(delivery, 'DespatchAdviceReferencedDocument/IssuerAssignedID'),
        receivingAdvice: txt(delivery, 'ReceivingAdviceReferencedDocument/IssuerAssignedID'),
        chargeFreeQuantity: txt(delivery, 'ChargeFreeQuantity'),
        packageQuantity: txt(delivery, 'PackageQuantity'),
        perPackageQuantity: txt(delivery, 'PerPackageUnitQuantity'),
        shipTo: ciiParty(one(delivery, 'ShipToTradeParty')),
        ultimateShipTo: ciiParty(one(delivery, 'UltimateShipToTradeParty')),
        deliveryTerms: txt(agreement, 'ApplicableTradeDeliveryTerms/DeliveryTypeCode'),
        salesOrder: txt(agreement, 'SellerOrderReferencedDocument/IssuerAssignedID'),
        quotation: txt(agreement, 'QuotationReferencedDocument/IssuerAssignedID'),
        contract: txt(agreement, 'ContractReferencedDocument/IssuerAssignedID'),
        ultimateCustomerOrders: texts(agreement, 'UltimateCustomerOrderReferencedDocument/IssuerAssignedID'),
        itemSeller: ciiParty(one(agreement, 'ItemSellerTradeParty')),
        references: all(agreement, 'AdditionalReferencedDocument').map(d => clean({
            id: txt(d, 'IssuerAssignedID'),
            scheme: txt(d, 'ReferenceTypeCode')
        })),
        priceAdjustments: all(gross, 'AppliedTradeAllowanceCharge').length > 1 ||
        all(gross, 'AppliedTradeAllowanceCharge').some(a => txt(a, 'Reason') || txt(a, 'ReasonCode')) ?
            all(gross, 'AppliedTradeAllowanceCharge').map(ciiAllowanceCharge) : undefined,
        precedingInvoice: txt(settlement, 'InvoiceReferencedDocument/IssuerAssignedID'),
        // BT-128 may repeat in EXTENDED, e.g. the sub-invoice (AFL) next to the object (AVV).
        objectIds: all(settlement, 'AdditionalReferencedDocument')
            .filter(d => !txt(d, 'TypeCode') || txt(d, 'TypeCode') === '130')
            .map(d => txt(d, 'IssuerAssignedID')),
        accountingCost: texts(settlement, 'ReceivableSpecifiedTradeAccountingAccount/ID').join(', ') || undefined,
        lineVat: txt(settlement, 'SpecifiedTradeSettlementLineMonetarySummation/TaxTotalAmount'),
        lineGross: txt(settlement, 'SpecifiedTradeSettlementLineMonetarySummation/GrandTotalAmount'),
        allowanceCharges: all(settlement, 'SpecifiedTradeAllowanceCharge').map(ciiAllowanceCharge)
    });
}

/** Nests the lines of CII EXTENDED with a ParentLineID below their parent. */
function nestLines(lines) {
    const byId = new Map(lines.filter(l => l.id).map(l => [l.id, l]));
    const top = [];
    for (const line of lines) {
        const parent = line.parentId && byId.get(line.parentId);
        if (parent && parent !== line) {
            (parent.subLines = parent.subLines || []).push(line);
        } else {
            top.push(line);
        }
    }
    return top;
}

function ciiDocument(d) {
    const binary = one(d, 'AttachmentBinaryObject');
    return clean({
        source: 'xml',
        id: txt(d, 'IssuerAssignedID'),
        description: txt(d, 'Name'),
        typeCode: txt(d, 'TypeCode'),
        name: at(binary, null, 'filename'),
        mime: at(binary, null, 'mimeCode'),
        data: binary ? base64(binary.textContent) : undefined,
        uri: txt(d, 'URIID')
    });
}

/**
 * Splits the payment terms as the Java reader does: terms with a partial amount, penalty, discount,
 * or payee are additional terms; the first plain term carries the payment terms text (BT-20) and
 * the due date (BT-9); further plain terms with text or date are additional terms too. A document
 * with additional terms only has no due date.
 */
function ciiPaymentTerms(terms) {
    const structured = t => ['PartialPaymentAmount', 'ApplicableTradePaymentPenaltyTerms',
        'ApplicableTradePaymentDiscountTerms', 'PayeeTradeParty'].some(name => one(t, name));
    let main;
    const additional = [];
    for (const term of terms) {
        if (!structured(term) && !main) {
            main = term;
        } else if (structured(term) || txt(term, 'Description') || one(term, 'DueDateDateTime')) {
            additional.push(clean({
                description: txt(term, 'Description'),
                dueDate: ciiDate(one(term, 'DueDateDateTime')),
                partialAmount: txt(term, 'PartialPaymentAmount'),
                discountPercent: txt(term, 'ApplicableTradePaymentDiscountTerms/CalculationPercent'),
                discountAmount: txt(term, 'ApplicableTradePaymentDiscountTerms/ActualDiscountAmount'),
                penaltyPercent: txt(term, 'ApplicableTradePaymentPenaltyTerms/CalculationPercent'),
                penaltyAmount: txt(term, 'ApplicableTradePaymentPenaltyTerms/ActualPenaltyAmount')
            }));
        }
    }
    return {main, additional};
}

function mapCii(root, family) {
    const context = one(root, 'ExchangedDocumentContext|SpecifiedExchangedDocumentContext');
    const doc = one(root, 'ExchangedDocument|HeaderExchangedDocument');
    const tx = one(root, 'SupplyChainTradeTransaction|SpecifiedSupplyChainTradeTransaction');
    const agreement = one(tx, 'ApplicableHeaderTradeAgreement|ApplicableSupplyChainTradeAgreement');
    const delivery = one(tx, 'ApplicableHeaderTradeDelivery|ApplicableSupplyChainTradeDelivery');
    const settlement = one(tx, 'ApplicableHeaderTradeSettlement|ApplicableSupplyChainTradeSettlement');
    const summation = one(settlement, 'SpecifiedTradeSettlementHeaderMonetarySummation|SpecifiedTradeSettlementMonetarySummation');
    const typeCode = txt(doc, 'TypeCode');
    const currency = txt(settlement, 'InvoiceCurrencyCode|OrderCurrencyCode') || at(summation, 'GrandTotalAmount', 'currencyID');
    const taxTotals = all(summation, 'TaxTotalAmount');
    const mainTax = taxTotals.find(t => !at(t, null, 'currencyID') || at(t, null, 'currencyID') === currency) || taxTotals[0];
    const otherTax = taxTotals.find(t => t !== mainTax);
    const documents = all(agreement, 'AdditionalReferencedDocument');
    const terms = all(settlement, 'SpecifiedTradePaymentTerms');
    const paymentTerms = ciiPaymentTerms(terms);
    const specification = txt(context, 'GuidelineSpecifiedDocumentContextParameter/ID');
    const kind = family === 'orderx' ? (ORDER_KINDS[typeCode] || 'order') :
        family === 'cida' ? 'despatchAdvice' :
            CREDIT_NOTE_TYPES.includes(typeCode) ? 'creditNote' : 'invoice';
    const syntax = {cii: 'CII', zf1: 'ZUGFeRD 1.0', orderx: 'Order-X', cida: 'CIDA'}[family];
    const businessProcess = txt(context, 'BusinessProcessSpecifiedDocumentContextParameter/ID');
    const lines = all(tx, 'IncludedSupplyChainTradeLineItem').map((l, i) => ciiLine(l, i + 1));
    return {
        kind,
        syntax,
        specification: specification || businessProcess,
        profile: profileLabel(specification || businessProcess),
        businessProcess: specification ? businessProcess : undefined,
        number: txt(doc, 'ID'),
        name: txt(doc, 'Name'),
        testDocument: txt(context, 'TestIndicator/Indicator') === 'true',
        copy: txt(doc, 'CopyIndicator/Indicator') === 'true',
        effectiveDate: ciiDate(one(doc, 'EffectiveSpecifiedPeriod/CompleteDateTime')),
        periodDescription: txt(settlement, 'BillingSpecifiedPeriod/Description'),
        typeCode,
        purposeCode: txt(doc, 'PurposeCode'),
        issueDate: ciiDate(one(doc, 'IssueDateTime')),
        dueDate: paymentTerms.main ? ciiDate(one(paymentTerms.main, 'DueDateDateTime')) : undefined,
        currency,
        taxCurrency: txt(settlement, 'TaxCurrencyCode'),
        notes: all(doc, 'IncludedNote').flatMap(n => texts(n, 'Content').map(text => clean({
            subject: txt(n, 'SubjectCode'),
            text
        }))),
        period: ciiPeriod(one(settlement, 'BillingSpecifiedPeriod')),
        seller: ciiParty(one(agreement, 'SellerTradeParty')),
        buyer: ciiParty(one(agreement, 'BuyerTradeParty')),
        payee: ciiParty(one(settlement, 'PayeeTradeParty')),
        invoicee: ciiParty(one(settlement, 'InvoiceeTradeParty')),
        taxRepresentative: ciiParty(one(agreement, 'SellerTaxRepresentativeTradeParty')),
        shipTo: ciiParty(one(delivery, 'ShipToTradeParty')),
        shipFrom: ciiParty(one(delivery, 'ShipFromTradeParty')),
        ultimateShipTo: ciiParty(one(delivery, 'UltimateShipToTradeParty')),
        salesAgent: ciiParty(one(agreement, 'SalesAgentTradeParty')),
        buyerAgent: ciiParty(one(agreement, 'BuyerAgentTradeParty')),
        buyerTaxRepresentative: ciiParty(one(agreement, 'BuyerTaxRepresentativeTradeParty')),
        productEndUser: ciiParty(one(agreement, 'ProductEndUserTradeParty')),
        invoicer: ciiParty(one(settlement, 'InvoicerTradeParty')),
        payer: ciiParty(one(settlement, 'PayerTradeParty')),
        delivery: clean({
            date: ciiDate(one(delivery, 'ActualDeliverySupplyChainEvent/OccurrenceDateTime')),
            requested: ciiDate(one(delivery, 'RequestedDeliverySupplyChainEvent/OccurrenceDateTime')) ||
                ciiPeriod(one(delivery, 'RequestedDeliverySupplyChainEvent/OccurrenceSpecifiedPeriod')),
            despatchDate: ciiDate(one(delivery, 'ActualDespatchSupplyChainEvent/OccurrenceDateTime')),
            terms: [txt(agreement, 'ApplicableTradeDeliveryTerms/DeliveryTypeCode'),
                txt(agreement, 'ApplicableTradeDeliveryTerms/RelevantTradeLocation/Name')].filter(Boolean).join(' · ') || undefined,
            termsCountry: txt(agreement, 'ApplicableTradeDeliveryTerms/RelevantTradeLocation/CountryID'),
            transportMode: txt(delivery, 'RelatedSupplyChainConsignment/SpecifiedLogisticsTransportMovement/ModeCode')
        }),
        references: clean({
            buyerReference: txt(agreement, 'BuyerReference'),
            order: txt(agreement, 'BuyerOrderReferencedDocument/IssuerAssignedID'),
            salesOrder: txt(agreement, 'SellerOrderReferencedDocument/IssuerAssignedID'),
            contract: txt(agreement, 'ContractReferencedDocument/IssuerAssignedID'),
            quotation: txt(agreement, 'QuotationReferencedDocument/IssuerAssignedID'),
            project: [txt(agreement, 'SpecifiedProcuringProject/ID'), txt(agreement, 'SpecifiedProcuringProject/Name')]
                .filter(Boolean).join(' – ') || undefined,
            despatchAdvice: txt(delivery, 'DespatchAdviceReferencedDocument/IssuerAssignedID'),
            receivingAdvice: txt(delivery, 'ReceivingAdviceReferencedDocument/IssuerAssignedID'),
            deliveryNote: txt(delivery, 'DeliveryNoteReferencedDocument/IssuerAssignedID'),
            ultimateCustomerOrders: texts(agreement, 'UltimateCustomerOrderReferencedDocument/IssuerAssignedID').join(', ') || undefined,
            invoiceIssuer: txt(settlement, 'InvoiceIssuerReference'),
            tender: documents.filter(d => txt(d, 'TypeCode') === '50').map(d => txt(d, 'IssuerAssignedID'))[0],
            object: documents.filter(d => txt(d, 'TypeCode') === '130').map(d => txt(d, 'IssuerAssignedID'))[0],
            accountingCost: texts(settlement, 'ReceivableSpecifiedTradeAccountingAccount/ID').join(', ') || undefined,
            precedingInvoices: all(settlement, 'InvoiceReferencedDocument').map(r => clean({
                id: txt(r, 'IssuerAssignedID'),
                date: ciiDate(one(r, 'FormattedIssueDateTime'))
            }))
        }),
        payment: clean({
            means: all(settlement, 'SpecifiedTradeSettlementPaymentMeans').map(pm => clean({
                code: txt(pm, 'TypeCode'),
                text: txt(pm, 'Information'),
                accounts: all(pm, 'PayeePartyCreditorFinancialAccount').map(a => clean({
                    iban: txt(a, 'IBANID') || txt(a, 'ProprietaryID'),
                    name: txt(a, 'AccountName'),
                    bic: txt(pm, 'PayeeSpecifiedCreditorFinancialInstitution/BICID')
                })),
                card: clean({
                    number: txt(pm, 'ApplicableTradeSettlementFinancialCard/ID'),
                    holder: txt(pm, 'ApplicableTradeSettlementFinancialCard/CardholderName')
                }),
                debitAccount: txt(pm, 'PayerPartyDebtorFinancialAccount/IBANID'),
                debitAccountName: txt(pm, 'PayerPartyDebtorFinancialAccount/AccountName'),
                debitBic: txt(pm, 'PayerSpecifiedDebtorFinancialInstitution/BICID')
            })),
            remittance: txt(settlement, 'PaymentReference'),
            creditorId: txt(settlement, 'CreditorReferenceID'),
            mandate: terms.map(t => txt(t, 'DirectDebitMandateID')).find(Boolean),
            terms: paymentTerms.main ? txt(paymentTerms.main, 'Description') : undefined,
            additionalTerms: paymentTerms.additional.length ? paymentTerms.additional : undefined,
            advancePayments: all(settlement, 'SpecifiedAdvancePayment').map(a => clean({
                amount: txt(a, 'PaidAmount'),
                date: ciiDate(one(a, 'FormattedReceivedDateTime')),
                invoice: txt(a, 'InvoiceSpecifiedReferencedDocument/IssuerAssignedID'),
                taxRate: txt(a, 'IncludedTradeTax/RateApplicablePercent'),
                taxAmount: txt(a, 'IncludedTradeTax/CalculatedAmount')
            }))
        }),
        allowanceCharges: all(settlement, 'SpecifiedTradeAllowanceCharge').map(ciiAllowanceCharge),
        // EXTENDED: logistics service charges count in the charges, financial adjustments in the amount due.
        logisticsCharges: all(settlement, 'SpecifiedLogisticsServiceCharge').map(c => clean({
            description: txt(c, 'Description'),
            amount: txt(c, 'AppliedAmount'),
            taxRate: txt(c, 'AppliedTradeTax/RateApplicablePercent')
        })),
        financialAdjustments: all(settlement, 'SpecifiedFinancialAdjustment').map(a => clean({
            reason: txt(a, 'Reason'),
            amount: txt(a, 'ActualAmount')
        })),
        exchangeRate: clean({
            rate: txt(settlement, 'TaxApplicableTradeCurrencyExchange/ConversionRate'),
            date: ciiDate(one(settlement, 'TaxApplicableTradeCurrencyExchange/ConversionRateDateTime'))
        }),
        taxes: all(settlement, 'ApplicableTradeTax').map(t => clean({
            category: txt(t, 'CategoryCode'),
            rate: txt(t, 'RateApplicablePercent|ApplicablePercent'),
            taxable: txt(t, 'BasisAmount'),
            amount: txt(t, 'CalculatedAmount'),
            exemptionReason: txt(t, 'ExemptionReason'),
            exemptionCode: txt(t, 'ExemptionReasonCode')
        })),
        totals: clean({
            lineTotal: txt(summation, 'LineTotalAmount'),
            allowanceTotal: txt(summation, 'AllowanceTotalAmount'),
            chargeTotal: txt(summation, 'ChargeTotalAmount'),
            taxExclusive: txt(summation, 'TaxBasisTotalAmount'),
            taxTotal: txt(mainTax),
            taxTotalTaxCurrency: txt(otherTax),
            taxInclusive: txt(summation, 'GrandTotalAmount'),
            prepaid: txt(summation, 'TotalPrepaidAmount'),
            rounding: txt(summation, 'RoundingAmount'),
            payable: txt(summation, 'DuePayableAmount')
        }),
        lines: nestLines(lines),
        attachments: documents.filter(d => !['50', '130'].includes(txt(d, 'TypeCode'))).map(ciiDocument)
    };
}

// ------------------------------------------------------------------------------------------------
// Document detection
// ------------------------------------------------------------------------------------------------

const UBL_NAMESPACES = {
    'urn:oasis:names:specification:ubl:schema:xsd:Invoice-2': 'Invoice',
    'urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2': 'CreditNote',
    'urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2': 'DespatchAdvice'
};

function mapRoot(root) {
    const ns = root.namespaceURI || '';
    const local = root.localName;
    if (UBL_NAMESPACES[ns] === local) {
        return mapUbl(root);
    }
    if (local === 'CrossIndustryInvoice' && ns.startsWith('urn:un:unece:uncefact:data:standard:CrossIndustryInvoice')) {
        return mapCii(root, 'cii');
    }
    if (local === 'CrossIndustryDocument' && ns.startsWith('urn:ferd:CrossIndustryDocument')) {
        return mapCii(root, 'zf1');
    }
    if (local === 'SCRDMCCBDACIOMessageStructure') {
        return mapCii(root, 'orderx');
    }
    if (local === 'SCRDMCCBDACIDAMessageStructure') {
        return mapCii(root, 'cida');
    }
    return null;
}

function isReportRoot(root) {
    return (root.localName === 'report' && /paniolo:validation-report|xoev\.de\/de\/validator\/varl/.test(root.namespaceURI || '')) ||
        root.localName === 'schematron-output';
}

// ------------------------------------------------------------------------------------------------
// PDF: reads the embedded files of a PDF (incl. compressed object streams)
// ------------------------------------------------------------------------------------------------

const PDF_SPACE = ' \t\r\n\f\0';
const PDF_DELIMITERS = '()<>[]{}/%';
const PDF_NUMBER = /[+-]?(?:\d+\.?\d*|\.\d+)/y;
const PDF_REF = /\s+(\d+)\s+R(?![A-Za-z0-9])/y;
const PDF_KEYWORD = /[^\s()<>[\]{}/%]+/y;
const INVOICE_FILE_NAMES = ['factur-x.xml', 'zugferd-invoice.xml', 'zugferd_invoice.xml', 'xrechnung.xml',
    'order-x.xml', 'cida.xml'];

class PdfParser {
    constructor(source, position) {
        this.s = source;
        this.p = position || 0;
    }

    space() {
        const s = this.s;
        for (; ;) {
            while (this.p < s.length && PDF_SPACE.includes(s[this.p])) {
                this.p++;
            }
            if (s[this.p] !== '%') {
                return;
            }
            while (this.p < s.length && s[this.p] !== '\n' && s[this.p] !== '\r') {
                this.p++;
            }
        }
    }

    value(depth = 0) {
        if (depth > 64) {
            throw new Error('pdf nesting');
        }
        this.space();
        const s = this.s;
        const c = s[this.p];
        if (c === undefined) {
            throw new Error('pdf eof');
        }
        if (c === '<' && s[this.p + 1] === '<') {
            this.p += 2;
            const dict = new Map();
            for (; ;) {
                this.space();
                if (s.startsWith('>>', this.p)) {
                    this.p += 2;
                    return {dict};
                }
                const key = this.value(depth + 1);
                if (!key || key.name === undefined) {
                    throw new Error('pdf dict');
                }
                dict.set(key.name, this.value(depth + 1));
            }
        }
        if (c === '[') {
            this.p++;
            const list = [];
            for (; ;) {
                this.space();
                if (s[this.p] === ']') {
                    this.p++;
                    return list;
                }
                list.push(this.value(depth + 1));
            }
        }
        if (c === '/') {
            const start = ++this.p;
            while (this.p < s.length && !PDF_SPACE.includes(s[this.p]) && !PDF_DELIMITERS.includes(s[this.p])) {
                this.p++;
            }
            return {name: s.slice(start, this.p).replace(/#([0-9a-fA-F]{2})/g, (m, h) => String.fromCharCode(parseInt(h, 16)))};
        }
        if (c === '(') {
            return {str: this.literal()};
        }
        if (c === '<') {
            const end = s.indexOf('>', this.p);
            if (end < 0) {
                throw new Error('pdf hex');
            }
            let hex = s.slice(this.p + 1, end).replace(/[^0-9a-fA-F]/g, '');
            if (hex.length % 2) {
                hex += '0';
            }
            this.p = end + 1;
            const bytes = new Uint8Array(hex.length / 2);
            for (let i = 0; i < bytes.length; i++) {
                bytes[i] = parseInt(hex.substr(i * 2, 2), 16);
            }
            return {str: bytes};
        }
        PDF_NUMBER.lastIndex = this.p;
        const number = PDF_NUMBER.exec(s);
        if (number) {
            this.p = PDF_NUMBER.lastIndex;
            if (/^\d+$/.test(number[0])) {
                PDF_REF.lastIndex = this.p;
                const ref = PDF_REF.exec(s);
                if (ref) {
                    this.p = PDF_REF.lastIndex;
                    return {ref: Number(number[0])};
                }
            }
            return Number(number[0]);
        }
        PDF_KEYWORD.lastIndex = this.p;
        const keyword = PDF_KEYWORD.exec(s);
        if (!keyword) {
            throw new Error('pdf token');
        }
        this.p = PDF_KEYWORD.lastIndex;
        return keyword[0] === 'true' ? true : keyword[0] === 'false' ? false : keyword[0] === 'null' ? null : {keyword: keyword[0]};
    }

    literal() {
        const s = this.s;
        const out = [];
        let depth = 0;
        for (this.p++; this.p < s.length; this.p++) {
            const c = s[this.p];
            if (c === '\\') {
                const n = s[++this.p];
                const escapes = {n: 10, r: 13, t: 9, b: 8, f: 12, '(': 40, ')': 41, '\\': 92};
                if (n in escapes) {
                    out.push(escapes[n]);
                } else if (n === '\r') {
                    if (s[this.p + 1] === '\n') {
                        this.p++;
                    }
                } else if (n >= '0' && n <= '7') {
                    let octal = n;
                    while (octal.length < 3 && s[this.p + 1] >= '0' && s[this.p + 1] <= '7') {
                        octal += s[++this.p];
                    }
                    out.push(parseInt(octal, 8) & 0xff);
                } else if (n !== '\n') {
                    out.push(n.charCodeAt(0));
                }
                continue;
            }
            if (c === '(') {
                depth++;
            } else if (c === ')') {
                if (depth === 0) {
                    this.p++;
                    return Uint8Array.from(out);
                }
                depth--;
            }
            out.push(c.charCodeAt(0));
        }
        throw new Error('pdf string');
    }
}

function pdfText(value) {
    const bytes = value && value.str;
    if (!bytes) {
        return undefined;
    }
    if (bytes[0] === 0xfe && bytes[1] === 0xff) {
        return new TextDecoder('utf-16be').decode(bytes.subarray(2));
    }
    if (bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
        return new TextDecoder('utf-8').decode(bytes.subarray(3));
    }
    return latin1(bytes);
}

async function inflate(data) {
    const stream = new Blob([data]).stream().pipeThrough(new DecompressionStream('deflate'));
    const reader = stream.getReader();
    const chunks = [];
    let size = 0;
    try {
        for (; ;) {
            const {done, value} = await reader.read();
            if (done) {
                break;
            }
            chunks.push(value);
            size += value.length;
        }
    } catch (e) {
        // trailing bytes after the zlib stream are common; keep what was decoded
        if (!size) {
            throw e;
        }
    }
    const out = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) {
        out.set(chunk, offset);
        offset += chunk.length;
    }
    return out;
}

async function readPdf(bytes) {
    const s = latin1(bytes);
    if (/\/Encrypt\s*(?:\d+\s+\d+\s+R|<<)/.test(s)) {
        throw new PanioloError('pdfEncrypted');
    }
    const objects = new Map();
    const header = /(\d+)\s+\d+\s+obj\b/g;
    let match;
    while ((match = header.exec(s))) {
        const parser = new PdfParser(s, header.lastIndex);
        let value;
        try {
            value = parser.value();
        } catch (e) {
            continue;
        }
        parser.space();
        if (value && value.dict && s.startsWith('stream', parser.p)) {
            let start = parser.p + 6;
            if (s[start] === '\r') {
                start++;
            }
            if (s[start] === '\n') {
                start++;
            }
            const length = value.dict.get('Length');
            let end = -1;
            if (typeof length === 'number' && /^\s*endstream/.test(s.substr(start + length, 16))) {
                end = start + length;
            } else {
                const found = s.indexOf('endstream', start);
                if (found < 0) {
                    continue;
                }
                end = found;
                while (end > start && (s[end - 1] === '\n' || s[end - 1] === '\r')) {
                    end--;
                }
            }
            objects.set(Number(match[1]), {value, data: bytes.subarray(start, end)});
            header.lastIndex = end;
        } else {
            objects.set(Number(match[1]), {value});
            header.lastIndex = parser.p;
        }
    }
    const resolve = v => (v && v.ref !== undefined ? (objects.get(v.ref) || {}).value : v);
    const nameOf = v => (resolve(v) || {}).name;
    const decode = async object => {
        const filter = resolve(object.value.dict.get('Filter'));
        const filters = (Array.isArray(filter) ? filter : filter ? [filter] : []).map(nameOf);
        let data = object.data;
        for (const f of filters) {
            if (f === 'FlateDecode' || f === 'Fl') {
                data = await inflate(data);
            } else {
                throw new PanioloError('pdf', 'filter ' + f);
            }
        }
        return data;
    };

    // objects in compressed object streams (PDF 1.5)
    for (const object of [...objects.values()]) {
        if (!object.data || nameOf(object.value.dict.get('Type')) !== 'ObjStm') {
            continue;
        }
        try {
            const data = latin1(await decode(object));
            const count = resolve(object.value.dict.get('N'));
            const first = resolve(object.value.dict.get('First'));
            const numbers = data.slice(0, first).trim().split(/\s+/).map(Number);
            for (let i = 0; i < count; i++) {
                if (!objects.has(numbers[2 * i])) {
                    try {
                        objects.set(numbers[2 * i], {value: new PdfParser(data, first + numbers[2 * i + 1]).value()});
                    } catch (e) {
                        // skip a broken object
                    }
                }
            }
        } catch (e) {
            // skip a broken object stream
        }
    }

    // file specifications with embedded files, wherever they are: name trees, /AF arrays, annotations
    const specs = [];
    const seen = new Set();
    const visit = (v, depth) => {
        if (!v || typeof v !== 'object' || depth > 32) {
            return;
        }
        if (Array.isArray(v)) {
            v.forEach(x => visit(x, depth + 1));
            return;
        }
        if (!v.dict) {
            return;
        }
        const embedded = resolve(v.dict.get('EF'));
        if (embedded && embedded.dict) {
            const ref = embedded.dict.get('UF') || embedded.dict.get('F');
            if (ref && ref.ref !== undefined && !seen.has(ref.ref)) {
                seen.add(ref.ref);
                specs.push({spec: v.dict, ref: ref.ref});
            }
        }
        for (const x of v.dict.values()) {
            visit(x, depth + 1);
        }
    };
    for (const object of objects.values()) {
        visit(object.value, 0);
    }
    for (const [number, object] of objects) {
        if (object.data && nameOf(object.value.dict.get('Type')) === 'EmbeddedFile' && !seen.has(number)) {
            specs.push({spec: new Map(), ref: number});
        }
    }

    const files = [];
    for (const {spec, ref} of specs) {
        const object = objects.get(ref);
        if (!object || !object.data) {
            continue;
        }
        let data;
        try {
            data = await decode(object);
        } catch (e) {
            continue;
        }
        const mime = nameOf(object.value.dict.get('Subtype'));
        files.push(clean({
            source: 'pdf',
            name: pdfText(resolve(spec.get('UF'))) || pdfText(resolve(spec.get('F'))) || 'attachment-' + ref,
            description: pdfText(resolve(spec.get('Desc'))),
            relationship: nameOf(spec.get('AFRelationship')),
            mime: mime && mime.includes('/') ? mime : undefined,
            data
        }));
    }
    return files;
}

// ------------------------------------------------------------------------------------------------
// Validation reports: Paniolo report, KoSIT report, SVRL
// ------------------------------------------------------------------------------------------------

const SEVERITIES = ['FATAL', 'ERROR', 'WARNING', 'INFO'];

function lineIndexOf(location) {
    const m = /(IncludedSupplyChainTradeLineItem|InvoiceLine|CreditNoteLine|DespatchLine)\[(\d+)\]/.exec(location || '');
    return m ? Number(m[2]) : undefined;
}

function mapReport(root) {
    if (root.localName === 'report' && (root.namespaceURI || '').includes('paniolo')) {
        const summary = one(root, 'summary');
        const stages = all(root, 'stage').map(stage => ({
            name: at(stage, null, 'name'),
            valid: at(stage, null, 'valid') !== 'false',
            messages: all(stage, 'message').map(m => clean({
                severity: (at(m, null, 'severity') || 'ERROR').toUpperCase(),
                rule: at(m, null, 'rule'),
                location: at(m, null, 'location'),
                line: lineIndexOf(at(m, null, 'location')),
                text: txt(m)
            }))
        }));
        return {
            profile: txt(root, 'profile'),
            created: at(root, null, 'created'),
            generator: at(root, null, 'generator'),
            valid: at(summary, null, 'valid') === 'true',
            stages
        };
    }
    // SVRL, also inside a KoSIT report
    const messages = [...root.getElementsByTagName('*')]
        .filter(e => e.localName === 'failed-assert' || e.localName === 'successful-report')
        .map(e => {
            const flag = (at(e, null, 'flag') || at(e, null, 'role') || 'error').toLowerCase();
            const severity = flag.startsWith('fatal') ? 'FATAL' : flag.startsWith('warn') ? 'WARNING' : flag.startsWith('info') ? 'INFO' : 'ERROR';
            return clean({
                severity,
                rule: at(e, null, 'id'),
                location: at(e, null, 'location'),
                line: lineIndexOf(at(e, null, 'location')),
                text: txt(e, 'text')
            });
        });
    const accept = [...root.getElementsByTagName('*')].find(e => e.localName === 'assessment');
    return {
        profile: undefined,
        valid: accept ? !!one(accept, 'accept') : !messages.some(m => m.severity === 'FATAL' || m.severity === 'ERROR'),
        stages: [{name: 'SCHEMATRON', valid: !messages.some(m => m.severity === 'FATAL' || m.severity === 'ERROR'), messages}]
    };
}

// ------------------------------------------------------------------------------------------------
// Public parsing API
// ------------------------------------------------------------------------------------------------

/**
 * Reads a document from bytes and returns the view model.
 *
 * @param input Uint8Array, ArrayBuffer, Blob, Response, XML text, or Base64
 * @returns Promise of the model: kind, syntax, profile, parties, lines, totals, attachments, xml, ...
 */
export async function parse(input) {
    const bytes = await toBytes(input);
    if (!bytes.length) {
        throw new PanioloError('empty');
    }
    if (isPdf(bytes)) {
        let files;
        try {
            files = await readPdf(bytes);
        } catch (e) {
            throw e instanceof PanioloError ? e : new PanioloError('pdf', e.message);
        }
        const candidates = [...files.filter(f => INVOICE_FILE_NAMES.includes(f.name.toLowerCase())),
            ...files.filter(f => /xml/i.test(f.mime || '') || /\.xml$/i.test(f.name))];
        for (const file of candidates) {
            let model;
            try {
                model = fromXml(decodeText(file.data));
            } catch (e) {
                continue;
            }
            model.container = 'PDF';
            model.pdf = bytes;
            model.xmlFileName = file.name;
            model.attachments = [...(model.attachments || []), ...files.filter(f => f !== file)];
            return model;
        }
        throw new PanioloError('pdfNoXml');
    }
    const model = fromXml(decodeText(bytes));
    model.container = 'XML';
    return model;
}

function fromXml(text) {
    const doc = parseXml(text);
    const model = mapRoot(doc.documentElement);
    if (!model) {
        throw new PanioloError(isReportRoot(doc.documentElement) ? 'isReport' : 'unknownRoot', doc.documentElement.localName);
    }
    model.xml = text;
    return model;
}

/**
 * Reads a validation report: the Paniolo report (ValidationResult.toXml()), a KoSIT report, or SVRL.
 */
export async function parseReport(input) {
    const doc = parseXml(decodeText(await toBytes(input)));
    if (!isReportRoot(doc.documentElement)) {
        throw new PanioloError('notReport', doc.documentElement.localName);
    }
    return mapReport(doc.documentElement);
}

// ------------------------------------------------------------------------------------------------
// Texts
// ------------------------------------------------------------------------------------------------

const TEXTS = {
    de: {
        kind: {
            invoice: 'Rechnung', creditNote: 'Gutschrift', order: 'Bestellung', orderChange: 'Bestelländerung',
            orderResponse: 'Bestellantwort', despatchAdvice: 'Lieferavis'
        },
        tabs: {
            overview: 'Übersicht', lines: 'Positionen', details: 'Details', attachments: 'Anlagen',
            validation: 'Prüfung', xml: 'XML'
        },
        seller: 'Verkäufer',
        buyer: 'Käufer',
        payee: 'Zahlungsempfänger',
        taxRepresentative: 'Steuervertreter',
        shipTo: 'Lieferanschrift',
        shipFrom: 'Versandort',
        invoicee: 'Rechnungsempfänger',
        sellerSupplier: 'Lieferant',
        issueDate: 'Datum',
        dueDate: 'Fällig am',
        payable: 'Zahlbetrag',
        grandTotal: 'Gesamtbetrag',
        number: 'Nummer',
        documentType: 'Dokumentart',
        currency: 'Währung',
        taxCurrency: 'Steuerwährung',
        period: 'Leistungszeitraum',
        deliveryDate: 'Lieferdatum',
        requestedDelivery: 'Gewünschte Lieferung',
        despatchDate: 'Versanddatum',
        estimatedDelivery: 'Voraussichtliche Lieferung',
        shipment: 'Sendung',
        deliveryTerms: 'Lieferbedingungen',
        taxPointDate: 'Steuerdatum',
        purpose: 'Zweck',
        buyerReference: 'Käuferreferenz (Leitweg-ID)',
        order: 'Bestellnummer',
        salesOrder: 'Auftragsnummer',
        contract: 'Vertrag',
        quotation: 'Angebot',
        project: 'Projekt',
        despatchAdvice: 'Lieferavis',
        receivingAdvice: 'Wareneingang',
        tender: 'Vergabe / Los',
        object: 'Objektkennung',
        accountingCost: 'Buchungsreferenz',
        precedingInvoices: 'Vorausgegangene Rechnungen',
        vatId: 'USt-IdNr.',
        taxId: 'Steuernummer',
        legalId: 'Registernummer',
        endpoint: 'Elektronische Adresse',
        identifier: 'Kennung',
        contact: 'Kontakt',
        phone: 'Telefon',
        email: 'E-Mail',
        tradingName: 'Handelsname',
        legalInfo: 'Rechtliche Informationen',
        pos: 'Pos.',
        item: 'Artikel',
        quantity: 'Menge',
        unitPrice: 'Einzelpreis',
        vat: 'USt',
        amount: 'Betrag',
        delivered: 'Geliefert',
        outstanding: 'Ausstehend',
        orderLine: 'Bestellposition',
        requested: 'Bestellt',
        sellerItemId: 'Artikelnummer',
        industryItemId: 'Herstellerartikelnummer',
        lineDeliveryDate: 'Lieferdatum',
        deliveryNote: 'Lieferschein',
        buyerItemId: 'Artikelnummer Käufer',
        standardItemId: 'Standardkennung',
        classification: 'Klassifizierung',
        origin: 'Herkunftsland',
        grossPrice: 'Bruttopreis',
        priceDiscount: 'Rabatt auf den Preis',
        perQuantity: 'je',
        batch: 'Charge',
        serial: 'Seriennummer',
        expiry: 'Haltbar bis',
        status: 'Status',
        lineNote: 'Hinweis',
        subLines: 'Unterpositionen',
        lineTotal: 'Summe Positionen',
        allowances: 'Nachlässe',
        charges: 'Zuschläge',
        netTotal: 'Gesamtbetrag netto',
        taxTotal: 'Umsatzsteuer',
        grossTotal: 'Gesamtbetrag brutto',
        prepaid: 'Bereits gezahlt',
        rounding: 'Rundung',
        vatOf: 'von',
        vatBreakdown: 'Umsatzsteuer-Aufschlüsselung',
        category: 'Kategorie',
        rate: 'Satz',
        taxable: 'Bemessungsgrundlage',
        exemption: 'Befreiungsgrund',
        allowancesCharges: 'Nachlässe und Zuschläge',
        allowance: 'Nachlass',
        charge: 'Zuschlag',
        reason: 'Grund',
        base: 'Basisbetrag',
        percent: 'Prozent',
        payment: 'Zahlung',
        paymentMeans: 'Zahlungsart',
        iban: 'IBAN',
        bic: 'BIC',
        accountName: 'Kontoinhaber',
        remittance: 'Verwendungszweck',
        terms: 'Zahlungsbedingungen',
        additionalTerm: 'Weitere Zahlungsbedingung',
        advancePayment: 'Vorauszahlung',
        advanceInvoice: 'Rechnung',
        includedVat: 'darin USt',
        discount: 'Skonto',
        partialPayment: 'Teilbetrag',
        penalty: 'Verzugszuschlag',
        card: 'Karte',
        mandate: 'Mandatsreferenz',
        creditorId: 'Gläubiger-ID',
        debitAccount: 'Belastetes Konto',
        documentStatus: 'Status',
        testDocument: 'Testdokument, nicht zahlen',
        copyDocument: 'Kopie',
        effectiveDate: 'Gültig ab',
        including: 'davon',
        logisticsCharge: 'Logistikkosten',
        financialAdjustment: 'Finanzielle Korrektur',
        exchangeRate: 'Umrechnungskurs',
        ultimateCustomerOrders: 'Bestellung des Endkunden',
        invoiceIssuer: 'Referenz des Rechnungsstellers',
        transportMode: 'Verkehrszweig',
        transportModes: {
            0: 'Nicht angegeben', 1: 'Seeverkehr', 2: 'Schiene', 3: 'Straße', 4: 'Luftverkehr', 5: 'Post',
            6: 'Multimodal', 7: 'Feste Einrichtung', 8: 'Binnenschifffahrt', 9: 'Nicht anwendbar'
        },
        ultimateShipTo: 'Endempfänger',
        salesAgent: 'Handelsvertreter',
        buyerAgent: 'Einkaufsvertreter',
        buyerTaxRepresentative: 'Steuervertreter des Käufers',
        productEndUser: 'Endverbraucher',
        invoicer: 'Rechnungssteller',
        payer: 'Zahlungspflichtiger',
        debitAccountName: 'Kontoinhaber (Lastschrift)',
        debitBic: 'BIC (Lastschrift)',
        subtypes: {GROUP: 'Zwischensumme', INFORMATION: 'Zur Information, nicht in der Summe', DETAIL: 'Detailposition'},
        productId: 'Produkt-ID',
        model: 'Modell',
        brand: 'Marke',
        manufacturer: 'Hersteller',
        includedProducts: 'Enthält',
        chargeFree: 'Kostenlos',
        packages: 'Packstücke',
        itemSeller: 'Verkäufer der Position',
        precedingInvoice: 'Vorherige Rechnung',
        lineReferences: 'Weitere Referenzen',
        priceAdjustments: 'Preisnachlässe und -zuschläge',
        lineVat: 'USt der Position',
        lineGross: 'Brutto der Position',
        notes: 'Bemerkungen',
        references: 'Referenzen',
        delivery: 'Lieferung',
        parties: 'Weitere Beteiligte',
        process: 'Prozess',
        specification: 'Spezifikation',
        businessProcess: 'Geschäftsprozess',
        syntax: 'Syntax',
        container: 'Format',
        attachments: 'Anlagen',
        noAttachments: 'Keine Anlagen.',
        download: 'Herunterladen',
        preview: 'Vorschau',
        hidePreview: 'Vorschau schließen',
        originalPdf: 'Original-PDF',
        embeddedIn: 'eingebettet im PDF',
        externalLink: 'Externer Verweis',
        size: 'Größe',
        print: 'Drucken',
        downloadXml: 'XML',
        downloadPdf: 'PDF',
        copy: 'Kopieren',
        copied: 'Kopiert',
        valid: 'Gültig',
        invalid: 'Ungültig',
        findings: 'Befunde',
        noFindings: 'Keine Befunde.',
        severity: {FATAL: 'Fehler', ERROR: 'Fehler', WARNING: 'Warnung', INFO: 'Hinweis'},
        stage: 'Prüfstufe',
        line: 'Position',
        goToLine: 'Zur Position',
        dropHint: 'E-Rechnung (XML oder PDF) hierher ziehen oder',
        choose: 'Datei auswählen',
        dropReport: 'Ein Prüfbericht kann zusätzlich abgelegt werden.',
        loading: 'Dokument wird gelesen …',
        disclaimer: 'Darstellung der strukturierten Daten. Maßgeblich ist das Original-Dokument.',
        poweredBy: 'Paniolo Visualizer',
        errors: {
            empty: 'Das Dokument ist leer.',
            input: 'Diese Eingabe wird nicht unterstützt. Erwartet werden Bytes (Uint8Array, ArrayBuffer, Blob) oder XML-Text.',
            xml: 'Das XML ist nicht wohlgeformt.',
            unknownRoot: 'Dieses XML ist keine unterstützte E-Rechnung, Bestellung oder Lieferavis.',
            isReport: 'Dieses XML ist ein Prüfbericht. Bitte zusätzlich das Dokument laden.',
            notReport: 'Dieses XML ist kein Prüfbericht.',
            pdf: 'Das PDF kann nicht gelesen werden.',
            pdfEncrypted: 'Das PDF ist verschlüsselt und kann nicht gelesen werden.',
            pdfNoXml: 'Das PDF enthält keine eingebettete E-Rechnung (Factur-X / ZUGFeRD).',
            fetch: 'Das Dokument konnte nicht geladen werden.',
            unknown: 'Das Dokument kann nicht angezeigt werden.'
        }
    },
    en: {
        kind: {
            invoice: 'Invoice', creditNote: 'Credit note', order: 'Order', orderChange: 'Order change',
            orderResponse: 'Order response', despatchAdvice: 'Despatch advice'
        },
        tabs: {
            overview: 'Overview', lines: 'Lines', details: 'Details', attachments: 'Attachments',
            validation: 'Validation', xml: 'XML'
        },
        seller: 'Seller', buyer: 'Buyer', payee: 'Payee', taxRepresentative: 'Tax representative',
        shipTo: 'Deliver to', shipFrom: 'Ship from', invoicee: 'Invoicee', sellerSupplier: 'Supplier',
        issueDate: 'Date', dueDate: 'Due date', payable: 'Amount due', grandTotal: 'Total',
        number: 'Number', documentType: 'Document type', currency: 'Currency', taxCurrency: 'Tax currency',
        period: 'Invoicing period', deliveryDate: 'Delivery date', requestedDelivery: 'Requested delivery',
        despatchDate: 'Despatch date', estimatedDelivery: 'Estimated delivery', shipment: 'Shipment',
        deliveryTerms: 'Delivery terms', taxPointDate: 'Tax point date', purpose: 'Purpose',
        buyerReference: 'Buyer reference', order: 'Purchase order', salesOrder: 'Sales order',
        contract: 'Contract', quotation: 'Quotation', project: 'Project', despatchAdvice: 'Despatch advice',
        receivingAdvice: 'Receiving advice', tender: 'Tender or lot', object: 'Invoiced object',
        accountingCost: 'Buyer accounting reference', precedingInvoices: 'Preceding invoices',
        vatId: 'VAT ID', taxId: 'Tax number', legalId: 'Legal registration', endpoint: 'Electronic address',
        identifier: 'Identifier', contact: 'Contact', phone: 'Phone', email: 'E-mail', tradingName: 'Trading name',
        legalInfo: 'Additional legal information',
        pos: 'Line', item: 'Item', quantity: 'Quantity', unitPrice: 'Unit price', vat: 'VAT', amount: 'Amount',
        delivered: 'Delivered', outstanding: 'Outstanding', orderLine: 'Order line', requested: 'Ordered',
        sellerItemId: 'Item number', buyerItemId: 'Buyer item number', standardItemId: 'Standard identifier',
        industryItemId: 'Manufacturer item number', lineDeliveryDate: 'Delivery date', deliveryNote: 'Delivery note',
        classification: 'Classification', origin: 'Country of origin', grossPrice: 'Gross price',
        priceDiscount: 'Price discount', perQuantity: 'per', batch: 'Batch', serial: 'Serial number',
        expiry: 'Best before', status: 'Status', lineNote: 'Note', subLines: 'Sub-lines',
        lineTotal: 'Sum of lines', allowances: 'Allowances', charges: 'Charges', netTotal: 'Total without VAT',
        taxTotal: 'VAT', grossTotal: 'Total with VAT', prepaid: 'Paid amount', rounding: 'Rounding',
        vatOf: 'of', vatBreakdown: 'VAT breakdown', category: 'Category', rate: 'Rate',
        taxable: 'Taxable amount', exemption: 'Exemption reason',
        allowancesCharges: 'Allowances and charges', allowance: 'Allowance', charge: 'Charge', reason: 'Reason',
        base: 'Base amount', percent: 'Percent',
        payment: 'Payment', paymentMeans: 'Payment means', iban: 'IBAN', bic: 'BIC', accountName: 'Account name',
        remittance: 'Remittance information', terms: 'Payment terms', additionalTerm: 'Further payment term',
        advancePayment: 'Advance payment', advanceInvoice: 'Invoice', includedVat: 'including VAT',
        discount: 'Discount', partialPayment: 'Partial amount', penalty: 'Penalty', card: 'Card', mandate: 'Mandate reference',
        creditorId: 'Creditor ID', debitAccount: 'Debited account',
        documentStatus: 'Status', testDocument: 'Test document, not for payment', copyDocument: 'Copy',
        effectiveDate: 'Effective date', including: 'including', logisticsCharge: 'Logistics service charge',
        financialAdjustment: 'Financial adjustment', exchangeRate: 'Exchange rate',
        ultimateCustomerOrders: 'Ultimate customer order', invoiceIssuer: 'Invoice issuer reference',
        transportMode: 'Transport mode',
        transportModes: {
            0: 'Not specified', 1: 'Maritime', 2: 'Rail', 3: 'Road', 4: 'Air', 5: 'Mail', 6: 'Multimodal',
            7: 'Fixed installation', 8: 'Inland water', 9: 'Not applicable'
        },
        ultimateShipTo: 'Ultimate ship-to party', salesAgent: 'Sales agent', buyerAgent: 'Buyer agent',
        buyerTaxRepresentative: 'Buyer tax representative', productEndUser: 'Product end user', invoicer: 'Invoicer',
        payer: 'Payer', debitAccountName: 'Debited account holder', debitBic: 'Debited account BIC',
        subtypes: {GROUP: 'Subtotal', INFORMATION: 'For information, not included in the total', DETAIL: 'Detail line'},
        productId: 'Product ID', model: 'Model', brand: 'Brand', manufacturer: 'Manufacturer', includedProducts: 'Includes',
        chargeFree: 'Free of charge', packages: 'Packages', itemSeller: 'Seller of the line',
        precedingInvoice: 'Preceding invoice', lineReferences: 'Further references',
        priceAdjustments: 'Price allowances and charges', lineVat: 'Line VAT', lineGross: 'Line total with VAT',
        notes: 'Notes', references: 'References', delivery: 'Delivery', parties: 'Further parties',
        process: 'Process', specification: 'Specification', businessProcess: 'Business process', syntax: 'Syntax',
        container: 'Format',
        attachments: 'Attachments', noAttachments: 'No attachments.', download: 'Download', preview: 'Preview',
        hidePreview: 'Close preview', originalPdf: 'Original PDF', embeddedIn: 'embedded in the PDF',
        externalLink: 'External reference', size: 'Size',
        print: 'Print', downloadXml: 'XML', downloadPdf: 'PDF', copy: 'Copy', copied: 'Copied',
        valid: 'Valid', invalid: 'Invalid', findings: 'Findings', noFindings: 'No findings.',
        severity: {FATAL: 'Error', ERROR: 'Error', WARNING: 'Warning', INFO: 'Info'},
        stage: 'Stage', line: 'Line', goToLine: 'Go to line',
        dropHint: 'Drop an e-invoice (XML or PDF) here or', choose: 'choose a file',
        dropReport: 'A validation report can be dropped as well.',
        loading: 'Reading document …', disclaimer: 'View of the structured data. The original document is authoritative.',
        poweredBy: 'Paniolo Visualizer',
        errors: {
            empty: 'The document is empty.',
            input: 'This input is not supported. Expected bytes (Uint8Array, ArrayBuffer, Blob) or XML text.',
            xml: 'The XML is not well-formed.',
            unknownRoot: 'This XML is not a supported e-invoice, order, or despatch advice.',
            isReport: 'This XML is a validation report. Please load the document as well.',
            notReport: 'This XML is not a validation report.',
            pdf: 'The PDF cannot be read.',
            pdfEncrypted: 'The PDF is encrypted and cannot be read.',
            pdfNoXml: 'The PDF contains no embedded e-invoice (Factur-X / ZUGFeRD).',
            fetch: 'The document could not be loaded.',
            unknown: 'The document cannot be shown.'
        }
    },
    fr: {
        kind: {
            invoice: 'Facture', creditNote: 'Avoir', order: 'Commande', orderChange: 'Modification de commande',
            orderResponse: 'Réponse à la commande', despatchAdvice: "Avis d'expédition"
        },
        tabs: {
            overview: 'Aperçu', lines: 'Lignes', details: 'Détails', attachments: 'Pièces jointes',
            validation: 'Contrôle', xml: 'XML'
        },
        seller: 'Vendeur', buyer: 'Acheteur', payee: 'Bénéficiaire', taxRepresentative: 'Représentant fiscal',
        shipTo: 'Livrer à', shipFrom: 'Expédié de', invoicee: 'Facturé à', sellerSupplier: 'Fournisseur',
        issueDate: 'Date', dueDate: "Date d'échéance", payable: 'Net à payer', grandTotal: 'Total',
        number: 'Numéro', documentType: 'Type de document', currency: 'Devise', taxCurrency: 'Devise de TVA',
        period: 'Période de facturation', deliveryDate: 'Date de livraison', requestedDelivery: 'Livraison souhaitée',
        despatchDate: "Date d'expédition", estimatedDelivery: 'Livraison prévue', shipment: 'Envoi',
        deliveryTerms: 'Conditions de livraison', taxPointDate: "Date d'exigibilité de la TVA", purpose: 'Objet',
        buyerReference: "Référence de l'acheteur", order: 'Bon de commande', salesOrder: 'Commande client',
        contract: 'Contrat', quotation: 'Devis', project: 'Projet', despatchAdvice: "Avis d'expédition",
        receivingAdvice: 'Avis de réception', tender: "Appel d'offres ou lot", object: 'Objet facturé',
        accountingCost: "Référence comptable de l'acheteur", precedingInvoices: 'Factures antérieures',
        vatId: 'N° de TVA', taxId: 'Numéro fiscal', legalId: "N° d'immatriculation", endpoint: 'Adresse électronique',
        identifier: 'Identifiant', contact: 'Contact', phone: 'Téléphone', email: 'E-mail', tradingName: 'Nom commercial',
        legalInfo: 'Informations juridiques',
        pos: 'Ligne', item: 'Article', quantity: 'Quantité', unitPrice: 'Prix unitaire', vat: 'TVA', amount: 'Montant',
        delivered: 'Livré', outstanding: 'Restant', orderLine: 'Ligne de commande', requested: 'Commandé',
        sellerItemId: "Référence de l'article", buyerItemId: 'Référence acheteur', standardItemId: 'Identifiant standard',
        industryItemId: 'Référence fabricant', lineDeliveryDate: 'Date de livraison', deliveryNote: 'Bon de livraison',
        classification: 'Classification', origin: "Pays d'origine", grossPrice: 'Prix brut',
        priceDiscount: 'Remise sur le prix', perQuantity: 'par', batch: 'Lot', serial: 'Numéro de série',
        expiry: 'À consommer avant', status: 'Statut', lineNote: 'Note', subLines: 'Sous-lignes',
        lineTotal: 'Somme des lignes', allowances: 'Remises', charges: 'Frais', netTotal: 'Total HT',
        taxTotal: 'TVA', grossTotal: 'Total TTC', prepaid: 'Montant payé', rounding: 'Arrondi',
        vatOf: 'sur', vatBreakdown: 'Ventilation de la TVA', category: 'Catégorie', rate: 'Taux',
        taxable: "Base d'imposition", exemption: "Motif d'exonération",
        allowancesCharges: 'Remises et frais', allowance: 'Remise', charge: 'Frais', reason: 'Motif',
        base: 'Montant de base', percent: 'Pourcentage',
        payment: 'Paiement', paymentMeans: 'Moyen de paiement', iban: 'IBAN', bic: 'BIC', accountName: 'Titulaire du compte',
        remittance: 'Référence de paiement', terms: 'Conditions de paiement', additionalTerm: 'Autre condition de paiement',
        advancePayment: 'Acompte', advanceInvoice: 'Facture', includedVat: 'dont TVA',
        discount: 'Escompte', partialPayment: 'Montant partiel', penalty: 'Pénalité', card: 'Carte', mandate: 'Référence du mandat',
        creditorId: 'Identifiant créancier', debitAccount: 'Compte débité',
        documentStatus: 'Statut', testDocument: 'Document de test, ne pas payer', copyDocument: 'Copie',
        effectiveDate: "Date d'effet", including: 'dont', logisticsCharge: 'Frais logistiques',
        financialAdjustment: 'Ajustement financier', exchangeRate: 'Taux de change',
        ultimateCustomerOrders: 'Commande du client final', invoiceIssuer: "Référence de l'émetteur",
        transportMode: 'Mode de transport',
        transportModes: {
            0: 'Non précisé', 1: 'Maritime', 2: 'Ferroviaire', 3: 'Routier', 4: 'Aérien', 5: 'Postal',
            6: 'Multimodal', 7: 'Installation fixe', 8: 'Fluvial', 9: 'Non applicable'
        },
        ultimateShipTo: 'Destinataire final', salesAgent: 'Agent commercial', buyerAgent: "Agent de l'acheteur",
        buyerTaxRepresentative: "Représentant fiscal de l'acheteur", productEndUser: 'Utilisateur final',
        invoicer: 'Émetteur de la facture', payer: 'Payeur', debitAccountName: 'Titulaire du compte débité',
        debitBic: 'BIC du compte débité',
        subtypes: {GROUP: 'Sous-total', INFORMATION: 'Pour information, non inclus dans le total', DETAIL: 'Ligne de détail'},
        productId: 'ID produit', model: 'Modèle', brand: 'Marque', manufacturer: 'Fabricant', includedProducts: 'Comprend',
        chargeFree: 'Gratuit', packages: 'Colis', itemSeller: 'Vendeur de la ligne',
        precedingInvoice: 'Facture précédente', lineReferences: 'Autres références',
        priceAdjustments: 'Remises et majorations sur le prix', lineVat: 'TVA de la ligne', lineGross: 'TTC de la ligne',
        notes: 'Remarques', references: 'Références', delivery: 'Livraison', parties: 'Autres parties',
        process: 'Processus', specification: 'Spécification', businessProcess: 'Processus métier', syntax: 'Syntaxe',
        container: 'Format',
        attachments: 'Pièces jointes', noAttachments: 'Aucune pièce jointe.', download: 'Télécharger', preview: 'Aperçu',
        hidePreview: "Fermer l'aperçu", originalPdf: 'PDF original', embeddedIn: 'intégré au PDF',
        externalLink: 'Référence externe', size: 'Taille',
        print: 'Imprimer', downloadXml: 'XML', downloadPdf: 'PDF', copy: 'Copier', copied: 'Copié',
        valid: 'Valide', invalid: 'Non valide', findings: 'Constats', noFindings: 'Aucun constat.',
        severity: {FATAL: 'Erreur', ERROR: 'Erreur', WARNING: 'Avertissement', INFO: 'Information'},
        stage: 'Étape', line: 'Ligne', goToLine: 'Aller à la ligne',
        dropHint: 'Déposez une facture électronique (XML ou PDF) ici ou', choose: 'choisissez un fichier',
        dropReport: 'Un rapport de contrôle peut également être déposé.',
        loading: 'Lecture du document …', disclaimer: 'Représentation des données structurées. Le document original fait foi.',
        poweredBy: 'Paniolo Visualizer',
        errors: {
            empty: 'Le document est vide.',
            input: 'Cette entrée n’est pas prise en charge. Attendu : des octets (Uint8Array, ArrayBuffer, Blob) ou du texte XML.',
            xml: 'Le XML n’est pas bien formé.',
            unknownRoot: 'Ce XML n’est pas une facture électronique, une commande ou un avis d’expédition pris en charge.',
            isReport: 'Ce XML est un rapport de contrôle. Veuillez également charger le document.',
            notReport: 'Ce XML n’est pas un rapport de contrôle.',
            pdf: 'Le PDF ne peut pas être lu.',
            pdfEncrypted: 'Le PDF est chiffré et ne peut pas être lu.',
            pdfNoXml: 'Le PDF ne contient pas de facture électronique intégrée (Factur-X / ZUGFeRD).',
            fetch: 'Le document n’a pas pu être chargé.',
            unknown: 'Le document ne peut pas être affiché.'
        }
    }
};

// UNTDID 1001, extract used by EN 16931, Order-X, and despatch advices
const DOCUMENT_TYPES = {
    de: {
        '71': 'Rechnungsanforderung',
        '80': 'Belastungsanzeige (Waren und Dienstleistungen)',
        '81': 'Gutschrift (Waren und Dienstleistungen)',
        '82': 'Rechnung für Messdienste',
        '83': 'Gutschrift für Finanzanpassungen',
        '84': 'Belastungsanzeige für Finanzanpassungen',
        '102': 'Rechnung für Mieten',
        '130': 'Rechnungsdatenblatt',
        '202': 'Abschlagsrechnung (direkt)',
        '203': 'Abschlagsrechnung',
        '204': 'Zahlungsplan',
        '211': 'Abschlagsrechnung (Zwischenzahlung)',
        '218': 'Schlussrechnung',
        '219': 'Teilzahlungsrechnung',
        '220': 'Bestellung',
        '230': 'Bestelländerung',
        '231': 'Bestellantwort',
        '261': 'Selbst ausgestellte Gutschrift',
        '262': 'Konsolidierte Gutschrift',
        '270': 'Lieferschein',
        '295': 'Preisabweichungsrechnung',
        '296': 'Gutschrift für Preisabweichung',
        '308': 'Delcredere-Gutschrift',
        '325': 'Proformarechnung',
        '326': 'Teilrechnung',
        '331': 'Handelsrechnung mit Verpackungsliste',
        '351': 'Lieferavis',
        '380': 'Handelsrechnung',
        '381': 'Gutschrift',
        '382': 'Provisionsabrechnung',
        '383': 'Belastungsanzeige',
        '384': 'Korrigierte Rechnung',
        '385': 'Konsolidierte Rechnung',
        '386': 'Vorauszahlungsrechnung',
        '387': 'Mietrechnung',
        '388': 'Steuerrechnung',
        '389': 'Selbst ausgestellte Rechnung',
        '390': 'Delcredere-Rechnung',
        '393': 'Factoring-Rechnung',
        '394': 'Leasingrechnung',
        '395': 'Konsignationsrechnung',
        '396': 'Factoring-Gutschrift',
        '420': 'Optiker-Rechnung',
        '456': 'Belastungsanzeige',
        '457': 'Stornobeleg',
        '458': 'Gutschrift (Stornierung)',
        '527': 'Selbst ausgestellte Belastungsanzeige',
        '532': 'Gutschrift des Spediteurs',
        '553': 'Reisebüro-Rechnung',
        '575': 'Versicherungsrechnung',
        '623': 'Speditionsrechnung',
        '633': 'Hafenkostenrechnung',
        '751': 'Rechnungsinformation für Buchungszwecke',
        '780': 'Frachtrechnung',
        '817': 'Reklamationsrechnung',
        '870': 'Konsulatsrechnung',
        '875': 'Teilrechnung für Bauleistungen',
        '876': 'Teilschlussrechnung für Bauleistungen',
        '877': 'Schlussrechnung für Bauleistungen',
        '935': 'Zollrechnung'
    },
    en: {
        '71': 'Request for payment',
        '80': 'Debit note related to goods or services',
        '81': 'Credit note related to goods or services',
        '82': 'Metered services invoice',
        '83': 'Credit note related to financial adjustments',
        '84': 'Debit note related to financial adjustments',
        '102': 'Tax notification',
        '130': 'Invoicing data sheet',
        '202': 'Direct payment valuation',
        '203': 'Provisional payment valuation',
        '204': 'Payment valuation',
        '211': 'Interim application for payment',
        '218': 'Final payment request based on completion of work',
        '219': 'Payment request for completed units',
        '220': 'Order',
        '230': 'Order change',
        '231': 'Order response',
        '261': 'Self billed credit note',
        '262': 'Consolidated credit note - goods and services',
        '270': 'Delivery note',
        '295': 'Price variation invoice',
        '296': 'Credit note for price variation',
        '308': 'Delcredere credit note',
        '325': 'Proforma invoice',
        '326': 'Partial invoice',
        '331': 'Commercial invoice which includes a packing list',
        '351': 'Despatch advice',
        '380': 'Commercial invoice',
        '381': 'Credit note',
        '382': 'Commission note',
        '383': 'Debit note',
        '384': 'Corrected invoice',
        '385': 'Consolidated invoice',
        '386': 'Prepayment invoice',
        '387': 'Hire invoice',
        '388': 'Tax invoice',
        '389': 'Self-billed invoice',
        '390': 'Delcredere invoice',
        '393': 'Factored invoice',
        '394': 'Lease invoice',
        '395': 'Consignment invoice',
        '396': 'Factored credit note',
        '420': 'Optical Character Reading (OCR) payment credit note',
        '456': 'Debit advice',
        '457': 'Reversal of debit',
        '458': 'Reversal of credit',
        '527': 'Self billed debit note',
        '532': "Forwarder's credit note",
        '553': "Forwarder's invoice discrepancy report",
        '575': "Insurer's invoice",
        '623': "Forwarder's invoice",
        '633': 'Port charges documents',
        '751': 'Invoice information for accounting purposes',
        '780': 'Freight invoice',
        '817': 'Claim notification',
        '870': 'Consular invoice',
        '875': 'Partial construction invoice',
        '876': 'Partial final construction invoice',
        '877': 'Final construction invoice',
        '935': 'Customs invoice'
    }
};

// UNTDID 4461
const PAYMENT_MEANS = {
    de: {
        '1': 'Nicht definiert', '10': 'Barzahlung', '20': 'Scheck', '30': 'Überweisung', '31': 'Überweisung (Lastschrift)',
        '42': 'Zahlung auf Bankkonto', '48': 'Bankkarte', '49': 'Lastschrift', '54': 'Kreditkarte', '55': 'Debitkarte',
        '57': 'Dauerauftrag', '58': 'SEPA-Überweisung', '59': 'SEPA-Lastschrift', '68': 'Online-Zahlungsdienst',
        '97': 'Verrechnung', 'ZZZ': 'Vereinbarte Zahlungsart'
    },
    en: {
        '1': 'Instrument not defined', '10': 'In cash', '20': 'Cheque', '30': 'Credit transfer', '31': 'Debit transfer',
        '42': 'Payment to bank account', '48': 'Bank card', '49': 'Direct debit', '54': 'Credit card', '55': 'Debit card',
        '57': 'Standing agreement', '58': 'SEPA credit transfer', '59': 'SEPA direct debit', '68': 'Online payment service',
        '97': 'Clearing between partners', 'ZZZ': 'Mutually defined'
    },
    fr: {
        '1': 'Non défini',
        '10': 'Espèces',
        '20': 'Chèque',
        '30': 'Virement',
        '31': 'Prélèvement',
        '42': 'Paiement sur compte bancaire',
        '48': 'Carte bancaire',
        '49': 'Prélèvement',
        '54': 'Carte de crédit',
        '55': 'Carte de débit',
        '57': 'Ordre permanent',
        '58': 'Virement SEPA',
        '59': 'Prélèvement SEPA',
        '68': 'Service de paiement en ligne',
        '97': 'Compensation',
        'ZZZ': 'Défini d’un commun accord'
    }
};

// UNTDID 5305
const TAX_CATEGORIES = {
    de: {
        S: 'Normalsatz', Z: 'Nullsatz', E: 'Steuerbefreit', AE: 'Umkehrung der Steuerschuldnerschaft',
        K: 'Innergemeinschaftliche Lieferung', G: 'Ausfuhr außerhalb der EU', O: 'Nicht steuerbar',
        L: 'IGIC (Kanarische Inseln)', M: 'IPSI (Ceuta und Melilla)'
    },
    en: {
        S: 'Standard rate', Z: 'Zero rated', E: 'Exempt', AE: 'Reverse charge', K: 'Intra-community supply',
        G: 'Export outside the EU', O: 'Not subject to VAT', L: 'IGIC (Canary Islands)', M: 'IPSI (Ceuta and Melilla)'
    },
    fr: {
        S: 'Taux normal', Z: 'Taux zéro', E: 'Exonéré', AE: 'Autoliquidation', K: 'Livraison intracommunautaire',
        G: "Exportation hors de l'UE", O: 'Hors champ de la TVA', L: 'IGIC (îles Canaries)', M: 'IPSI (Ceuta et Melilla)'
    }
};

// UN/ECE Recommendation 20/21, the units in common use
const UNITS = {
    de: {
        C62: 'Stück', H87: 'Stück', XPP: 'Stück', EA: 'Stück', PCE: 'Stück', HUR: 'Std.', MIN: 'Min.', SEC: 'Sek.',
        DAY: 'Tag(e)', WEE: 'Woche(n)', MON: 'Monat(e)', ANN: 'Jahr(e)', QAN: 'Quartal(e)', KGM: 'kg', GRM: 'g', TNE: 't',
        MTR: 'm', KMT: 'km', CMT: 'cm', MMT: 'mm', MTK: 'm²', MTQ: 'm³', LTR: 'l', MLT: 'ml', KWH: 'kWh', KWT: 'kW',
        LS: 'pauschal', SET: 'Satz', PR: 'Paar', P1: '%', E48: 'Leistungseinheit', XBX: 'Kiste', XCT: 'Karton',
        XPK: 'Packung', XPA: 'Paket', XPX: 'Palette', XBO: 'Flasche', XRO: 'Rolle', XSA: 'Sack', NAR: 'Anzahl', XNA: ''
    },
    en: {
        C62: 'pcs', H87: 'pcs', XPP: 'pcs', EA: 'each', PCE: 'pcs', HUR: 'h', MIN: 'min', SEC: 's', DAY: 'day(s)',
        WEE: 'week(s)', MON: 'month(s)', ANN: 'year(s)', QAN: 'quarter(s)', KGM: 'kg', GRM: 'g', TNE: 't', MTR: 'm',
        KMT: 'km', CMT: 'cm', MMT: 'mm', MTK: 'm²', MTQ: 'm³', LTR: 'l', MLT: 'ml', KWH: 'kWh', KWT: 'kW',
        LS: 'lump sum', SET: 'set', PR: 'pair', P1: '%', E48: 'service unit', XBX: 'box', XCT: 'carton', XPK: 'pack',
        XPA: 'packet', XPX: 'pallet', XBO: 'bottle', XRO: 'roll', XSA: 'sack', NAR: 'number', XNA: ''
    },
    fr: {
        C62: 'unité', H87: 'pièce', XPP: 'pièce', EA: 'unité', PCE: 'pièce', HUR: 'h', MIN: 'min', SEC: 's', DAY: 'jour(s)',
        WEE: 'semaine(s)', MON: 'mois', ANN: 'an(s)', QAN: 'trimestre(s)', KGM: 'kg', GRM: 'g', TNE: 't', MTR: 'm',
        KMT: 'km', CMT: 'cm', MMT: 'mm', MTK: 'm²', MTQ: 'm³', LTR: 'l', MLT: 'ml', KWH: 'kWh', KWT: 'kW',
        LS: 'forfait', SET: 'lot', PR: 'paire', P1: '%', E48: 'unité de service', XBX: 'boîte', XCT: 'carton',
        XPK: 'paquet', XPA: 'colis', XPX: 'palette', XBO: 'bouteille', XRO: 'rouleau', XSA: 'sac', NAR: 'nombre', XNA: ''
    }
};

// UNTDID 4451, the subjects of notes in common use
const NOTE_SUBJECTS = {
    de: {
        AAI: 'Allgemeine Information', AAB: 'Zahlungsbedingungen', AAC: 'Gefahrgut', AAK: 'Preisbedingungen',
        ABL: 'Gesetzliche Informationen', ADU: 'Hinweis', PMT: 'Zahlungsinformation', PMD: 'Zahlungsinformation',
        REG: 'Regulatorische Information', SUR: 'Anmerkung des Verkäufers', TXD: 'Steuerliche Information',
        ACY: 'Bezeichnung', AAJ: 'Zusatzbedingungen', PAC: 'Verpackung', CUS: 'Zollinformation'
    },
    en: {
        AAI: 'General information', AAB: 'Terms of payments', AAC: 'Dangerous goods', AAK: 'Price conditions',
        ABL: 'Government information', ADU: 'Note', PMT: 'Payment information', PMD: 'Payment detail',
        REG: 'Regulatory information', SUR: 'Supplier remarks', TXD: 'Tax declaration', ACY: 'Description',
        AAJ: 'Additional conditions', PAC: 'Packing', CUS: 'Customs declaration'
    },
    fr: {
        AAI: 'Information générale', AAB: 'Conditions de paiement', AAC: 'Marchandises dangereuses', AAK: 'Conditions de prix',
        ABL: 'Informations légales', ADU: 'Note', PMT: 'Information de paiement', PMD: 'Détail du paiement',
        REG: 'Information réglementaire', SUR: 'Remarques du fournisseur', TXD: 'Information fiscale',
        ACY: 'Description', AAJ: 'Conditions supplémentaires', PAC: 'Emballage', CUS: 'Information douanière'
    }
};

// ISO 6523 ICD and EAS, the schemes in common use
const SCHEMES = {
    '0002': 'SIRENE', '0007': 'SE:ORGNR', '0009': 'SIRET', '0060': 'DUNS', '0088': 'GLN', '0096': 'DK:P', '0106': 'NL:KVK',
    '0130': 'EU', '0151': 'AU:ABN', '0160': 'GTIN', '0183': 'CH:UIDB', '0184': 'DK:DIGST', '0190': 'NL:OINO',
    '0192': 'NO:ORG', '0195': 'SG:UEN', '0196': 'IS:KT', '0198': 'DK:ERST', '0204': 'Leitweg-ID', '0208': 'BE:EN',
    '0209': 'GS1', '0225': 'FR:SIRET', '0230': 'MY:EIF', '9906': 'IT:VAT', '9907': 'IT:CF', '9910': 'HU:VAT',
    '9914': 'AT:VAT', '9915': 'AT:GOV', '9918': 'IBAN', '9919': 'AT:KUR', '9920': 'ES:VAT', '9925': 'BE:VAT',
    '9930': 'DE:VAT', '9931': 'EE:VAT', '9944': 'NL:VAT', '9957': 'FR:VAT', '9958': 'DE:LID', EM: 'E-Mail', SEPA: 'SEPA'
};

function texts_(lang) {
    return TEXTS[lang] || TEXTS.en;
}

// ------------------------------------------------------------------------------------------------
// Formatting
// ------------------------------------------------------------------------------------------------

class Formatter {
    constructor(locale) {
        this.locale = locale;
        this.lang = (locale || 'en').slice(0, 2).toLowerCase();
        this.t = texts_(this.lang);
        this.cache = new Map();
    }

    numberFormat(key, options) {
        if (!this.cache.has(key)) {
            try {
                this.cache.set(key, new Intl.NumberFormat(this.locale, options));
            } catch (e) {
                this.cache.set(key, new Intl.NumberFormat(this.locale, {...options, style: 'decimal', currency: undefined}));
            }
        }
        return this.cache.get(key);
    }

    decimals(value) {
        const m = /\.(\d+)$/.exec(String(value));
        return m ? m[1].length : 0;
    }

    amount(value, currency) {
        if (value === undefined || value === null || value === '') {
            return '';
        }
        const digits = Math.min(20, Math.max(2, this.decimals(value)));
        if (currency && /^[A-Z]{3}$/.test(currency)) {
            return this.numberFormat('c' + currency + digits, {
                style: 'currency', currency, minimumFractionDigits: digits, maximumFractionDigits: digits
            }).format(value);
        }
        return this.numberFormat('d' + digits, {minimumFractionDigits: digits, maximumFractionDigits: digits})
            .format(value) + (currency ? ' ' + currency : '');
    }

    number(value) {
        if (value === undefined || value === null || value === '') {
            return '';
        }
        const digits = Math.min(20, this.decimals(value));
        return this.numberFormat('n' + digits, {minimumFractionDigits: 0, maximumFractionDigits: digits}).format(value);
    }

    percent(value) {
        return value === undefined ? '' : this.number(value) + ' %';
    }

    date(value) {
        if (!value) {
            return '';
        }
        let m;
        try {
            if ((m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value))) {
                return new Intl.DateTimeFormat(this.locale, {dateStyle: 'medium', timeZone: 'UTC'})
                    .format(new Date(Date.UTC(+m[1], +m[2] - 1, +m[3])));
            }
            if ((m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value))) {
                return new Intl.DateTimeFormat(this.locale, {dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC'})
                    .format(new Date(Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5])));
            }
            if ((m = /^(\d{4})-(\d{2})$/.exec(value))) {
                return new Intl.DateTimeFormat(this.locale, {year: 'numeric', month: 'long', timeZone: 'UTC'})
                    .format(new Date(Date.UTC(+m[1], +m[2] - 1, 1)));
            }
        } catch (e) {
            // fall through
        }
        return value;
    }

    period(p) {
        if (!p) {
            return '';
        }
        if (typeof p === 'string') {
            return this.date(p);
        }
        return [this.date(p.start), this.date(p.end)].filter(Boolean).join(' – ');
    }

    display(type, code) {
        if (!code) {
            return '';
        }
        try {
            return new Intl.DisplayNames([this.locale], {type, fallback: 'code'}).of(code) || code;
        } catch (e) {
            return code;
        }
    }

    country(code) {
        return this.display('region', code);
    }

    code(list, code) {
        if (code === undefined) {
            return '';
        }
        const map = list[this.lang] || list.en || {};
        return map[code] !== undefined ? map[code] : code;
    }

    unit(code) {
        return code ? this.code(UNITS, code) : '';
    }

    documentType(code) {
        return code ? this.code(DOCUMENT_TYPES, code) : '';
    }

    taxCategory(code) {
        return code ? this.code(TAX_CATEGORIES, code) : '';
    }

    paymentMeans(code) {
        return code ? this.code(PAYMENT_MEANS, code) : '';
    }

    noteSubject(code) {
        return code ? this.code(NOTE_SUBJECTS, code) : '';
    }

    scheme(code) {
        return code ? SCHEMES[code] || code : '';
    }

    size(bytes) {
        if (bytes < 1024) {
            return bytes + ' B';
        }
        const units = ['KB', 'MB', 'GB'];
        let value = bytes / 1024;
        let i = 0;
        while (value >= 1024 && i < units.length - 1) {
            value /= 1024;
            i++;
        }
        return this.numberFormat('s', {maximumFractionDigits: 1}).format(value) + ' ' + units[i];
    }

    iban(value) {
        return /^[A-Z]{2}\d{2}[A-Z0-9]{10,30}$/.test(value || '') ? value.replace(/(.{4})(?=.)/g, '$1 ') : value;
    }
}

// ------------------------------------------------------------------------------------------------
// HTML templating with escaping of all values
// ------------------------------------------------------------------------------------------------

class Html {
    constructor(value) {
        this.value = value;
    }

    toString() {
        return this.value;
    }
}

function esc(value) {
    return String(value).replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[c]);
}

function part(value) {
    if (value === undefined || value === null || value === false) {
        return '';
    }
    if (value instanceof Html) {
        return value.value;
    }
    if (Array.isArray(value)) {
        return value.map(part).join('');
    }
    return esc(value);
}

function html(strings, ...values) {
    let out = strings[0];
    for (let i = 0; i < values.length; i++) {
        out += part(values[i]) + strings[i + 1];
    }
    return new Html(out);
}

const ICONS = {
    print: '<path d="M6 9V3h12v6M6 18H4a1 1 0 0 1-1-1v-6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v6a1 1 0 0 1-1 1h-2M6 14h12v7H6z"/>',
    download: '<path d="M12 3v12m0 0-4-4m4 4 4-4M4 17v3h16v-3"/>',
    file: '<path d="M14 3H6v18h12V7zM14 3v4h4"/>',
    check: '<path d="m5 12 5 5 9-10"/>',
    alert: '<path d="M12 4 2.5 20h19zM12 10v4m0 3v.01"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5m0-8v.01"/>',
    chevron: '<path d="m9 6 6 6-6 6"/>',
    link: '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
    copy: '<rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3"/>',
    upload: '<path d="M12 16V4m0 0-4 4m4-4 4 4M4 17v3h16v-3"/>'
};

function icon(name) {
    return new Html('<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">' + ICONS[name] + '</svg>');
}

// ------------------------------------------------------------------------------------------------
// Rendering
// ------------------------------------------------------------------------------------------------

const TAB_ORDER = ['overview', 'lines', 'details', 'attachments', 'validation', 'xml'];

class Renderer {
    constructor(model, report, f) {
        this.m = model;
        this.r = report;
        this.f = f;
        this.t = f.t;
        this.isInvoice = model.kind === 'invoice' || model.kind === 'creditNote';
        this.isDespatch = model.kind === 'despatchAdvice';
        this.findingsByLine = new Map();
        for (const stage of (report && report.stages) || []) {
            for (const message of stage.messages) {
                if (message.line) {
                    const list = this.findingsByLine.get(message.line) || [];
                    list.push(message);
                    this.findingsByLine.set(message.line, list);
                }
            }
        }
    }

    title() {
        return [this.t.kind[this.m.kind], this.m.number].filter(Boolean).join(' ');
    }

    money(value) {
        return this.f.amount(value, this.m.currency);
    }

    tabs(allowed) {
        const counts = {
            lines: this.countLines(this.m.lines || []),
            attachments: (this.m.attachments || []).length + (this.m.pdf ? 1 : 0),
            validation: this.r ? this.findingCount() : undefined
        };
        return TAB_ORDER.filter(tab => (!allowed || allowed.includes(tab)) &&
            (tab !== 'validation' || this.r) &&
            (tab !== 'lines' || counts.lines) &&
            (tab !== 'attachments' || counts.attachments))
            .map(tab => ({id: tab, label: this.t.tabs[tab], count: counts[tab]}));
    }

    countLines(lines) {
        return lines.reduce((sum, line) => sum + 1 + this.countLines(line.subLines || []), 0);
    }

    findingCount() {
        return this.r.stages.reduce((sum, s) => sum + s.messages.filter(m => m.severity !== 'INFO').length, 0);
    }

    // -- header -------------------------------------------------------------------------------------

    header(withActions) {
        const m = this.m;
        const t = this.t;
        const status = this.r ? html`<span class="chip ${this.r.valid ? 'ok' : 'bad'}"
                                           data-action="tab"
                                           data-tab="validation">
        ${icon(this.r.valid ? 'check' : 'alert')}${this.r.valid ? t.valid : t.invalid}${this.findingCount() ? html` · ${this.findingCount()}` : ''}</span>` : '';
        const headline = m.totals && (this.isInvoice ? m.totals.payable || m.totals.taxInclusive :
            m.totals.taxInclusive || m.totals.taxExclusive || m.totals.lineTotal);
        const typeLabel = this.f.documentType(m.typeCode);
        const showType = m.typeCode && typeLabel.toLowerCase() !== String(t.kind[m.kind]).toLowerCase();
        return html`
            <header class="head"
                    part="header">
                <div class="head-main">
                    <div class="eyebrow">
                        ${m.name && !this.isDespatch && m.kind !== 'order' ? m.name : t.kind[m.kind]}${showType ? html`
                            <span class="muted">· ${typeLabel} (${m.typeCode})</span>` : ''}
                    </div>
                    <h1 class="title">${m.number || '—'}</h1>
                    <div class="chips">
                        <span class="chip">${m.syntax}</span>
                        ${m.profile && m.profile !== m.syntax ? html`<span class="chip">${m.profile}</span>` : ''}
                        ${m.container === 'PDF' ? html`<span class="chip">PDF/A-3</span>` : ''}
                        ${m.testDocument ? html`<span class="chip warn">${t.testDocument}</span>` : ''}
                        ${m.copy ? html`<span class="chip">${t.copyDocument}</span>` : ''}
                        ${status}
                    </div>
                </div>
                <dl class="head-facts">
                    ${m.issueDate ? html`
                        <div>
                            <dt>${t.issueDate}</dt>
                            <dd>${this.f.date(m.issueDate)}</dd>
                        </div>` : ''}
                    ${m.dueDate ? html`
                        <div>
                            <dt>${t.dueDate}</dt>
                            <dd>${this.f.date(m.dueDate)}</dd>
                        </div>` : ''}
                    ${this.isDespatch && m.delivery && (m.delivery.despatchDate || m.delivery.date) ?
                      html`
                          <div>
                              <dt>${m.delivery.despatchDate ? t.despatchDate : t.deliveryDate}</dt>
                              <dd>${this.f.date(m.delivery.despatchDate || m.delivery.date)}</dd>
                          </div>` : ''}
                    ${headline ? html`
                        <div class="total">
                            <dt>${this.isInvoice && m.totals.payable ? t.payable :
                              m.totals.taxInclusive ? t.grandTotal : t.netTotal}
                            </dt>
                            <dd>${this.money(headline)}</dd>
                        </div>` : ''}
                </dl>
                ${withActions ? html`
                    <div class="actions"
                         part="actions">
                        <button type="button"
                                class="btn"
                                data-action="print"
                                title="${t.print}">${icon('print')}<span>${t.print}</span></button>
                        <button type="button"
                                class="btn"
                                data-action="download-xml"
                                title="${t.downloadXml}">${icon('download')}<span>${t.downloadXml}</span></button>
                        ${m.pdf ? html`
                            <button type="button"
                                    class="btn"
                                    data-action="download-pdf"
                                    title="${t.downloadPdf}">${icon('download')}<span>${t.downloadPdf}</span></button>` : ''}
                    </div>` : ''}
            </header>`;
    }

    // -- parties ------------------------------------------------------------------------------------

    address(a) {
        if (!a) {
            return '';
        }
        const city = [a.postcode, a.city].filter(Boolean).join(' ');
        return html`
            <div class="address">${(a.lines || []).map(l => html`
                <div>${l}</div>`)}${city ? html`
                <div>${city}</div>` : ''}${a.region ? html`
                <div>${a.region}</div>` : ''}${a.country ? html`
                <div>${this.f.country(a.country)}</div>` : ''}
            </div>`;
    }

    id(value) {
        if (!value) {
            return '';
        }
        return value.scheme ? html`${value.value} <span class="muted">(${this.f.scheme(value.scheme)})</span>` : value.value;
    }

    party(label, p, extra) {
        if (!p) {
            return '';
        }
        const t = this.t;
        const facts = [
            [t.tradingName, p.tradingName],
            [t.vatId, p.vatId],
            [t.taxId, p.taxId],
            [t.legalId, this.id(p.legalId)],
            [t.identifier, (p.ids || []).map((id, i) => html`${i ? ', ' : ''}${this.id(id)}`)],
            [t.endpoint, this.id(p.endpoint)],
            [t.contact, p.contact ? [p.contact.name, p.contact.department].filter(Boolean).join(', ') : ''],
            [t.phone, p.contact && p.contact.phone],
            [t.email, p.contact && p.contact.email],
            [t.legalInfo, p.legalInfo],
            ...(extra || [])
        ];
        return html`
            <section class="card party">
                <h3>${label}</h3>
                <div class="party-name">${p.name || '—'}</div>
                ${this.address(p.address)}
                ${this.facts(facts, 'small')}
            </section>`;
    }

    facts(rows, cls) {
        const visible = rows.filter(([, value]) => value !== undefined && value !== '' && value !== null &&
            !(Array.isArray(value) && !value.length) &&
            !(value instanceof Html && !value.value.trim()));
        if (!visible.length) {
            return '';
        }
        return html`
            <dl class="facts ${cls || ''}">${visible.map(([label, value]) => html`
                <dt>${label}</dt>
                <dd>${value}</dd>`)}
            </dl>`;
    }

    // -- overview -----------------------------------------------------------------------------------

    overview() {
        const m = this.m;
        const t = this.t;
        const ref = m.references || {};
        const delivery = m.delivery || {};
        return html`
            <div class="grid two">
                ${this.party(t.seller, m.seller)}
                ${this.party(this.isDespatch && !m.buyer ? t.shipTo : t.buyer, m.buyer)}
            </div>
            <div class="grid two">
                <section class="card">
                    <h3>${t.kind[m.kind]}</h3>
                    ${this.facts([
                        [t.number, m.number],
                        [t.documentStatus, [m.testDocument ? t.testDocument : '', m.copy ? t.copyDocument : '']
                            .filter(Boolean).join(' · ')],
                        [t.issueDate, this.f.date(m.issueDate)],
                        [t.period, [this.f.period(m.period), m.periodDescription].filter(Boolean).join(' · ')],
                        [t.effectiveDate, this.f.date(m.effectiveDate)],
                        [t.deliveryDate, this.f.date(delivery.date)],
                        [t.requestedDelivery, this.f.period(delivery.requested)],
                        [t.despatchDate, this.f.date(delivery.despatchDate)],
                        [t.estimatedDelivery, this.f.period(delivery.estimated)],
                        [t.buyerReference, ref.buyerReference],
                        [t.order, ref.order],
                        [t.project, ref.project],
                        [t.contract, ref.contract],
                        [t.currency, m.currency ? html`${m.currency}
                        <span class="muted">(${this.f.display('currency', m.currency)})</span>` : '']
                    ])}
                </section>
                ${this.isInvoice ? this.paymentCard() : (m.shipTo ? this.party(t.shipTo, m.shipTo) : '')}
            </div>
            ${m.totals ? this.totalsBlock() : ''}
            ${this.notes()}
            <p class="disclaimer">${t.disclaimer}</p>`;
    }

    paymentCard() {
        const m = this.m;
        const t = this.t;
        const p = m.payment || {};
        if (!p.means && !p.terms && !p.additionalTerms && !p.advancePayments && !p.remittance && !m.dueDate) {
            return m.shipTo ? this.party(t.shipTo, m.shipTo) : '';
        }
        return html`
            <section class="card">
                <h3>${t.payment}</h3>
                ${(p.means || [{}]).map(pm => this.facts([
                    [t.paymentMeans, this.paymentMeans(pm)],
                    ...(pm.accounts || []).flatMap(a => [[t.iban, html`
                        <span class="mono">${this.f.iban(a.iban)}</span>`], [t.bic, a.bic], [t.accountName, a.name]]),
                    [t.card, pm.card ? [pm.card.number, pm.card.network, pm.card.holder].filter(Boolean).join(' · ') : ''],
                    [t.debitAccount, pm.debitAccount ? html`<span class="mono">${this.f.iban(pm.debitAccount)}</span>` : ''],
                    [t.debitAccountName, pm.debitAccountName],
                    [t.debitBic, pm.debitBic]
                ]))}
                ${this.facts([
                    [t.remittance, p.remittance],
                    [t.dueDate, this.f.date(m.dueDate)],
                    [t.mandate, p.mandate],
                    [t.creditorId, p.creditorId],
                    [t.terms, p.terms ? html`<span class="pre">${p.terms}</span>` : ''],
                    ...(p.additionalTerms || []).map(term => [t.additionalTerm, this.paymentTerm(term)]),
                    ...(p.advancePayments || []).map(advance => [t.advancePayment, [this.money(advance.amount),
                        advance.date ? this.f.date(advance.date) : '',
                        advance.invoice ? `${t.advanceInvoice} ${advance.invoice}` : '',
                        advance.taxAmount !== undefined ? `${t.includedVat} ${advance.taxRate !== undefined ? this.f.percent(advance.taxRate) + ' ' : ''}${this.money(advance.taxAmount)}` : '']
                        .filter(Boolean).join(' · ')])
                ])}
            </section>`;
    }

    /**
     * A further payment term in one line: text, due date, partial amount, discount, and penalty.
     */
    paymentTerm(term) {
        const t = this.t;
        const adjustment = (label, percent, amount) => percent !== undefined || amount !== undefined ?
            `${label} ${[percent !== undefined ? this.f.percent(percent) : '', amount !== undefined ? this.money(amount) : '']
                .filter(Boolean).join(' / ')}` : '';
        return [term.description, term.dueDate ? `${t.dueDate} ${this.f.date(term.dueDate)}` : '',
            term.partialAmount !== undefined ? `${t.partialPayment} ${this.money(term.partialAmount)}` : '',
            adjustment(t.discount, term.discountPercent, term.discountAmount),
            adjustment(t.penalty, term.penaltyPercent, term.penaltyAmount)]
            .filter(Boolean).join(' · ');
    }

    paymentMeans(pm) {
        if (!pm.code) {
            return pm.text;
        }
        const label = this.f.paymentMeans(pm.code);
        const known = label !== pm.code;
        return html`${known ? label : pm.text || pm.code} <span class="muted">(${pm.code}
                                                                              )</span>${known && pm.text && pm.text !== label ?
          html`
              <div class="muted small">${pm.text}</div>` : ''}`;
    }

    totalsBlock() {
        const m = this.m;
        const t = this.t;
        const s = m.totals;
        const rows = [];
        const row = (label, value, cls, sign) => {
            if (value !== undefined) {
                rows.push(html`
                    <tr class="${cls || ''}">
                        <th scope="row">${sign ? html`<span class="sign">${sign}</span>` : ''}${label}</th>
                        <td>${this.money(value)}</td>
                    </tr>`);
            }
        };
        const nonZero = v => v !== undefined && Number(v) !== 0;
        row(t.lineTotal, s.lineTotal);
        if (nonZero(s.allowanceTotal)) {
            row(t.allowances, s.allowanceTotal, '', '−');
        }
        if (nonZero(s.chargeTotal)) {
            row(t.charges, s.chargeTotal, '', '+');
        }
        // EXTENDED logistics service charges are part of the charges (BT-108).
        for (const charge of m.logisticsCharges || []) {
            row(html`<span class="muted">${t.including} ${charge.description || t.logisticsCharge}${charge.taxRate !== undefined ?
                html` (${t.vat} ${this.f.percent(charge.taxRate)})` : ''}</span>`, charge.amount, 'tax');
        }
        row(t.netTotal, s.taxExclusive, 'sub');
        for (const tax of m.taxes || []) {
            if (tax.amount !== undefined) {
                rows.push(html`
                    <tr class="tax">
                        <th scope="row"><span class="sign">+</span>${t.vat}
                                                                   ${tax.rate !== undefined ? this.f.percent(tax.rate) : this.f.taxCategory(tax.category)}
                            <span class="muted">${t.vatOf} ${this.money(tax.taxable)}</span></th>
                        <td>${this.money(tax.amount)}</td>
                    </tr>`);
            }
        }
        if (!(m.taxes || []).length) {
            row(t.taxTotal, s.taxTotal, '', '+');
        }
        if (s.taxTotalTaxCurrency && m.taxCurrency) {
            rows.push(html`
                <tr class="tax">
                    <th scope="row">${t.taxTotal} (${m.taxCurrency})</th>
                    <td>${this.f.amount(s.taxTotalTaxCurrency, m.taxCurrency)}</td>
                </tr>`);
            if (m.exchangeRate && m.exchangeRate.rate) {
                rows.push(html`
                    <tr class="tax">
                        <th scope="row">${t.exchangeRate}</th>
                        <td>${[this.f.number(m.exchangeRate.rate), this.f.date(m.exchangeRate.date)].filter(Boolean).join(' · ')}</td>
                    </tr>`);
            }
        }
        row(t.grossTotal, s.taxInclusive, 'sub');
        if (nonZero(s.prepaid)) {
            row(t.prepaid, s.prepaid, '', '−');
        }
        if (nonZero(s.rounding)) {
            row(t.rounding, s.rounding, '', '±');
        }
        // EXTENDED financial adjustments change the amount due only.
        for (const adjustment of m.financialAdjustments || []) {
            row(adjustment.reason || t.financialAdjustment, adjustment.amount, '', '±');
        }
        if (this.isInvoice) {
            row(t.payable, s.payable, 'grand');
        }
        return html`
            <section class="totals-wrap">
                <table class="totals"
                       part="totals">
                    <tbody>${rows}</tbody>
                </table>
            </section>`;
    }

    notes() {
        const notes = this.m.notes || [];
        if (!notes.length) {
            return '';
        }
        return html`
            <section class="card">
                <h3>${this.t.notes}</h3>
                ${notes.map(n => html`
                    <div class="note">${n.subject ? html`
                        <div class="muted small">${this.f.noteSubject(n.subject)} (${n.subject})</div>` : ''}
                        <div class="pre">${n.text}</div>
                    </div>`)}
            </section>`;
    }

    // -- lines --------------------------------------------------------------------------------------

    lines() {
        const t = this.t;
        const head = this.isDespatch ?
            [t.pos, t.item, [t.delivered, 'num'], [t.outstanding, 'num'], [t.orderLine, 'num']] :
            [t.pos, t.item, [t.quantity, 'num'], [t.unitPrice, 'num'], [t.vat, 'num'], [t.amount, 'num']];
        return html`
            <div class="table-wrap">
                <table class="lines"
                       part="lines">
                    <thead>
                    <tr>
                        <th class="toggle-col"></th>
                        ${head.map(h => Array.isArray(h) ? html`
                            <th class="${h[1]}">${h[0]}</th>` : html`
                            <th>${h}</th>`)}
                    </tr>
                    </thead>
                    <tbody>${this.lineRows(this.m.lines || [], 0)}</tbody>
                </table>
            </div>`;
    }

    lineRows(lines, depth) {
        return lines.map(line => [this.lineRow(line, depth), this.lineRows(line.subLines || [], depth + 1)]);
    }

    lineRow(l, depth) {
        const t = this.t;
        const f = this.f;
        const findings = (l.index && this.findingsByLine.get(l.index)) || [];
        const key = 'line-' + (l.index || l.id);
        const details = this.lineDetails(l);
        const quantity = l.quantity !== undefined ? html`
            <span>${f.number(l.quantity)} <span class="unit">${f.unit(l.unit)}</span></span>` : '';
        const badge = findings.length ?
            html`
                <button type="button"
                        class="badge ${findings.some(x => x.severity === 'FATAL' || x.severity === 'ERROR') ? 'bad' : 'warn'}"
                        data-action="tab"
                        data-tab="validation"
                        title="${t.findings}">${icon('alert')}${findings.length}
                </button>` : '';
        const name = html`
            <div class="item-name"
                 data-depth="${Math.min(depth, 4)}">${l.name || '—'} ${badge}
            </div>
            ${l.description ? html`
                <div class="item-desc"
                     data-depth="${Math.min(depth, 4)}">${l.description}
                </div>` : ''}
            ${l.sellerId || l.standardId ? html`
                <div class="item-ids muted"
                     data-depth="${Math.min(depth, 4)}">
                    ${[l.sellerId, l.standardId && l.standardId.value].filter(Boolean).join(' · ')}
                </div>` : ''}`;
        const cells = this.isDespatch ?
            [html`
                <td class="num"
                    data-label="${t.delivered}">${quantity}
                </td>`,
                html`
                    <td class="num"
                        data-label="${t.outstanding}">${l.outstanding !== undefined ? f.number(l.outstanding) : ''}
                    </td>`,
                html`
                    <td class="num"
                        data-label="${t.orderLine}">${l.orderLine || ''}
                    </td>`] :
            [html`
                <td class="num"
                    data-label="${t.quantity}">${quantity}
                </td>`,
                html`
                    <td class="num"
                        data-label="${t.unitPrice}">${l.netPrice !== undefined ? html`
                        <span>${this.money(l.netPrice)}${l.baseQuantity && Number(l.baseQuantity) !== 1 ? html`
                            <span class="muted small block">${t.perQuantity} ${f.number(l.baseQuantity)} ${f.unit(l.baseUnit || l.unit)}</span>` : ''}</span>` : ''}
                    </td>`,
                html`
                    <td class="num"
                        data-label="${t.vat}">${l.taxRate !== undefined ? f.percent(l.taxRate) : (l.taxCategory || '')}
                    </td>`,
                html`
                    <td class="num strong"
                        data-label="${t.amount}">${l.total !== undefined ? this.money(l.total) : ''}
                    </td>`];
        const columns = this.isDespatch ? 6 : 7;
        return html`
            <tr class="line ${depth ? 'sub' : ''} ${findings.length ? 'has-findings' : ''}"
                id="${key}"
                data-line="${l.index || ''}">
                <td class="toggle-col">${details ? html`
                    <button type="button"
                            class="toggle"
                            data-action="toggle-line"
                            data-target="${key}-details"
                            aria-expanded="false"
                            aria-label="Details">${icon('chevron')}
                    </button>` : ''}
                </td>
                <td class="pos"
                    data-label="${t.pos}">${l.id || ''}
                </td>
                <td>${name}</td>
                ${cells}
            </tr>
            ${details ? html`
                <tr class="line-details"
                    id="${key}-details"
                    hidden>
                    <td></td>
                    <td colspan="${columns - 1}">${details}</td>
                </tr>` : ''}`;
    }

    lineDetails(l) {
        const t = this.t;
        const f = this.f;
        const rows = [
            [t.lineNote, l.note ? html`<span class="pre">${l.note}</span>` : ''],
            [t.sellerItemId, l.sellerId],
            [t.buyerItemId, l.buyerId],
            [t.industryItemId, l.industryId],
            [t.standardItemId, this.id(l.standardId)],
            [t.classification, (l.classifications || []).map((c, i) => html`${i ? ', ' : ''}${c.value}${c.scheme ? html`
                <span class="muted">(${c.scheme})</span>` : ''}`)],
            [t.origin, f.country(l.origin)],
            [t.grossPrice, l.grossPrice !== undefined ? this.money(l.grossPrice) : ''],
            [t.priceDiscount, l.priceDiscount !== undefined ? this.money(l.priceDiscount) : ''],
            [t.requested, l.requestedQuantity !== undefined ? html`${f.number(l.requestedQuantity)} ${f.unit(l.unit)}` : ''],
            [t.vat, l.taxCategory ? html`${f.taxCategory(l.taxCategory)} <span class="muted">(${l.taxCategory}
                                                                                             )</span>${l.taxRate !== undefined ? html` · ${f.percent(l.taxRate)}` : ''}` : ''],
            [t.period, f.period(l.period)],
            [t.orderLine, this.isDespatch ? '' : l.orderLine],
            [t.lineDeliveryDate, f.date(l.deliveryDate)],
            [t.deliveryNote, l.deliveryNote],
            [t.despatchAdvice, l.despatchAdvice],
            [t.object, l.objectIds ? l.objectIds.join(', ') : l.objectId],
            [t.accountingCost, l.accountingCost],
            [t.status, l.status ? (t.subtypes[l.status] || l.status) : ''],
            // EXTENDED product, delivery, and reference details
            [t.productId, l.productId],
            [t.model, l.model],
            [t.brand, l.brand],
            [t.manufacturer, l.manufacturer],
            [t.batch, (l.batches || []).join(', ')],
            [t.includedProducts, (l.includedProducts || []).map(i => [i.quantity !== undefined ? `${f.number(i.quantity)} ${f.unit(i.unit)}` : '',
                i.name, i.id].filter(Boolean).join(' ')).join(', ')],
            [t.chargeFree, l.chargeFreeQuantity !== undefined ? `${f.number(l.chargeFreeQuantity)} ${f.unit(l.unit)}` : ''],
            [t.packages, l.packageQuantity !== undefined ? [f.number(l.packageQuantity),
                l.perPackageQuantity !== undefined ? `× ${f.number(l.perPackageQuantity)} ${f.unit(l.unit)}` : ''].filter(Boolean).join(' ') : ''],
            [t.deliveryTerms, l.deliveryTerms],
            [t.itemSeller, l.itemSeller ? [l.itemSeller.name, l.itemSeller.legalId && l.itemSeller.legalId.value, l.itemSeller.vatId]
                .filter(Boolean).join(' · ') : ''],
            [t.shipTo, l.shipTo ? [l.shipTo.name, l.shipTo.address && l.shipTo.address.city].filter(Boolean).join(', ') : ''],
            [t.ultimateShipTo, l.ultimateShipTo ? [l.ultimateShipTo.name, l.ultimateShipTo.address && l.ultimateShipTo.address.city].filter(Boolean).join(', ') : ''],
            [t.salesOrder, l.salesOrder],
            [t.quotation, l.quotation],
            [t.contract, l.contract],
            [t.receivingAdvice, l.receivingAdvice],
            [t.precedingInvoice, l.precedingInvoice],
            [t.ultimateCustomerOrders, (l.ultimateCustomerOrders || []).join(', ')],
            [t.lineReferences, (l.references || []).map(r => r.scheme ? `${r.id} (${r.scheme})` : r.id).join(', ')],
            [t.priceAdjustments, (l.priceAdjustments || []).map(a => `${a.reason || (a.charge ? t.charge : t.allowance)}: ${a.charge ? '' : '−'}${this.money(a.amount)}`).join(', ')],
            [t.lineVat, l.lineVat !== undefined ? this.money(l.lineVat) : ''],
            [t.lineGross, l.lineGross !== undefined ? this.money(l.lineGross) : ''],
            [t.outstanding, this.isDespatch && l.outstandingReason ? l.outstandingReason : ''],
            ...(l.attributes || []).map(a => [a.name, a.value]),
            ...(l.instances || []).flatMap(i => [[t.batch, i.batch], [t.serial, i.serial], [t.expiry, f.date(i.expiry)]])
        ];
        const facts = this.facts(rows, 'small');
        const allowances = (l.allowanceCharges || []).length ? this.allowanceTable(l.allowanceCharges) : '';
        const findings = (l.index && this.findingsByLine.get(l.index)) || [];
        if (!facts && !allowances && !findings.length) {
            return '';
        }
        return html`${facts}${allowances}${findings.length ? html`
            <ul class="findings compact">${findings.map(x => this.finding(x, false))}</ul>` : ''}`;
    }

    // -- details ------------------------------------------------------------------------------------

    details() {
        const m = this.m;
        const t = this.t;
        const ref = m.references || {};
        const delivery = m.delivery || {};
        const references = this.facts([
            [t.buyerReference, ref.buyerReference],
            [t.order, ref.order],
            [t.salesOrder, ref.salesOrder],
            [t.contract, ref.contract],
            [t.quotation, ref.quotation],
            [t.project, ref.project],
            [t.despatchAdvice, ref.despatchAdvice],
            [t.receivingAdvice, ref.receivingAdvice],
            [t.deliveryNote, ref.deliveryNote],
            [t.ultimateCustomerOrders, ref.ultimateCustomerOrders],
            [t.invoiceIssuer, ref.invoiceIssuer],
            [t.tender, ref.tender],
            [t.object, ref.object],
            [t.accountingCost, ref.accountingCost],
            [t.precedingInvoices, (ref.precedingInvoices || []).map((p, i) => html`${i ? ', ' : ''}${p.id}${p.date ? html`
                <span class="muted">(${this.f.date(p.date)})</span>` : ''}`)]
        ]);
        const deliveryFacts = this.facts([
            [t.deliveryDate, this.f.date(delivery.date)],
            [t.requestedDelivery, this.f.period(delivery.requested)],
            [t.despatchDate, this.f.date(delivery.despatchDate)],
            [t.estimatedDelivery, this.f.period(delivery.estimated)],
            [t.shipment, delivery.shipment],
            [t.deliveryTerms, [delivery.terms, this.f.country(delivery.termsCountry)].filter(Boolean).join(' · ')],
            [t.transportMode, delivery.transportMode ? html`${t.transportModes[delivery.transportMode] || delivery.transportMode} <span class="muted">(${delivery.transportMode})</span>` : '']
        ]);
        const parties = [
            this.party(t.payee, m.payee),
            this.party(t.taxRepresentative, m.taxRepresentative),
            this.party(t.invoicee, m.invoicee),
            this.party(t.sellerSupplier, m.sellerSupplier),
            this.party(t.shipFrom, m.shipFrom),
            this.isInvoice ? this.party(t.shipTo, m.shipTo) : '',
            this.party(t.ultimateShipTo, m.ultimateShipTo),
            this.party(t.salesAgent, m.salesAgent),
            this.party(t.buyerAgent, m.buyerAgent),
            this.party(t.buyerTaxRepresentative, m.buyerTaxRepresentative),
            this.party(t.productEndUser, m.productEndUser),
            this.party(t.invoicer, m.invoicer),
            this.party(t.payer, m.payer)
        ].filter(p => p && String(p).trim());
        return html`
            ${(m.taxes || []).length ? html`
                <section class="card"><h3>${t.vatBreakdown}</h3>${this.taxTable()}</section>` : ''}
            ${(m.allowanceCharges || []).length ? html`
                <section class="card"><h3>${t.allowancesCharges}</h3>${this.allowanceTable(m.allowanceCharges)}</section>` : ''}
            <div class="grid two">
                ${references ? html`
                    <section class="card"><h3>${t.references}</h3>${references}</section>` : ''}
                ${deliveryFacts ? html`
                    <section class="card"><h3>${t.delivery}</h3>${deliveryFacts}</section>` : ''}
                ${!this.isInvoice || !m.payment ? '' : this.paymentCard()}
                <section class="card">
                    <h3>${t.process}</h3>
                    ${this.facts([
                        [t.syntax, m.syntax],
                        [t.container, m.container === 'PDF' ? html`PDF/A-3 <span class="muted">(${m.xmlFileName}
                                                                                               )</span>` : 'XML'],
                        [t.specification, m.specification ? html`<span class="mono break">${m.specification}</span>` : ''],
                        [t.businessProcess, m.businessProcess ? html`
                            <span class="mono break">${m.businessProcess}</span>` : ''],
                        [t.documentType, m.typeCode ? html`${this.f.documentType(m.typeCode)} <span class="muted">(${m.typeCode}
                                                                                                                  )</span>` : ''],
                        [t.purpose, m.purposeCode],
                        [t.currency, m.currency],
                        [t.taxCurrency, m.taxCurrency],
                        [t.taxPointDate, this.f.date(m.taxPointDate)]
                    ])}
                </section>
            </div>
            ${parties.length ? html`<h2 class="section-title">${t.parties}</h2>
            <div class="grid two">${parties}</div>` : ''}`;
    }

    taxTable() {
        const t = this.t;
        return html`
            <div class="table-wrap">
                <table class="data">
                    <thead>
                    <tr>
                        <th>${t.category}</th>
                        <th class="num">${t.rate}</th>
                        <th class="num">${t.taxable}</th>
                        <th class="num">${t.vat}</th>
                        <th>${t.exemption}</th>
                    </tr>
                    </thead>
                    <tbody>${this.m.taxes.map(x => html`
                        <tr>
                            <td>${this.f.taxCategory(x.category)} <span class="muted">(${x.category})</span></td>
                            <td class="num">${x.rate !== undefined ? this.f.percent(x.rate) : ''}</td>
                            <td class="num">${this.money(x.taxable)}</td>
                            <td class="num">${this.money(x.amount)}</td>
                            <td>${[x.exemptionCode, x.exemptionReason].filter(Boolean).join(' – ')}</td>
                        </tr>`)}
                    </tbody>
                </table>
            </div>`;
    }

    allowanceTable(list) {
        const t = this.t;
        return html`
            <div class="table-wrap">
                <table class="data">
                    <thead>
                    <tr>
                        <th></th>
                        <th>${t.reason}</th>
                        <th class="num">${t.base}</th>
                        <th class="num">${t.percent}</th>
                        <th class="num">${t.vat}</th>
                        <th class="num">${t.amount}</th>
                    </tr>
                    </thead>
                    <tbody>${list.map(a => html`
                        <tr>
                            <td>
                                <span class="pill ${a.charge ? 'charge' : 'allowance'}">${a.charge ? t.charge : t.allowance}</span>
                            </td>
                            <td>${[a.reason, a.reasonCode ? html`<span class="muted">(${a.reasonCode}
                                                                                     )</span>` : ''].filter(Boolean).map((x, i) => html`${i ? ' ' : ''}${x}`)}
                            </td>
                            <td class="num">${a.base !== undefined ? this.money(a.base) : ''}</td>
                            <td class="num">${a.percent !== undefined ? this.f.percent(a.percent) : ''}</td>
                            <td class="num">${a.taxRate !== undefined ? this.f.percent(a.taxRate) : (a.taxCategory || '')}</td>
                            <td class="num strong">${a.charge ? '' : '−'}${this.money(a.amount)}</td>
                        </tr>`)}
                    </tbody>
                </table>
            </div>`;
    }

    // -- attachments --------------------------------------------------------------------------------

    attachments(previews) {
        const t = this.t;
        const m = this.m;
        const items = (m.attachments || []).map((a, i) => {
            const previewable = a.data && /^(image\/(png|jpe?g|gif|webp)|application\/pdf)$/.test(a.mime || '');
            const open = previews.get(i);
            return html`
                <li class="attachment">
                    <div class="att-icon">${icon(a.uri && !a.data ? 'link' : 'file')}</div>
                    <div class="att-body">
                        <div class="att-name">${a.name || a.id || a.uri || '—'}</div>
                        <div class="muted small">${[a.description, a.id && a.id !== a.name ? a.id : '', a.mime,
                            a.data ? this.f.size(a.data.length) : '', a.source === 'pdf' ? t.embeddedIn : '',
                            a.relationship].filter(Boolean).join(' · ')}
                        </div>
                        ${a.uri ? html`
                            <div class="small">${/^https?:\/\//i.test(a.uri) ?
                              html`<a href="${a.uri}"
                                      target="_blank"
                                      rel="noopener noreferrer nofollow">${a.uri}</a>` :
                              html`<span class="mono break">${a.uri}</span>`}
                            </div>` : ''}
                    </div>
                    <div class="att-actions">
                        ${previewable ? html`
                            <button type="button"
                                    class="btn small"
                                    data-action="preview"
                                    data-index="${i}">${open ? t.hidePreview : t.preview}
                            </button>` : ''}
                        ${a.data ? html`
                            <button type="button"
                                    class="btn small"
                                    data-action="download-attachment"
                                    data-index="${i}">${icon('download')}<span>${t.download}</span></button>` : ''}
                    </div>
                    ${open ? (a.mime === 'application/pdf' ?
                      html`
                          <iframe class="preview pdf"
                                  src="${open}"
                                  title="${a.name || ''}"></iframe>` :
                      html`<img class="preview"
                                src="${open}"
                                alt="${a.name || ''}">`) : ''}
                </li>`;
        });
        const pdfOpen = previews.get('pdf');
        const pdf = m.pdf ? html`
            <li class="attachment">
                <div class="att-icon">${icon('file')}</div>
                <div class="att-body">
                    <div class="att-name">${t.originalPdf}</div>
                    <div class="muted small">PDF/A-3 · ${this.f.size(m.pdf.length)}</div>
                </div>
                <div class="att-actions">
                    <button type="button"
                            class="btn small"
                            data-action="preview"
                            data-index="pdf">${pdfOpen ? t.hidePreview : t.preview}
                    </button>
                    <button type="button"
                            class="btn small"
                            data-action="download-pdf">${icon('download')}<span>${t.download}</span></button>
                </div>
                ${pdfOpen ? html`
                    <iframe class="preview pdf"
                            src="${pdfOpen}"
                            title="${t.originalPdf}"></iframe>` : ''}
            </li>` : '';
        if (!items.length && !pdf) {
            return html`<p class="muted">${t.noAttachments}</p>`;
        }
        return html`
            <ul class="attachments">${pdf}${items}</ul>`;
    }

    // -- validation ---------------------------------------------------------------------------------

    validation() {
        const r = this.r;
        const t = this.t;
        const counts = {};
        for (const stage of r.stages) {
            for (const msg of stage.messages) {
                counts[msg.severity] = (counts[msg.severity] || 0) + 1;
            }
        }
        return html`
            <section class="card verdict ${r.valid ? 'ok' : 'bad'}">
                <div class="verdict-icon">${icon(r.valid ? 'check' : 'alert')}</div>
                <div>
                    <div class="verdict-title">${r.valid ? t.valid : t.invalid}</div>
                    <div class="muted small">
                        ${[r.profile, r.generator, r.created ? new Date(r.created).toLocaleString(this.f.locale) : ''].filter(Boolean).join(' · ')}
                    </div>
                </div>
                <div class="verdict-counts">${SEVERITIES.filter(s => counts[s]).map(s => html`
                    <span class="pill sev-${s.toLowerCase()}">${counts[s]} ${s === 'FATAL' ? 'FATAL' : t.severity[s]}</span>`)}
                </div>
            </section>
            ${r.stages.map(stage => html`
                <section class="card">
                    <h3>${t.stage}: ${stage.name} ${icon(stage.valid ? 'check' : 'alert')}</h3>
                    ${stage.messages.length ? html`
                        <ul class="findings">${stage.messages.map(x => this.finding(x, true))}</ul>` : html`<p class="muted">
                        ${t.noFindings}</p>`}
                </section>`)}`;
    }

    finding(x, withLink) {
        const t = this.t;
        return html`
            <li class="finding sev-${x.severity.toLowerCase()}">
                <span class="pill sev-${x.severity.toLowerCase()}">${t.severity[x.severity] || x.severity}</span>
                <div class="finding-body">
                    ${x.rule ? html`<code class="rule">${x.rule}</code>` : ''}
                    <span>${(x.text || '').replace(/^\[[^\]]+\]\s*-?\s*/, '')}</span>
                    ${x.location ? html`
                        <div class="muted small mono break">${x.location.replace(/Q\{[^}]*\}/g, '')}</div>` : ''}
                </div>
                ${withLink && x.line ? html`
                    <button type="button"
                            class="btn small"
                            data-action="goto-line"
                            data-line="${x.line}">${t.line} ${x.line}
                    </button>` : ''}
            </li>`;
    }

    // -- xml ----------------------------------------------------------------------------------------

    xml() {
        const doc = parseXml(this.m.xml);
        return html`
            <div class="xml-actions">
                <button type="button"
                        class="btn small"
                        data-action="copy-xml">${icon('copy')}<span>${this.t.copy}</span></button>
                <button type="button"
                        class="btn small"
                        data-action="download-xml">${icon('download')}<span>${this.t.download}</span></button>
            </div>
            <pre class="xml"
                 part="xml"><code>${new Html(prettyXml(doc.documentElement, 0))}</code></pre>`;
    }
}

function prettyXml(node, depth) {
    const pad = '  '.repeat(depth);
    if (node.nodeType === 8) {
        return pad + '<span class="x-c">&lt;!--' + esc(node.nodeValue) + '--&gt;</span>\n';
    }
    if (node.nodeType !== 1) {
        return '';
    }
    const attrs = [...node.attributes].map(a => ' <span class="x-a">' + esc(a.name) + '</span>=<span class="x-v">"' + esc(a.value) + '"</span>').join('');
    const open = '<span class="x-t">&lt;' + esc(node.nodeName) + '</span>' + attrs;
    const kids = [...node.childNodes].filter(n => n.nodeType === 1 || n.nodeType === 8);
    const text = [...node.childNodes].filter(n => n.nodeType === 3 || n.nodeType === 4).map(n => n.nodeValue).join('').trim();
    if (!kids.length) {
        if (!text) {
            return pad + open + '<span class="x-t">/&gt;</span>\n';
        }
        const shown = text.length > 400 ? text.slice(0, 120) + ' … (' + text.length + ')' : text;
        return pad + open + '<span class="x-t">&gt;</span><span class="x-x">' + esc(shown) + '</span><span class="x-t">&lt;/' + esc(node.nodeName) + '&gt;</span>\n';
    }
    return pad + open + '<span class="x-t">&gt;</span>\n' +
        kids.map(k => prettyXml(k, depth + 1)).join('') +
        pad + '<span class="x-t">&lt;/' + esc(node.nodeName) + '&gt;</span>\n';
}

// ------------------------------------------------------------------------------------------------
// Styles
// ------------------------------------------------------------------------------------------------

const CSS = `
:host {
  --p-navy: #01244f;
  --p-teal: #019abc;
  --p-accent: var(--paniolo-accent, #017a96);
  --p-bg: var(--paniolo-background, #ffffff);
  --p-surface: var(--paniolo-surface, #f5f7fa);
  --p-border: var(--paniolo-border, #dfe4eb);
  --p-text: var(--paniolo-text, #14202e);
  --p-heading: var(--paniolo-heading, var(--p-navy));
  --p-muted: var(--paniolo-muted, #5d6b7c);
  --p-ok: #12805c;
  --p-ok-bg: #e5f5ee;
  --p-warn: #9a5b00;
  --p-warn-bg: #fdf1dc;
  --p-bad: #b42318;
  --p-bad-bg: #fdeceb;
  --p-info: #2459a8;
  --p-info-bg: #e8f0fb;
  --p-radius: var(--paniolo-radius, 10px);
  display: block;
  container-type: inline-size;
  color: var(--p-text);
  background: var(--p-bg);
  font-family: var(--paniolo-font-family, ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif);
  font-size: var(--paniolo-font-size, 14px);
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
}
:host([hidden]) { display: none; }
:host([theme="dark"]) {
  --p-navy: #e2ecf5;
  --p-accent: var(--paniolo-accent, #3cc3e0);
  --p-bg: var(--paniolo-background, #10161e);
  --p-surface: var(--paniolo-surface, #18212c);
  --p-border: var(--paniolo-border, #2a3646);
  --p-text: var(--paniolo-text, #e6ebf1);
  --p-muted: var(--paniolo-muted, #93a1b3);
  --p-ok: #4ccf9b; --p-ok-bg: #11322a; --p-warn: #f0b552; --p-warn-bg: #3a2b12;
  --p-bad: #ff8a7f; --p-bad-bg: #3d1a18; --p-info: #8ab4f8; --p-info-bg: #172a46;
}
@media (prefers-color-scheme: dark) {
  :host([theme="auto"]) {
    --p-navy: #e2ecf5;
    --p-accent: var(--paniolo-accent, #3cc3e0);
    --p-bg: var(--paniolo-background, #10161e);
    --p-surface: var(--paniolo-surface, #18212c);
    --p-border: var(--paniolo-border, #2a3646);
    --p-text: var(--paniolo-text, #e6ebf1);
    --p-muted: var(--paniolo-muted, #93a1b3);
    --p-ok: #4ccf9b; --p-ok-bg: #11322a; --p-warn: #f0b552; --p-warn-bg: #3a2b12;
    --p-bad: #ff8a7f; --p-bad-bg: #3d1a18; --p-info: #8ab4f8; --p-info-bg: #172a46;
  }
}
* { box-sizing: border-box; }
.root { display: flex; flex-direction: column; gap: 16px; }
.icon { width: 1.1em; height: 1.1em; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; flex: none; vertical-align: -0.15em; }
.muted { color: var(--p-muted); }
.small { font-size: 0.86em; }
.strong { font-weight: 600; }
.block { display: block; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 0.92em; }
.break { overflow-wrap: anywhere; }
.pre { white-space: pre-line; }
h1, h2, h3 { color: var(--p-heading); margin: 0; }
h3 { font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.06em; color: var(--p-muted); font-weight: 600; margin-bottom: 8px; display: flex; gap: 6px; align-items: center; }
.section-title { font-size: 1rem; margin: 8px 0 -4px; }

.head { display: grid; grid-template-columns: 1fr auto; grid-template-areas: "main actions" "facts facts"; gap: 12px 24px;
        padding: 20px; border: 1px solid var(--p-border); border-radius: var(--p-radius); background: var(--p-surface);
        border-top: 4px solid var(--p-teal); }
.head-main { grid-area: main; min-width: 0; }
.eyebrow { font-size: 0.9em; color: var(--p-accent); font-weight: 600; }
.title { font-size: 1.7rem; line-height: 1.2; margin: 2px 0 8px; overflow-wrap: anywhere; }
.chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chip { display: inline-flex; align-items: center; gap: 4px; padding: 2px 10px; border-radius: 999px; font-size: 0.8em; font-weight: 600;
        border: 1px solid var(--p-border); background: var(--p-bg); color: var(--p-text); }
.chip.ok { color: var(--p-ok); background: var(--p-ok-bg); border-color: transparent; cursor: pointer; }
.chip.bad { color: var(--p-bad); background: var(--p-bad-bg); border-color: transparent; cursor: pointer; }
.chip.warn { color: var(--p-warn); background: var(--p-warn-bg); border-color: transparent; }
.head-facts { grid-area: facts; display: flex; flex-wrap: wrap; gap: 8px 32px; margin: 0; align-items: flex-end; }
.head-facts dt { font-size: 0.78em; color: var(--p-muted); text-transform: uppercase; letter-spacing: 0.05em; }
.head-facts dd { margin: 0; font-weight: 600; font-size: 1.05em; }
.head-facts .total { margin-left: auto; text-align: right; }
.head-facts .total dd { font-size: 1.6rem; color: var(--p-heading); font-variant-numeric: tabular-nums; }
.actions { grid-area: actions; display: flex; gap: 6px; align-items: flex-start; }

.btn { display: inline-flex; align-items: center; gap: 6px; font: inherit; font-size: 0.9em; font-weight: 500; cursor: pointer;
       padding: 6px 12px; border-radius: 8px; border: 1px solid var(--p-border); background: var(--p-bg); color: var(--p-text); }
.btn:hover { border-color: var(--p-accent); color: var(--p-accent); }
.btn:focus-visible, .tab:focus-visible, .toggle:focus-visible, .chip:focus-visible { outline: 2px solid var(--p-accent); outline-offset: 2px; }
.btn.small { padding: 3px 9px; font-size: 0.82em; }

.tabs { display: flex; gap: 2px; border-bottom: 1px solid var(--p-border); overflow-x: auto; scrollbar-width: thin; }
.tab { font: inherit; font-weight: 500; background: none; border: none; border-bottom: 2px solid transparent; color: var(--p-muted);
       padding: 8px 14px; cursor: pointer; white-space: nowrap; margin-bottom: -1px; display: inline-flex; gap: 6px; align-items: center; }
.tab:hover { color: var(--p-text); }
.tab[aria-selected="true"] { color: var(--p-accent); border-bottom-color: var(--p-accent); }
.count { font-size: 0.75em; background: var(--p-surface); border: 1px solid var(--p-border); border-radius: 999px; padding: 0 7px; color: var(--p-muted); }
.panel { display: flex; flex-direction: column; gap: 16px; }
.panel[hidden] { display: none; }

.grid { display: grid; gap: 16px; }
.grid.two { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.card { border: 1px solid var(--p-border); border-radius: var(--p-radius); padding: 16px; background: var(--p-bg); min-width: 0; }
.party-name { font-weight: 600; font-size: 1.05em; }
.address { margin: 2px 0 8px; }
.facts { display: grid; grid-template-columns: minmax(110px, max-content) 1fr; gap: 4px 16px; margin: 0; }
.facts dt { color: var(--p-muted); }
.facts dd { margin: 0; min-width: 0; overflow-wrap: anywhere; }
.facts.small { font-size: 0.9em; }
.facts + .facts { margin-top: 10px; padding-top: 10px; border-top: 1px dashed var(--p-border); }
.note + .note { margin-top: 10px; }
.disclaimer { font-size: 0.8em; color: var(--p-muted); margin: 0; }

.totals-wrap { display: flex; justify-content: flex-end; }
.totals { border-collapse: collapse; min-width: min(420px, 100%); font-variant-numeric: tabular-nums; }
.totals th { text-align: left; font-weight: 400; padding: 4px 16px 4px 0; }
.totals td { text-align: right; padding: 4px 0; white-space: nowrap; }
.totals .sign { display: inline-block; width: 1.2em; color: var(--p-muted); }
.totals tr.tax th, .totals tr.tax td { color: var(--p-muted); font-size: 0.92em; }
.totals tr.sub th, .totals tr.sub td { border-top: 1px solid var(--p-border); font-weight: 600; }
.totals tr.grand th, .totals tr.grand td { border-top: 2px solid var(--p-heading); font-weight: 700; font-size: 1.15em; color: var(--p-heading); padding-top: 8px; }

.table-wrap { overflow-x: auto; border: 1px solid var(--p-border); border-radius: var(--p-radius); }
.card .table-wrap { border: none; border-radius: 0; margin: 0 -16px -16px; }
table.lines, table.data { width: 100%; border-collapse: collapse; font-variant-numeric: tabular-nums; }
.lines th, .data th { text-align: left; font-size: 0.78em; text-transform: uppercase; letter-spacing: 0.05em; color: var(--p-muted);
                      font-weight: 600; padding: 10px 12px; background: var(--p-surface); border-bottom: 1px solid var(--p-border); white-space: nowrap; }
.lines td, .data td { padding: 10px 12px; border-bottom: 1px solid var(--p-border); vertical-align: top; }
.lines tbody tr:last-child td, .data tbody tr:last-child td { border-bottom: none; }
.num { text-align: right !important; white-space: nowrap; }
.pos { color: var(--p-muted); white-space: nowrap; }
.toggle-col { width: 36px; padding-right: 0 !important; }
.toggle { background: none; border: none; color: var(--p-muted); cursor: pointer; padding: 2px; border-radius: 6px; display: inline-flex; }
.toggle .icon { transition: transform 0.15s; }
.toggle[aria-expanded="true"] .icon { transform: rotate(90deg); }
.toggle:hover { color: var(--p-accent); }
.item-name { font-weight: 600; }
[data-depth="1"] { padding-left: 18px; } [data-depth="2"] { padding-left: 36px; } [data-depth="3"] { padding-left: 54px; } [data-depth="4"] { padding-left: 72px; }
.line.sub .item-name::before { content: "↳ "; color: var(--p-muted); }
.item-desc, .item-ids { font-size: 0.88em; }
.item-desc { color: var(--p-muted); white-space: pre-line; }
.unit { color: var(--p-muted); }
.line-details > td { background: var(--p-surface); }
.line.flash > td { animation: flash 1.6s ease-out; }
@keyframes flash { from { background: var(--p-warn-bg); } to { background: transparent; } }
.badge { display: inline-flex; align-items: center; gap: 3px; border: none; border-radius: 999px; padding: 0 7px; font: inherit; font-size: 0.75em; font-weight: 600; cursor: pointer; vertical-align: 0.1em; }
.badge.bad { color: var(--p-bad); background: var(--p-bad-bg); }
.badge.warn { color: var(--p-warn); background: var(--p-warn-bg); }
.pill { display: inline-block; padding: 1px 8px; border-radius: 999px; font-size: 0.78em; font-weight: 600; white-space: nowrap; }
.pill.allowance { background: var(--p-ok-bg); color: var(--p-ok); }
.pill.charge { background: var(--p-info-bg); color: var(--p-info); }
.sev-fatal.pill, .sev-error.pill { background: var(--p-bad-bg); color: var(--p-bad); }
.sev-warning.pill { background: var(--p-warn-bg); color: var(--p-warn); }
.sev-info.pill { background: var(--p-info-bg); color: var(--p-info); }

.attachments { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 10px; }
.attachment { display: grid; grid-template-columns: auto 1fr auto; gap: 12px; align-items: start; border: 1px solid var(--p-border); border-radius: var(--p-radius); padding: 12px 14px; }
.att-icon { color: var(--p-accent); font-size: 1.3em; }
.att-body { min-width: 0; }
.att-name { font-weight: 600; overflow-wrap: anywhere; }
.att-actions { display: flex; gap: 6px; flex-wrap: wrap; justify-content: flex-end; }
.preview { grid-column: 1 / -1; display: block; max-width: 100%; border: 1px solid var(--p-border); border-radius: 8px; background: #fff; }
.preview.pdf { width: 100%; height: 70vh; min-height: 420px; }
a { color: var(--p-accent); }

.verdict { display: flex; gap: 14px; align-items: center; flex-wrap: wrap; }
.verdict.ok { background: var(--p-ok-bg); border-color: transparent; }
.verdict.bad { background: var(--p-bad-bg); border-color: transparent; }
.verdict-icon { font-size: 1.8em; display: flex; }
.verdict.ok .verdict-icon { color: var(--p-ok); }
.verdict.bad .verdict-icon { color: var(--p-bad); }
.verdict-title { font-weight: 700; font-size: 1.15em; }
.verdict-counts { margin-left: auto; display: flex; gap: 6px; flex-wrap: wrap; }
.findings { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; }
.finding { display: grid; grid-template-columns: 90px 1fr auto; gap: 10px; align-items: start; padding: 10px 0; border-top: 1px solid var(--p-border); }
.finding:first-child { border-top: none; padding-top: 0; }
.findings.compact { margin-top: 10px; }
.findings.compact .finding { grid-template-columns: 90px 1fr; }
.finding .pill { justify-self: start; }
.rule { font-weight: 600; margin-right: 6px; color: var(--p-heading); }

.xml-actions { display: flex; gap: 6px; justify-content: flex-end; }
pre.xml { margin: 0; padding: 16px; overflow: auto; max-height: 75vh; border: 1px solid var(--p-border); border-radius: var(--p-radius);
          background: var(--p-surface); font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; line-height: 1.55; tab-size: 2; }
.x-t { color: var(--p-accent); }
.x-a { color: #9c4f00; }
.x-v { color: #1d7a3e; }
.x-c { color: var(--p-muted); font-style: italic; }
.x-x { color: var(--p-text); font-weight: 600; }
:host([theme="dark"]) .x-a { color: #f0b552; }
:host([theme="dark"]) .x-v { color: #7fd99a; }
@media (prefers-color-scheme: dark) { :host([theme="auto"]) .x-a { color: #f0b552; } :host([theme="auto"]) .x-v { color: #7fd99a; } }

.state { display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; gap: 10px;
         padding: 40px 20px; border: 1px dashed var(--p-border); border-radius: var(--p-radius); color: var(--p-muted); min-height: 200px; }
.state .icon { font-size: 2em; color: var(--p-accent); }
.state.error { border-style: solid; border-color: var(--p-bad); background: var(--p-bad-bg); color: var(--p-bad); }
.state.error .icon { color: var(--p-bad); }
.state.drag { border-color: var(--p-accent); background: var(--p-surface); }
.state .detail { font-size: 0.85em; color: var(--p-muted); font-family: ui-monospace, monospace; overflow-wrap: anywhere; }
.linkish { font: inherit; background: none; border: none; color: var(--p-accent); text-decoration: underline; cursor: pointer; padding: 0; }
.spinner { width: 28px; height: 28px; border-radius: 50%; border: 3px solid var(--p-border); border-top-color: var(--p-accent); animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.footer { display: flex; justify-content: flex-end; font-size: 0.75em; color: var(--p-muted); }
.footer b { color: var(--p-teal); }

@container (max-width: 720px) {
  .grid.two { grid-template-columns: minmax(0, 1fr); }
  .head { grid-template-columns: 1fr; grid-template-areas: "main" "facts" "actions"; padding: 16px; }
  .head-facts .total { margin-left: 0; text-align: left; }
  .actions .btn span { display: none; }
  .title { font-size: 1.35rem; }
  .finding { grid-template-columns: 1fr; gap: 4px; }
  .attachment { grid-template-columns: auto 1fr; }
  .att-actions { grid-column: 1 / -1; justify-content: flex-start; }
  .facts { grid-template-columns: 1fr; gap: 0 0; }
  .facts dd { margin-bottom: 6px; }
}

@container (max-width: 560px) {
  table.lines, .lines tbody { display: block; }
  .lines thead { display: none; }
  .lines tr.line { display: grid; grid-template-columns: 28px minmax(0, 1fr); column-gap: 4px; padding: 10px 12px; border-bottom: 1px solid var(--p-border); }
  .lines tr.line > td { border: none; padding: 0; grid-column: 2; }
  .lines tr.line > td.toggle-col { grid-column: 1; grid-row: 1 / span 8; width: auto; }
  .lines tr.line > td.pos { font-size: 0.8em; }
  .lines tr.line > td.pos::before { content: attr(data-label) " "; }
  .lines tr.line > td.num { display: flex; justify-content: space-between; gap: 12px; text-align: right; padding-top: 2px; }
  .lines tr.line > td.num:empty { display: none; }
  .lines tr.line > td.num::before { content: attr(data-label); color: var(--p-muted); font-weight: 400; font-size: 0.86em; }
  .lines tr.line-details { display: block; border-bottom: 1px solid var(--p-border); }
  .lines tr.line-details[hidden] { display: none; }
  .lines tr.line-details > td { display: block; border: none; }
  .lines tr.line-details > td:first-child { display: none; }
  .lines tbody tr:last-child { border-bottom: none; }
}

@media print {
  :host { --p-bg: #fff; --p-surface: #fff; --p-text: #000; --p-muted: #555; --p-border: #ccc; --p-heading: #01244f; font-size: 10.5pt; }
  .tabs, .actions, .toggle-col, .xml-actions, .att-actions, .footer, .panel[data-tab="xml"] { display: none !important; }
  .panel[hidden] { display: flex !important; }
  .line-details[hidden] { display: table-row !important; }
  .card, .head, tr { break-inside: avoid; }
  .head { border-top-width: 2px; }
  pre.xml { max-height: none; }
  .preview { display: none; }
}
:host(.print) .tabs, :host(.print) .actions, :host(.print) .toggle-col, :host(.print) .xml-actions,
:host(.print) .att-actions, :host(.print) .panel[data-tab="xml"] { display: none !important; }
:host(.print) .panel[hidden] { display: flex !important; }
:host(.print) .line-details[hidden] { display: table-row !important; }
`;

// ------------------------------------------------------------------------------------------------
// Web component
// ------------------------------------------------------------------------------------------------

let sheet;

function styleSheet() {
    if (sheet === undefined) {
        try {
            sheet = new CSSStyleSheet();
            sheet.replaceSync(CSS);
        } catch (e) {
            sheet = null;
        }
    }
    return sheet;
}

const Base = typeof HTMLElement !== 'undefined' ? HTMLElement : class {
};

/**
 * <paniolo-viewer> shows an electronic invoice, order, or despatch advice.
 *
 * Attributes: lang (de, en, fr; default: closest lang or the browser language), theme (auto, light,
 * dark; default auto), src (URL to fetch the document from), report-src (URL of a validation
 * report), tabs (comma-separated subset of overview, lines, details, attachments, validation, xml),
 * dropzone (lets users drop or choose files).
 *
 * Properties and methods: load(input, { report }), loadReport(input), clear(), print(), model,
 * report, tab.
 *
 * Events: paniolo-load (detail: { model, title }), paniolo-error (detail: { error, code, message }),
 * paniolo-tab (detail: { tab }).
 */
export class PanioloViewer extends Base {
    constructor() {
        super();
        this._root = this.attachShadow({mode: 'open'});
        const css = styleSheet();
        if (css && 'adoptedStyleSheets' in this._root) {
            this._root.adoptedStyleSheets = [css];
        } else {
            const style = document.createElement('style');
            style.textContent = CSS;
            this._root.appendChild(style);
        }
        this._view = document.createElement('div');
        this._view.className = 'root';
        this._view.setAttribute('part', 'root');
        this._root.appendChild(this._view);
        this._model = null;
        this._report = null;
        this._error = null;
        this._loading = false;
        this._tab = 'overview';
        this._previews = new Map();
        this._sequence = 0;
        this._root.addEventListener('click', e => this._onClick(e));
        this._root.addEventListener('keydown', e => this._onKey(e));
        this._root.addEventListener('change', e => this._onChange(e));
        for (const type of ['dragenter', 'dragover']) {
            this.addEventListener(type, e => {
                if (this.hasAttribute('dropzone')) {
                    e.preventDefault();
                    this._view.querySelector('.state') && this._view.querySelector('.state').classList.add('drag');
                }
            });
        }
        this.addEventListener('dragleave', () => {
            const state = this._view.querySelector('.state');
            if (state) {
                state.classList.remove('drag');
            }
        });
        this.addEventListener('drop', e => {
            if (this.hasAttribute('dropzone') && e.dataTransfer && e.dataTransfer.files.length) {
                e.preventDefault();
                this._loadFiles([...e.dataTransfer.files]);
            }
        });
    }

    static get observedAttributes() {
        return ['lang', 'theme', 'src', 'report-src', 'tabs', 'dropzone'];
    }

    /** The view model of the shown document, or null. */
    get model() {
        return this._model;
    }

    /** The validation report, or null. */
    get report() {
        return this._report;
    }

    set report(value) {
        this.loadReport(value);
    }

    /** Setting a document (bytes) loads it. */
    set document(value) {
        this.load(value);
    }

    get tab() {
        return this._tab;
    }

    set tab(value) {
        this._selectTab(value, false);
    }

    get language() {
        const lang = this.getAttribute('lang') ||
            (this.closest && this.closest('[lang]') && this.closest('[lang]').getAttribute('lang')) ||
            (typeof navigator !== 'undefined' && navigator.language) || 'en';
        return lang;
    }

    connectedCallback() {
        if (!this.hasAttribute('theme')) {
            this.setAttribute('theme', 'auto');
        }
        if (!this._model && !this._loading && this.getAttribute('src')) {
            this._fetch(this.getAttribute('src'), false);
        } else {
            this._render();
        }
    }

    disconnectedCallback() {
        this._revokePreviews();
    }

    attributeChangedCallback(name, previous, value) {
        if (previous === value || !this.isConnected) {
            return;
        }
        if (name === 'src' && value) {
            this._fetch(value, false);
        } else if (name === 'report-src' && value) {
            this._fetch(value, true);
        } else {
            this._render();
        }
    }

    /**
     * Loads and shows a document.
     *
     * @param input Uint8Array, ArrayBuffer, Blob, Response, XML text, or Base64
     * @param options { report }: a validation report as bytes, optional
     * @returns Promise of the model; rejected if the document cannot be read (the viewer shows the error)
     */
    async load(input, options) {
        const sequence = ++this._sequence;
        this._loading = true;
        this._error = null;
        this._render();
        try {
            const model = await parse(input);
            const report = options && options.report ? await parseReport(options.report) : null;
            if (sequence !== this._sequence) {
                return model;
            }
            this._revokePreviews();
            this._model = model;
            this._report = report;
            this._loading = false;
            const allowed = this._allowedTabs();
            this._tab = allowed && !allowed.includes('overview') ? allowed[0] : 'overview';
            this._render();
            this.dispatchEvent(new CustomEvent('paniolo-load', {
                bubbles: true, composed: true, detail: {model, title: this._renderer().title()}
            }));
            return model;
        } catch (error) {
            if (sequence === this._sequence) {
                this._fail(error);
            }
            throw error;
        }
    }

    /** Loads a validation report for the shown document. */
    async loadReport(input) {
        if (input == null) {
            this._report = null;
            this._render();
            return null;
        }
        try {
            this._report = await parseReport(input);
            this._render();
            return this._report;
        } catch (error) {
            this._fail(error);
            throw error;
        }
    }

    /** Removes the document. */
    clear() {
        this._sequence++;
        this._revokePreviews();
        this._model = null;
        this._report = null;
        this._error = null;
        this._loading = false;
        this._render();
    }

    /** Prints the document with all tabs, without the host page. */
    print() {
        if (!this._model) {
            return;
        }
        const f = new Formatter(this.language);
        const renderer = new Renderer(this._model, this._report, f);
        const body = this._content(renderer, true);
        const css = CSS.replace(/:host\(([^)]*)\)/g, '.host$1').replace(/:host/g, '.host');
        const frame = document.createElement('iframe');
        frame.setAttribute('aria-hidden', 'true');
        frame.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden';
        frame.srcdoc = '<!doctype html><html lang="' + esc(f.lang) + '"><head><meta charset="utf-8"><title>' + esc(renderer.title()) +
            '</title><style>' + css + '@page{margin:14mm}body{margin:0}</style></head><body><div class="host print" theme="light">' +
            '<div class="root">' + body + '</div></div></body></html>';
        frame.addEventListener('load', () => {
            const win = frame.contentWindow;
            win.addEventListener('afterprint', () => setTimeout(() => frame.remove(), 100));
            win.focus();
            win.print();
            setTimeout(() => frame.isConnected && frame.remove(), 120000);
        });
        document.body.appendChild(frame);
    }

    // -- internals ----------------------------------------------------------------------------------

    async _fetch(url, report) {
        try {
            const response = await fetch(url, {credentials: 'same-origin'});
            if (report) {
                await this.loadReport(response);
            } else {
                await this.load(response);
            }
        } catch (error) {
            // shown by load or loadReport; a network error is shown here
            if (!(error instanceof PanioloError)) {
                this._fail(new PanioloError('fetch', error.message));
            }
        }
    }

    async _loadFiles(files) {
        let documentFile = null;
        let reportFile = null;
        for (const file of files) {
            const bytes = new Uint8Array(await file.arrayBuffer());
            if (!isPdf(bytes)) {
                try {
                    if (isReportRoot(parseXml(decodeText(bytes)).documentElement)) {
                        reportFile = bytes;
                        continue;
                    }
                } catch (e) {
                    // not XML, let load report it
                }
            }
            documentFile = documentFile || bytes;
        }
        try {
            if (documentFile) {
                await this.load(documentFile, {report: reportFile});
            } else if (reportFile && this._model) {
                await this.loadReport(reportFile);
            } else if (reportFile) {
                this._fail(new PanioloError('isReport'));
            }
        } catch (e) {
            // shown in the viewer
        }
    }

    _fail(error) {
        this._loading = false;
        this._error = error;
        this._render();
        const code = error instanceof PanioloError ? error.code : 'unknown';
        this.dispatchEvent(new CustomEvent('paniolo-error', {
            bubbles: true, composed: true, detail: {error, code, message: this._message(error)}
        }));
    }

    _message(error) {
        const t = texts_(new Formatter(this.language).lang);
        return (error instanceof PanioloError && t.errors[error.code]) || t.errors.unknown;
    }

    _allowedTabs() {
        const tabs = this.getAttribute('tabs');
        return tabs ? tabs.split(',').map(s => s.trim()).filter(s => TAB_ORDER.includes(s)) : null;
    }

    _renderer() {
        return new Renderer(this._model, this._report, new Formatter(this.language));
    }

    _render() {
        const f = new Formatter(this.language);
        const t = f.t;
        if (this._loading) {
            this._view.innerHTML = html`
                <div class="state"
                     role="status">
                    <div class="spinner"></div>
                    <div>${t.loading}</div>
                </div>`.value;
            return;
        }
        if (this._error) {
            this._view.innerHTML = html`
                <div class="state error"
                     role="alert">
                    ${icon('alert')}
                    <div><strong>${this._message(this._error)}</strong></div>
                    ${this._error.detail ? html`
                        <div class="detail">${this._error.detail}</div>` : ''}
                    ${this.hasAttribute('dropzone') ? this._dropHint(t) : ''}
                </div>`.value;
            return;
        }
        if (!this._model) {
            this._view.innerHTML = this.hasAttribute('dropzone') ?
                html`
                    <div class="state"
                         part="dropzone">${icon('upload')}${this._dropHint(t)}
                    </div>`.value : '';
            return;
        }
        const renderer = this._renderer();
        const tabs = renderer.tabs(this._allowedTabs());
        if (!tabs.some(tab => tab.id === this._tab)) {
            this._tab = tabs.length ? tabs[0].id : 'overview';
        }
        this._view.innerHTML = html`
            ${renderer.header(true)}
            <nav class="tabs"
                 role="tablist"
                 part="tabs">
                ${tabs.map(tab => html`
                    <button type="button"
                            class="tab"
                            role="tab"
                            id="tab-${tab.id}"
                            aria-controls="panel-${tab.id}"
                            aria-selected="${String(tab.id === this._tab)}"
                            tabindex="${tab.id === this._tab ? 0 : -1}"
                            data-action="tab"
                            data-tab="${tab.id}">${tab.label}${tab.count ? html`<span class="count">${tab.count}</span>` : ''}
                    </button>`)}
            </nav>
            ${new Html(this._content(renderer, false, tabs))}
            <div class="footer">${t.poweredBy}&nbsp;<b>●</b></div>`.value;
    }

    _content(renderer, print, tabs) {
        const shown = tabs || renderer.tabs(this._allowedTabs());
        const panels = shown.filter(tab => !print || tab.id !== 'xml').map(tab => {
            let body;
            try {
                body = this._panel(renderer, tab.id);
            } catch (e) {
                body = html`
                    <div class="state error">${e.message}</div>`;
            }
            return html`
                <section class="panel"
                         role="tabpanel"
                         id="panel-${tab.id}"
                         aria-labelledby="tab-${tab.id}"
                         data-tab="${tab.id}"
                         ${!print && tab.id !== this._tab ? new Html('hidden') : ''}>
                    ${print && shown.length > 1 ? html`<h2 class="section-title">${tab.label}</h2>` : ''}${body}
                </section>`;
        });
        return (print ? renderer.header(false).value : '') + part(panels);
    }

    _panel(renderer, tab) {
        switch (tab) {
            case 'overview':
                return renderer.overview();
            case 'lines':
                return renderer.lines();
            case 'details':
                return renderer.details();
            case 'attachments':
                return renderer.attachments(this._previews);
            case 'validation':
                return renderer.validation();
            case 'xml':
                return renderer.xml();
            default:
                return '';
        }
    }

    _dropHint(t) {
        return html`
            <div>${t.dropHint}
                <button type="button"
                        class="linkish"
                        data-action="choose">${t.choose}
                </button>
                 .
            </div>
            <div class="small">${t.dropReport}</div>
            <input type="file"
                   accept=".xml,.pdf,application/xml,text/xml,application/pdf"
                   multiple
                   hidden>`;
    }

    _selectTab(tab, focus) {
        if (!tab || tab === this._tab) {
            return;
        }
        this._tab = tab;
        for (const button of this._root.querySelectorAll('.tab')) {
            const selected = button.dataset.tab === tab;
            button.setAttribute('aria-selected', String(selected));
            button.tabIndex = selected ? 0 : -1;
            if (selected && focus) {
                button.focus();
            }
        }
        for (const panel of this._root.querySelectorAll('.panel')) {
            panel.hidden = panel.dataset.tab !== tab;
        }
        this.dispatchEvent(new CustomEvent('paniolo-tab', {bubbles: true, composed: true, detail: {tab}}));
    }

    _onKey(e) {
        const tab = e.target.closest && e.target.closest('.tab');
        if (!tab || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(e.key)) {
            return;
        }
        const tabs = [...this._root.querySelectorAll('.tab')];
        const i = tabs.indexOf(tab);
        const next = e.key === 'Home' ? 0 : e.key === 'End' ? tabs.length - 1 :
            (i + (e.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length;
        e.preventDefault();
        this._selectTab(tabs[next].dataset.tab, true);
    }

    _onChange(e) {
        if (e.target.matches && e.target.matches('input[type=file]') && e.target.files.length) {
            this._loadFiles([...e.target.files]);
        }
    }

    _onClick(e) {
        const target = e.target.closest && e.target.closest('[data-action]');
        if (!target) {
            return;
        }
        const m = this._model;
        switch (target.dataset.action) {
            case 'tab':
                this._selectTab(target.dataset.tab, false);
                break;
            case 'print':
                this.print();
                break;
            case 'download-xml':
                this._download(new TextEncoder().encode(m.xml), m.xmlFileName || (m.number || 'document') + '.xml', 'application/xml');
                break;
            case 'download-pdf':
                this._download(m.pdf, (m.number || 'document') + '.pdf', 'application/pdf');
                break;
            case 'download-attachment': {
                const a = m.attachments[Number(target.dataset.index)];
                this._download(a.data, a.name || a.id || 'attachment', a.mime || 'application/octet-stream');
                break;
            }
            case 'preview':
                this._togglePreview(target.dataset.index);
                break;
            case 'toggle-line': {
                const row = this._root.getElementById(target.dataset.target);
                const open = target.getAttribute('aria-expanded') !== 'true';
                target.setAttribute('aria-expanded', String(open));
                row.hidden = !open;
                break;
            }
            case 'goto-line':
                this._gotoLine(target.dataset.line);
                break;
            case 'copy-xml':
                if (navigator.clipboard) {
                    navigator.clipboard.writeText(m.xml).then(() => {
                        const label = target.querySelector('span');
                        const t = new Formatter(this.language).t;
                        label.textContent = t.copied;
                        setTimeout(() => {
                            label.textContent = t.copy;
                        }, 1500);
                    }, () => {
                    });
                }
                break;
            case 'choose':
                this._root.querySelector('input[type=file]').click();
                break;
            default:
                break;
        }
    }

    _gotoLine(index) {
        this._selectTab('lines', false);
        const row = this._root.querySelector('tr.line[data-line="' + digits(index) + '"]');
        if (!row) {
            return;
        }
        const toggle = row.querySelector('.toggle');
        if (toggle && toggle.getAttribute('aria-expanded') !== 'true') {
            toggle.click();
        }
        row.scrollIntoView({behavior: 'smooth', block: 'center'});
        row.classList.remove('flash');
        void row.offsetWidth;
        row.classList.add('flash');
    }

    _togglePreview(index) {
        const key = index === 'pdf' ? 'pdf' : Number(index);
        if (this._previews.has(key)) {
            URL.revokeObjectURL(this._previews.get(key));
            this._previews.delete(key);
        } else {
            const item = key === 'pdf' ? {data: this._model.pdf, mime: 'application/pdf'} : this._model.attachments[key];
            this._previews.set(key, URL.createObjectURL(new Blob([item.data], {type: item.mime})));
        }
        const panel = this._root.querySelector('.panel[data-tab="attachments"]');
        if (panel) {
            panel.innerHTML = this._renderer().attachments(this._previews).value;
        }
    }

    _revokePreviews() {
        for (const url of this._previews.values()) {
            URL.revokeObjectURL(url);
        }
        this._previews.clear();
    }

    _download(data, name, type) {
        const url = URL.createObjectURL(new Blob([data], {type}));
        const a = document.createElement('a');
        a.href = url;
        a.download = name.replace(/[\\/:*?"<>|]+/g, '_');
        a.hidden = true;
        this._root.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 60000);
    }
}

function digits(value) {
    return String(value).replace(/[^0-9]/g, '');
}

if (typeof customElements !== 'undefined' && !customElements.get(TAG_NAME)) {
    customElements.define(TAG_NAME, PanioloViewer);
}

export {VERSION, TAG_NAME};
