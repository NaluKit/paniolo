<?xml version="1.0" encoding="UTF-8"?>
<!-- Synthetic stand-in for the compiled Peppol BIS Despatch Advice Schematron: the despatch
     supplier needs an endpoint, as in rule PEPPOL-T16. -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:svrl="http://purl.oclc.org/dsdl/svrl"
                xmlns:da="urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2"
                xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
                version="2.0">
  <xsl:template match="/">
    <svrl:schematron-output>
      <xsl:if test="not(/da:DespatchAdvice/cac:DespatchSupplierParty/cac:Party/cbc:EndpointID)">
        <svrl:failed-assert id="TEST-ENDPOINT" flag="fatal" location="/DespatchAdvice/DespatchSupplierParty">
          <svrl:text>The despatch supplier needs an endpoint.</svrl:text>
        </svrl:failed-assert>
      </xsl:if>
    </svrl:schematron-output>
  </xsl:template>
</xsl:stylesheet>
