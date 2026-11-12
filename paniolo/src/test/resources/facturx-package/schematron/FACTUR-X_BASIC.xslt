<?xml version="1.0" encoding="UTF-8"?>
<!-- Synthetic stand-in for the compiled Factur-X BASIC Schematron: checks the currency against
     the code list in FACTUR-X_BASIC_codedb.xml, read with document() like the official stylesheets. -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:svrl="http://purl.oclc.org/dsdl/svrl"
                xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100"
                version="2.0">
  <xsl:template match="/">
    <svrl:schematron-output>
      <xsl:variable name="currency" select="string(//ram:InvoiceCurrencyCode)"/>
      <xsl:if test="not(document('FACTUR-X_BASIC_codedb.xml')/codedb/cl[@id='currency']/enumeration[@value = $currency])">
        <svrl:failed-assert id="FX-TEST-CURRENCY" flag="fatal" location="//ram:InvoiceCurrencyCode">
          <svrl:text>Currency
            <xsl:value-of select="$currency"/>
            is not in the code list.
          </svrl:text>
        </svrl:failed-assert>
      </xsl:if>
    </svrl:schematron-output>
  </xsl:template>
</xsl:stylesheet>
