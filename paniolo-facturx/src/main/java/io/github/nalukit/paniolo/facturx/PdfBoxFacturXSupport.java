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
package io.github.nalukit.paniolo.facturx;

import io.github.nalukit.paniolo.config.AfRelationship;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.PdfAttachment;
import io.github.nalukit.paniolo.config.PdfProducer;
import io.github.nalukit.paniolo.model.order.OrderTypeCode;
import io.github.nalukit.paniolo.model.order.OrderXProfile;
import io.github.nalukit.paniolo.spi.FacturXSupport;
import io.github.nalukit.paniolo.xml.util.XmlDocuments;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.filter.FilterFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Factur-X / ZUGFeRD implementation based on Apache PDFBox, registered for
 * {@link java.util.ServiceLoader}. Use it through {@code Paniolo#toFacturX}, {@code Paniolo#read}
 * and {@code Paniolo#validate}. Stateless and thread-safe.
 *
 * <p>Embedded files are stored Flate-compressed and the PDF is saved with object streams, which
 * PDF/A-3 allows; the streams of the given PDF are taken as they are.
 */
public final class PdfBoxFacturXSupport
    implements FacturXSupport {
  private static final System.Logger LOGGER        = System.getLogger(PdfBoxFacturXSupport.class.getName());
  private static final String        FX            = "urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#";
  /**
   * XMP namespace of Order-X documents and the name of the embedded order.
   */
  private static final String        OX            = "urn:factur-x:pdfa:CrossIndustryDocument:1p0#";
  private static final String        ORDER_FILE    = "order-x.xml";
  /**
   * XMP namespace of despatch advices and the name of the embedded CIDA XML, as the Mustang project
   * writes them after the AWV specification of the digital delivery note.
   */
  private static final String        DX            = "urn:factur-x:pdfa:CrossIndustryDocument:despatchadvice:1p0#";
  private static final String        DESPATCH_FILE = "cida.xml";
  private static final String        PDFAID        = "http://www.aiim.org/pdfa/ns/id/";
  private static final COSName       AF            = COSName.getPDFName("AF");
  private static final COSName       RELATIONSHIP  = COSName.getPDFName("AFRelationship");
  /**
   * Embedded invoice XML larger than this is rejected when reading.
   */
  private static final int           MAX_XML       = 20 * 1024 * 1024;
  /**
   * Embedded attachments larger than this are rejected when reading.
   */
  private static final int           MAX_FILE      = 100 * 1024 * 1024;
  /**
   * Most attachments {@link #attachments(byte[])} returns for one PDF; more are rejected.
   */
  static final int                   MAX_ATTACHMENTS      = 1_000;
  /**
   * Most content bytes {@link #attachments(byte[])} returns for one PDF, summed over all
   * attachments; the limit is inclusive and counts every returned copy, also of attachments that
   * share one embedded stream.
   */
  static final long                  MAX_ATTACHMENT_BYTES = 256L * 1024 * 1024;
  /**
   * File names of embedded invoices, including ZUGFeRD 1.0.
   */
  private static final List<String>  INVOICE_NAMES = List.of("factur-x.xml",
                                                             "xrechnung.xml",
                                                             "zugferd-invoice.xml",
                                                             "ZUGFeRD-invoice.xml");

  /**
   * Creates the implementation; {@link java.util.ServiceLoader} calls it.
   */
  public PdfBoxFacturXSupport() {
  }

  private static PDDocument load(byte[] pdf)
      throws IOException {
    try {
      return Loader.loadPDF(pdf);
    } catch (InvalidPasswordException e) {
      throw new IllegalArgumentException("Password protected PDF documents are not supported",
                                         e);
    } catch (IOException e) {
      throw new IllegalArgumentException("Not a readable PDF document",
                                         e);
    }
  }

  /**
   * Returns the PDF/A conformance letter (A, B or U) of the input; rejects documents that do not
   * declare PDF/A, because the visual part must already be archivable.
   */
  private static String pdfaConformance(PDDocumentCatalog catalog) {
    Document xmp = parseXmp(catalog.getMetadata());
    String part = xmp == null ?
                  null :
                  value(xmp,
                        PDFAID,
                        "part");
    if (part == null || !List.of("1",
                                 "2",
                                 "3")
                             .contains(part)) {
      throw new IllegalArgumentException("The PDF is not a PDF/A document (no pdfaid:part 1-3 in its XMP metadata); " + "convert it to PDF/A before embedding the invoice");
    }
    String conformance = value(xmp,
                               PDFAID,
                               "conformance");
    return conformance == null ?
           "B" :
           conformance.toUpperCase(java.util.Locale.ROOT);
  }

  /**
   * Embeds a file as PDF/A-3 associated file: in the name tree and the catalog's {@code /AF} array,
   * with MIME type, size, dates, and relationship.
   */
  private static void embedFile(PDDocument document,
                                PDDocumentCatalog catalog,
                                String name,
                                byte[] content,
                                String mimeType,
                                String relationship,
                                String description,
                                Calendar now)
      throws IOException {
    var embedded = new PDEmbeddedFile(document,
                                      new ByteArrayInputStream(content),
                                      COSName.FLATE_DECODE);
    embedded.setSubtype(mimeType);
    embedded.setSize(content.length);
    embedded.setCreationDate(now);
    embedded.setModDate(now);

    var file = new PDComplexFileSpecification();
    file.setFile(name);
    file.setFileUnicode(name);
    file.setFileDescription(description);
    file.setEmbeddedFile(embedded);
    file.setEmbeddedFileUnicode(embedded);
    file.getCOSObject()
        .setName(RELATIONSHIP,
                 relationship);

    PDDocumentNameDictionary names = catalog.getNames();
    if (names == null) {
      names = new PDDocumentNameDictionary(catalog);
    }
    // Rebuild a flat name tree that keeps all existing attachments.
    Map<String, PDComplexFileSpecification> files = new java.util.TreeMap<>(embeddedFiles(names.getEmbeddedFiles()));
    files.put(name,
              file);
    var tree = new PDEmbeddedFilesNameTreeNode();
    tree.setNames(files);
    names.setEmbeddedFiles(tree);
    catalog.setNames(names);

    COSArray associated = catalog.getCOSObject()
                                 .getCOSArray(AF);
    if (associated == null) {
      associated = new COSArray();
      catalog.getCOSObject()
             .setItem(AF,
                      associated);
    }
    associated.add(file);
  }

  /**
   * Removes embedded invoice files from the name tree and the catalog's {@code /AF} array.
   */
  private static void removeInvoiceFiles(PDDocumentCatalog catalog)
      throws IOException {
    var names = catalog.getNames();
    if (names != null && names.getEmbeddedFiles() != null) {
      Map<String, PDComplexFileSpecification> files = new java.util.TreeMap<>(embeddedFiles(names.getEmbeddedFiles()));
      files.keySet()
           .removeIf(INVOICE_NAMES::contains);
      var tree = new PDEmbeddedFilesNameTreeNode();
      tree.setNames(files);
      names.setEmbeddedFiles(tree);
      catalog.setNames(names);
    }
    COSArray associated = catalog.getCOSObject()
                                 .getCOSArray(AF);
    if (associated != null) {
      for (int i = associated.size() - 1; i >= 0; i--) {
        if (associated.getObject(i) instanceof COSDictionary dictionary && INVOICE_NAMES.contains(fileName(new PDComplexFileSpecification(dictionary)))) {
          associated.remove(i);
        }
      }
    }
  }

  private static void addSrgbOutputIntent(PDDocument document,
                                          PDDocumentCatalog catalog)
      throws IOException {
    byte[] icc = ICC_Profile.getInstance(ColorSpace.CS_sRGB)
                            .getData();
    var intent = new PDOutputIntent(document,
                                    new ByteArrayInputStream(icc));
    intent.setInfo("sRGB IEC61966-2.1");
    intent.setOutputCondition("sRGB IEC61966-2.1");
    intent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
    intent.setRegistryName("http://www.color.org");
    catalog.addOutputIntent(intent);
  }

  /**
   * Embedded invoice files by name, from the associated files and the name tree.
   */
  private static Map<String, PDComplexFileSpecification> invoiceFiles(PDDocumentCatalog catalog)
      throws IOException {
    var                                     all      = allFiles(catalog);
    Map<String, PDComplexFileSpecification> invoices = new LinkedHashMap<>();
    for (String name : INVOICE_NAMES) {
      all.forEach((fileName, file) -> {
        if (name.equals(fileName)) {
          invoices.put(fileName,
                       file);
        }
      });
    }
    return invoices;
  }

  /**
   * All embedded files by name, from the associated files and the name tree.
   */
  private static Map<String, PDComplexFileSpecification> allFiles(PDDocumentCatalog catalog)
      throws IOException {
    Map<String, PDComplexFileSpecification> all = new LinkedHashMap<>();
    for (var entry : fileSpecifications(catalog)) {
      all.putIfAbsent(entry.getKey(),
                      entry.getValue());
    }
    return all;
  }

  /**
   * Every embedded file specification with its name, before files of the same name are merged. The
   * same specification reached from the associated files and the name tree appears once.
   */
  private static List<Map.Entry<String, PDComplexFileSpecification>> fileSpecifications(PDDocumentCatalog catalog)
      throws IOException {
    var result = new java.util.ArrayList<Map.Entry<String, PDComplexFileSpecification>>();
    var seen   = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<COSDictionary, Boolean>());
    COSArray associated = catalog.getCOSObject()
                                 .getCOSArray(AF);
    if (associated != null) {
      for (int i = 0; i < associated.size(); i++) {
        // getObject dereferences indirect references, which /AF entries usually are.
        if (associated.getObject(i) instanceof COSDictionary dictionary && seen.add(dictionary)) {
          var file = new PDComplexFileSpecification(dictionary);
          result.add(Map.entry(fileName(file),
                               file));
        }
      }
    }
    if (catalog.getNames() != null) {
      for (var entry : embeddedFilePairs(catalog.getNames()
                                                .getEmbeddedFiles())) {
        if (seen.add(entry.getValue()
                          .getCOSObject())) {
          result.add(entry);
        }
      }
    }
    return result;
  }

  /**
   * Deepest name tree of embedded files that is followed. Name trees are balanced and a tree of
   * 32 levels holds far more files than a PDF can; a deeper one is a damaged or hostile document and
   * would exhaust the stack.
   */
  private static final int MAX_NAME_TREE_DEPTH = 32;

  private static List<Map.Entry<String, PDComplexFileSpecification>> embeddedFilePairs(org.apache.pdfbox.pdmodel.common.PDNameTreeNode<PDComplexFileSpecification> node)
      throws IOException {
    var result = new java.util.ArrayList<Map.Entry<String, PDComplexFileSpecification>>();
    collectEmbeddedFiles(node,
                         result,
                         java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()),
                         java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()),
                         1);
    return result;
  }

  /**
   * Walks the name tree below a node. A node that is its own ancestor is a cycle and a tree deeper than
   * {@link #MAX_NAME_TREE_DEPTH} is too deep; both are refused. A node reached a second time on
   * another path is read once.
   */
  private static void collectEmbeddedFiles(org.apache.pdfbox.pdmodel.common.PDNameTreeNode<PDComplexFileSpecification> node,
                                           List<Map.Entry<String, PDComplexFileSpecification>> result,
                                           java.util.Set<COSBase> path,
                                           java.util.Set<COSBase> visited,
                                           int depth)
      throws IOException {
    if (node == null) {
      return;
    }
    COSBase identity = node.getCOSObject();
    if (path.contains(identity)) {
      throw new IllegalArgumentException("The name tree of the embedded files refers to itself (/Kids cycle)");
    }
    if (depth > MAX_NAME_TREE_DEPTH) {
      throw new IllegalArgumentException("The name tree of the embedded files is deeper than " + MAX_NAME_TREE_DEPTH + " levels");
    }
    if (!visited.add(identity)) {
      return;
    }
    path.add(identity);
    if (node.getNames() != null) {
      result.addAll(node.getNames()
                        .entrySet());
    }
    if (node.getKids() != null) {
      for (var kid : node.getKids()) {
        collectEmbeddedFiles(kid,
                             result,
                             path,
                             visited,
                             depth + 1);
      }
    }
    path.remove(identity);
  }

  /**
   * The invoice file names that two different file specifications carry with different content.
   */
  private static java.util.Set<String> conflictingInvoiceFiles(PDDocumentCatalog catalog)
      throws IOException {
    var byName = new LinkedHashMap<String, List<PDComplexFileSpecification>>();
    for (var entry : fileSpecifications(catalog)) {
      if (INVOICE_NAMES.contains(entry.getKey())) {
        byName.computeIfAbsent(entry.getKey(),
                               name -> new java.util.ArrayList<>())
              .add(entry.getValue());
      }
    }
    var conflicts = new java.util.TreeSet<String>();
    for (var entry : byName.entrySet()) {
      var     specifications = entry.getValue();
      byte[]  first          = contentOrNull(specifications.get(0));
      for (int i = 1; i < specifications.size(); i++) {
        if (first == null || !java.util.Arrays.equals(first,
                                            contentOrNull(specifications.get(i)))) {
          conflicts.add(entry.getKey());
        }
      }
    }
    return conflicts;
  }

  private static byte[] contentOrNull(PDComplexFileSpecification file)
      throws IOException {
    try {
      return read(file);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static Map<String, PDComplexFileSpecification> embeddedFiles(org.apache.pdfbox.pdmodel.common.PDNameTreeNode<PDComplexFileSpecification> node)
      throws IOException {
    Map<String, PDComplexFileSpecification> result = new LinkedHashMap<>();
    for (var entry : embeddedFilePairs(node)) {
      result.put(entry.getKey(),
                 entry.getValue());
    }
    return result;
  }

  private static String fileName(PDComplexFileSpecification file) {
    return file.getFileUnicode() != null ?
           file.getFileUnicode() :
           file.getFile();
  }

  private static byte[] read(PDComplexFileSpecification file)
      throws IOException {
    return read(file,
                MAX_XML);
  }

  private static byte[] read(PDComplexFileSpecification file,
                             int limit)
      throws IOException {
    return read(file,
                limit,
                "Embedded file " + fileName(file) + " exceeds " + limit + " bytes");
  }

  private static byte[] read(PDComplexFileSpecification file,
                             int limit,
                             String tooLarge)
      throws IOException {
    PDEmbeddedFile embedded = embeddedFile(file);
    if (embedded == null) {
      throw new IllegalArgumentException("Embedded file " + fileName(file) + " has no content");
    }
    return decode(embedded.getCOSObject(),
                  limit,
                  tooLarge);
  }

  /**
   * Filters that are decoded with a limit: they write to an output stream that this class bounds.
   * Images and similar filters (DCT, JPX, CCITT, JBIG2) and the Crypt filter do not occur in embedded
   * files and are refused.
   */
  private static final java.util.Set<String> DECODABLE_FILTERS = java.util.Set.of("FlateDecode",
                                                                                  "Fl",
                                                                                  "LZWDecode",
                                                                                  "LZW",
                                                                                  "ASCII85Decode",
                                                                                  "A85",
                                                                                  "ASCIIHexDecode",
                                                                                  "AHx",
                                                                                  "RunLengthDecode",
                                                                                  "RL");

  /**
   * Thrown by {@link BoundedOutput} when a decoder produces more than the limit; unchecked, so that no
   * decoder can hold it back like an I/O error.
   */
  private static final class LimitExceeded extends RuntimeException {
    LimitExceeded() {
      super(null,
            null,
            false,
            false);
    }
  }

  /**
   * Output of a decoder that holds at most {@code limit} bytes. The decoder stops with
   * {@link LimitExceeded} at the first byte beyond the limit, so no more than the limit is ever
   * allocated; the buffer grows by doubling up to the limit.
   */
  private static final class BoundedOutput extends OutputStream {
    private final int limit;
    private byte[]    buffer = new byte[0];
    private int       count;

    BoundedOutput(int limit) {
      this.limit = limit;
    }

    @Override
    public void write(int b) {
      ensure(1);
      buffer[count++] = (byte) b;
    }

    @Override
    public void write(byte[] b,
                      int off,
                      int len) {
      ensure(len);
      System.arraycopy(b,
                       off,
                       buffer,
                       count,
                       len);
      count += len;
    }

    private void ensure(int more) {
      if (more > limit - count) {
        throw new LimitExceeded();
      }
      int needed = count + more;
      if (needed > buffer.length) {
        buffer = java.util.Arrays.copyOf(buffer,
                                         (int) Math.min(limit,
                                                        Math.max(needed,
                                                                 Math.max(8192,
                                                                          2L * buffer.length))));
      }
    }

    byte[] toByteArray() {
      return java.util.Arrays.copyOf(buffer,
                                     count);
    }
  }

  /**
   * Largest row of a predictor that is accepted: PDFBox allocates two buffers of one row each when
   * the predictor starts, before it writes a byte to the bounded output, so the row has to be
   * limited beforehand. Rows of embedded files are small; 8 MiB is far above any usual one. The
   * buffers of a stage together are at most twice this size.
   */
  static final int MAX_PREDICTOR_ROW = 8 * 1024 * 1024;

  /**
   * The decode parameters of the filter at {@code index}, chosen like PDFBox does
   * ({@code Filter.getDecodeParams}), as they are used by the decoder of that stage.
   */
  private static COSDictionary parametersOf(COSStream stream,
                                            int index) {
    COSBase filter     = stream.getDictionaryObject(COSName.F,
                                                    COSName.FILTER);
    COSBase parameters = stream.getDictionaryObject(COSName.DP,
                                                    COSName.DECODE_PARMS);
    if (filter instanceof COSName && parameters instanceof COSDictionary dictionary) {
      return dictionary;
    }
    if (filter instanceof COSArray && parameters instanceof COSArray array && index < array.size() && array.getObject(index) instanceof COSDictionary dictionary) {
      return dictionary;
    }
    return new COSDictionary();
  }

  /**
   * The decode parameters of a stage after the check of the predictor. A predictor (Flate and LZW)
   * works row by row and allocates two buffers of one row before it writes anything, with the row
   * length from {@code Columns}, {@code Colors}, and {@code BitsPerComponent} of the document. The
   * values are checked with the same defaults as in PDFBox, and the row length is calculated without
   * overflow: it has to fit in the limit of the stage and in {@link #MAX_PREDICTOR_ROW}.
   *
   * @throws IllegalArgumentException with {@code tooLarge} if the row exceeds the limit, or with a
   *                                  message if a value is not valid or not supported
   */
  private static COSDictionary checkedParameters(COSStream stream,
                                                 int index,
                                                 int limit,
                                                 String tooLarge) {
    COSDictionary parameters = parametersOf(stream,
                                            index);
    int predictor = parameters.getInt(COSName.PREDICTOR);
    if (predictor <= 1) {
      return parameters;
    }
    if (predictor != 2 && (predictor < 10 || predictor > 15)) {
      throw new IllegalArgumentException("The predictor " + predictor + " of a stream is not supported (2 and 10 to 15 are)");
    }
    int colors  = Math.min(parameters.getInt(COSName.COLORS,
                                             1),
                           32);
    int bits    = parameters.getInt(COSName.BITS_PER_COMPONENT,
                                    8);
    int columns = parameters.getInt(COSName.COLUMNS,
                                    1);
    if (colors < 1 || columns < 1 || bits != 1 && bits != 2 && bits != 4 && bits != 8 && bits != 16) {
      throw new IllegalArgumentException("The predictor of a stream has invalid parameters: Colors " + colors + ", BitsPerComponent " + bits + ", Columns " + columns);
    }
    long row = ((long) columns * colors * bits + 7) / 8;
    if (row > Math.min(limit,
                       MAX_PREDICTOR_ROW)) {
      throw new IllegalArgumentException(row > limit ?
                                         tooLarge :
                                         "A predictor row of a stream has " + row + " bytes, more than " + MAX_PREDICTOR_ROW);
    }
    return parameters;
  }

  /**
   * The content of a stream with its filters decoded, at most {@code limit} bytes. Every filter stage
   * is decoded into a bounded buffer and stops at the first byte beyond the limit, so an intermediate
   * result is limited like the final one. The decoding of PDFBox itself
   * ({@code COSStream.createInputStream()}) is not used: it expands the whole stream into the heap
   * before the caller can count. The raw stream is as large as the PDF.
   *
   * <p>A predictor allocates its row buffers before the first byte; its parameters are checked first,
   * see {@link #checkedParameters}.
   *
   * @throws IllegalArgumentException with {@code tooLarge} if a stage produces more than the limit, or
   *                                  if the stream uses a filter that is not decoded or a predictor
   *                                  that is not valid or too large
   */
  private static byte[] decode(COSStream stream,
                               int limit,
                               String tooLarge)
      throws IOException {
    List<COSName> names = new java.util.ArrayList<>();
    COSBase declared = stream.getFilters();
    if (declared instanceof COSName name) {
      names.add(name);
    } else if (declared instanceof COSArray array) {
      for (int i = 0; i < array.size(); i++) {
        if (array.getObject(i) instanceof COSName name) {
          names.add(name);
        } else {
          throw new IllegalArgumentException("A stream has an invalid filter entry");
        }
      }
    }
    // Like PDFBox, a filter that is listed more than once is applied once (some producers write
    // /Filter [/FlateDecode /FlateDecode] for data that is encoded once); an abbreviation counts as
    // the same filter.
    var filters = new java.util.LinkedHashMap<org.apache.pdfbox.filter.Filter, COSName>();
    for (COSName name : names) {
      if (!DECODABLE_FILTERS.contains(name.getName())) {
        throw new IllegalArgumentException("The stream filter " + name.getName() + " is not supported for embedded files");
      }
      filters.putIfAbsent(FilterFactory.INSTANCE.getFilter(name),
                          name);
    }
    byte[] data;
    try (InputStream raw = stream.createRawInputStream()) {
      data = raw.readAllBytes();
    }
    if (filters.isEmpty() && data.length > limit) {
      throw new IllegalArgumentException(tooLarge);
    }
    try {
      int index = 0;
      for (var entry : filters.entrySet()) {
        // The decoder gets a dictionary of its own with exactly the parameters that were checked.
        var stage = new COSDictionary();
        stage.setItem(COSName.FILTER,
                      entry.getValue());
        stage.setItem(COSName.DECODE_PARMS,
                      checkedParameters(stream,
                                        index++,
                                        limit,
                                        tooLarge));
        var out = new BoundedOutput(limit);
        entry.getKey()
             .decode(new ByteArrayInputStream(data),
                     out,
                     stage,
                     0);
        data = out.toByteArray();
      }
    } catch (LimitExceeded e) {
      throw new IllegalArgumentException(tooLarge);
    }
    return data;
  }

  private static PDEmbeddedFile embeddedFile(PDComplexFileSpecification file) {
    return file.getEmbeddedFileUnicode() != null ?
           file.getEmbeddedFileUnicode() :
           file.getEmbeddedFile();
  }

  private static Document parseXmp(PDMetadata metadata) {
    if (metadata == null) {
      return null;
    }
    try {
      // Metadata of more than an invoice XML is no XMP; it counts as missing.
      return XmlDocuments.parse(new ByteArrayInputStream(decode(metadata.getCOSObject(),
                                                                MAX_XML,
                                                                "XMP metadata exceeds " + MAX_XML + " bytes")));
    } catch (IOException | SAXException | IllegalArgumentException e) {
      LOGGER.log(System.Logger.Level.DEBUG,
                 "Cannot parse XMP metadata",
                 e);
      return null;
    }
  }

  /**
   * The generations whose namespace carries at least one invoice property in the XMP metadata, as
   * element or as attribute of rdf:Description, in the order of {@link HybridGeneration}.
   * Properties with the same local names in other namespaces do not count.
   */
  private static List<HybridGeneration> declaredGenerations(Document xmp) {
    if (xmp == null) {
      return List.of();
    }
    return java.util.Arrays.stream(HybridGeneration.values())
                           .filter(generation -> HybridGeneration.PROPERTIES.stream()
                                                                            .anyMatch(property -> value(xmp,
                                                                                                        generation.namespace(),
                                                                                                        property) != null))
                           .toList();
  }

  /**
   * Chooses the generation of the XMP metadata and the embedded invoice file; container check and
   * extraction both use it.
   *
   * <ul>
   *   <li>The generation is the one whose namespace carries the invoice properties. If several do,
   *       the first whose {@code DocumentFileName} names an embedded file of its own wins, otherwise
   *       the first in the order Factur-X, ZUGFeRD 2.0, ZUGFeRD 1.0; the check reports
   *       {@code FX-XMP-GENERATIONS}.</li>
   *   <li>Without any invoice property the only declared extension schema, or else the exact file
   *       name, selects the generation whose missing properties are reported.</li>
   *   <li>The file is the one {@code DocumentFileName} of that generation names, otherwise the first
   *       in the order of {@link #INVOICE_NAMES}.</li>
   * </ul>
   */
  private static Selection select(Document xmp,
                                  Map<String, PDComplexFileSpecification> files) {
    List<HybridGeneration> declared = declaredGenerations(xmp);
    String first = files.keySet()
                        .iterator()
                        .next();
    HybridGeneration generation;
    if (declared.size() == 1) {
      generation = declared.get(0);
    } else if (declared.size() > 1) {
      generation = declared.stream()
                           .filter(candidate -> {
                             String named = value(xmp,
                                                  candidate.namespace(),
                                                  "DocumentFileName");
                             return files.containsKey(named) && candidate.fileNames()
                                                                         .contains(named);
                           })
                           .findFirst()
                           .orElse(declared.get(0));
    } else {
      List<HybridGeneration> schemas = xmp == null || xmp.getDocumentElement() == null ?
                                       List.of() :
                                       java.util.Arrays.stream(HybridGeneration.values())
                                                       .filter(candidate -> hasExtensionSchema(xmp,
                                                                                               candidate.namespace()))
                                                       .toList();
      generation = schemas.size() == 1 ?
                   schemas.get(0) :
                   HybridGeneration.ofFileName(first);
    }
    String named = xmp == null ?
                   null :
                   value(xmp,
                         generation.namespace(),
                         "DocumentFileName");
    String name = named != null && files.containsKey(named) ?
                  named :
                  first;
    return new Selection(generation,
                         declared,
                         name,
                         files.get(name));
  }

  /**
   * XMP property as element or as attribute of rdf:Description.
   */
  private static String value(Document xmp,
                              String namespace,
                              String property) {
    var elements = xmp.getElementsByTagNameNS(namespace,
                                              property);
    if (elements.getLength() > 0) {
      return elements.item(0)
                     .getTextContent()
                     .strip();
    }
    var descriptions = xmp.getElementsByTagNameNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                                                  "Description");
    for (int i = 0; i < descriptions.getLength(); i++) {
      String attribute = ((Element) descriptions.item(i)).getAttributeNS(namespace,
                                                                         property);
      if (!attribute.isBlank()) {
        return attribute.strip();
      }
    }
    return null;
  }

  /**
   * XMP packet for PDF/A-3 with the Factur-X extension schema. Document information is mirrored
   * from the info dictionary, as PDF/A requires both to match.
   */
  private static String xmp(org.apache.pdfbox.pdmodel.PDDocumentInformation info,
                            String conformance,
                            Hybrid hybrid) {
    var xml = new StringBuilder();
    xml.append("<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
       .append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n")
       .append(" <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
       .append("  <rdf:Description rdf:about=\"\" xmlns:pdfaid=\"")
       .append(PDFAID)
       .append("\">\n")
       .append("   <pdfaid:part>3</pdfaid:part>\n")
       .append("   <pdfaid:conformance>")
       .append(conformance)
       .append("</pdfaid:conformance>\n")
       .append("  </rdf:Description>\n");

    xml.append("  <rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
       .append("   <dc:format>application/pdf</dc:format>\n");
    if (info.getTitle() != null) {
      xml.append("   <dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">")
         .append(escape(info.getTitle()))
         .append("</rdf:li></rdf:Alt></dc:title>\n");
    }
    if (info.getAuthor() != null) {
      xml.append("   <dc:creator><rdf:Seq><rdf:li>")
         .append(escape(info.getAuthor()))
         .append("</rdf:li></rdf:Seq></dc:creator>\n");
    }
    if (info.getSubject() != null) {
      xml.append("   <dc:description><rdf:Alt><rdf:li xml:lang=\"x-default\">")
         .append(escape(info.getSubject()))
         .append("</rdf:li></rdf:Alt></dc:description>\n");
    }
    xml.append("  </rdf:Description>\n");

    xml.append("  <rdf:Description rdf:about=\"\" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">\n");
    optional(xml,
             "xmp:CreatorTool",
             info.getCreator());
    optional(xml,
             "xmp:CreateDate",
             date(info.getCreationDate()));
    optional(xml,
             "xmp:ModifyDate",
             date(info.getModificationDate()));
    optional(xml,
             "xmp:MetadataDate",
             date(info.getModificationDate()));
    xml.append("  </rdf:Description>\n");

    xml.append("  <rdf:Description rdf:about=\"\" xmlns:pdf=\"http://ns.adobe.com/pdf/1.3/\">\n");
    optional(xml,
             "pdf:Producer",
             info.getProducer());
    optional(xml,
             "pdf:Keywords",
             info.getKeywords());
    xml.append("  </rdf:Description>\n");

    xml.append("  <rdf:Description rdf:about=\"\" xmlns:fx=\"")
       .append(hybrid.namespace())
       .append("\">\n")
       .append("   <fx:DocumentType>")
       .append(hybrid.documentType())
       .append("</fx:DocumentType>\n")
       .append("   <fx:DocumentFileName>")
       .append(hybrid.fileName())
       .append("</fx:DocumentFileName>\n")
       .append("   <fx:Version>")
       .append(hybrid.version())
       .append("</fx:Version>\n")
       .append("   <fx:ConformanceLevel>")
       .append(hybrid.conformanceLevel())
       .append("</fx:ConformanceLevel>\n")
       .append("  </rdf:Description>\n");

    xml.append("  <rdf:Description rdf:about=\"\"")
       .append(" xmlns:pdfaExtension=\"http://www.aiim.org/pdfa/ns/extension/\"")
       .append(" xmlns:pdfaSchema=\"http://www.aiim.org/pdfa/ns/schema#\"")
       .append(" xmlns:pdfaProperty=\"http://www.aiim.org/pdfa/ns/property#\">\n")
       .append("   <pdfaExtension:schemas><rdf:Bag><rdf:li rdf:parseType=\"Resource\">\n")
       .append("    <pdfaSchema:schema>")
       .append(hybrid.schemaName())
       .append("</pdfaSchema:schema>\n")
       .append("    <pdfaSchema:namespaceURI>")
       .append(hybrid.namespace())
       .append("</pdfaSchema:namespaceURI>\n")
       .append("    <pdfaSchema:prefix>fx</pdfaSchema:prefix>\n")
       .append("    <pdfaSchema:property><rdf:Seq>\n");
    extensionProperty(xml,
                      "DocumentFileName",
                      "The name of the embedded XML document");
    extensionProperty(xml,
                      "DocumentType",
                      "The type of the hybrid document in capital letters, e.g. INVOICE or ORDER");
    extensionProperty(xml,
                      "Version",
                      "The actual version of the standard applying to the embedded XML document");
    extensionProperty(xml,
                      "ConformanceLevel",
                      "The conformance level of the embedded XML document");
    xml.append("    </rdf:Seq></pdfaSchema:property>\n")
       .append("   </rdf:li></rdf:Bag></pdfaExtension:schemas>\n")
       .append("  </rdf:Description>\n")
       .append(" </rdf:RDF>\n")
       .append("</x:xmpmeta>\n")
       .append("<?xpacket end=\"w\"?>");
    return xml.toString();
  }

  private static void extensionProperty(StringBuilder xml,
                                        String name,
                                        String description) {
    xml.append("     <rdf:li rdf:parseType=\"Resource\">")
       .append("<pdfaProperty:name>")
       .append(name)
       .append("</pdfaProperty:name>")
       .append("<pdfaProperty:valueType>Text</pdfaProperty:valueType>")
       .append("<pdfaProperty:category>external</pdfaProperty:category>")
       .append("<pdfaProperty:description>")
       .append(description)
       .append("</pdfaProperty:description></rdf:li>\n");
  }

  private static void optional(StringBuilder xml,
                               String element,
                               String value) {
    if (value != null) {
      xml.append("   <")
         .append(element)
         .append('>')
         .append(escape(value))
         .append("</")
         .append(element)
         .append(">\n");
    }
  }

  private static String date(Calendar calendar) {
    if (calendar == null) {
      return null;
    }
    ZoneId zone = calendar.getTimeZone()
                          .toZoneId();
    return ZonedDateTime.ofInstant(calendar.toInstant(),
                                   zone)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
  }

  private static String escape(String value) {
    var result = new StringBuilder(value.length());
    for (char c : value.toCharArray()) {
      switch (c) {
        case '&' -> result.append("&amp;");
        case '<' -> result.append("&lt;");
        case '>' -> result.append("&gt;");
        case '"' -> result.append("&quot;");
        default -> {
          if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
            result.append(c);
          }
        }
      }
    }
    return result.toString();
  }

  private static void checkFile(List<io.github.nalukit.paniolo.validation.ValidationMessage> findings,
                                PDDocumentCatalog catalog,
                                String name,
                                PDComplexFileSpecification file)
      throws IOException {
    boolean associated = false;
    COSArray array = catalog.getCOSObject()
                            .getCOSArray(AF);
    if (array != null) {
      for (int i = 0; i < array.size(); i++) {
        if (array.getObject(i) instanceof COSDictionary dictionary && dictionary.equals(file.getCOSObject())) {
          associated = true;
        }
      }
    }
    if (!associated) {
      finding(findings,
              "FX-NOT-ASSOCIATED",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              name + " must be referenced from the /AF array of the document catalog",
              "/Root/AF");
    }
    String relationship = file.getCOSObject()
                              .getNameAsString(RELATIONSHIP);
    if (!List.of("Alternative",
                 "Data",
                 "Source")
             .contains(relationship)) {
      finding(findings,
              "FX-AF-RELATIONSHIP",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "AFRelationship of " + name + " must be Alternative, Data or Source, but is " + relationship,
              "/Root/AF/AFRelationship");
    }
    PDEmbeddedFile embedded = file.getEmbeddedFileUnicode() != null ?
                              file.getEmbeddedFileUnicode() :
                              file.getEmbeddedFile();
    if (embedded == null) {
      finding(findings,
              "FX-NO-CONTENT",
              io.github.nalukit.paniolo.validation.Severity.FATAL,
              name + " has no embedded file stream",
              "/Root/Names/EmbeddedFiles/EF");
      return;
    }
    if (!"text/xml".equals(embedded.getSubtype())) {
      finding(findings,
              "FX-MIME-TYPE",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "The MIME type of " + name + " must be text/xml, but is " + embedded.getSubtype(),
              "/Root/Names/EmbeddedFiles/EF/Subtype");
    }
    if (embedded.getModDate() == null) {
      finding(findings,
              "FX-MOD-DATE",
              io.github.nalukit.paniolo.validation.Severity.WARNING,
              "The embedded file " + name + " should state its modification date (Params/ModDate)",
              "/Root/Names/EmbeddedFiles/EF/Params");
    }
  }

  /**
   * The embedded file name: Factur-X {@code xrechnung.xml} for XRECHNUNG, {@code factur-x.xml} (or
   * {@code zugferd-invoice.xml}) otherwise; ZUGFeRD 2.0 {@code zugferd-invoice.xml}; ZUGFeRD 1.0
   * {@code ZUGFeRD-invoice.xml}. Names are compared case-sensitively.
   */
  private static void checkFileName(List<io.github.nalukit.paniolo.validation.ValidationMessage> findings,
                                    HybridGeneration generation,
                                    FacturXProfile profile,
                                    String name) {
    if (generation == HybridGeneration.FACTUR_X) {
      if (profile == null) {
        return;
      }
      String expected = profile == FacturXProfile.XRECHNUNG ?
                        "xrechnung.xml" :
                        "factur-x.xml";
      if (!expected.equals(name) && !"zugferd-invoice.xml".equals(name)) {
        finding(findings,
                "FX-FILE-NAME",
                io.github.nalukit.paniolo.validation.Severity.ERROR,
                "Profile " + profile.getConformanceLevel() + " requires the embedded file name " + expected + ", but it is " + name,
                "/Root/Names/EmbeddedFiles");
      }
    } else if (!generation.fileNames()
                          .contains(name)) {
      finding(findings,
              "FX-FILE-NAME",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              generation.title() + " requires the embedded file name " + generation.fileNames()
                                                                                   .get(0) + ", but it is " + name,
              "/Root/Names/EmbeddedFiles");
    }
  }

  /**
   * The {@code Version} property, separately for each generation:
   *
   * <ul>
   *   <li>Factur-X: 1.0, for XRECHNUNG the XRechnung version; another value is a warning, checked
   *       only for a known conformance level.</li>
   *   <li>ZUGFeRD 2.0: 2p0. 1.0, the Factur-X value some ZUGFeRD 2.0 tools wrote, is a warning for
   *       compatibility; any other value is an error.</li>
   *   <li>ZUGFeRD 1.0: 1.0; another value is a warning.</li>
   * </ul>
   *
   * A missing version is an error.
   */
  private static void checkVersion(List<io.github.nalukit.paniolo.validation.ValidationMessage> findings,
                                   Document xmp,
                                   HybridGeneration generation,
                                   FacturXProfile profile) {
    if (generation == HybridGeneration.FACTUR_X && profile == null) {
      return;
    }
    String property = generation.prefix() + ":Version";
    String version = value(xmp,
                           generation.namespace(),
                           "Version");
    if (version == null) {
      finding(findings,
              "FX-VERSION",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              property + " is missing",
              "XMP " + property);
    } else if (generation == HybridGeneration.FACTUR_X) {
      if (profile != FacturXProfile.XRECHNUNG && !generation.version()
                                                            .equals(version)) {
        finding(findings,
                "FX-VERSION",
                io.github.nalukit.paniolo.validation.Severity.WARNING,
                property + " of Factur-X profiles is 1.0, but is " + version,
                "XMP " + property);
      }
    } else if (generation == HybridGeneration.ZUGFERD_2) {
      if ("1.0".equals(version)) {
        finding(findings,
                "FX-VERSION",
                io.github.nalukit.paniolo.validation.Severity.WARNING,
                property + " of ZUGFeRD 2.0 is 2p0, but is 1.0, the value of Factur-X; accepted for compatibility with ZUGFeRD 2.0 producers that wrote it",
                "XMP " + property);
      } else if (!generation.version()
                            .equals(version)) {
        finding(findings,
                "FX-VERSION",
                io.github.nalukit.paniolo.validation.Severity.ERROR,
                property + " of ZUGFeRD 2.0 must be 2p0, but is " + version,
                "XMP " + property);
      }
    } else if (!generation.version()
                          .equals(version)) {
      finding(findings,
              "FX-VERSION",
              io.github.nalukit.paniolo.validation.Severity.WARNING,
              property + " of ZUGFeRD 1.0 is 1.0, but is " + version,
              "XMP " + property);
    }
  }

  /**
   * Relationship and specification identifier of a Factur-X or ZUGFeRD 2.0 profile.
   */
  private static void checkProfile(List<io.github.nalukit.paniolo.validation.ValidationMessage> findings,
                                   HybridGeneration generation,
                                   String level,
                                   Document invoice,
                                   PDComplexFileSpecification file) {
    // Germany requires Alternative, except Data for the profiles MINIMUM and BASIC WL.
    String relationship = file.getCOSObject()
                              .getNameAsString(RELATIONSHIP);
    String german = "MINIMUM".equals(level) || "BASIC WL".equals(level) ?
                    "Data" :
                    "Alternative";
    if (relationship != null && !german.equals(relationship)) {
      finding(findings,
              "FX-AF-RELATIONSHIP-DE",
              io.github.nalukit.paniolo.validation.Severity.WARNING,
              "For profile " + level + " Germany requires AFRelationship " + german + ", the document uses " + relationship,
              "/Root/AF/AFRelationship");
    }
    if (embeddedFile(file) == null) {
      // Reported as FX-NO-CONTENT.
      return;
    }
    String guideline = guideline(invoice);
    // Also accepts the ZUGFeRD 2.0 identifiers and every XRechnung version.
    boolean matches = generation.profile(level) == FacturXProfile.ofSpecificationId(guideline);
    if (!matches) {
      finding(findings,
              "FX-GUIDELINE",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              generation.prefix() + ":ConformanceLevel " + level + " does not match the specification identifier (BT-24) " + guideline + " of the embedded invoice",
              "/rsm:CrossIndustryInvoice/rsm:ExchangedDocumentContext/ram:GuidelineSpecifiedDocumentContextParameter/ram:ID");
    }
  }

  /**
   * The root element of the embedded XML belongs to the generation: CII {@code CrossIndustryInvoice}
   * for Factur-X and ZUGFeRD 2.0, {@code CrossIndustryDocument} for ZUGFeRD 1.0. XML that cannot be
   * parsed is left to the validation of the invoice XML.
   */
  private static void checkRoot(List<io.github.nalukit.paniolo.validation.ValidationMessage> findings,
                                HybridGeneration generation,
                                String name,
                                Document invoice) {
    if (invoice == null || invoice.getDocumentElement() == null) {
      return;
    }
    Element root = invoice.getDocumentElement();
    if (!generation.rootName()
                   .equals(root.getLocalName()) || !generation.rootNamespace()
                                                              .equals(root.getNamespaceURI())) {
      finding(findings,
              "FX-XML-ROOT",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "The XMP metadata declare " + generation.title() + ", which embeds {" + generation.rootNamespace() + "}" + generation.rootName() + ", but " + name + " is {" + root.getNamespaceURI() + "}" + root.getLocalName(),
              "/Root/Names/EmbeddedFiles/" + name);
    }
  }

  private static boolean hasExtensionSchema(Document xmp,
                                            String namespace) {
    var uris = xmp.getElementsByTagNameNS("http://www.aiim.org/pdfa/ns/schema#",
                                          "namespaceURI");
    for (int i = 0; i < uris.getLength(); i++) {
      if (namespace.equals(uris.item(i)
                               .getTextContent()
                               .strip())) {
        return true;
      }
    }
    return false;
  }

  /**
   * The embedded XML as namespace-aware DOM, or null if it has no content or cannot be parsed.
   */
  private static Document parseInvoice(PDComplexFileSpecification file)
      throws IOException {
    if (embeddedFile(file) == null) {
      return null;
    }
    byte[] xml = read(file);
    try {
      return XmlDocuments.parse(new ByteArrayInputStream(xml));
    } catch (IOException | SAXException e) {
      return null;
    }
  }

  private static String levels(HybridGeneration generation) {
    List<String> levels = generation.levels();
    return String.join(", ",
                       levels.subList(0,
                                      levels.size() - 1)) + " or " + levels.get(levels.size() - 1);
  }

  /**
   * BT-24 of an embedded CII invoice, or null.
   */
  private static String guideline(byte[] xml) {
    try {
      return guideline(XmlDocuments.parse(new ByteArrayInputStream(xml)));
    } catch (IOException | SAXException e) {
      return null;
    }
  }

  /**
   * BT-24 of a parsed CII invoice, or null.
   */
  private static String guideline(Document document) {
    if (document == null) {
      return null;
    }
    var ids = document.getElementsByTagNameNS("urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100",
                                              "GuidelineSpecifiedDocumentContextParameter");
    if (ids.getLength() == 0) {
      return null;
    }
    var id = ((Element) ids.item(0)).getElementsByTagNameNS("urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100",
                                                            "ID");
    return id.getLength() == 0 ?
           null :
           id.item(0)
             .getTextContent()
             .strip();
  }

  private static void finding(List<io.github.nalukit.paniolo.validation.ValidationMessage> findings,
                              String rule,
                              io.github.nalukit.paniolo.validation.Severity severity,
                              String message,
                              String location) {
    findings.add(new io.github.nalukit.paniolo.validation.ValidationMessage(rule,
                                                                            severity,
                                                                            message,
                                                                            location,
                                                                            io.github.nalukit.paniolo.validation.ValidationMessage.Stage.HYBRID));
  }

  @Override
  public byte[] embed(byte[] pdf,
                      byte[] xml,
                      FacturXProfile profile) {
    return embed(pdf,
                 xml,
                 profile,
                 List.of());
  }

  @Override
  public byte[] embed(byte[] pdf,
                      byte[] xml,
                      FacturXProfile profile,
                      List<PdfAttachment> attachments) {
    return embed(pdf,
                 xml,
                 profile,
                 attachments,
                 false);
  }

  @Override
  public byte[] replaceInvoice(byte[] pdf,
                               byte[] xml,
                               FacturXProfile profile) {
    return embed(pdf,
                 xml,
                 profile,
                 List.of(),
                 true);
  }

  /**
   * Embeds the invoice; with {@code replace} an embedded invoice XML, e.g. a ZUGFeRD 1.0 one, is
   * removed first, otherwise it is rejected.
   */
  private byte[] embed(byte[] pdf,
                       byte[] xml,
                       FacturXProfile profile,
                       List<PdfAttachment> attachments,
                       boolean replace) {
    Objects.requireNonNull(profile,
                           "profile");
    return embed(pdf,
                 xml,
                 new Hybrid(FX,
                            "Factur-X PDFA Extension Schema",
                            "INVOICE",
                            profile.getFileName(),
                            profile.getXmpVersion(),
                            profile.getConformanceLevel(),
                            profile.getAfRelationship(),
                            "Factur-X invoice (" + profile.getConformanceLevel() + ")"),
                 attachments,
                 replace);
  }

  @Override
  public byte[] embedOrder(byte[] pdf,
                           byte[] xml,
                           OrderXProfile profile,
                           OrderTypeCode type,
                           List<PdfAttachment> attachments) {
    Objects.requireNonNull(profile,
                           "profile");
    Objects.requireNonNull(type,
                           "type");
    return embed(pdf,
                 xml,
                 new Hybrid(OX,
                            "Order-X PDFA Extension Schema",
                            type.getDocumentType(),
                            ORDER_FILE,
                            "1.0",
                            profile.getConformanceLevel(),
                            AfRelationship.ALTERNATIVE.getPdfName(),
                            "Order-X " +
                            type.getDocumentType()
                                .toLowerCase(java.util.Locale.ROOT) +
                            " (" +
                            profile.getConformanceLevel() +
                            ")"),
                 attachments,
                 false);
  }

  @Override
  public byte[] extractOrder(byte[] pdf) {
    Objects.requireNonNull(pdf,
                           "pdf");
    try (PDDocument document = load(pdf)) {
      var file = allFiles(document.getDocumentCatalog()).get(ORDER_FILE);
      if (file == null) {
        throw new IllegalArgumentException("The PDF contains no Order-X document (" + ORDER_FILE + ")");
      }
      return read(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read Order-X document",
                                     e);
    }
  }

  @Override
  public byte[] embedDespatchAdvice(byte[] pdf,
                                    byte[] xml,
                                    List<PdfAttachment> attachments) {
    return embed(pdf,
                 xml,
                 new Hybrid(DX,
                            "Despatch Advice PDFA Extension Schema",
                            "DESPATCHADVICE",
                            DESPATCH_FILE,
                            "1.0",
                            "PILOT",
                            AfRelationship.ALTERNATIVE.getPdfName(),
                            "Delivery note (CIDA)"),
                 attachments,
                 false);
  }

  @Override
  public byte[] extractDespatchAdvice(byte[] pdf) {
    Objects.requireNonNull(pdf,
                           "pdf");
    try (PDDocument document = load(pdf)) {
      var file = allFiles(document.getDocumentCatalog()).get(DESPATCH_FILE);
      if (file == null) {
        throw new IllegalArgumentException("The PDF contains no despatch advice (" + DESPATCH_FILE + ")");
      }
      return read(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read despatch advice",
                                     e);
    }
  }

  private byte[] embed(byte[] pdf,
                       byte[] xml,
                       Hybrid hybrid,
                       List<PdfAttachment> attachments,
                       boolean replace) {
    Objects.requireNonNull(attachments,
                           "attachments");
    Objects.requireNonNull(pdf,
                           "pdf");
    Objects.requireNonNull(xml,
                           "xml");
    try (PDDocument document = load(pdf)) {
      if (document.isEncrypted()) {
        throw new IllegalArgumentException("Encrypted PDF documents cannot be Factur-X documents");
      }
      PDDocumentCatalog catalog     = document.getDocumentCatalog();
      String            conformance = pdfaConformance(catalog);
      if (replace) {
        removeInvoiceFiles(catalog);
      } else if (!invoiceFiles(catalog).isEmpty() || allFiles(catalog).containsKey(ORDER_FILE) || allFiles(catalog).containsKey(DESPATCH_FILE)) {
        throw new IllegalArgumentException("The PDF already contains an embedded invoice, order, or despatch advice XML");
      }
      Calendar now = new GregorianCalendar();
      now.set(Calendar.MILLISECOND,
              0);
      var names = new java.util.HashSet<>(allFiles(catalog).keySet());
      for (var attachment : attachments) {
        if (!names.add(attachment.fileName())) {
          throw new IllegalArgumentException("The PDF already contains a file named " + attachment.fileName());
        }
      }
      embedFile(document,
                catalog,
                hybrid.fileName(),
                xml,
                "text/xml",
                hybrid.relationship(),
                hybrid.description(),
                now);
      for (var attachment : attachments) {
        embedFile(document,
                  catalog,
                  attachment.fileName(),
                  attachment.content(),
                  attachment.mimeType(),
                  attachment.relationship()
                            .getPdfName(),
                  attachment.description(),
                  now);
      }
      if (catalog.getOutputIntents()
                 .isEmpty()) {
        addSrgbOutputIntent(document,
                            catalog);
      }
      var info = document.getDocumentInformation();
      info.setModificationDate(now);
      // The XMP pdf:Producer below is written from the document information.
      info.setProducer(PdfProducer.sign(info.getProducer()));
      if (document.getVersion() < 1.7f) {
        document.setVersion(1.7f);
      }
      var metadata = new PDMetadata(document);
      metadata.importXMPMetadata(xmp(info,
                                     conformance,
                                     hybrid).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      catalog.setMetadata(metadata);
      var out = new ByteArrayOutputStream(pdf.length + xml.length + 8192);
      document.save(out);
      LOGGER.log(System.Logger.Level.DEBUG,
                 "Embedded {0} ({1}) into PDF/A-3{2}",
                 hybrid.fileName(),
                 hybrid.conformanceLevel(),
                 conformance);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot create Factur-X document",
                                     e);
    }
  }

  @Override
  public PdfProducer producer(byte[] pdf) {
    Objects.requireNonNull(pdf,
                           "pdf");
    try (PDDocument document = load(pdf)) {
      var    info     = document.getDocumentInformation();
      String producer = info.getProducer();
      String creator  = info.getCreator();
      // The XMP metadata fills in what the document information lacks.
      Document xmp = parseXmp(document.getDocumentCatalog()
                                      .getMetadata());
      if (xmp != null) {
        if (producer == null || producer.isBlank()) {
          producer = value(xmp,
                           "http://ns.adobe.com/pdf/1.3/",
                           "Producer");
        }
        if (creator == null || creator.isBlank()) {
          creator = value(xmp,
                          "http://ns.adobe.com/xap/1.0/",
                          "CreatorTool");
        }
      }
      return new PdfProducer(producer,
                             creator,
                             PdfProducer.recognize(producer,
                                                   creator,
                                                   pdf));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the PDF document",
                                     e);
    }
  }

  @Override
  public List<PdfAttachment> attachments(byte[] pdf) {
    return attachments(pdf,
                       MAX_ATTACHMENTS,
                       MAX_ATTACHMENT_BYTES);
  }

  /**
   * Reads the attachments within a budget. Every attachment is decoded with the remaining budget as
   * its limit, filter stage by filter stage, and decoding stops at the first byte beyond it. No
   * decoder produces more than the budget, so a small PDF cannot expand into the heap before the
   * check. A predictor allocates two row buffers before it writes a byte; its parameters are checked
   * first, and a row has to fit in the limit and in {@link #MAX_PREDICTOR_ROW}. At most the budget
   * plus the buffer of one stage and, with a predictor, two rows are held for the attachment.
   *
   * @throws IllegalArgumentException if there are more than {@code maxCount} attachments, an
   *                                  attachment exceeds {@link #MAX_FILE}, or the contents together
   *                                  exceed {@code maxBytes}
   */
  List<PdfAttachment> attachments(byte[] pdf,
                                  int maxCount,
                                  long maxBytes) {
    Objects.requireNonNull(pdf,
                           "pdf");
    try (PDDocument document = load(pdf)) {
      var  result    = new java.util.ArrayList<PdfAttachment>();
      long remaining = maxBytes;
      for (var entry : allFiles(document.getDocumentCatalog()).entrySet()) {
        // Files of other producers may use paths, other cases, or no valid MIME type.
        String name = entry.getKey()
                           .replaceAll(".*[/\\\\]",
                                       "");
        if (name.isBlank() || PdfAttachment.INVOICE_FILE_NAMES.contains(name.toLowerCase(java.util.Locale.ROOT))) {
          continue;
        }
        if (result.size() >= maxCount) {
          throw new IllegalArgumentException("The PDF has more than " + maxCount + " attachments");
        }
        var file     = entry.getValue();
        var embedded = embeddedFile(file);
        String mimeType = PdfAttachment.normalizeMimeType(embedded == null ?
                                                          null :
                                                          embedded.getSubtype());
        int    limit   = (int) Math.min(MAX_FILE,
                                        remaining);
        byte[] content = read(file,
                              limit,
                              limit < MAX_FILE ?
                              "The attachments of the PDF exceed " + maxBytes + " bytes together" :
                              "Embedded file " + fileName(file) + " exceeds " + MAX_FILE + " bytes");
        remaining -= content.length;
        result.add(new PdfAttachment(name,
                                     content,
                                     mimeType,
                                     AfRelationship.ofPdfName(file.getCOSObject()
                                                                  .getNameAsString(RELATIONSHIP)),
                                     file.getFileDescription()));
      }
      return List.copyOf(result);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the attachments of the PDF document",
                                     e);
    }
  }

  @Override
  public <T> T probeInvoice(byte[] pdf,
                            Function<InputStream, T> probe) {
    Objects.requireNonNull(pdf,
                           "pdf");
    Objects.requireNonNull(probe,
                           "probe");
    try (PDDocument document = load(pdf)) {
      // A PDF with several invoice files is no valid container, but extraction and reading take the
      // one the XMP metadata names; the probe asks that file first and then every other candidate,
      // so an empty answer never hides an invoice that the other ways read.
      var catalog = document.getDocumentCatalog();
      var files   = invoiceFiles(catalog);
      if (files.isEmpty()) {
        return null;
      }
      var candidates = new java.util.ArrayList<PDComplexFileSpecification>();
      if (files.size() > 1) {
        candidates.add(select(parseXmp(catalog.getMetadata()),
                              files).file());
      }
      for (var file : files.values()) {
        if (!candidates.contains(file)) {
          candidates.add(file);
        }
      }
      for (var file : candidates) {
        PDEmbeddedFile embedded = embeddedFile(file);
        if (embedded == null) {
          continue;
        }
        try (InputStream input = new ByteArrayInputStream(decode(embedded.getCOSObject(),
                                                                 MAX_XML,
                                                                 "Embedded invoice exceeds " + MAX_XML + " bytes"))) {
          T answer = probe.apply(input);
          if (answer != null) {
            return answer;
          }
        } catch (IllegalArgumentException | IOException e) {
          // try the next candidate
        }
      }
      return null;
    } catch (IllegalArgumentException | IOException e) {
      return null;
    }
  }

  /**
   * The diagnosis for a PDF without invoice XML: an embedded Order-X document or despatch advice is
   * named, as is any other attached file, so that the caller can tell the kind of document.
   */
  private static String noInvoice(PDDocumentCatalog catalog) throws IOException {
    var all = allFiles(catalog);
    if (all.containsKey(ORDER_FILE)) {
      return "The PDF contains no Factur-X / ZUGFeRD invoice XML but an Order-X document (" + ORDER_FILE + "); read it as an order";
    }
    if (all.containsKey(DESPATCH_FILE)) {
      return "The PDF contains no Factur-X / ZUGFeRD invoice XML but a despatch advice (" + DESPATCH_FILE + "); read it as a despatch advice";
    }
    return all.isEmpty() ?
           "The PDF contains no Factur-X / ZUGFeRD invoice XML and no embedded file" :
           "The PDF contains no Factur-X / ZUGFeRD invoice XML; embedded files: " + String.join(", ",
                                                                                                 new java.util.TreeSet<>(all.keySet()));
  }

  @Override
  public EmbeddedInvoice extract(byte[] pdf) {
    Objects.requireNonNull(pdf,
                           "pdf");
    try (PDDocument document = load(pdf)) {
      PDDocumentCatalog catalog = document.getDocumentCatalog();
      Document          xmp     = parseXmp(catalog.getMetadata());
      var               files   = invoiceFiles(catalog);
      if (files.isEmpty()) {
        throw new IllegalArgumentException(noInvoice(catalog));
      }
      // The same generation and file as the container check.
      Selection selection = select(xmp,
                                   files);
      String name = selection.name();
      byte[] xml = read(selection.file());
      // Only a level of the namespace the XMP actually uses counts, never one of another generation.
      FacturXProfile profile = xmp == null ?
                               null :
                               selection.generation()
                                        .profile(value(xmp,
                                                       selection.generation()
                                                                .namespace(),
                                                       "ConformanceLevel"));
      if (profile == null) {
        profile = FacturXProfile.ofFileName(name);
      }
      // Without XMP the specification identifier of the invoice tells the profile.
      if (profile == null) {
        profile = FacturXProfile.ofSpecificationId(guideline(xml));
      }
      return new EmbeddedInvoice(name,
                                 profile,
                                 xml);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read Factur-X document",
                                     e);
    }
  }

  @Override
  public List<io.github.nalukit.paniolo.validation.ValidationMessage> checkContainer(byte[] pdf) {
    Objects.requireNonNull(pdf,
                           "pdf");
    var findings = new java.util.ArrayList<io.github.nalukit.paniolo.validation.ValidationMessage>();
    try (PDDocument document = load(pdf)) {
      if (document.isEncrypted()) {
        finding(findings,
                "FX-ENCRYPTED",
                io.github.nalukit.paniolo.validation.Severity.ERROR,
                "Factur-X documents must not be encrypted",
                "/Encrypt");
      }
      PDDocumentCatalog catalog = document.getDocumentCatalog();
      var               files   = invoiceFiles(catalog);
      if (files.isEmpty()) {
        finding(findings,
                "FX-NO-INVOICE",
                io.github.nalukit.paniolo.validation.Severity.FATAL,
                "The PDF contains no embedded invoice XML (factur-x.xml, xrechnung.xml or zugferd-invoice.xml)",
                "/Root/Names/EmbeddedFiles");
        return List.copyOf(findings);
      }
      if (files.size() > 1) {
        finding(findings,
                "FX-MULTIPLE-INVOICES",
                io.github.nalukit.paniolo.validation.Severity.ERROR,
                "Only one invoice XML may be embedded, found " + files.keySet(),
                "/Root/Names/EmbeddedFiles");
      }
      for (String name : conflictingInvoiceFiles(catalog)) {
        finding(findings,
                "FX-MULTIPLE-INVOICES",
                io.github.nalukit.paniolo.validation.Severity.ERROR,
                "Different embedded files named " + name + " carry different content; only one invoice XML may be embedded",
                "/Root/AF");
      }
      Document  xmp       = parseXmp(catalog.getMetadata());
      Selection selection = select(xmp,
                                   files);
      checkFile(findings,
                catalog,
                selection.name(),
                selection.file());
      checkXmp(findings,
               xmp,
               selection);
    } catch (IllegalArgumentException e) {
      finding(findings,
              "FX-NOT-PDF",
              io.github.nalukit.paniolo.validation.Severity.FATAL,
              e.getMessage(),
              "");
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read Factur-X document",
                                     e);
    }
    return List.copyOf(findings);
  }

  /**
   * The XMP metadata against the generation they declare (see {@link #select}): document type,
   * file name, conformance level, version, extension schema, and the embedded XML.
   */
  private void checkXmp(List<io.github.nalukit.paniolo.validation.ValidationMessage> findings,
                        Document xmp,
                        Selection selection)
      throws IOException {
    if (xmp == null) {
      finding(findings,
              "FX-XMP",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "The document has no readable XMP metadata",
              "/Root/Metadata");
      return;
    }
    if (!"3".equals(value(xmp,
                          PDFAID,
                          "part"))) {
      finding(findings,
              "FX-PDFA-PART",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "Factur-X requires PDF/A-3, but pdfaid:part is " +
              value(xmp,
                    PDFAID,
                    "part"),
              "XMP pdfaid:part");
    }
    HybridGeneration generation = selection.generation();
    String           namespace  = generation.namespace();
    String           prefix     = generation.prefix();
    String           name       = selection.name();
    if (selection.declared()
                 .size() > 1) {
      finding(findings,
              "FX-XMP-GENERATIONS",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "The XMP metadata describe the invoice in the namespaces of several generations (" +
              selection.declared()
                       .stream()
                       .map(declared -> declared.title() + " " + declared.namespace())
                       .collect(java.util.stream.Collectors.joining(", ")) +
              "); only one is allowed, checked as " +
              generation.title(),
              "XMP");
    }
    String type = value(xmp,
                        namespace,
                        "DocumentType");
    if (!"INVOICE".equals(type)) {
      finding(findings,
              "FX-DOCUMENT-TYPE",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              prefix + ":DocumentType (" + generation.title() + ") must be INVOICE, but is " + type,
              "XMP " + prefix + ":DocumentType");
    }
    String declared = value(xmp,
                            namespace,
                            "DocumentFileName");
    if (!name.equals(declared)) {
      finding(findings,
              "FX-DOCUMENT-FILE-NAME",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              prefix + ":DocumentFileName (" + generation.title() + ") must name the embedded file " + name + ", but is " + declared,
              "XMP " + prefix + ":DocumentFileName");
    }
    String level = value(xmp,
                         namespace,
                         "ConformanceLevel");
    // Factur-X accepted its levels in any case before; ZUGFeRD 1.0 and 2.0 take them exactly.
    boolean known = generation == HybridGeneration.FACTUR_X ?
                    FacturXProfile.ofConformanceLevel(level) != null :
                    generation.hasLevel(level);
    FacturXProfile profile = known ?
                             generation.profile(level) :
                             null;
    if (!known) {
      finding(findings,
              "FX-CONFORMANCE-LEVEL",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              prefix + ":ConformanceLevel (" + generation.title() + ") must be " + levels(generation) + ", but is " + level,
              "XMP " + prefix + ":ConformanceLevel");
    }
    checkFileName(findings,
                  generation,
                  profile,
                  name);
    checkVersion(findings,
                 xmp,
                 generation,
                 profile);
    Document invoice = parseInvoice(selection.file());
    if (known && generation != HybridGeneration.ZUGFERD_1) {
      checkProfile(findings,
                   generation,
                   level,
                   invoice,
                   selection.file());
    }
    checkRoot(findings,
              generation,
              name,
              invoice);
    if (xmp.getDocumentElement() == null || !hasExtensionSchema(xmp,
                                                                namespace)) {
      finding(findings,
              "FX-EXTENSION-SCHEMA",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "The XMP metadata must declare the " + generation.title() + " namespace " + namespace + " in a PDF/A extension schema",
              "XMP pdfaExtension:schemas");
    }
  }

  /**
   * The generation of the XMP metadata, all generations whose namespace carries invoice properties,
   * and the embedded invoice file that is checked or extracted.
   */
  private record Selection(HybridGeneration generation, List<HybridGeneration> declared, String name, PDComplexFileSpecification file) {
  }

  /**
   * What a hybrid PDF embeds and declares in its XMP metadata.
   */
  private record Hybrid(String namespace, String schemaName, String documentType, String fileName, String version, String conformanceLevel, String relationship, String description) {
  }

}
