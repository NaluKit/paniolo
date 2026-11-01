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
package io.github.nalukit.paniolo.mustang;

import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.Attachment;
import io.github.nalukit.paniolo.model.common.CashDiscount;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.DirectDebit;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.ElectronicAddress;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.ItemClassification;
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.PartyIdentifier;
import io.github.nalukit.paniolo.model.common.Payee;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.PrecedingInvoiceReference;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.References;
import io.github.nalukit.paniolo.model.common.SellerTaxRepresentativeParty;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.DateTimeQualifier;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.model.extended.AdjustmentExtension;
import io.github.nalukit.paniolo.model.extended.DocumentReference;
import io.github.nalukit.paniolo.model.extended.ExtendedContact;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.GrossPriceAdjustment;
import io.github.nalukit.paniolo.model.extended.LineExtension;
import io.github.nalukit.paniolo.model.extended.LineSubtype;
import io.github.nalukit.paniolo.model.extended.LogisticsServiceCharge;
import io.github.nalukit.paniolo.model.extended.PartyExtension;
import io.github.nalukit.paniolo.model.extended.PaymentTerm;
import io.github.nalukit.paniolo.model.extended.PaymentTermsAdjustment;
import io.github.nalukit.paniolo.model.extended.ProductCharacteristic;
import io.github.nalukit.paniolo.model.extended.ProductInstance;
import io.github.nalukit.paniolo.model.extended.TaxExchangeRate;
import io.github.nalukit.paniolo.model.extended.TypedDocumentReference;
import org.mustangproject.FileAttachment;
import org.mustangproject.IncludedNote;
import org.mustangproject.ZUGFeRD.IAbsoluteValueProvider;
import org.mustangproject.ZUGFeRD.IExportableTransaction;
import org.mustangproject.ZUGFeRD.IReferencedDocument;
import org.mustangproject.ZUGFeRD.IZUGFeRDAllowanceCharge;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableContact;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableItem;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableProduct;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableTradeParty;
import org.mustangproject.ZUGFeRD.IZUGFeRDLegalOrganisation;
import org.mustangproject.ZUGFeRD.IZUGFeRDLogisticsServiceCharge;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentDiscountTerms;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentTerms;
import org.mustangproject.ZUGFeRD.IZUGFeRDTradeSettlementDebit;
import org.mustangproject.ZUGFeRD.IZUGFeRDTradeSettlementPayment;
import org.mustangproject.ZUGFeRD.LineCalculator;
import org.mustangproject.ZUGFeRD.TransactionCalculator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maps a Mustang invoice ({@code org.mustangproject.Invoice}, or any other
 * {@link IExportableTransaction}) to a Paniolo {@link Invoice}, so that an existing Mustang
 * integration can switch to Paniolo step by step: it keeps building its Mustang objects and lets
 * Paniolo write, validate, and embed the invoice.
 *
 * <pre>{@code
 * MustangInvoiceAdapter adapter = MustangInvoiceAdapter.create();             // XRechnung 3
 * MustangMapping mapping = adapter.map(mustangInvoice);
 * mapping.losses().forEach(loss -> log.warn("not carried over: {}", loss));
 * byte[] xml = Paniolo.create().toBytes(mapping.invoice(), InvoiceSyntax.UBL);
 *
 * Invoice invoice = adapter.toInvoice(mustangInvoice);   // fails if a value would be lost
 * }</pre>
 *
 * <p>The mapping follows Mustang's own export: amounts of percentage allowances and charges, net
 * prices, and price allowances are calculated with Mustang's {@link LineCalculator} and
 * {@link TransactionCalculator}, so Paniolo writes the same amounts as Mustang. Line net amounts
 * and totals are calculated by the Paniolo model; a stated line amount
 * ({@code getLineTotalAmount}) must agree with it.
 *
 * <p>No value is dropped silently: every value without Paniolo counterpart in the target profile is
 * listed in {@link MustangMapping#losses()}, and {@link #toInvoice} refuses to map such an invoice.
 * {@link #FIELDS} documents the mapping of every getter. Content of the Factur-X profile EXTENDED
 * (fax numbers, invoicer and invoicee, line hierarchy, several price allowances, product
 * characteristics, ...) is carried over only if the target profile is EXTENDED.
 *
 * <p>Compiled and tested against Mustang 2.26.0; the application brings its own Mustang library.
 * Getters of newer Mustang versions that the adapter maps (BT-19 {@code getAccountingReference},
 * BT-7 {@code getTaxPointDate}, {@code getIndustryAssignedID}) are read by reflection; every other
 * getter of the export interfaces it does not know is reported as loss if it carries a value.
 * Instances are immutable and thread-safe; the Mustang objects are only read.
 */
public final class MustangInvoiceAdapter {
  /**
   * The mapping of every getter of Mustang's export interfaces, for migration reviews.
   */
  public static final List<FieldMapping> FIELDS = MustangFields.ALL;

  private final ProcessControl processControl;
  private final ZoneId         zone;
  private final boolean        extended;

  private MustangInvoiceAdapter(ProcessControl processControl,
                                ZoneId zone) {
    this.processControl = processControl;
    this.zone           = zone;
    this.extended       = processControl.isExtended();
  }

  /**
   * An adapter for XRechnung 3 (CIUS of EN 16931) in the default time zone of the JVM.
   *
   * @return the adapter
   */
  public static MustangInvoiceAdapter create() {
    return builder().build();
  }

  /**
   * Starts the configuration of an adapter.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Maps the invoice and lists the values that the Paniolo invoice does not carry.
   *
   * @param transaction the Mustang invoice; must not be {@code null}
   * @return the invoice and the losses
   * @throws IllegalArgumentException if the Mustang invoice violates a check of the Paniolo model,
   *                                  e.g. a missing invoice number, or an unknown code
   */
  public MustangMapping map(IExportableTransaction transaction) {
    Objects.requireNonNull(transaction,
                           "transaction");
    var run     = new Run(transaction);
    var invoice = run.invoice();
    return new MustangMapping(invoice,
                              run.losses);
  }

  /**
   * Maps the invoice and refuses invoices with values that the Paniolo invoice would not carry.
   *
   * @param transaction the Mustang invoice; must not be {@code null}
   * @return the invoice
   * @throws IllegalArgumentException if a value would be lost (the message lists every lost value),
   *                                  or if the Mustang invoice violates a check of the Paniolo model
   */
  public Invoice toInvoice(IExportableTransaction transaction) {
    var mapping = map(transaction);
    if (!mapping.isLossless()) {
      throw new IllegalArgumentException("The Mustang invoice " + mapping.invoice()
                                                                         .number() + " has values without counterpart in " + processControl.specificationId() + ": " + mapping.losses());
    }
    return mapping.invoice();
  }

  /**
   * The target profile: specification identifier (BT-24) and default business process (BT-23).
   *
   * @return the process control of the mapped invoices
   */
  public ProcessControl processControl() {
    return processControl;
  }

  /**
   * One mapping; collects the losses.
   */
  private final class Run {
    private final IExportableTransaction t;
    private final List<MappingLoss>      losses = new ArrayList<>();
    private       TransactionCalculator  calculator;

    Run(IExportableTransaction transaction) {
      this.t = transaction;
    }

    private Invoice invoice() {
      unsupported(t,
                  IExportableTransaction.class,
                  "the invoice");
      var info    = ExtendedInformation.create();
      var builder = Invoice.create()
                           .number(t.getNumber())
                           .issueDate(date(t.getIssueDate()))
                           .processControl(new ProcessControl(t.getBusinessProcessId() != null ?
                                                              t.getBusinessProcessId() :
                                                              processControl.businessProcessId(),
                                                              processControl.specificationId()))
                           .buyerReference(t.getReferenceNumber());
      if (t.getDocumentCode() != null) {
        builder.typeCode(InvoiceTypeCode.of(t.getDocumentCode()));
      }
      if (t.getCurrency() != null) {
        builder.currencyCode(CurrencyCode.of(t.getCurrency()));
      }
      onlyExtended("IExportableTransaction.getDocumentName",
                   t.getDocumentName(),
                   "the document name",
                   () -> info.documentName(t.getDocumentName()));
      onlyExtended("IExportableTransaction.getTestIndicator",
                   t.getTestIndicator() ?
                   Boolean.TRUE :
                   null,
                   "the test indicator",
                   () -> info.testIndicator(true));

      builder.seller(t.getSender() == null ?
                     legacySeller() :
                     party(t.getSender(),
                           "seller",
                           true));
      builder.buyer(party(t.getRecipient(),
                          "buyer",
                          false));
      if (t.getPayee() != null) {
        builder.payee(payee(t.getPayee()));
      }
      if (t.getTaxRepresentative() != null) {
        builder.taxRepresentative(taxRepresentative(t.getTaxRepresentative()));
      }
      onlyExtended("IExportableTransaction.getInvoicer",
                   t.getInvoicer(),
                   "the invoicer",
                   () -> info.invoicer(party(t.getInvoicer(),
                                             "invoicer",
                                             false)));
      onlyExtended("IExportableTransaction.getInvoicee",
                   t.getInvoicee(),
                   "the invoicee",
                   () -> info.invoicee(party(t.getInvoicee(),
                                             "invoicee",
                                             false)));
      onlyExtended("IExportableTransaction.getEndCustomerDeliveryAddress",
                   t.getEndCustomerDeliveryAddress(),
                   "the ultimate ship-to party",
                   () -> info.ultimateShipTo(party(t.getEndCustomerDeliveryAddress(),
                                                   "ultimate ship-to party",
                                                   false)));
      builder.delivery(delivery());
      onlyExtended("IExportableTransaction.getDeliveryTypeCode",
                   t.getDeliveryTypeCode(),
                   "the delivery terms",
                   () -> info.deliveryTermsCode(t.getDeliveryTypeCode()));

      var items = list(t.getZFItems());
      for (int index = 0; index < items.size(); index++) {
        builder.addLine(line(items.get(index),
                             index + 1));
      }
      for (var allowance : list(t.getZFAllowances())) {
        builder.addAdjustment(adjustment(allowance));
      }
      for (var charge : list(t.getZFCharges())) {
        builder.addAdjustment(adjustment(charge));
      }
      for (var logistics : list(t.getZFLogisticsServiceCharges())) {
        unsupported(logistics,
                    IZUGFeRDLogisticsServiceCharge.class,
                    "the logistics service charge " + logistics.getDescription());
        var tax = tax(logistics.getTaxCategoryCode(),
                      logistics.getTaxRateApplicablePercent(),
                      logistics.getTaxExemptionReason(),
                      logistics.getTaxExemptionReasonCode());
        if (extended) {
          info.addLogisticsServiceCharge(new LogisticsServiceCharge(logistics.getDescription(),
                                                                    logistics.getAppliedAmount(),
                                                                    tax));
        } else {
          // EN 16931 has no logistics service charges; the amount stays a document charge.
          builder.addAdjustment(DocumentAdjustment.createCharge()
                                                  .amount(logistics.getAppliedAmount())
                                                  .reason(logistics.getDescription())
                                                  .tax(tax)
                                                  .build());
        }
      }

      builder.paymentInstruction(payment());
      terms(builder,
            info);
      if (t.getTotalPrepaidAmount() != null && t.getTotalPrepaidAmount()
                                                .signum() != 0) {
        builder.paidAmount(t.getTotalPrepaidAmount());
      }
      if (t.getRoundingAmount() != null && t.getRoundingAmount()
                                            .signum() != 0) {
        builder.roundingAmount(t.getRoundingAmount());
      }

      builder.references(references());
      precedingInvoices(builder);
      onlyExtended("IExportableTransaction.getDeliveryNoteReferencedDocument",
                   deliveryNote(),
                   "the delivery note reference",
                   () -> info.deliveryNoteReference(deliveryNote()));
      if (t.getDetailedDeliveryPeriodFrom() != null || t.getDetailedDeliveryPeriodTo() != null) {
        builder.invoicePeriod(new InvoicePeriod(date(t.getDetailedDeliveryPeriodFrom()),
                                                date(t.getDetailedDeliveryPeriodTo())));
      }
      if (t.getVATDueDateTypeCode() != null) {
        // CII codes of the VAT due date to UNTDID 2005 (BT-8).
        builder.taxPointDateCode(switch (t.getVATDueDateTypeCode()) {
          case "5" -> DateTimeQualifier.CODE_3;
          case "29" -> DateTimeQualifier.CODE_35;
          case "72" -> DateTimeQualifier.CODE_432;
          default -> DateTimeQualifier.of(t.getVATDueDateTypeCode());
        });
      }
      // Mustang after 2.26.0: the VAT point date (BT-7).
      Date taxPoint = newer(t,
                            IExportableTransaction.class,
                            "getTaxPointDate",
                            Date.class);
      if (taxPoint != null && t.getVATDueDateTypeCode() != null) {
        lost("IExportableTransaction.getTaxPointDate",
             taxPoint,
             "the VAT point date (BT-7) and the VAT point date code (BT-8) exclude each other (BR-CO-3); the code is kept");
      } else if (taxPoint != null) {
        builder.taxPointDate(date(taxPoint));
      }

      notes(builder);
      supportingDocuments(builder);
      taxCurrency(builder,
                  info);
      if (extended) {
        builder.extended(info.build());
      }
      return builder.build();
    }

    // Parties

    private Party party(IZUGFeRDExportableTradeParty party,
                        String role,
                        boolean seller) {
      var builder = Party.create()
                         .name(party.getName())
                         .address(address(party.getStreet(),
                                          party.getAdditionalAddress(),
                                          party.getAdditionalAddressExtension(),
                                          party.getZIP(),
                                          party.getLocation(),
                                          party.getCountry()))
                         .vatId(party.getVATID());
      String name    = role + " " + party.getName();
      String subject = "the " + name;
      unsupported(party,
                  IZUGFeRDExportableTradeParty.class,
                  subject);
      if (seller) {
        builder.taxId(party.getTaxID())
               .additionalLegalInformation(party.getDescription());
      } else {
        lost("IZUGFeRDExportableTradeParty.getTaxID",
             party.getTaxID(),
             subject,
             "EN 16931 has a tax number for the seller only, not for the " + name);
        lost("IZUGFeRDExportableTradeParty.getDescription",
             party.getDescription(),
             subject,
             "EN 16931 has additional legal information for the seller only, not for the " + name);
      }
      if (party.getGlobalID() != null) {
        String  local  = party.getID();
        boolean second = local != null && !local.equals(party.getGlobalID());
        if (second && (seller || extended)) {
          // The seller identifier (BT-29) is repeatable; EXTENDED keeps several identifiers of
          // every party.
          builder.identifier(local)
                 .addAdditionalIdentifier(new PartyIdentifier(party.getGlobalID(),
                                                              party.getGlobalIDScheme()));
        } else {
          builder.identifier(party.getGlobalID(),
                             party.getGlobalIDScheme());
          lost("IZUGFeRDExportableTradeParty.getID",
               second ?
               local :
               null,
               subject,
               "EN 16931 has one identifier of the " + name + ", the global identifier; a second one needs the profile EXTENDED");
        }
      } else {
        builder.identifier(party.getID());
      }
      if (party.getUriUniversalCommunicationID() != null) {
        builder.electronicAddress(new ElectronicAddress(party.getUriUniversalCommunicationID(),
                                                        party.getUriUniversalCommunicationIDScheme()));
      }
      ExtendedContact extendedContact = null;
      var             contact         = party.getContact();
      if (contact != null) {
        unsupported(contact,
                    IZUGFeRDExportableContact.class,
                    "the contact of the " + name);
        String owner = contactName(contact,
                                   subject);
        if (contact.getFax() != null && extended) {
          extendedContact = new ExtendedContact(contact.getName(),
                                                null,
                                                null,
                                                contact.getPhone(),
                                                contact.getFax(),
                                                contact.getEMail());
        } else {
          lost("IZUGFeRDExportableContact.getFax",
               contact.getFax(),
               owner,
               "the fax number of the " + name + " needs the profile EXTENDED");
          builder.contact(new Contact(contact.getName(),
                                      contact.getPhone(),
                                      contact.getEMail()));
        }
        contactDetails(contact,
                       owner);
      }
      if (extendedContact != null) {
        builder.extended(new PartyExtension(null,
                                            null,
                                            extendedContact));
      }
      var legal = party.getLegalOrganisation();
      if (legal != null) {
        unsupported(legal,
                    IZUGFeRDLegalOrganisation.class,
                    "the legal organisation of the " + name);
        if (legal.getSchemedID() != null) {
          builder.legalRegistrationId(legal.getSchemedID()
                                           .getID(),
                                      legal.getSchemedID()
                                           .getScheme());
        }
        builder.tradingName(legal.getTradingBusinessName());
      }
      return builder.build();
    }

    /**
     * The seller of implementations that still use the {@code getOwn...} getters.
     */
    private Party legacySeller() {
      return Party.create()
                  .name(t.getOwnOrganisationName())
                  .address(address(t.getOwnStreet(),
                                   null,
                                   null,
                                   t.getOwnZIP(),
                                   t.getOwnLocation(),
                                   t.getOwnCountry()))
                  .vatId(t.getOwnVATID())
                  .taxId(t.getOwnTaxID())
                  .identifier(t.getOwnForeignOrganisationID())
                  .build();
    }

    private Payee payee(IZUGFeRDExportableTradeParty party) {
      unsupported(party,
                  IZUGFeRDExportableTradeParty.class,
                  "the payee " + party.getName());
      var  legal  = party.getLegalOrganisation();
      var  schemed = legal == null ?
                     null :
                     legal.getSchemedID();
      boolean global = party.getGlobalID() != null;
      var mapped = new HashSet<>(Set.of("getName",
                                        "getSchemedID"));
      mapped.addAll(global ?
                    Set.of("getGlobalID",
                           "getGlobalIDScheme") :
                    Set.of("getID"));
      if (global && party.getGlobalID()
                         .equals(party.getID())) {
        mapped.add("getID");
      }
      unmapped(party,
               "the payee " + party.getName(),
               "EN 16931 keeps name, one identifier, and legal registration of the payee " + party.getName() + " only",
               mapped);
      return Payee.create()
                  .name(party.getName())
                  .identifier(global ?
                              party.getGlobalID() :
                              party.getID(),
                              global ?
                              party.getGlobalIDScheme() :
                              null)
                  .legalRegistrationId(schemed == null ?
                                       null :
                                       schemed.getID(),
                                       schemed == null ?
                                       null :
                                       schemed.getScheme())
                  .build();
    }

    private SellerTaxRepresentativeParty taxRepresentative(IZUGFeRDExportableTradeParty party) {
      unsupported(party,
                  IZUGFeRDExportableTradeParty.class,
                  "the tax representative " + party.getName());
      unmapped(party,
               "the tax representative " + party.getName(),
               "EN 16931 keeps name, VAT ID, and address of the tax representative " + party.getName() + " only",
               Set.of("getName",
                      "getVATID",
                      "getStreet",
                      "getAdditionalAddress",
                      "getAdditionalAddressExtension",
                      "getZIP",
                      "getLocation",
                      "getCountry"));
      return SellerTaxRepresentativeParty.create()
                                         .name(party.getName())
                                         .vatId(party.getVATID())
                                         .address(address(party.getStreet(),
                                                          party.getAdditionalAddress(),
                                                          party.getAdditionalAddressExtension(),
                                                          party.getZIP(),
                                                          party.getLocation(),
                                                          party.getCountry()))
                                         .build();
    }

    private DeliveryInformation delivery() {
      var builder = DeliveryInformation.create()
                                       .actualDeliveryDate(date(t.getDeliveryDate()));
      var shipTo  = t.getDeliveryAddress();
      if (shipTo != null) {
        unsupported(shipTo,
                    IZUGFeRDExportableTradeParty.class,
                    "the ship-to party " + shipTo.getName());
        builder.partyName(shipTo.getName())
               .locationId(shipTo.getGlobalID() != null ?
                           shipTo.getGlobalID() :
                           shipTo.getID())
               .address(address(shipTo.getStreet(),
                                shipTo.getAdditionalAddress(),
                                shipTo.getAdditionalAddressExtension(),
                                shipTo.getZIP(),
                                shipTo.getLocation(),
                                shipTo.getCountry()));
        var mapped = new HashSet<>(Set.of("getName",
                                          "getStreet",
                                          "getAdditionalAddress",
                                          "getAdditionalAddressExtension",
                                          "getZIP",
                                          "getLocation",
                                          "getCountry",
                                          shipTo.getGlobalID() != null ?
                                          "getGlobalID" :
                                          "getID"));
        if (shipTo.getGlobalID() != null && shipTo.getGlobalID()
                                                  .equals(shipTo.getID())) {
          mapped.add("getID");
        }
        unmapped(shipTo,
                 "the ship-to party " + shipTo.getName(),
                 "EN 16931 keeps name, location identifier without scheme, and address of the ship-to party " + shipTo.getName() + " only",
                 mapped);
      } else if (t.getShipToOrganisationName() != null || t.getShipToStreet() != null) {
        builder.partyName(t.getShipToOrganisationName())
               .locationId(t.getShipToOrganisationID())
               .address(address(t.getShipToStreet(),
                                null,
                                null,
                                t.getShipToZIP(),
                                t.getShipToLocation(),
                                t.getShipToCountry()));
      }
      return builder.build();
    }

    private static Address address(String street,
                                   String additionalStreet,
                                   String additionalLine,
                                   String postalCode,
                                   String city,
                                   String country) {
      return Address.create()
                    .street(street)
                    .additionalStreet(additionalStreet)
                    .additionalLine(additionalLine)
                    .postalCode(postalCode)
                    .city(city)
                    .country(country == null ?
                             null :
                             Country.of(country))
                    .build();
    }

    // Lines

    private InvoiceLine line(IZUGFeRDExportableItem item,
                             int position) {
      var    product   = item.getProduct();
      // Mustang numbers lines without identifier by position.
      String id        = item.getId() != null ?
                         item.getId() :
                         String.valueOf(position);
      String name      = "line " + id;
      var    calculated = new LineCalculator(item);
      BigDecimal basis = item.getBasisQuantity() == null || item.getBasisQuantity()
                                                                .signum() == 0 ?
                         BigDecimal.ONE :
                         item.getBasisQuantity();
      var builder = InvoiceLine.create()
                               .id(id)
                               .name(product.getName())
                               .description(product.getDescription())
                               .quantity(item.getQuantity())
                               .unitCode(product.getUnit() == null ?
                                         null :
                                         UnitCode.of(product.getUnit()))
                               .netUnitPrice(price(calculated.getPrice()))
                               .priceBaseQuantity(basis)
                               .tax(tax(product.getTaxCategoryCode(),
                                        product.getVATPercent(),
                                        product.getTaxExemptionReason(),
                                        product.getTaxExemptionReasonCode()))
                               .sellerItemId(product.getSellerAssignedID())
                               .buyerItemId(product.getBuyerAssignedID())
                               .orderLineReference(orderLine(item))
                               .buyerAccountingReference(item.getAccountingReference());
      if (item.getLineTotalAmount() != null) {
        builder.netAmount(item.getLineTotalAmount());
      }
      if (product.getGlobalID() != null) {
        builder.standardItemId(product.getGlobalID(),
                               product.getGlobalIDScheme());
      }
      if (product.getCountryOfOrigin() != null) {
        builder.originCountry(Country.of(product.getCountryOfOrigin()));
      }
      if (product.getAttributes() != null) {
        product.getAttributes()
               .forEach(builder::addAttribute);
      }
      unsupported(item,
                  IZUGFeRDExportableItem.class,
                  name);
      unsupported(product,
                  IZUGFeRDExportableProduct.class,
                  "the product of " + name);
      var lineExtension = LineExtension.create();
      // Mustang after 2.26.0: the manufacturer's or industry item identifier.
      String industryId = newer(product,
                                IZUGFeRDExportableProduct.class,
                                "getIndustryAssignedID",
                                String.class);
      onlyExtended("IZUGFeRDExportableProduct.getIndustryAssignedID",
                   industryId,
                   "the industry item identifier of " + name,
                   () -> lineExtension.industryItemId(industryId));
      prices(item,
             calculated,
             builder,
             lineExtension,
             name);
      for (var classification : list(product.getClassifications())) {
        var    code      = classification.getClassCode();
        String className = classification.getClassName();
        if (className != null && !extended) {
          lost("IDesignatedProductClassification.getClassName",
               className,
               "the class name of the classification of " + name + " needs the profile EXTENDED");
          className = null;
        }
        builder.addClassification(new ItemClassification(code.getCode(),
                                                         code.getListID(),
                                                         code.getListVersionID(),
                                                         className));
      }
      for (var characteristic : list(product.getCharacteristics())) {
        var type = characteristic.getTypeCode();
        if (type == null && characteristic.getValueMeasure() == null) {
          builder.addAttribute(characteristic.getDescription(),
                               characteristic.getValue());
        } else {
          onlyExtended("IZUGFeRDExportableProduct.getCharacteristics",
                       characteristic.getDescription(),
                       "the characteristic " + characteristic.getDescription() + " of " + name + " with type code or measure",
                       () -> lineExtension.addCharacteristic(new ProductCharacteristic(type == null ?
                                                                                      null :
                                                                                      type.getCode(),
                                                                                      characteristic.getDescription(),
                                                                                      characteristic.getValue(),
                                                                                      characteristic.getValueMeasure(),
                                                                                      characteristic.getUnitCode() == null ?
                                                                                      null :
                                                                                      UnitCode.of(characteristic.getUnitCode()))));
        }
      }
      for (var instance : list(product.getIndividualTradeProductInstances())) {
        String batch  = instance.getBatchID() == null ?
                        null :
                        instance.getBatchID()
                                .getID();
        String serial = instance.getSupplierAssignedSerialID() == null ?
                        null :
                        instance.getSupplierAssignedSerialID()
                                .getID();
        onlyExtended("IZUGFeRDExportableProduct.getIndividualTradeProductInstances",
                     firstOf(batch,
                             serial),
                     "the product instances of " + name,
                     () -> lineExtension.addInstance(new ProductInstance(batch,
                                                                         serial)));
      }
      lineNotes(item,
                builder,
                lineExtension,
                name);
      if (item.getDetailedDeliveryPeriodFrom() != null || item.getDetailedDeliveryPeriodTo() != null) {
        builder.period(new InvoicePeriod(date(item.getDetailedDeliveryPeriodFrom()),
                                         date(item.getDetailedDeliveryPeriodTo())));
      }
      // Line allowances and charges refer to price / base quantity x quantity, as in Mustang.
      IAbsoluteValueProvider line = new IAbsoluteValueProvider() {
        @Override
        public BigDecimal getValue() {
          return item.getPrice()
                     .divide(basis,
                             18,
                             RoundingMode.HALF_UP);
        }

        @Override
        public BigDecimal getQuantity() {
          return item.getQuantity();
        }
      };
      for (var adjustment : concat(item.getItemAllowances(),
                                   item.getItemCharges())) {
        unsupported(adjustment,
                    IZUGFeRDAllowanceCharge.class,
                    "a line allowance or charge of " + name);
        BigDecimal base = adjustment.getBasisAmount();
        if (base == null && adjustment.getPercent() != null) {
          base = line.getValue()
                     .multiply(line.getQuantity())
                     .setScale(2,
                               RoundingMode.HALF_UP);
        }
        lost("IZUGFeRDAllowanceCharge.getCategoryCode",
             adjustment.getCategoryCode() != null && !adjustment.getCategoryCode()
                                                                .equals(product.getTaxCategoryCode()) ?
             adjustment.getCategoryCode() :
             null,
             "a line allowance or charge of " + name + " takes the VAT category of its line");
        builder.addAdjustment(new LineAdjustment(adjustment.isCharge(),
                                                 adjustment.getTotalAmount(line)
                                                           .setScale(2,
                                                                     RoundingMode.HALF_UP),
                                                 base,
                                                 adjustment.getPercent(),
                                                 adjustment.getReason(),
                                                 adjustment.getReasonCode()));
      }
      lineReferences(item,
                     lineExtension,
                     name);
      hierarchy(item,
                lineExtension,
                name);
      if (extended) {
        builder.extended(lineExtension.build());
      }
      return builder.build();
    }

    /**
     * Gross price, price discount (BT-147), and net price as Mustang calculates them; in EXTENDED
     * every price allowance and charge is kept.
     */
    private void prices(IZUGFeRDExportableItem item,
                        LineCalculator calculated,
                        InvoiceLine.Builder builder,
                        LineExtension.Builder lineExtension,
                        String name) {
      var product = item.getProduct();
      // Price allowances and charges refer to the price of one base quantity, as in Mustang.
      IAbsoluteValueProvider unit = new IAbsoluteValueProvider() {
        @Override
        public BigDecimal getValue() {
          return item.getPrice();
        }

        @Override
        public BigDecimal getQuantity() {
          return BigDecimal.ONE;
        }
      };
      var adjustments = concat(product.getAllowances(),
                               product.getCharges());
      if (adjustments.isEmpty()) {
        return;
      }
      // EN 16931 has one price discount: several allowances, or details of one, are summed up.
      boolean summed = adjustments.size() > 1 || adjustments.stream()
                                                            .anyMatch(adjustment -> adjustment.getReason() != null || adjustment.getReasonCode() != null || adjustment.getPercent() != null);
      BigDecimal discount    = BigDecimal.ZERO;
      boolean    charges     = false;
      int        allowanceNo = 0;
      int        chargeNo    = 0;
      var        parts       = new ArrayList<GrossPriceAdjustment>();
      for (var adjustment : adjustments) {
        unsupported(adjustment,
                    IZUGFeRDAllowanceCharge.class,
                    "a price allowance or charge of " + name);
        BigDecimal amount  = price(adjustment.getTotalAmount(unit));
        String     subject = adjustment.isCharge() ?
                             "the price charge " + ++chargeNo + " of " + name :
                             "the price allowance " + ++allowanceNo + " of " + name;
        if (adjustment.isCharge()) {
          charges = true;
          if (extended) {
            discount = discount.subtract(amount);
            parts.add(new GrossPriceAdjustment(true,
                                               amount,
                                               adjustment.getReasonCode(),
                                               adjustment.getReason()));
          } else {
            lost("IZUGFeRDExportableProduct.getCharges",
                 amount,
                 subject,
                 "EN 16931 has no charge on the gross price: the charge " + amount.toPlainString() + " of " + name + " is part of the net price");
          }
        } else {
          discount = discount.add(amount);
          if (extended) {
            parts.add(new GrossPriceAdjustment(false,
                                               amount,
                                               adjustment.getReasonCode(),
                                               adjustment.getReason()));
          } else if (summed) {
            lost("IZUGFeRDExportableProduct.getAllowances",
                 amount,
                 subject,
                 "EN 16931 has one price discount: the price allowances of " + name + " are summed up, their amounts, reasons, and percentages need the profile EXTENDED");
          }
        }
        if (extended) {
          lost("IZUGFeRDAllowanceCharge.getPercent",
               adjustment.getPercent(),
               subject,
               "the gross price adjustments of EXTENDED have no percentage: " + subject + " keeps its calculated amount " + amount.toPlainString() + " only");
        } else if (adjustment.isCharge() || summed) {
          String reason = "EN 16931 has no reason or percentage of " + subject + ", which needs the profile EXTENDED";
          lost("IZUGFeRDAllowanceCharge.getReason",
               adjustment.getReason(),
               subject,
               reason);
          lost("IZUGFeRDAllowanceCharge.getReasonCode",
               adjustment.getReasonCode(),
               subject,
               reason);
          lost("IZUGFeRDAllowanceCharge.getPercent",
               adjustment.getPercent(),
               subject,
               reason);
        }
      }
      // A single allowance without reason is the plain price discount (BT-147), as when reading.
      boolean plain = parts.size() == 1 && !parts.getFirst()
                                                .charge() && parts.getFirst()
                                                                  .reason() == null && parts.getFirst()
                                                                                            .reasonCode() == null;
      if (!plain) {
        parts.forEach(lineExtension::addPriceAdjustment);
      }
      if (!extended && discount.signum() == 0) {
        return;
      }
      if (!extended && charges) {
        // The gross price is the net price plus the discount (BT-148 = BT-146 + BT-147).
        builder.grossPrice(price(calculated.getPrice()
                                           .add(discount)))
               .priceDiscount(discount);
        return;
      }
      builder.grossPrice(item.getPrice())
             .priceDiscount(discount);
    }

    /**
     * A price as exact as Mustang calculates it (up to 18 decimals), without trailing zeros, at
     * least in cents.
     */
    private static BigDecimal price(BigDecimal value) {
      BigDecimal exact = value.stripTrailingZeros();
      return exact.scale() < 2 ?
             exact.setScale(2) :
             exact;
    }

    private void lineNotes(IZUGFeRDExportableItem item,
                           InvoiceLine.Builder builder,
                           LineExtension.Builder lineExtension,
                           String name) {
      var texts = new ArrayList<>(list(item.getNotes()));
      var coded = new ArrayList<IncludedNote>();
      for (IncludedNote note : list(item.getNotesWithSubjectCode())) {
        texts.add(note.getContent());
        if (note.getSubjectCode() != null) {
          coded.add(note);
        }
      }
      if (texts.isEmpty()) {
        return;
      }
      // EN 16931 has one line note (BT-127), EXTENDED one subject code for it.
      builder.note(String.join("\n",
                               texts));
      // The code belongs to the joined note only if every text has it.
      boolean single = coded.size() == texts.size() && coded.stream()
                                                            .map(IncludedNote::getSubjectCode)
                                                            .distinct()
                                                            .count() == 1;
      if (single) {
        String subjectCode = coded.getFirst()
                                  .getSubjectCode()
                                  .name();
        onlyExtended("IZUGFeRDExportableItem.getNotesWithSubjectCode",
                     subjectCode,
                     "the subject code of the note of " + name,
                     () -> lineExtension.noteSubjectCode(subjectCode));
        return;
      }
      for (IncludedNote note : coded) {
        lost("IZUGFeRDExportableItem.getNotesWithSubjectCode",
             note.getSubjectCode()
                 .name(),
             "the note \"" + note.getContent() + "\" of " + name,
             "the notes of " + name + " are joined into one line note, which has one subject code for its whole text");
      }
    }

    private void lineReferences(IZUGFeRDExportableItem item,
                                LineExtension.Builder lineExtension,
                                String name) {
      onlyExtended("IZUGFeRDExportableItem.getSellerOrderReferencedDocument",
                   reference(item.getSellerOrderReferencedDocument()),
                   "the seller order of " + name,
                   () -> lineExtension.sellerOrderReference(reference(item.getSellerOrderReferencedDocument())));
      onlyExtended("IZUGFeRDExportableItem.getContractReferencedDocument",
                   reference(item.getContractReferencedDocument()),
                   "the contract of " + name,
                   () -> lineExtension.contractReference(reference(item.getContractReferencedDocument())));
      onlyExtended("IZUGFeRDExportableItem.getDespatchAdviceReferencedDocument",
                   reference(item.getDespatchAdviceReferencedDocument()),
                   "the despatch advice of " + name,
                   () -> lineExtension.despatchAdviceReference(reference(item.getDespatchAdviceReferencedDocument())));
      DocumentReference deliveryNote = reference(item.getDeliveryNoteReferencedDocument());
      if (deliveryNote == null && item.getDeliveryNoteReferencedDocumentID() != null) {
        deliveryNote = new DocumentReference(item.getDeliveryNoteReferencedDocumentID(),
                                             item.getDeliveryNoteReferencedDocumentLineID(),
                                             date(item.getDeliveryNoteReferencedDocumentDate()));
      }
      DocumentReference note = deliveryNote;
      onlyExtended("IZUGFeRDExportableItem.getDeliveryNoteReferencedDocument",
                   note,
                   "the delivery note of " + name,
                   () -> lineExtension.deliveryNoteReference(note));
      for (var reference : concat(item.getAdditionalReferences(),
                                  item.getReferencedDocuments())) {
        onlyExtended("IZUGFeRDExportableItem.getAdditionalReferences",
                     reference.getIssuerAssignedID(),
                     "the referenced document " + reference.getIssuerAssignedID() + " of " + name,
                     () -> lineExtension.addAdditionalReference(new TypedDocumentReference(reference.getIssuerAssignedID(),
                                                                                           reference.getTypeCode(),
                                                                                           reference.getReferenceTypeCode())));
      }
      var lineSeller = item.getLineSeller();
      onlyExtended("IZUGFeRDExportableItem.getLineSeller",
                   lineSeller,
                   "the item seller of " + name,
                   () -> lineExtension.itemSeller(party(lineSeller,
                                                        "item seller of " + name,
                                                        false)));
    }

    private void hierarchy(IZUGFeRDExportableItem item,
                           LineExtension.Builder lineExtension,
                           String name) {
      String     code    = item.getLineStatusReasonCode();
      LineSubtype subtype = code == null ?
                           null :
                           LineSubtype.valueOf(code);
      if (subtype == null && !item.isCalculationRelevant()) {
        subtype = LineSubtype.INFORMATION;
      }
      LineSubtype result = subtype;
      onlyExtended("IZUGFeRDExportableItem.getParentLineID",
                   firstOf(item.getParentLineID(),
                           result),
                   "the line hierarchy of " + name + " (XRechnung sub-lines are nested lines instead)",
                   () -> lineExtension.parentLineId(item.getParentLineID())
                                      .subtype(result));
    }

    private String orderLine(IZUGFeRDExportableItem item) {
      if (item.getBuyerOrderReferencedDocumentLineID() != null) {
        return item.getBuyerOrderReferencedDocumentLineID();
      }
      var order = item.getBuyerOrderReferencedDocument();
      return order == null ?
             null :
             order.getLineID();
    }

    // Allowances, charges, payment

    private DocumentAdjustment adjustment(IZUGFeRDAllowanceCharge adjustment) {
      unsupported(adjustment,
                  IZUGFeRDAllowanceCharge.class,
                  "the document allowance or charge " + adjustment.getReason());
      var builder = DocumentAdjustment.create()
                                      .charge(adjustment.isCharge())
                                      .amount(adjustment.getTotalAmount(calculator())
                                                        .setScale(2,
                                                                  RoundingMode.HALF_UP))
                                      .baseAmount(adjustment.getBasisAmount())
                                      .percentage(adjustment.getBasisAmount() == null ?
                                                  null :
                                                  adjustment.getPercent())
                                      .reason(adjustment.getReason())
                                      .reasonCode(adjustment.getReasonCode())
                                      .tax(tax(adjustment.getCategoryCode(),
                                               adjustment.getTaxPercent(),
                                               adjustment.getTaxExemptionReason(),
                                               adjustment.getTaxExemptionReasonCode()));
      var    built   = builder.build();
      String subject = "the document " + (adjustment.isCharge() ?
                                          "charge " :
                                          "allowance ") + (adjustment.getReason() == null ?
                                                           built.amount()
                                                                .toPlainString() :
                                                           adjustment.getReason());
      // EN 16931 states a percentage with its base amount only (BR-CO-21); no base is invented.
      lost("IZUGFeRDAllowanceCharge.getPercent",
           adjustment.getBasisAmount() == null ?
           adjustment.getPercent() :
           null,
           subject,
           "the percentage of " + subject + " needs its base amount; the calculated amount " + built.amount()
                                                                                                   .toPlainString() + " is kept");
      if (adjustment.getSequenceNumeric() != null || adjustment.getBasisQuantity() != null) {
        if (extended) {
          builder.extended(new AdjustmentExtension(adjustment.getSequenceNumeric(),
                                                   adjustment.getBasisQuantity(),
                                                   null));
          return builder.build();
        }
        String reason = "sequence number and base quantity of " + subject + " need the profile EXTENDED";
        lost("IZUGFeRDAllowanceCharge.getSequenceNumeric",
             adjustment.getSequenceNumeric(),
             subject,
             reason);
        lost("IZUGFeRDAllowanceCharge.getBasisQuantity",
             adjustment.getBasisQuantity(),
             subject,
             reason);
      }
      return built;
    }

    private TransactionCalculator calculator() {
      if (calculator == null) {
        calculator = new TransactionCalculator(t);
      }
      return calculator;
    }

    private PaymentInstruction payment() {
      var settlements = list(t.getTradeSettlement());
      var payments    = settlements.stream()
                                   .filter(IZUGFeRDTradeSettlementPayment.class::isInstance)
                                   .map(IZUGFeRDTradeSettlementPayment.class::cast)
                                   .toList();
      var debits      = settlements.stream()
                                   .filter(IZUGFeRDTradeSettlementDebit.class::isInstance)
                                   .map(IZUGFeRDTradeSettlementDebit.class::cast)
                                   .toList();
      for (var other : settlements) {
        if (!(other instanceof IZUGFeRDTradeSettlementPayment) && !(other instanceof IZUGFeRDTradeSettlementDebit)) {
          lost("IExportableTransaction.getTradeSettlement",
               other.getClass()
                    .getSimpleName(),
               "the settlement " + other.getClass()
                                         .getSimpleName() + " has no counterpart; supported are credit transfers and direct debits");
        }
      }
      if (payments.isEmpty() && debits.isEmpty()) {
        lost("IExportableTransaction.getCreditorReferenceID",
             t.getCreditorReferenceID(),
             "a creditor identifier needs a direct debit");
        return null;
      }
      var builder = PaymentInstruction.create()
                                      .remittanceInformation(t.getPaymentReference());
      if (!payments.isEmpty()) {
        var first = payments.getFirst();
        builder.meansCode(PaymentMeansCode.of(first.getPaymentMeansCode()))
               .meansText(first.getPaymentMeansInformation() != null ?
                          first.getPaymentMeansInformation() :
                          first.getOwnPaymentInfoText());
        for (var payment : payments) {
          unsupported(payment,
                      IZUGFeRDTradeSettlementPayment.class,
                      "a credit transfer");
          if (payment.getOwnIBAN() != null) {
            builder.addCreditTransfer(new CreditTransfer(payment.getOwnIBAN(),
                                                         payment.getAccountName(),
                                                         payment.getOwnBIC()));
          }
          if (payment != first && !Objects.equals(payment.getPaymentMeansCode(),
                                                  first.getPaymentMeansCode())) {
            lost("IZUGFeRDTradeSettlementPayment.getPaymentMeansCode",
                 payment.getPaymentMeansCode(),
                 "EN 16931 has one payment means code per invoice; " + first.getPaymentMeansCode() + " is used");
          }
        }
        for (var debit : debits) {
          lost("IExportableTransaction.getTradeSettlement",
               firstOf(debit.getMandate(),
                       debit.getIBAN()),
               "the invoice has a credit transfer; EN 16931 has one payment means per invoice, the direct debit is lost");
        }
        lost("IExportableTransaction.getCreditorReferenceID",
             t.getCreditorReferenceID(),
             "a creditor identifier needs a direct debit");
        return builder.build();
      }
      var debit = debits.getFirst();
      unsupported(debit,
                  IZUGFeRDTradeSettlementDebit.class,
                  "the direct debit");
      return builder.meansCode(PaymentMeansCode.of(debit.getPaymentMeansCode()))
                    .meansText(debit.getPaymentMeansInformation())
                    .directDebit(new DirectDebit(debit.getMandate(),
                                                 t.getCreditorReferenceID(),
                                                 debit.getIBAN()))
                    .build();
    }

    private void terms(Invoice.Builder builder,
                       ExtendedInformation.Builder info) {
      var    terms       = t.getPaymentTerms();
      if (terms != null) {
        unsupported(terms,
                    IZUGFeRDPaymentTerms.class,
                    "the payment terms");
      }
      // As in Mustang's export: the payment terms object wins over description and due date.
      String description = terms == null ?
                           t.getPaymentTermDescription() :
                           terms.getDescription();
      Date   due         = terms == null ?
                           t.getDueDate() :
                           terms.getDueDate();
      if (terms != null) {
        lost("IExportableTransaction.getPaymentTermDescription",
             t.getPaymentTermDescription() != null && !t.getPaymentTermDescription()
                                                       .equals(terms.getDescription()) ?
             t.getPaymentTermDescription() :
             null,
             "the payment terms object (getPaymentTerms) has another description, which Mustang writes instead");
        lost("IExportableTransaction.getDueDate",
             t.getDueDate() != null && !Objects.equals(date(t.getDueDate()),
                                                       date(terms.getDueDate())) ?
             t.getDueDate() :
             null,
             "the payment terms object (getPaymentTerms) has another due date, which Mustang writes instead");
      }
      builder.dueDate(date(due));
      var discounts = new ArrayList<CashDiscount>();
      if (terms != null && terms.getDiscountTerms() != null) {
        var discount = terms.getDiscountTerms();
        unsupported(discount,
                    IZUGFeRDPaymentDiscountTerms.class,
                    "the cash discount terms");
        cashDiscount(discount,
                     discounts);
      }
      for (var discount : list(t.getCashDiscounts())) {
        discounts.addAll(CashDiscount.parse(discount.getAsXRechnung())
                                     .discounts());
      }
      // Mustang's extended payment terms start with getPaymentTerms; Mustang writes the further ones
      // as additional payment terms, which only the profile EXTENDED has.
      var extendedTerms = list(t.getExtendedPaymentTerms());
      for (int index = 0; index < extendedTerms.size(); index++) {
        var term = extendedTerms.get(index);
        if (index == 0) {
          // Implementations may return a new object on every call: compare the values, not the
          // instances. Values of unknown getters of a copy are reported by unsupported.
          if (term != null && term != terms) {
            unsupported(term,
                        IZUGFeRDPaymentTerms.class,
                        "the first extended payment term");
          }
          lost("IExportableTransaction.getExtendedPaymentTerms",
               term != null && !sameTerms(term,
                                          terms) ?
               firstOf(term.getDescription(),
                       term.getDueDate(),
                       term.getDiscountTerms(),
                       "-") :
               null,
               "Mustang writes the first extended payment term as getPaymentTerms; this one differs from getPaymentTerms and is not written by Mustang either");
          continue;
        }
        String name = "the payment term " + (index + 1) + (term.getDescription() == null ?
                                                           "" :
                                                           " (" + term.getDescription() + ")");
        unsupported(term,
                    IZUGFeRDPaymentTerms.class,
                    name);
        if (!extended) {
          lost("IExportableTransaction.getExtendedPaymentTerms",
               name,
               name + " is an additional payment term, which needs the profile EXTENDED");
          continue;
        }
        var adjustment = paymentTermsDiscount(term.getDiscountTerms(),
                                              name);
        if (term.getDescription() == null && term.getDueDate() == null && adjustment == null) {
          continue;
        }
        info.addPaymentTerm(new PaymentTerm(term.getDescription(),
                                            date(term.getDueDate()),
                                            null,
                                            null,
                                            adjustment));
      }
      builder.paymentTerms(description)
             .cashDiscounts(discounts);
    }

    /**
     * The cash discount of the payment terms (BT-20), whose period counts from the issue date. A
     * period from another base date keeps its end; a period in another unit than days is not
     * converted.
     */
    private void cashDiscount(IZUGFeRDPaymentDiscountTerms discount,
                              List<CashDiscount> discounts) {
      String     subject = "the cash discount of the payment terms";
      int        period  = discount.getBasePeriodMeasure();
      String     unit    = discount.getBasePeriodUnitCode();
      BigDecimal percent = discount.getCalculationPercentage();
      LocalDate  base    = date(discount.getBaseDate());
      LocalDate  issue   = date(t.getIssueDate());
      if (unit == null && period == 0 && base == null) {
        lost("IZUGFeRDPaymentDiscountTerms.getCalculationPercentage",
             percent,
             subject,
             "the cash discount has no period; a cash discount of the payment terms (BT-20) needs one in days");
        return;
      }
      if (!"DAY".equals(unit)) {
        // A month has no fixed number of days.
        String reason = "cash discount periods are counted in days; the period of " + period + " " + unit + " is not converted";
        lost("IZUGFeRDPaymentDiscountTerms.getBasePeriodUnitCode",
             unit,
             subject,
             reason);
        lost("IZUGFeRDPaymentDiscountTerms.getBasePeriodMeasure",
             period,
             subject,
             reason);
        lost("IZUGFeRDPaymentDiscountTerms.getCalculationPercentage",
             percent,
             subject,
             reason);
        lost("IZUGFeRDPaymentDiscountTerms.getBaseDate",
             base,
             subject,
             reason);
        return;
      }
      if (base == null || base.equals(issue)) {
        discounts.add(CashDiscount.of(period,
                                      percent));
        return;
      }
      LocalDate end  = base.plusDays(period);
      long      days = issue == null ?
                       -1 :
                       ChronoUnit.DAYS.between(issue,
                                               end);
      if (days >= 0) {
        // The same end of the period, counted from the issue date.
        discounts.add(CashDiscount.of(Math.toIntExact(days),
                                      percent));
        lost("IZUGFeRDPaymentDiscountTerms.getBaseDate",
             base,
             subject,
             "the cash discount period counts from the issue date: " + period + " days from " + base + " end on " + end + " and are written as " + days + " days from the issue date " + issue);
        return;
      }
      String reason = "the cash discount period of " + period + " days from " + base + " ends before the issue date and cannot count from it";
      lost("IZUGFeRDPaymentDiscountTerms.getBaseDate",
           base,
           subject,
           reason);
      lost("IZUGFeRDPaymentDiscountTerms.getBasePeriodMeasure",
           period,
           subject,
           reason);
      lost("IZUGFeRDPaymentDiscountTerms.getCalculationPercentage",
           percent,
           subject,
           reason);
    }

    /**
     * Returns true if two payment terms state the same values, as Mustang writes them:
     * description, due date, and cash discount (percentage, base date, period, and unit). Dates
     * are compared as the dates of the invoice, i.e. in the configured time zone.
     */
    private boolean sameTerms(IZUGFeRDPaymentTerms term,
                              IZUGFeRDPaymentTerms other) {
      if (term == other) {
        return true;
      }
      if (term == null || other == null) {
        return false;
      }
      return Objects.equals(term.getDescription(),
                            other.getDescription()) && Objects.equals(date(term.getDueDate()),
                                                                      date(other.getDueDate())) && sameDiscount(term.getDiscountTerms(),
                                                                                                                other.getDiscountTerms());
    }

    private boolean sameDiscount(IZUGFeRDPaymentDiscountTerms discount,
                                 IZUGFeRDPaymentDiscountTerms other) {
      if (discount == other) {
        return true;
      }
      if (discount == null || other == null) {
        return false;
      }
      BigDecimal percent      = discount.getCalculationPercentage();
      BigDecimal otherPercent = other.getCalculationPercentage();
      boolean    samePercent  = percent == null ?
                                otherPercent == null :
                                otherPercent != null && percent.compareTo(otherPercent) == 0;
      return samePercent && Objects.equals(date(discount.getBaseDate()),
                                           date(other.getBaseDate())) && discount.getBasePeriodMeasure() == other.getBasePeriodMeasure() && Objects.equals(discount.getBasePeriodUnitCode(),
                                                                                                                                                         other.getBasePeriodUnitCode());
    }

    /**
     * The cash discount of an additional payment term as Mustang writes it: base date and period
     * if the base date is set, the grand total as base amount, and the percentage.
     */
    private PaymentTermsAdjustment paymentTermsDiscount(IZUGFeRDPaymentDiscountTerms discount,
                                                        String name) {
      if (discount == null) {
        return null;
      }
      unsupported(discount,
                  IZUGFeRDPaymentDiscountTerms.class,
                  "the cash discount of " + name);
      Integer days = null;
      if (discount.getBaseDate() != null) {
        if ("DAY".equals(discount.getBasePeriodUnitCode())) {
          days = discount.getBasePeriodMeasure();
        } else {
          String reason = "the cash discount period of " + name + " is counted in days; " + discount.getBasePeriodMeasure() + " " + discount.getBasePeriodUnitCode() + " are not converted";
          lost("IZUGFeRDPaymentDiscountTerms.getBasePeriodUnitCode",
               discount.getBasePeriodUnitCode(),
               "the cash discount of " + name,
               reason);
          lost("IZUGFeRDPaymentDiscountTerms.getBasePeriodMeasure",
               discount.getBasePeriodMeasure(),
               "the cash discount of " + name,
               reason);
        }
      }
      return new PaymentTermsAdjustment(date(discount.getBaseDate()),
                                        days,
                                        calculator().getGrandTotal()
                                                    .setScale(2,
                                                              RoundingMode.HALF_UP),
                                        discount.getCalculationPercentage(),
                                        null);
    }

    // References, notes, attachments

    private References references() {
      var builder = References.create()
                              .purchaseOrderReference(id(t.getBuyerOrderReferencedDocument()))
                              .salesOrderReference(id(t.getSellerOrderReferencedDocument()))
                              .contractReference(id(t.getContractReferencedDocument()))
                              .projectReference(t.getSpecifiedProcuringProjectID())
                              .despatchAdviceReference(t.getDespatchAdviceReferencedDocument() != null ?
                                                       id(t.getDespatchAdviceReferencedDocument()) :
                                                       t.getDespatchAdviceReferencedDocumentID())
                              .tenderOrLotReference(id(t.getTenderReferencedDocument()))
                              // Mustang after 2.26.0: the buyer accounting reference (BT-19).
                              .buyerAccountingReference(newer(t,
                                                              IExportableTransaction.class,
                                                              "getAccountingReference",
                                                              String.class));
      var object = t.getObjectIdentifierReferencedDocument();
      if (object != null) {
        builder.invoicedObjectId(object.getIssuerAssignedID())
               .invoicedObjectSchemeId(object.getReferenceTypeCode());
      }
      String order = "the purchase order reference " + Objects.requireNonNullElse(id(t.getBuyerOrderReferencedDocument()),
                                                                                  "");
      lost("IExportableTransaction.getBuyerOrderReferencedDocumentIssueDateTime",
           t.getBuyerOrderReferencedDocumentIssueDateTime(),
           order.strip(),
           "EN 16931 has no date of the purchase order reference");
      lost("IExportableTransaction.getBuyerOrderReferencedDocument",
           issueDate(t.getBuyerOrderReferencedDocument()),
           order.strip(),
           "EN 16931 has no date of the purchase order reference");
      lost("IExportableTransaction.getContractReferencedDocument",
           issueDate(t.getContractReferencedDocument()),
           ("the contract reference " + Objects.requireNonNullElse(id(t.getContractReferencedDocument()),
                                                                   "")).strip(),
           "EN 16931 has no date of the contract reference");
      lost("IExportableTransaction.getSpecifiedProcuringProjectName",
           t.getSpecifiedProcuringProjectName(),
           ("the project " + Objects.requireNonNullElse(t.getSpecifiedProcuringProjectID(),
                                                        "")).strip(),
           "EN 16931 has no project name");
      var references = builder.build();
      return references.equals(References.create()
                                         .build()) ?
             null :
             references;
    }

    private void precedingInvoices(Invoice.Builder builder) {
      var documents = list(t.getInvoiceReferencedDocuments());
      if (documents.isEmpty() && t.getInvoiceReferencedDocumentID() != null) {
        builder.addPrecedingInvoiceReference(new PrecedingInvoiceReference(t.getInvoiceReferencedDocumentID(),
                                                                           date(t.getInvoiceReferencedIssueDate())));
      }
      for (var document : documents) {
        builder.addPrecedingInvoiceReference(new PrecedingInvoiceReference(document.getIssuerAssignedID(),
                                                                           date(document.getFormattedIssueDateTime())));
      }
    }

    private DocumentReference deliveryNote() {
      var document = reference(t.getDeliveryNoteReferencedDocument());
      if (document == null && t.getDeliveryNoteReferencedDocumentID() != null) {
        document = new DocumentReference(t.getDeliveryNoteReferencedDocumentID(),
                                         null,
                                         date(t.getDeliveryNoteReferencedDocumentDate()));
      }
      return document;
    }

    private void notes(Invoice.Builder builder) {
      for (String note : list(t.getNotes())) {
        builder.addNote(note);
      }
      for (IncludedNote note : list(t.getNotesWithSubjectCode())) {
        builder.addNote(new InvoiceNote(note.getSubjectCode() == null ?
                                        null :
                                        note.getSubjectCode()
                                            .name(),
                                        note.getContent(),
                                        null));
      }
      if (t.rebateAgreementExists()) {
        // The note Mustang writes for rebate agreements.
        builder.addNote(new InvoiceNote("AAK",
                                        "Es bestehen Rabatt- und Bonusvereinbarungen.",
                                        null));
      }
      if (t.getOwnOrganisationFullPlaintextInfo() != null) {
        builder.addNote(new InvoiceNote("REG",
                                        t.getOwnOrganisationFullPlaintextInfo(),
                                        null));
      }
      if (t.getSubjectNote() != null) {
        builder.addNote(t.getSubjectNote());
      }
    }

    private void supportingDocuments(Invoice.Builder builder) {
      for (FileAttachment file : list(t.getAdditionalReferencedDocuments())) {
        builder.addSupportingDocument(AdditionalSupportingDocument.create()
                                                                  .reference(file.getFilename())
                                                                  .description(file.getDescription())
                                                                  .attachment(Attachment.create()
                                                                                        .content(file.getData())
                                                                                        .mimeCode(file.getMimetype())
                                                                                        .filename(file.getFilename())
                                                                                        .build())
                                                                  .build());
        lost("FileAttachment.getRelation",
             file.getRelation(),
             "the attachment " + file.getFilename(),
             "embedded in the XML, the attachment" + file.getFilename() + " has no relationship; embed it as PdfAttachment to keep it");
      }
      var related = t.getRelatedReferencedDocument();
      if (related != null && related.getIssuerAssignedID() != null) {
        // Mustang writes it as additional referenced document (BG-24) with type code 916.
        builder.addSupportingDocument(AdditionalSupportingDocument.create()
                                                                  .reference(related.getIssuerAssignedID())
                                                                  .description(related.getName())
                                                                  .build());
      }
    }

    // Tax and dates

    private void taxCurrency(Invoice.Builder builder,
                             ExtendedInformation.Builder info) {
      if (t.getTaxCurrency() == null || t.getTaxCurrency()
                                         .equals(t.getCurrency())) {
        return;
      }
      BigDecimal rate = t.getTaxConversionRate();
      if (rate == null) {
        lost("IExportableTransaction.getTaxCurrency",
             t.getTaxCurrency(),
             "the VAT accounting currency",
             "the VAT accounting currency needs the conversion rate for the VAT amount in that currency (BT-111)");
        return;
      }
      // BT-111: the VAT total converted with the rate of the Mustang invoice.
      BigDecimal vat = builder.build()
                              .totals()
                              .taxAmount();
      builder.taxCurrencyCode(CurrencyCode.of(t.getTaxCurrency()))
             .taxAmountInTaxCurrency(vat.multiply(rate)
                                        .setScale(2,
                                                  RoundingMode.HALF_UP));
      // EN 16931 has no conversion rate; EXTENDED keeps rate and date.
      if (extended) {
        info.taxExchangeRate(new TaxExchangeRate(rate,
                                                 date(t.getTaxConversionRateDateTime())));
      } else {
        lost("IExportableTransaction.getTaxConversionRateDateTime",
             t.getTaxConversionRateDateTime(),
             "the conversion rate " + rate.toPlainString() + " from " + t.getCurrency() + " to " + t.getTaxCurrency(),
             "EN 16931 has no date of the conversion rate; it needs the profile EXTENDED");
      }
    }

    private static TaxCategory tax(String code,
                                   BigDecimal rate,
                                   String exemptionReason,
                                   String exemptionReasonCode) {
      var category = DutyTaxFeeCategory.of(code == null ?
                                           "S" :
                                           code);
      return new TaxCategory(category,
                             category == DutyTaxFeeCategory.O ?
                             null :
                             rate,
                             exemptionReason,
                             exemptionReasonCode);
    }

    private DocumentReference reference(IReferencedDocument document) {
      return document == null || document.getIssuerAssignedID() == null ?
             null :
             new DocumentReference(document.getIssuerAssignedID(),
                                   document.getLineID(),
                                   date(document.getFormattedIssueDateTime()));
    }

    private static String id(IReferencedDocument document) {
      return document == null ?
             null :
             document.getIssuerAssignedID();
    }

    private static Date issueDate(IReferencedDocument document) {
      return document == null ?
             null :
             document.getFormattedIssueDateTime();
    }

    private LocalDate date(Date date) {
      if (date == null) {
        return null;
      }
      // java.sql.Date does not support toInstant.
      return Instant.ofEpochMilli(date.getTime())
                    .atZone(zone)
                    .toLocalDate();
    }

    // Losses

    private void onlyExtended(String getter,
                              Object value,
                              String what,
                              Runnable mapping) {
      if (value == null) {
        return;
      }
      if (extended) {
        mapping.run();
      } else {
        losses.add(new MappingLoss(getter,
                                   what + " needs the profile EXTENDED",
                                   what,
                                   original(value)));
      }
    }

    /**
     * The value of a getter that only Mustang versions after {@value MustangGetters#RELEASE} have,
     * or null if the Mustang version at runtime has no such getter.
     */
    private <T> T newer(Object target,
                        Class<?> type,
                        String getter,
                        Class<T> result) {
      Method method;
      try {
        method = type.getMethod(getter);
      } catch (NoSuchMethodException e) {
        // Not (yet) in the interface, but possibly in the class of the newer Mustang version.
        try {
          method = target.getClass()
                         .getMethod(getter);
        } catch (NoSuchMethodException missing) {
          return null;
        }
      }
      Object value;
      try {
        value = invoke(method,
                       target);
      } catch (IllegalStateException e) {
        return null;
      }
      return result.isInstance(value) ?
             result.cast(value) :
             null;
    }

    /**
     * Reports every getter of the Mustang interface at runtime that the adapter does not know and
     * that carries a value: a newer Mustang version may have added it, and its value would be lost.
     */
    private void unsupported(Object target,
                             Class<?> type,
                             String context) {
      var seen = new java.util.HashSet<String>();
      for (Method method : getters(target,
                                   type)) {
        String getter = method.getName();
        if (!seen.add(getter) || MustangGetters.known(type,
                                                      getter) || MustangGetters.known(method.getDeclaringClass(),
                                                                                      getter)) {
          continue;
        }
        Object value;
        try {
          value = invoke(method,
                         target);
        } catch (RuntimeException e) {
          losses.add(new MappingLoss(type.getSimpleName() + "." + getter,
                                     "the adapter, built for Mustang " + MustangGetters.RELEASE + ", does not know this getter of " + context + ", which cannot be read: " + e));
          continue;
        }
        if (hasValue(value)) {
          losses.add(new MappingLoss(type.getSimpleName() + "." + getter,
                                     "the adapter, built for Mustang " + MustangGetters.RELEASE + ", does not know this getter; the value of " + context + " is not carried over"));
        }
      }
    }

    /**
     * The getters of the interface and of its sub-interfaces that the object implements, e.g. of a
     * newer Mustang version.
     */
    private static List<Method> getters(Object target,
                                        Class<?> type) {
      var types = new ArrayList<Class<?>>();
      types.add(type);
      for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
        addInterfaces(current,
                      type,
                      types);
      }
      var result = new ArrayList<Method>();
      for (Class<?> each : types) {
        for (Method method : each.getMethods()) {
          String name = method.getName();
          if (method.getParameterCount() == 0 && method.getReturnType() != void.class && (name.startsWith("get") || name.startsWith("is"))) {
            result.add(method);
          }
        }
      }
      return result;
    }

    private static void addInterfaces(Class<?> current,
                                      Class<?> type,
                                      List<Class<?>> types) {
      for (Class<?> each : current.getInterfaces()) {
        if (type.isAssignableFrom(each) && !types.contains(each)) {
          types.add(each);
        }
        addInterfaces(each,
                      type,
                      types);
      }
    }

    private static Object invoke(Method method,
                                 Object target) {
      try {
        return method.invoke(target);
      } catch (InvocationTargetException e) {
        throw e.getCause() instanceof RuntimeException runtime ?
              runtime :
              new IllegalStateException(e.getCause());
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    }

    private static boolean hasValue(Object value) {
      return switch (value) {
        case null -> false;
        case Boolean flag -> flag;
        case String text -> !text.isBlank();
        case BigDecimal number -> number.signum() != 0;
        case Collection<?> values -> !values.isEmpty();
        case Map<?, ?> values -> !values.isEmpty();
        case Object[] values -> values.length > 0;
        default -> true;
      };
    }

    private void lost(String getter,
                      Object value,
                      String reason) {
      lost(getter,
           value,
           null,
           reason);
    }

    /**
     * Reports a value without counterpart, keeping the original value and what it belongs to.
     */
    private void lost(String getter,
                      Object value,
                      String subject,
                      String reason) {
      if (value != null) {
        losses.add(new MappingLoss(getter,
                                   reason,
                                   subject,
                                   original(value)));
      }
    }

    /**
     * The value as a loss keeps it: a date as the date of the invoice, i.e. in the time zone of the
     * adapter.
     */
    private Object original(Object value) {
      return value instanceof Date date ?
             date(date) :
             value;
    }

    /**
     * Reports every value of a party that the target does not carry, each with its own getter:
     * address, identifiers, tax registrations, contact, and legal organization.
     *
     * @param mapped the getters of the party, its contact, and its legal organization that the target
     *               carries
     */
    private void unmapped(IZUGFeRDExportableTradeParty party,
                          String subject,
                          String reason,
                          Set<String> mapped) {
      var values = new LinkedHashMap<String, Object>();
      values.put("getName",
                 party.getName());
      values.put("getStreet",
                 party.getStreet());
      values.put("getAdditionalAddress",
                 party.getAdditionalAddress());
      values.put("getAdditionalAddressExtension",
                 party.getAdditionalAddressExtension());
      values.put("getZIP",
                 party.getZIP());
      values.put("getLocation",
                 party.getLocation());
      values.put("getCountry",
                 party.getCountry());
      values.put("getVATID",
                 party.getVATID());
      values.put("getTaxID",
                 party.getTaxID());
      values.put("getID",
                 party.getID());
      values.put("getGlobalID",
                 party.getGlobalID());
      values.put("getGlobalIDScheme",
                 party.getGlobalIDScheme());
      values.put("getDescription",
                 party.getDescription());
      values.put("getUriUniversalCommunicationID",
                 party.getUriUniversalCommunicationID());
      values.put("getUriUniversalCommunicationIDScheme",
                 party.getUriUniversalCommunicationIDScheme());
      values.forEach((getter, value) -> {
        if (!mapped.contains(getter)) {
          lost("IZUGFeRDExportableTradeParty." + getter,
               value,
               subject,
               reason);
        }
      });
      var contact = party.getContact();
      if (contact != null && !mapped.contains("getContact")) {
        unsupported(contact,
                    IZUGFeRDExportableContact.class,
                    "the contact of " + subject);
        String owner = contactName(contact,
                                   subject);
        lost("IZUGFeRDExportableContact.getName",
             contact.getName(),
             owner,
             reason);
        lost("IZUGFeRDExportableContact.getPhone",
             contact.getPhone(),
             owner,
             reason);
        lost("IZUGFeRDExportableContact.getEMail",
             contact.getEMail(),
             owner,
             reason);
        lost("IZUGFeRDExportableContact.getFax",
             contact.getFax(),
             owner,
             reason);
        contactDetails(contact,
                       owner);
      }
      var legal = party.getLegalOrganisation();
      if (legal != null) {
        unsupported(legal,
                    IZUGFeRDLegalOrganisation.class,
                    "the legal organisation of " + subject);
        if (!mapped.contains("getSchemedID") && legal.getSchemedID() != null) {
          lost("IZUGFeRDLegalOrganisation.getSchemedID",
               legal.getSchemedID()
                    .getID() + (legal.getSchemedID()
                                     .getScheme() == null ?
                                "" :
                                " (scheme " + legal.getSchemedID()
                                                   .getScheme() + ")"),
               subject,
               reason);
        }
        if (!mapped.contains("getTradingBusinessName")) {
          lost("IZUGFeRDLegalOrganisation.getTradingBusinessName",
               legal.getTradingBusinessName(),
               subject,
               reason);
        }
      }
    }

    /**
     * Reports address, VAT ID, and identifier of a contact, which EN 16931 does not have.
     */
    private void contactDetails(IZUGFeRDExportableContact contact,
                                String owner) {
      String reason = "EN 16931 has no address, VAT ID, or identifier of " + owner;
      lost("IZUGFeRDExportableContact.getStreet",
           contact.getStreet(),
           owner,
           reason);
      lost("IZUGFeRDExportableContact.getZIP",
           contact.getZIP(),
           owner,
           reason);
      lost("IZUGFeRDExportableContact.getLocation",
           contact.getLocation(),
           owner,
           reason);
      lost("IZUGFeRDExportableContact.getCountry",
           contact.getCountry(),
           owner,
           reason);
      lost("IZUGFeRDExportableContact.getAdditionalAddress",
           contact.getAdditionalAddress(),
           owner,
           reason);
      lost("IZUGFeRDExportableContact.getVATID",
           contact.getVATID(),
           owner,
           reason);
      lost("IZUGFeRDExportableContact.getID",
           contact.getID(),
           owner,
           reason);
    }

    private static String contactName(IZUGFeRDExportableContact contact,
                                      String subject) {
      return "the contact " + (contact.getName() == null ?
                               "" :
                               contact.getName() + " ") + "of " + subject;
    }
  }

  private static Object firstOf(Object... values) {
    for (Object value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static <T> List<T> list(T[] values) {
    return values == null ?
           List.of() :
           Arrays.asList(values);
  }

  private static <T> List<T> list(Collection<T> values) {
    return values == null ?
           List.of() :
           List.copyOf(values);
  }

  @SafeVarargs
  private static <T> List<T> concat(T[]... arrays) {
    var result = new ArrayList<T>();
    for (T[] array : arrays) {
      result.addAll(list(array));
    }
    return result;
  }

  /**
   * Configuration of an adapter.
   */
  public static final class Builder {
    private ProcessControl processControl = ProcessControl.xrechnung();
    private ZoneId         zone           = ZoneId.systemDefault();

    private Builder() {
    }

    /**
     * The target profile, e.g. {@link ProcessControl#en16931()} or
     * {@link ProcessControl#facturXExtended()}; default XRechnung 3. Its specification identifier
     * becomes BT-24; its business process is used if the Mustang invoice has none.
     *
     * @param processControl the target profile; must not be {@code null}
     * @return this builder
     */
    public Builder processControl(ProcessControl processControl) {
      this.processControl = Objects.requireNonNull(processControl,
                                                   "processControl");
      return this;
    }

    /**
     * The time zone the {@code java.util.Date} values of the application refer to; default the
     * time zone of the JVM, which Mustang uses when it writes dates.
     *
     * @param zone the time zone; must not be {@code null}
     * @return this builder
     */
    public Builder zone(ZoneId zone) {
      this.zone = Objects.requireNonNull(zone,
                                         "zone");
      return this;
    }

    /**
     * Creates the adapter.
     *
     * @return the immutable adapter
     */
    public MustangInvoiceAdapter build() {
      return new MustangInvoiceAdapter(processControl,
                                       zone);
    }
  }
}
