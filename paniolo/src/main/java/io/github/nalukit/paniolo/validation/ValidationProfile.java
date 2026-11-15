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
package io.github.nalukit.paniolo.validation;

import io.github.nalukit.paniolo.xml.util.XmlDocuments;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Local XSD and compiled Schematron XSLT artifacts. Treat these as trusted application code.
 * Paths and their imports must stay inside resourceRoot. No resources are downloaded at runtime.
 *
 * @param schema                XSD, or null for a profile with Schematron only
 * @param documents             lookup documents the stylesheets read with {@code document()} while validating,
 *                              e.g. the code lists of the Factur-X Schematron; all other documents are refused
 * @param name                  profile name used in validation results; required
 * @param resourceRoot          root directory allowed for local schema and stylesheet resources; normalized to an absolute path
 * @param schematronStylesheets compiled Schematron XSLT files, in execution order; an empty list for schema-only validation
 * @param severityOverrides     rule identifiers mapped to replacement severities; an empty map for the original flags
 */
public record ValidationProfile(String name, Path resourceRoot, Path schema, List<Path> schematronStylesheets, Map<String, Severity> severityOverrides, List<Path> documents) {
  /**
   * Key in {@link #severityOverrides()} for the severity of successful reports without flag. Older
   * Schematrons such as ZUGFeRD 1.0 report unused elements this way; by default such a report is an
   * error, like every finding without flag.
   */
  public static final  String       UNFLAGGED_REPORTS = "#unflagged-report";
  /**
   * Namespace of the KoSIT {@code scenarios.xml}.
   */
  private static final String       SCENARIOS_NS      = "http://www.xoev.de/de/validator/framework/1/scenarios";
  /**
   * A string literal of an XPath expression, in single or double quotes.
   */
  private static final Pattern      LITERAL           = Pattern.compile("'([^']*)'|\"([^\"]*)\"");
  /**
   * Factur-X / ZUGFeRD profiles with their own XSD and Schematron in the official package.
   */
  private static final List<String> FACTURX_LEVELS    = List.of("MINIMUM",
                                                                "BASIC-WL",
                                                                "BASIC",
                                                                "EN16931",
                                                                "EXTENDED");

  /**
   * Normalizes the paths to absolute paths and copies the lists and the map.
   *
   * @throws NullPointerException     if name, resource root, or a collection is null
   * @throws IllegalArgumentException if the profile has neither an XSD nor a stylesheet
   */
  public ValidationProfile {
    Objects.requireNonNull(name,
                           "name");
    resourceRoot          = Objects.requireNonNull(resourceRoot,
                                                   "resourceRoot")
                                   .toAbsolutePath()
                                   .normalize();
    schema                = schema == null ?
                            null :
                            schema.toAbsolutePath()
                                  .normalize();
    schematronStylesheets = schematronStylesheets.stream()
                                                 .map(p -> p.toAbsolutePath()
                                                            .normalize())
                                                 .toList();
    // A profile checks an XSD, Schematron, or both.
    if (schema == null && schematronStylesheets.isEmpty()) {
      throw new IllegalArgumentException("A validation profile needs an XSD or a compiled Schematron stylesheet");
    }
    severityOverrides = Map.copyOf(severityOverrides);
    documents         = documents.stream()
                                 .map(p -> p.toAbsolutePath()
                                            .normalize())
                                 .toList();
  }

  /**
   * Profile without lookup documents.
   *
   * @param name                  profile name used in validation results
   * @param resourceRoot          root directory allowed for local schema and stylesheet resources
   * @param schema                XSD, or null for a profile with Schematron only
   * @param schematronStylesheets compiled Schematron XSLT files, in execution order
   * @param severityOverrides     rule identifiers mapped to replacement severities
   */
  public ValidationProfile(String name,
                           Path resourceRoot,
                           Path schema,
                           List<Path> schematronStylesheets,
                           Map<String, Severity> severityOverrides) {
    this(name,
         resourceRoot,
         schema,
         schematronStylesheets,
         severityOverrides,
         List.of());
  }

  /**
   * Loads the schema and compiled Schematron of one Factur-X / ZUGFeRD profile from an extracted
   * official package of FNFE-MPE or FeRD, or from the rules packaged in paniolo-rules. The
   * files are found by their names anywhere below the directory: the schema
   * {@code FACTUR-X_<level>.xsd} or, as in the FeRD package, {@code Factur-X_<version>_<level>.xsd}
   * (for BASIC-WL with {@code BASICWL}) with its imported schemas, {@code FACTUR-X_<level>.xslt}, and
   * the code list {@code FACTUR-X_<level>_codedb.xml} next to the stylesheet.
   *
   * @param rules extracted package directory
   * @param level MINIMUM, BASIC-WL, BASIC, EN16931 or EXTENDED
   * @return the validation profile
   * @throws ValidationException if the files cannot be found or read
   */
  public static ValidationProfile facturX(Path rules,
                                          String level) {
    Objects.requireNonNull(rules,
                           "rules");
    if (!FACTURX_LEVELS.contains(level)) {
      throw new IllegalArgumentException("Factur-X level must be one of " + FACTURX_LEVELS + ", but is " + level);
    }
    try {
      Path schema = facturXSchemaFile(rules,
                                      level);
      Path stylesheet = findOne(rules,
                                "FACTUR-X_" + level + ".xslt");
      Path codes = stylesheet.resolveSibling("FACTUR-X_" + level + "_codedb.xml");
      return new ValidationProfile("Factur-X " + level,
                                   rules,
                                   schema,
                                   List.of(stylesheet),
                                   Map.of(),
                                   Files.isRegularFile(codes) ?
                                   List.of(codes) :
                                   List.of());
    } catch (IOException e) {
      throw new ValidationException("Cannot load Factur-X rules: " + rules,
                                    e);
    }
  }

  /**
   * Finds the official Factur-X schema of a profile below the rules directory.
   */
  private static Path facturXSchemaFile(Path rules,
                                        String level)
      throws IOException {
    String schemaLevel = level.replace("-",
                                       "");
    // FNFE-MPE names the schema FACTUR-X_<level>.xsd, FeRD Factur-X_<version>_<level>.xsd.
    return findOne(rules,
                   "FACTUR-X_" + schemaLevel + ".xsd",
                   Pattern.compile("FACTUR-X_(\\d+(\\.\\d+)*_)?" + schemaLevel + "\\.xsd",
                                   Pattern.CASE_INSENSITIVE));
  }

  /**
   * Loads only the official Factur-X schema of a profile, without its Schematron, e.g. the
   * EXTENDED schema for the French profile EXTENDED-CTC-FR, whose Schematron comes from FNFE-MPE
   * ({@link #extendedCtcFr(Path, boolean)}).
   *
   * @param rules directory of the extracted official package, as for {@link #facturX(Path, String)}
   * @param level one of MINIMUM, BASIC-WL, BASIC, EN16931, EXTENDED
   * @return the validation profile
   * @throws ValidationException if the files cannot be found or read
   */
  public static ValidationProfile facturXSchema(Path rules,
                                                String level) {
    Objects.requireNonNull(rules,
                           "rules");
    if (!FACTURX_LEVELS.contains(level)) {
      throw new IllegalArgumentException("Factur-X level must be one of " + FACTURX_LEVELS + ", but is " + level);
    }
    try {
      return new ValidationProfile("Factur-X " + level + " schema",
                                   rules,
                                   facturXSchemaFile(rules,
                                                     level),
                                   List.of(),
                                   Map.of());
    } catch (IOException e) {
      throw new ValidationException("Cannot load Factur-X rules: " + rules,
                                    e);
    }
  }

  /**
   * Loads the compiled Schematron of the French profile EXTENDED-CTC-FR (XP Z12-012) for CII and,
   * with {@code frenchRules}, the French rules BR-FR for invoices between French parties (flux 2),
   * from the CII rules of FNFE-MPE: {@code EXTENDED-CTC-FR-CII.xslt} and
   * {@code BR-FR-Flux2-Schematron-CII.xslt}, e.g. as packaged in paniolo-rules. The
   * directory is the folder {@code CII/EXTENDED-CTC-FR} of the release or a parent of
   * {@code FNFE_RFE_INVOICE/CII/EXTENDED-CTC-FR}, such as the root of the release. The schema is
   * the Factur-X EXTENDED schema ({@link #facturXSchema(Path, String)}).
   *
   * @param rules       directory with the French CII rules
   * @param frenchRules {@code true} to check the rules BR-FR as well
   * @return the validation profile
   * @throws ValidationException if the files cannot be found or read
   */
  public static ValidationProfile extendedCtcFr(Path rules,
                                                boolean frenchRules) {
    Objects.requireNonNull(rules,
                           "rules");
    Path folder = rules.resolve("FNFE_RFE_INVOICE/CII/EXTENDED-CTC-FR");
    Path root = Files.isDirectory(folder) ?
                folder :
                rules;
    try {
      var stylesheets = new ArrayList<Path>();
      stylesheets.add(findOne(root,
                              "EXTENDED-CTC-FR-CII.xslt"));
      if (frenchRules) {
        stylesheets.add(findOne(root,
                                "BR-FR-Flux2-Schematron-CII.xslt"));
      }
      return new ValidationProfile(frenchRules ?
                                   "EXTENDED-CTC-FR with BR-FR" :
                                   "EXTENDED-CTC-FR",
                                   rules,
                                   null,
                                   stylesheets,
                                   Map.of());
    } catch (IOException e) {
      throw new ValidationException("Cannot load EXTENDED-CTC-FR rules: " + rules,
                                    e);
    }
  }

  /**
   * Loads the schema and compiled Schematron of ZUGFeRD 1.0, e.g. from the rules packaged in
   * paniolo-rules: {@code ZUGFeRD1p0.xsd} with its imported schemas and
   * {@code ZUGFeRD_1p0.xslt}, found by name anywhere below the directory. The Schematron reports
   * elements that ZUGFeRD 1.0 does not use; they are information.
   *
   * @param rules directory with the ZUGFeRD 1.0 schema and compiled Schematron
   * @return the validation profile
   * @throws ValidationException if the files cannot be found or read
   */
  public static ValidationProfile zugferd1(Path rules) {
    Objects.requireNonNull(rules,
                           "rules");
    try {
      return new ValidationProfile("ZUGFeRD 1.0",
                                   rules,
                                   findOne(rules,
                                           "ZUGFeRD1p0.xsd"),
                                   List.of(findOne(rules,
                                                   "ZUGFeRD_1p0.xslt")),
                                   Map.of(UNFLAGGED_REPORTS,
                                          Severity.INFO));
    } catch (IOException e) {
      throw new ValidationException("Cannot load ZUGFeRD 1.0 rules: " + rules,
                                    e);
    }
  }

  /**
   * Loads the schema and compiled Schematron of one Order-X profile, e.g. from the rules packaged in
   * paniolo-rules. The stylesheet {@code SCRDMCCBDACIOMessageStructure_100pD20B_<level>.xslt}
   * is found by name anywhere below the directory, the schema
   * {@code SCRDMCCBDACIOMessageStructure_100pD20B.xsd} below a directory named like the level (case is
   * ignored), because all levels use the same schema file name. The stylesheet must write an SVRL
   * report; the {@code -compiled.xsl} files of the official package report with
   * {@code xsl:message} instead and cannot be used, compile the {@code .sch} instead.
   *
   * @param rules extracted package directory
   * @param level BASIC, COMFORT or EXTENDED
   * @return the validation profile
   * @throws ValidationException if the files cannot be found or read
   */
  public static ValidationProfile orderX(Path rules,
                                         String level) {
    Objects.requireNonNull(rules,
                           "rules");
    if (!List.of("BASIC",
                 "COMFORT",
                 "EXTENDED")
             .contains(level)) {
      throw new IllegalArgumentException("Order-X level must be BASIC, COMFORT or EXTENDED, but is " + level);
    }
    try {
      Path stylesheet = findOne(rules,
                                "SCRDMCCBDACIOMessageStructure_100pD20B_" + level + ".xslt");
      List<Path> schemas;
      try (var paths = Files.walk(rules,
                                  10)) {
        schemas = paths.filter(path -> path.getFileName()
                                           .toString()
                                           .equals("SCRDMCCBDACIOMessageStructure_100pD20B.xsd") && Files.isRegularFile(path))
                       .filter(path -> {
                         for (Path part : rules.relativize(path)) {
                           if (part.toString()
                                   .equalsIgnoreCase(level)) {
                             return true;
                           }
                         }
                         return false;
                       })
                       .toList();
      }
      if (schemas.size() != 1) {
        throw new IOException("Expected exactly one SCRDMCCBDACIOMessageStructure_100pD20B.xsd below a directory named " + level + " in " + rules + ", found " + schemas);
      }
      return new ValidationProfile("Order-X " + level,
                                   rules,
                                   schemas.get(0),
                                   List.of(stylesheet),
                                   Map.of());
    } catch (IOException e) {
      throw new ValidationException("Cannot load Order-X rules: " + rules,
                                    e);
    }
  }

  private static Path findOne(Path root,
                              String fileName)
      throws IOException {
    return findOne(root,
                   fileName,
                   Pattern.compile(Pattern.quote(fileName)));
  }

  /**
   * Finds the one file below the root whose name matches the pattern; the description names the
   * file in the error message.
   */
  private static Path findOne(Path root,
                              String description,
                              Pattern fileName)
      throws IOException {
    List<Path> found;
    try (var paths = Files.walk(root,
                                10)) {
      found = paths.filter(path -> fileName.matcher(path.getFileName()
                                                        .toString())
                                           .matches() && Files.isRegularFile(path))
                   .toList();
    }
    if (found.size() != 1) {
      throw new IOException("Expected exactly one " + description + " below " + root + ", found " + found);
    }
    return found.get(0);
  }

  /**
   * Loads the standard CIUS scenario and severity overrides from an extracted KoSIT bundle.
   *
   * @param bundle directory of the extracted KoSIT bundle
   * @param syntax syntax of the documents to validate
   * @return the validation profile
   * @throws ValidationException if the bundle cannot be read or has no matching scenario
   */
  public static ValidationProfile xrechnung(Path bundle,
                                            Syntax syntax) {
    return xrechnung(bundle,
                     syntax,
                     XrechnungScenario.STANDARD);
  }

  /**
   * Loads the scenario of the XRechnung extension from an extracted KoSIT bundle: the XRechnung
   * rules with the severity overrides for sub invoice lines, third party payments, and the further
   * code values. KoSIT defines it for UBL Invoice and CII only.
   *
   * @param bundle directory of the extracted KoSIT bundle
   * @param syntax syntax of the documents to validate
   * @return the validation profile
   * @throws IllegalArgumentException for UBL CreditNote, which the extension does not cover
   * @throws ValidationException      if the bundle cannot be read or has no matching scenario
   */
  public static ValidationProfile xrechnungExtension(Path bundle,
                                                     Syntax syntax) {
    if (syntax == Syntax.UBL_CREDIT_NOTE) {
      throw new IllegalArgumentException("The XRechnung extension has no scenario for UBL CreditNote");
    }
    return xrechnung(bundle,
                     syntax,
                     XrechnungScenario.EXTENSION);
  }

  /**
   * Loads a scenario of an extracted KoSIT bundle, e.g. the standard CIUS, the extension or the CVD
   * scenario of a document type, with its severity overrides.
   *
   * @param bundle   directory of the extracted KoSIT bundle
   * @param syntax   syntax of the documents to validate
   * @param scenario the scenario
   * @return the validation profile
   * @throws ValidationException if the bundle cannot be read or has no such scenario for the syntax
   */
  public static ValidationProfile xrechnung(Path bundle,
                                            Syntax syntax,
                                            XrechnungScenario scenario) {
    return scenario(bundle,
                    scenario.names(Objects.requireNonNull(syntax,
                                                          "syntax"))
                            .toArray(String[]::new));
  }

  /**
   * Returns the specification identifiers (BT-24) a KoSIT bundle accepts, read from the
   * {@code match} expressions of the XRechnung scenarios in its {@code scenarios.xml}: the string
   * literals of each standard, extension and CVD scenario, each with the scenario and document type.
   * Other scenarios, e.g. plain EN 16931, are ignored.
   *
   * <p>The KoSIT validator selects the scenario by these expressions, so they describe exactly which
   * identifiers the bundle supports. Releases for XRechnung 1.2 use {@code starts-with}; their
   * literal is returned as an exact identifier as well.
   *
   * @param bundle directory of the extracted KoSIT bundle
   * @return the identifiers in the order of the scenarios; never empty
   * @throws ValidationException if the bundle cannot be read or names no XRechnung identifier
   */
  public static List<XrechnungSpecification> xrechnungSpecifications(Path bundle) {
    try (var input = Files.newInputStream(scenariosXml(bundle))) {
      var    document       = XmlDocuments.parse(input);
      var    specifications = new ArrayList<XrechnungSpecification>();
      var    scenarios      = document.getElementsByTagNameNS(SCENARIOS_NS,
                                                              "scenario");
      for (int i = 0; i < scenarios.getLength(); i++) {
        Element scenario = (Element) scenarios.item(i);
        var target = XrechnungScenario.of(text(scenario,
                                               "name"));
        if (target == null) {
          continue;
        }
        var matcher = LITERAL.matcher(text(scenario,
                                           "match"));
        while (matcher.find()) {
          String literal = matcher.group(1) != null ?
                           matcher.group(1) :
                           matcher.group(2);
          if (literal.toLowerCase(Locale.ROOT)
                     .contains("xrechnung")) {
            specifications.add(new XrechnungSpecification(literal,
                                                          target.scenario(),
                                                          target.syntax()));
          }
        }
      }
      if (specifications.isEmpty()) {
        throw new IOException("No XRechnung specification identifier in the scenarios");
      }
      return List.copyOf(specifications);
    } catch (Exception e) {
      throw new ValidationException("Cannot read the XRechnung specification identifiers of bundle: " + bundle,
                                    e);
    }
  }

  private static Path scenariosXml(Path bundle)
      throws IOException {
    return new LocalResources(bundle).resolve("scenarios.xml",
                                              bundle.toAbsolutePath()
                                                    .resolve("placeholder")
                                                    .toUri()
                                                    .toString());
  }

  private static String text(Element element,
                             String name) {
    var nodes = element.getElementsByTagNameNS(SCENARIOS_NS,
                                               name);
    return nodes.getLength() == 0 ?
           "" :
           nodes.item(0)
                .getTextContent()
                .trim();
  }

  /**
   * Loads the first scenario of the bundle whose name is one of the given names.
   */
  private static ValidationProfile scenario(Path bundle,
                                            String... scenarioNames) {
    List<String> names = List.of(scenarioNames);
    try {
      var resources = new LocalResources(bundle);
      Path scenarios = resources.resolve("scenarios.xml",
                                         bundle.toAbsolutePath()
                                               .resolve("placeholder")
                                               .toUri()
                                               .toString());
      try (var input = Files.newInputStream(scenarios)) {
        var    document = XmlDocuments.parse(input);
        String ns       = "http://www.xoev.de/de/validator/framework/1/scenarios";
        var scenariosList = document.getElementsByTagNameNS(ns,
                                                            "scenario");
        for (int i = 0; i < scenariosList.getLength(); i++) {
          Element scenario = (Element) scenariosList.item(i);
          String scenarioName = scenario.getElementsByTagNameNS(ns,
                                                                "name")
                                        .item(0)
                                        .getTextContent()
                                        .trim();
          if (!names.contains(scenarioName)) {
            continue;
          }
          var schemas = scenario.getElementsByTagNameNS(ns,
                                                        "validateWithXmlSchema");
          if (schemas.getLength() != 1) {
            throw new IOException("Expected exactly one schema in " + scenarioName);
          }
          String schemaLocation = ((Element) schemas.item(0)).getElementsByTagNameNS(ns,
                                                                                     "location")
                                                             .item(0)
                                                             .getTextContent()
                                                             .trim();
          List<Path> stylesheets = new ArrayList<>();
          var rules = scenario.getElementsByTagNameNS(ns,
                                                      "validateWithSchematron");
          for (int j = 0; j < rules.getLength(); j++) {
            String location = ((Element) rules.item(j)).getElementsByTagNameNS(ns,
                                                                               "location")
                                                       .item(0)
                                                       .getTextContent()
                                                       .trim();
            stylesheets.add(resources.resolve(location,
                                              scenarios.toUri()
                                                       .toString()));
          }
          Map<String, Severity> overrides = new LinkedHashMap<>();
          var levels = scenario.getElementsByTagNameNS(ns,
                                                       "customLevel");
          for (int j = 0; j < levels.getLength(); j++) {
            Element level = (Element) levels.item(j);
            overrides.put(level.getTextContent()
                               .trim(),
                          Severity.fromFlag(level.getAttribute("level")));
          }
          return new ValidationProfile(scenarioName,
                                       bundle,
                                       resources.resolve(schemaLocation,
                                                         scenarios.toUri()
                                                                  .toString()),
                                       stylesheets,
                                       overrides);
        }
      }
      throw new IOException("Scenario not found: " +
                            String.join(" / ",
                                        names));
    } catch (Exception e) {
      throw new ValidationException("Cannot load XRechnung bundle: " + bundle,
                                    e);
    }
  }

  /**
   * Loads the plain EN 16931 rules from an extracted KoSIT bundle: the syntax schema and the
   * EN 16931 Schematron, without the German XRechnung CIUS rules and their severity overrides.
   * Used e.g. for the Factur-X / ZUGFeRD profile EN 16931.
   *
   * @param bundle directory of the extracted KoSIT bundle
   * @param syntax syntax of the documents to validate
   * @return the validation profile
   * @throws ValidationException if the bundle cannot be read or has no matching scenario
   */
  public static ValidationProfile en16931(Path bundle,
                                          Syntax syntax) {
    var xrechnung = xrechnung(bundle,
                              syntax);
    var stylesheets = xrechnung.schematronStylesheets()
                               .stream()
                               .filter(path -> path.getFileName()
                                                   .toString()
                                                   .startsWith("EN16931-"))
                               .toList();
    if (stylesheets.isEmpty()) {
      throw new ValidationException("No EN 16931 Schematron in bundle: " + bundle,
                                    null);
    }
    return new ValidationProfile(xrechnung.name()
                                          .replace("EN16931 XRechnung",
                                                   "EN16931"),
                                 xrechnung.resourceRoot(),
                                 xrechnung.schema(),
                                 stylesheets,
                                 Map.of());
  }

  /**
   * Invoice syntax used to select the schema and Schematron artifacts of an XRechnung rule
   * bundle. UBL invoices and credit notes have different root elements and schemas.
   */
  public enum Syntax {
    /**
     * UBL Invoice document.
     */

    UBL_INVOICE,
    /**
     * UBL CreditNote document.
     */

    UBL_CREDIT_NOTE,
    /**
     * UN/CEFACT CrossIndustryInvoice document.
     */

    CII
  }
}
