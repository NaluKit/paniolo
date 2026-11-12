<?xml version="1.0" encoding="UTF-8"?>
<!-- Reads a document that is not declared in the profile; validation must refuse it. -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:svrl="http://purl.oclc.org/dsdl/svrl"
                version="2.0">
  <xsl:template match="/">
    <svrl:schematron-output>
      <xsl:value-of select="count(document('../schema/BASIC/FACTUR-X_BASIC.xsd')/*)"/>
    </svrl:schematron-output>
  </xsl:template>
</xsl:stylesheet>
