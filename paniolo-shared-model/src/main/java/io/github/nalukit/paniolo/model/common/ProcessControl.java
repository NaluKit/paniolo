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
package io.github.nalukit.paniolo.model.common;

import io.github.nalukit.paniolo.model.common.validation.ModelChecks;

/**
 * Business process (BT-23) and specification identifier (BT-24).
 *
 * <p>The factories and constants cover EN 16931:2017 with XRechnung up to 3.0 and Factur-X /
 * ZUGFeRD 2.x. The model accepts any identifier, but the writers reject EN 16931:2026 (e.g.
 * XRechnung 4.0) and the validation reports it as not supported until its official artefacts are
 * published.
 *
 * @param businessProcessId business process identifier (BT-23), or null
 * @param specificationId   specification identifier (BT-24); must not be blank
 */
public record ProcessControl(String businessProcessId, String specificationId) {

  public static final String XRECHNUNG_3             = "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0";
  /**
   * Specification identifier of the XRechnung 3 extension: sub invoice lines, third party payments,
   * and further code values and MIME codes.
   */
  public static final String XRECHNUNG_3_EXTENSION   = XRECHNUNG_3 + "#conformant#urn:xeinkauf.de:kosit:extension:xrechnung_3.0";
  public static final String PEPPOL_BILLING          = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
  /**
   * Specification identifier of plain EN 16931 invoices, e.g. the Factur-X profile EN 16931.
   */
  public static final String EN16931                 = "urn:cen.eu:en16931:2017";
  /**
   * Specification identifier of the Factur-X / ZUGFeRD profile MINIMUM.
   */
  public static final String FACTURX_MINIMUM         = "urn:factur-x.eu:1p0:minimum";
  /**
   * Specification identifier of the Factur-X / ZUGFeRD profile BASIC WL.
   */
  public static final String FACTURX_BASIC_WL        = "urn:factur-x.eu:1p0:basicwl";
  /**
   * Specification identifier of the Factur-X / ZUGFeRD profile BASIC, a subset of EN 16931.
   */
  public static final String FACTURX_BASIC           = "urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:basic";
  /**
   * Specification identifier of the Factur-X / ZUGFeRD profile EXTENDED, an extension of EN 16931.
   */
  public static final String FACTURX_EXTENDED        = "urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended";
  /**
   * Specification identifier of the profile EXTENDED in ZUGFeRD 2.0 and 2.1, which documents of
   * those versions still carry.
   */
  public static final String ZUGFERD_2_EXTENDED      = "urn:cen.eu:en16931:2017#conformant#urn:zugferd.de:2p0:extended";
  /**
   * Specification identifier of the French profile EXTENDED-CTC-FR (XP Z12-012) of the e-invoicing
   * reform, a subset of the Factur-X profile EXTENDED.
   */
  public static final String FACTURX_EXTENDED_CTC_FR = "urn:cen.eu:en16931:2017#conformant#urn.cpro.gouv.fr:1p0:extended-ctc-fr";

  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public ProcessControl {
    // BT-23 is optional in EN 16931; XRechnung requires it via its own rules.
    if (businessProcessId != null) {
      ModelChecks.required(businessProcessId,
                           "businessProcessId");
    }
    ModelChecks.required(specificationId,
                         "specificationId");
  }

  /**
   * XRechnung 3 with the Peppol billing business process, the default of the builder.
   *
   * @return the process control
   */
  public static ProcessControl xrechnung() {
    return create().build();
  }

  /**
   * XRechnung 3 extension with the Peppol billing business process.
   *
   * @return the process control
   */
  public static ProcessControl xrechnungExtension() {
    return create().specificationId(XRECHNUNG_3_EXTENSION)
                   .build();
  }

  /**
   * Plain EN 16931 without business process (BT-23), e.g. for Factur-X / ZUGFeRD profile EN 16931.
   *
   * @return the process control
   */
  public static ProcessControl en16931() {
    return create().businessProcessId(null)
                   .specificationId(EN16931)
                   .build();
  }

  /**
   * Factur-X / ZUGFeRD profile MINIMUM without business process (BT-23), for an invoice without
   * lines with stated totals.
   *
   * @return the process control
   */
  public static ProcessControl facturXMinimum() {
    return create().businessProcessId(null)
                   .specificationId(FACTURX_MINIMUM)
                   .build();
  }

  /**
   * Factur-X / ZUGFeRD profile BASIC WL without business process (BT-23), for an invoice without
   * lines with stated totals.
   *
   * @return the process control
   */
  public static ProcessControl facturXBasicWl() {
    return create().businessProcessId(null)
                   .specificationId(FACTURX_BASIC_WL)
                   .build();
  }

  /**
   * Factur-X / ZUGFeRD profile BASIC without business process (BT-23).
   *
   * @return the process control
   */
  public static ProcessControl facturXBasic() {
    return create().businessProcessId(null)
                   .specificationId(FACTURX_BASIC)
                   .build();
  }

  /**
   * Factur-X / ZUGFeRD profile EXTENDED without business process (BT-23).
   *
   * @return the process control
   */
  public static ProcessControl facturXExtended() {
    return create().businessProcessId(null)
                   .specificationId(FACTURX_EXTENDED)
                   .build();
  }

  /**
   * French profile EXTENDED-CTC-FR with the billing framework (BT-23, cadre de facturation), e.g.
   * {@code B1} for goods or {@code S1} for services, which the French rules (BR-FR-08) require.
   *
   * @param billingFramework the billing framework (BT-23)
   * @return the process control
   */
  public static ProcessControl facturXExtendedCtcFr(String billingFramework) {
    return create().businessProcessId(billingFramework)
                   .specificationId(FACTURX_EXTENDED_CTC_FR)
                   .build();
  }

  /**
   * Starts with the XRechnung 3 / Peppol billing defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Returns true for the specification identifier of the XRechnung 3 extension.
   *
   * @return true for the XRechnung 3 extension
   */
  public boolean isXrechnungExtension() {
    return XRECHNUNG_3_EXTENSION.equals(specificationId);
  }

  /**
   * Returns true for the specification identifiers of the Factur-X profile EXTENDED (also as
   * ZUGFeRD 2.0 / 2.1 identifier) and the French profile EXTENDED-CTC-FR, which carry the
   * information in {@code Invoice.extended()}.
   *
   * @return true for EXTENDED and EXTENDED-CTC-FR
   */
  public boolean isExtended() {
    return FACTURX_EXTENDED.equals(specificationId) || ZUGFERD_2_EXTENDED.equals(specificationId) || FACTURX_EXTENDED_CTC_FR.equals(specificationId);
  }

  /**
   * Returns true for a French multi-vendor invoice: profile EXTENDED-CTC-FR with the billing
   * framework (BT-23) S8, B8, M8, S9, B9, or M9. Its VAT breakdown has one entry per sub-invoice,
   * category, and rate, told apart by the sub-invoice number at the start of the exemption reason
   * (BR-FR-MV-08, BR-FR-MV-09); see {@link TaxCategory#subInvoiceNumber()}.
   *
   * @return true for a French multi-vendor invoice
   */
  public boolean isFrenchMultiVendor() {
    return FACTURX_EXTENDED_CTC_FR.equals(specificationId) && businessProcessId != null && businessProcessId.matches("[SBM][89]");
  }



  /**
   * Named construction of an immutable process control.
   *
   * <p>Mutable construction state for {@link ProcessControl}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String businessProcessId = PEPPOL_BILLING;
    private String specificationId   = XRECHNUNG_3;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link ProcessControl#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link ProcessControl#businessProcessId() businessProcessId}.
     *
     * @param businessProcessId business process identifier (BT-23), or null
     * @return this builder
     */
    public Builder businessProcessId(String businessProcessId) {
      this.businessProcessId = businessProcessId;
      return this;
    }

    /**
     * Sets {@link ProcessControl#specificationId() specificationId}.
     *
     * @param specificationId specification identifier (BT-24); must not be blank
     * @return this builder
     */
    public Builder specificationId(String specificationId) {
      this.specificationId = specificationId;
      return this;
    }

    /**
     * Constructs a new {@link ProcessControl} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public ProcessControl build() {
      return new ProcessControl(businessProcessId,
                                specificationId);
    }
  }
}
