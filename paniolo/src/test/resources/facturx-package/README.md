# Synthetic Factur-X package

A minimal package with the folder layout of the official Factur-X release, written for the tests
of `ValidationProfile.facturX`. The files are **not** the official FeRD / FNFE-MPE artifacts:

| File                                     | Purpose                                                                  |
|------------------------------------------|--------------------------------------------------------------------------|
| `schema/BASIC/FACTUR-X_BASIC.xsd`        | Stand-in schema that accepts any CrossIndustryInvoice                    |
| `schematron/FACTUR-X_BASIC.xslt`         | Stand-in Schematron XSLT that checks the currency against the code list  |
| `schematron/FACTUR-X_BASIC_codedb.xml`   | Code list read with `document()`, like the official stylesheets do       |
| `schematron/FORBIDDEN.xslt`              | Reads an undeclared document; validation must refuse it                  |
