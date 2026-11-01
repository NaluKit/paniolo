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
package io.github.nalukit.paniolo.migration;

import io.github.nalukit.paniolo.migration.MustangInterfaces.AllowanceCharge;
import io.github.nalukit.paniolo.migration.MustangInterfaces.CashDiscount;
import io.github.nalukit.paniolo.migration.MustangInterfaces.Contact;
import io.github.nalukit.paniolo.migration.MustangInterfaces.ExportableTransaction;
import io.github.nalukit.paniolo.migration.MustangInterfaces.FileAttachment;
import io.github.nalukit.paniolo.migration.MustangInterfaces.IncludedNote;
import io.github.nalukit.paniolo.migration.MustangInterfaces.Item;
import io.github.nalukit.paniolo.migration.MustangInterfaces.LegalOrganisation;
import io.github.nalukit.paniolo.migration.MustangInterfaces.PaymentDiscountTerms;
import io.github.nalukit.paniolo.migration.MustangInterfaces.PaymentTerms;
import io.github.nalukit.paniolo.migration.MustangInterfaces.Product;
import io.github.nalukit.paniolo.migration.MustangInterfaces.SchemedID;
import io.github.nalukit.paniolo.migration.MustangInterfaces.TradeParty;
import io.github.nalukit.paniolo.migration.MustangInterfaces.TradeSettlementDebit;
import io.github.nalukit.paniolo.migration.MustangInterfaces.TradeSettlementPayment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The existing integration: application objects that implement Mustang's export interfaces, as an
 * ERP connector would. It uses invoice data, payments, cash discounts, attachments, price, line and
 * document allowances and charges, and EXTENDED content.
 */
final class SampleIntegration {
  static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

  private SampleIntegration() {
  }

  static Date date(int year,
                   int month,
                   int day) {
    return Date.from(LocalDate.of(year,
                                  month,
                                  day)
                              .atStartOfDay(ZONE)
                              .toInstant());
  }

  static BigDecimal amount(String value) {
    return new BigDecimal(value);
  }

  static TradeParty party(String name,
                          String street,
                          String zip,
                          String city,
                          String vatId,
                          String globalId,
                          Contact contact,
                          LegalOrganisation legal) {
    return new TradeParty() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getStreet() {
        return street;
      }

      @Override
      public String getZIP() {
        return zip;
      }

      @Override
      public String getLocation() {
        return city;
      }

      @Override
      public String getCountry() {
        return "DE";
      }

      @Override
      public String getVATID() {
        return vatId;
      }

      @Override
      public String getGlobalID() {
        return globalId;
      }

      @Override
      public String getGlobalIDScheme() {
        return globalId == null ?
               null :
               "0088";
      }

      @Override
      public String getUriUniversalCommunicationID() {
        return contact == null ?
               null :
               contact.getEMail();
      }

      @Override
      public String getUriUniversalCommunicationIDScheme() {
        return contact == null ?
               null :
               "EM";
      }

      @Override
      public Contact getContact() {
        return contact;
      }

      @Override
      public LegalOrganisation getLegalOrganisation() {
        return legal;
      }
    };
  }

  static Contact contact(String name,
                         String phone,
                         String email,
                         String fax) {
    return new Contact() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getPhone() {
        return phone;
      }

      @Override
      public String getEMail() {
        return email;
      }

      @Override
      public String getFax() {
        return fax;
      }
    };
  }

  static LegalOrganisation register(String id,
                                    String scheme,
                                    String tradingName) {
    return new LegalOrganisation() {
      @Override
      public SchemedID getSchemedID() {
        return new SchemedID() {
          @Override
          public String getScheme() {
            return scheme;
          }

          @Override
          public String getID() {
            return id;
          }
        };
      }

      @Override
      public String getTradingBusinessName() {
        return tradingName;
      }
    };
  }

  static AllowanceCharge allowanceCharge(boolean charge,
                                         String amount,
                                         String basis,
                                         String percent,
                                         String reason,
                                         String reasonCode) {
    return new AllowanceCharge() {
      @Override
      public BigDecimal getTotalAmount() {
        return amount(amount);
      }

      @Override
      public BigDecimal getPercent() {
        return percent == null ?
               null :
               amount(percent);
      }

      @Override
      public BigDecimal getBasisAmount() {
        return basis == null ?
               null :
               amount(basis);
      }

      @Override
      public String getReason() {
        return reason;
      }

      @Override
      public String getReasonCode() {
        return reasonCode;
      }

      @Override
      public BigDecimal getTaxPercent() {
        return amount("19");
      }

      @Override
      public boolean isCharge() {
        return charge;
      }
    };
  }

  static Product product(String name,
                         String description,
                         String unit,
                         String sellerId,
                         AllowanceCharge... priceAllowances) {
    return new Product() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getDescription() {
        return description;
      }

      @Override
      public String getUnit() {
        return unit;
      }

      @Override
      public BigDecimal getVATPercent() {
        return amount("19");
      }

      @Override
      public String getSellerAssignedID() {
        return sellerId;
      }

      @Override
      public String getBuyerAssignedID() {
        return sellerId == null ?
               null :
               "K-" + sellerId;
      }

      @Override
      public String getGlobalID() {
        return sellerId == null ?
               null :
               "4012345000009";
      }

      @Override
      public String getGlobalIDScheme() {
        return sellerId == null ?
               null :
               "0160";
      }

      @Override
      public String getCountryOfOrigin() {
        return sellerId == null ?
               null :
               "DE";
      }

      @Override
      public Map<String, String> getAttributes() {
        if (sellerId == null) {
          return null;
        }
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("Farbe",
                       "blau");
        return attributes;
      }

      @Override
      public AllowanceCharge[] getAllowances() {
        return priceAllowances;
      }
    };
  }

  static Item item(String id,
                   Product product,
                   String price,
                   String quantity,
                   AllowanceCharge... lineAdjustments) {
    return new Item() {
      @Override
      public String getId() {
        return id;
      }

      @Override
      public Product getProduct() {
        return product;
      }

      @Override
      public BigDecimal getPrice() {
        return amount(price);
      }

      @Override
      public BigDecimal getQuantity() {
        return amount(quantity);
      }

      @Override
      public AllowanceCharge[] getItemAllowances() {
        return java.util.Arrays.stream(lineAdjustments)
                               .filter(adjustment -> !adjustment.isCharge())
                               .toArray(AllowanceCharge[]::new);
      }

      @Override
      public AllowanceCharge[] getItemCharges() {
        return java.util.Arrays.stream(lineAdjustments)
                               .filter(AllowanceCharge::isCharge)
                               .toArray(AllowanceCharge[]::new);
      }

      @Override
      public String[] getNotes() {
        return id.equals("1") ?
               new String[] { "Termin nach Absprache",
                              "Anfahrt inklusive" } :
               null;
      }

      @Override
      public Date getDetailedDeliveryPeriodFrom() {
        return date(2026,
                    10,
                    1);
      }

      @Override
      public Date getDetailedDeliveryPeriodTo() {
        return date(2026,
                    10,
                    31);
      }

      @Override
      public String getBuyerOrderReferencedDocumentLineID() {
        return id;
      }

      @Override
      public String getAccountingReference() {
        return "4711-" + id;
      }
    };
  }

  /**
   * The invoice of the ERP connector: two lines (one with price discount and line allowance), a
   * document allowance and charge, a logistics charge, credit transfer, cash discount, attachment.
   */
  static ExportableTransaction invoice() {
    var seller = party("Muster GmbH",
                       "Musterstraße 1",
                       "10115",
                       "Berlin",
                       "DE123456788",
                       "4000001000005",
                       contact("Erika Muster",
                               "+49 30 123456",
                               "rechnung@example.org",
                               "+49 30 123457"),
                       register("HRB 12345 B",
                                "0002",
                                "Muster Shop"));
    var buyer = party("Kunde GmbH",
                      "Kundenweg 2",
                      "20095",
                      "Hamburg",
                      "DE987654321",
                      null,
                      contact("Max Kunde",
                              "+49 40 654321",
                              "einkauf@example.org",
                              null),
                      null);
    var items = new Item[] { item("1",
                                  product("Beratung",
                                          "Workshop zur E-Rechnung",
                                          "HUR",
                                          "B-100"),
                                  "100.00",
                                  "8",
                                  allowanceCharge(false,
                                                  "40.00",
                                                  "800.00",
                                                  "5",
                                                  "Treuerabatt",
                                                  "95")),
                             item("2",
                                  product("Fachbuch",
                                          "Fachbuch E-Rechnung",
                                          "C62",
                                          null,
                                          allowanceCharge(false,
                                                          "5.00",
                                                          null,
                                                          null,
                                                          "Aktionspreis",
                                                          "95")),
                                  "45.00",
                                  "2") };
    return new ExportableTransaction() {
      @Override
      public String getDocumentName() {
        return "RECHNUNG";
      }

      @Override
      public String getNumber() {
        return "R-2026-0815";
      }

      @Override
      public Date getIssueDate() {
        return date(2026,
                    10,
                    29);
      }

      @Override
      public Date getDueDate() {
        return date(2026,
                    11,
                    28);
      }

      @Override
      public Date getDeliveryDate() {
        return date(2026,
                    10,
                    31);
      }

      @Override
      public String getReferenceNumber() {
        return "04011000-12345-03";
      }

      @Override
      public TradeParty getSender() {
        return seller;
      }

      @Override
      public TradeParty getRecipient() {
        return buyer;
      }

      @Override
      public TradeParty getPayee() {
        return party("Factoring AG",
                     "Bankstraße 3",
                     "60311",
                     "Frankfurt",
                     null,
                     "4000001000029",
                     null,
                     register("HRB 999",
                              "0002",
                              null));
      }

      @Override
      public TradeParty getInvoicee() {
        return party("Kunde GmbH Buchhaltung",
                     "Kundenweg 4",
                     "20095",
                     "Hamburg",
                     null,
                     null,
                     null,
                     null);
      }

      @Override
      public Item[] getZFItems() {
        return items;
      }

      @Override
      public AllowanceCharge[] getZFAllowances() {
        return new AllowanceCharge[] { allowanceCharge(false,
                                                       "10.00",
                                                       null,
                                                       null,
                                                       "Rabatt",
                                                       "95") };
      }

      @Override
      public AllowanceCharge[] getZFCharges() {
        return new AllowanceCharge[] { allowanceCharge(true,
                                                       "5.00",
                                                       null,
                                                       null,
                                                       "Verpackung",
                                                       "ABL") };
      }

      @Override
      public AllowanceCharge[] getZFLogisticsServiceCharges() {
        return new AllowanceCharge[] { allowanceCharge(true,
                                                       "7.50",
                                                       null,
                                                       null,
                                                       "Versand",
                                                       null) };
      }

      @Override
      public TradeSettlementPayment[] getTradeSettlementPayment() {
        return new TradeSettlementPayment[] { new TradeSettlementPayment() {
          @Override
          public String getPaymentMeansInformation() {
            return "Überweisung";
          }

          @Override
          public String getOwnIBAN() {
            return "DE89370400440532013000";
          }

          @Override
          public String getOwnBIC() {
            return "COBADEFFXXX";
          }

          @Override
          public String getAccountName() {
            return "Muster GmbH";
          }
        } };
      }

      @Override
      public String getPaymentReference() {
        return "R-2026-0815";
      }

      @Override
      public PaymentTerms getPaymentTerms() {
        return new PaymentTerms() {
          @Override
          public String getDescription() {
            return "Zahlbar innerhalb von 30 Tagen netto.";
          }

          @Override
          public Date getDueDate() {
            return null;
          }

          @Override
          public PaymentDiscountTerms getDiscountTerms() {
            return new PaymentDiscountTerms() {
              @Override
              public BigDecimal getCalculationPercentage() {
                return amount("2");
              }

              @Override
              public Date getBaseDate() {
                return null;
              }

              @Override
              public int getBasePeriodMeasure() {
                return 14;
              }

              @Override
              public String getBasePeriodUnitCode() {
                return "DAY";
              }
            };
          }
        };
      }

      @Override
      public BigDecimal getTotalPrepaidAmount() {
        return amount("100.00");
      }

      @Override
      public String getBuyerOrderReferencedDocumentID() {
        return "PO-2026-17";
      }

      @Override
      public String getBuyerOrderReferencedDocumentIssueDateTime() {
        return "20261001";
      }

      @Override
      public String getContractReferencedDocument() {
        return "V-2026-3";
      }

      @Override
      public String getSpecifiedProcuringProjectID() {
        return "P-42";
      }

      @Override
      public String getSpecifiedProcuringProjectName() {
        return "E-Rechnung Einführung";
      }

      @Override
      public String getDeliveryNoteReferencedDocumentID() {
        return "LS-2026-42";
      }

      @Override
      public Date getDeliveryNoteReferencedDocumentDate() {
        return date(2026,
                    10,
                    30);
      }

      @Override
      public Date getDetailedDeliveryPeriodFrom() {
        return date(2026,
                    10,
                    1);
      }

      @Override
      public Date getDetailedDeliveryPeriodTo() {
        return date(2026,
                    10,
                    31);
      }

      @Override
      public String[] getNotes() {
        return new String[] { "Vielen Dank für Ihren Auftrag." };
      }

      @Override
      public IncludedNote[] getNotesWithSubjectCode() {
        return new IncludedNote[] { new IncludedNote() {
          @Override
          public String getSubjectCode() {
            return "AAI";
          }

          @Override
          public String getContent() {
            return "Lieferung frei Haus.";
          }
        } };
      }

      @Override
      public String getOwnOrganisationFullPlaintextInfo() {
        return "Muster GmbH, Geschäftsführerin Erika Muster, Amtsgericht Charlottenburg HRB 12345 B";
      }

      @Override
      public FileAttachment[] getAdditionalReferencedDocuments() {
        return new FileAttachment[] { new FileAttachment() {
          @Override
          public String getFilename() {
            return "stundenzettel.csv";
          }

          @Override
          public String getMimetype() {
            return "text/csv";
          }

          @Override
          public String getRelation() {
            return "Supplement";
          }

          @Override
          public byte[] getData() {
            return "Datum;Stunden\n2026-10-15;8\n".getBytes(StandardCharsets.UTF_8);
          }

          @Override
          public String getDescription() {
            return "Stundenzettel Oktober";
          }
        } };
      }
    };
  }

  /**
   * A second invoice of the connector, paid by SEPA direct debit and with a cash discount as text.
   */
  static ExportableTransaction directDebitInvoice() {
    var seller = invoice().getSender();
    var buyer  = invoice().getRecipient();
    return new ExportableTransaction() {
      @Override
      public String getNumber() {
        return "R-2026-0816";
      }

      @Override
      public Date getIssueDate() {
        return date(2026,
                    10,
                    29);
      }

      @Override
      public Date getDeliveryDate() {
        return date(2026,
                    10,
                    29);
      }

      @Override
      public String getReferenceNumber() {
        return "04011000-12345-03";
      }

      @Override
      public TradeParty getSender() {
        return seller;
      }

      @Override
      public TradeParty getRecipient() {
        return buyer;
      }

      @Override
      public Item[] getZFItems() {
        return new Item[] { item("1",
                                 product("Wartung",
                                         "Monatliche Wartung",
                                         "MON",
                                         null),
                                 "250.00",
                                 "1") };
      }

      @Override
      public TradeSettlementDebit[] getTradeSettlement() {
        return new TradeSettlementDebit[] { new TradeSettlementDebit() {
          @Override
          public String getIBAN() {
            return "DE02120300000000202051";
          }

          @Override
          public String getMandate() {
            return "MANDAT-1";
          }
        } };
      }

      @Override
      public String getCreditorReferenceID() {
        return "DE98ZZZ09999999999";
      }

      @Override
      public CashDiscount[] getCashDiscounts() {
        return new CashDiscount[] { new CashDiscount() {
          @Override
          public String getAsCII() {
            return "Skonto 2 % bei Zahlung binnen 7 Tagen";
          }

          @Override
          public String getAsXRechnung() {
            return "#SKONTO#TAGE=7#PROZENT=2.00#\n";
          }
        } };
      }

      @Override
      public String getVATDueDateTypeCode() {
        return "29";
      }
    };
  }
}
