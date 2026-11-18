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
package io.github.nalukit.paniolo.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Settings of the visual document that {@code paniolo-facturx} renders from an invoice,
 * order, or despatch advice, by default a PDF/A-3b document (PDF 1.7); see {@link PdfProfile}, e.g. for {@code Paniolo#toPdf(Invoice, PdfLayout)} and
 * {@code Paniolo#toFacturX(Invoice, FacturXProfile, PdfLayout, List)}. Immutable; the byte arrays
 * are copied defensively.
 *
 * <pre>{@code
 * PdfLayout layout = PdfLayout.of(Locale.GERMAN)
 *                             .withLogo(Files.readAllBytes(Path.of("logo.png")))
 *                             .withShowLogo(true)
 *                             .withLogoPosition(PdfLayout.LogoPosition.RIGHT)
 *                             .withAccentColor(0x01244F)
 *                             .withFooter("Managing director: Erika Muster · Berlin commercial register HRB 12345");
 * }</pre>
 *
 * <p>The profile selects the kind of PDF: PDF/A-3b (PDF 1.7, the default), PDF/A-1b (PDF 1.4, the
 * visual part to pass to {@code Paniolo#toFacturX}), or a plain PDF 1.7 or 2.0 preview. PDF/A-3 is
 * based on PDF 1.7 and does not allow PDF 2.0.
 *
 * <p>For letter paper with a preprinted letterhead, hide the sender's address with
 * {@code withShowSenderAddress(false)}; the sender line above the address window and the footer
 * remain. Combined with {@code withShowLogo(true)}, the letterhead shows only the logo, on the side
 * chosen with {@code withLogoPosition}. If the paper also has the fold and hole marks preprinted,
 * hide the ones of the document with {@code withFoldMarks(false)}.
 *
 * @param locale            language of the labels and the number and date formats; German,
 *                          English and French are supported, other languages fall back to English
 * @param showSenderAddress whether the letterhead shows the sender's name, address, phone, and
 *                          e-mail; {@code true} by default
 * @param logo              PNG or JPEG image for the letterhead, or null; see
 *                          {@link #withLogo(byte[])} for the requirements
 * @param showLogo          whether the letterhead shows the logo; {@code false} by default, and
 *                          without effect if there is no logo
 * @param logoPosition      side of the letterhead the logo is shown on; {@link LogoPosition#LEFT}
 *                          by default. The sender's address is shown on the other side.
 * @param regularFont       TrueType font for regular text, or null for IBM Plex Sans (packaged
 *                          with paniolo-facturx)
 * @param boldFont          TrueType font for bold text, or null to embolden the regular font; with
 *                          no regular font, IBM Plex Sans Bold is used
 * @param accentColor       accent color as RGB, e.g. {@code 0x019ABC}: the rule below the title,
 *                          the section headings, the rule above the amount due, and INFO notes of
 *                          validation reports; see {@link #withAccentColor(int)}
 * @param footer            additional footer text on every page, e.g. management and register
 *                          court, or null
 * @param watermark         text printed diagonally across every page, e.g. {@code PREVIEW}, or
 *                          null; see {@link #withWatermark(String)}
 * @param profile           kind of PDF to render, {@link PdfProfile#PDF_A_3B} by default; see
 *                          {@link #withProfile(PdfProfile)}
 * @param foldMarks         whether page 1 of a document with an address window shows the fold and
 *                          hole marks of DIN 5008 form B at the left edge; {@code true} by
 *                          default, see {@link #withFoldMarks(boolean)}
 */
public record PdfLayout(Locale locale, boolean showSenderAddress, byte[] logo, boolean showLogo, LogoPosition logoPosition, byte[] regularFont, byte[] boldFont, int accentColor, String footer, String watermark, PdfProfile profile, boolean foldMarks) {
  /**
   * Accent color of the default layout: the teal of the Paniolo brand.
   */
  public static final int DEFAULT_ACCENT_COLOR = 0x019ABC;



  /**
   * Creates the layout and copies the byte arrays; a blank footer or watermark becomes null.
   *
   * @throws NullPointerException     if locale or logo position is null
   * @throws IllegalArgumentException if a bold font is given without regular font, or if the
   *                                  accent color is not an RGB value
   */
  public PdfLayout {
    Objects.requireNonNull(locale,
                           "locale");
    Objects.requireNonNull(logoPosition,
                           "logoPosition");
    logo        = copy(logo);
    regularFont = copy(regularFont);
    boldFont    = copy(boldFont);
    if (boldFont != null && regularFont == null) {
      throw new IllegalArgumentException("A bold font needs a regular font as well");
    }
    if (accentColor < 0 || accentColor > 0xFFFFFF) {
      throw new IllegalArgumentException("Accent color must be an RGB value between 0x000000 and 0xFFFFFF");
    }
    if (footer != null && footer.isBlank()) {
      footer = null;
    }
    Objects.requireNonNull(profile,
                           "profile");
    if (watermark != null) {
      watermark = watermark.isBlank() ?
                  null :
                  watermark.strip();
    }
  }

  /**
   * The default layout: German labels, the sender's address in the letterhead, no logo,
   * IBM Plex Sans, the Paniolo accent color.
   *
   * @return the default layout
   */
  public static PdfLayout defaults() {
    return of(Locale.GERMAN);
  }

  /**
   * The default layout in a language.
   *
   * @param locale language of the labels and formats
   * @return the layout
   */
  public static PdfLayout of(Locale locale) {
    return new PdfLayout(locale,
                         true,
                         null,
                         false,
                         LogoPosition.LEFT,
                         null,
                         null,
                         DEFAULT_ACCENT_COLOR,
                         null,
                         null,
                         PdfProfile.PDF_A_3B,
                         true);
  }

  private static byte[] copy(byte[] bytes) {
    return bytes == null ?
           null :
           bytes.clone();
  }

  /**
   * Returns a copy that shows or hides the sender's name, address, phone, and e-mail in the
   * letterhead, e.g. hides them for paper with a preprinted letterhead.
   *
   * @param show whether the letterhead shows the sender's address
   * @return the layout
   */
  public PdfLayout withShowSenderAddress(boolean show) {
    return new PdfLayout(locale,
                         show,
                         logo,
                         showLogo,
                         logoPosition,
                         regularFont,
                         boldFont,
                         accentColor,
                         footer,
                         watermark,
                         profile,
                         foldMarks);
  }

  /**
   * Returns a copy with a logo image. The logo is shown only with {@link #withShowLogo(boolean)}.
   *
   * <p>Requirements of the image:
   * <ul>
   *   <li><b>Format</b>: PNG or JPEG, given as the content of the file. PNG transparency is kept.
   *       Colors must be RGB or grayscale; CMYK JPEGs break PDF/A, whose output intent is sRGB.
   *       Other formats such as SVG or PDF are not supported.</li>
   *   <li><b>Size</b>: the logo is scaled proportionally, up or down, to fit a box of 60&nbsp;mm
   *       width and 20&nbsp;mm height at the top margin. Logos with an aspect ratio of 3:1 fill the
   *       box; wider logos are 60&nbsp;mm wide and lower, narrower ones 20&nbsp;mm high and
   *       narrower. Trim empty borders, they count as part of the logo.</li>
   *   <li><b>Resolution</b>: for sharp print at 300&nbsp;dpi, the box needs about 710 &times; 240
   *       pixels; smaller images are enlarged and look blurred.</li>
   *   <li><b>File size</b>: there is no limit, but the image is embedded in every document, as it
   *       is (JPEG) or recompressed lossless (PNG); some 100&nbsp;KB are usually enough.</li>
   * </ul>
   *
   * <p>The image is read only when the document is rendered with a visible logo; an unreadable
   * image fails then with an {@link IllegalArgumentException}.
   *
   * @param logo content of a PNG or JPEG file, or null for none
   * @return the layout
   */
  public PdfLayout withLogo(byte[] logo) {
    return new PdfLayout(locale,
                         showSenderAddress,
                         logo,
                         showLogo,
                         logoPosition,
                         regularFont,
                         boldFont,
                         accentColor,
                         footer,
                         watermark,
                         profile,
                         foldMarks);
  }

  /**
   * Returns a copy that shows or hides the logo in the letterhead.
   *
   * @param show whether the letterhead shows the logo; without effect if there is no logo
   * @return the layout
   */
  public PdfLayout withShowLogo(boolean show) {
    return new PdfLayout(locale,
                         showSenderAddress,
                         logo,
                         show,
                         logoPosition,
                         regularFont,
                         boldFont,
                         accentColor,
                         footer,
                         watermark,
                         profile,
                         foldMarks);
  }

  /**
   * Returns a copy with the logo on another side of the letterhead; the sender's address moves to
   * the other side. Also applies if the sender's address is hidden and only the logo is shown.
   *
   * @param position side of the logo
   * @return the layout
   */
  public PdfLayout withLogoPosition(LogoPosition position) {
    return new PdfLayout(locale,
                         showSenderAddress,
                         logo,
                         showLogo,
                         position,
                         regularFont,
                         boldFont,
                         accentColor,
                         footer,
                         watermark,
                         profile,
                         foldMarks);
  }

  /**
   * Returns a copy with own TrueType fonts instead of IBM Plex Sans, e.g. the corporate font. The
   * fonts are embedded as subsets, as PDF/A requires; their license must allow embedding in
   * documents. Fonts whose embedding permissions forbid embedding or subsetting are rejected when
   * the document is rendered.
   *
   * @param regular font for regular text
   * @param bold    font for bold text, or null to embolden the regular font
   * @return the layout
   */
  public PdfLayout withFonts(byte[] regular,
                             byte[] bold) {
    return new PdfLayout(locale,
                         showSenderAddress,
                         logo,
                         showLogo,
                         logoPosition,
                         Objects.requireNonNull(regular,
                                                "regular"),
                         bold,
                         accentColor,
                         footer,
                         watermark,
                         profile,
                         foldMarks);
  }

  /**
   * Returns a copy with another accent color. The accent color is the only color of the document
   * besides gray and black. It is used for:
   * <ul>
   *   <li>the rule below the title (e.g. "Rechnung"),</li>
   *   <li>the section headings (e.g. "Zahlung"),</li>
   *   <li>the heavy rule above the amount due in the totals,</li>
   *   <li>notes of level INFO in validation reports.</li>
   * </ul>
   * Body text, table rules, watermark, and fold marks stay gray or black. The accent color changes
   * only the look: the XML, the profile, and the PDF/A conformance are not affected. The default
   * is {@link #DEFAULT_ACCENT_COLOR}. Choose a color that is dark enough to read on white paper;
   * a very light value makes headings and rules hard to see.
   *
   * <p>The color is a Java hexadecimal literal: {@code 0x} followed by six digits, two each for red,
   * green, and blue (00 to FF). {@code 0x01244F} is red 1, green 36, blue 79, a dark navy blue. A web
   * color such as {@code #01244F} becomes {@code 0x01244F}, or pass the text to
   * {@link #withAccentColor(String)}.
   *
   * <pre>{@code
   * PdfLayout layout = PdfLayout.defaults().withAccentColor(0xC0392B);
   * }</pre>
   *
   * @param rgb color as RGB between {@code 0x000000} and {@code 0xFFFFFF}, e.g. {@code 0x01244F}
   * @return the layout
   * @throws IllegalArgumentException if the value is not an RGB value
   */
  public PdfLayout withAccentColor(int rgb) {
    return new PdfLayout(locale,
                         showSenderAddress,
                         logo,
                         showLogo,
                         logoPosition,
                         regularFont,
                         boldFont,
                         rgb,
                         footer,
                         watermark,
                         profile,
                         foldMarks);
  }

  /**
   * Returns a copy with another accent color, given as a web color: six hexadecimal digits, with or
   * without a leading {@code #}, in upper or lower case, e.g. {@code "#01244F"}. The same as
   * {@link #withAccentColor(int)} with {@code 0x01244F}; see there for what the accent color
   * changes.
   *
   * <pre>{@code
   * PdfLayout layout = PdfLayout.defaults().withAccentColor("#C0392B");
   * }</pre>
   *
   * @param hex color as {@code #RRGGBB} or {@code RRGGBB}; surrounding blanks are ignored
   * @return the layout
   * @throws NullPointerException     if the value is null
   * @throws IllegalArgumentException if it is not exactly six hexadecimal digits, e.g. a short
   *                                  form such as {@code #014}, a color name, or {@code 0x01244F}
   */
  public PdfLayout withAccentColor(String hex) {
    Objects.requireNonNull(hex,
                           "hex");
    String digits = hex.strip();
    if (digits.startsWith("#")) {
      digits = digits.substring(1);
    }
    if (!digits.matches("[0-9a-fA-F]{6}")) {
      throw new IllegalArgumentException("Accent color must be six hexadecimal digits like #01244F, but was '" + hex + "'");
    }
    return withAccentColor(Integer.parseInt(digits,
                                            16));
  }

  /**
   * Returns a copy with additional footer text on every page.
   *
   * @param footer the text, or null for none
   * @return the layout
   */
  public PdfLayout withFooter(String footer) {
    return new PdfLayout(locale,
                         showSenderAddress,
                         logo,
                         showLogo,
                         logoPosition,
                         regularFont,
                         boldFont,
                         accentColor,
                         footer,
                         watermark,
                         profile,
                         foldMarks);
  }

  /**
   * Returns a copy with a watermark: the text is printed large, diagonally, and semi-transparent
   * across every page, e.g. {@code PREVIEW} or {@code ENTWURF} for a document that is not to be
   * sent. The text is used as given, not translated; it is drawn with the layout's bold font, and
   * characters the font cannot show are replaced.
   *
   * <p>A watermark is meant for documents without embedded XML only:
   * {@code Paniolo#toFacturX(Invoice, FacturXProfile, PdfLayout, List)} and the other methods that
   * create hybrid documents reject a layout with watermark, since such a document would be a valid
   * e-invoice or order with a visual part that says otherwise.
   *
   * @param watermark the text, or null for none
   * @return the layout
   */
  public PdfLayout withWatermark(String watermark) {
    return new PdfLayout(locale,
                         showSenderAddress,
                         logo,
                         showLogo,
                         logoPosition,
                         regularFont,
                         boldFont,
                         accentColor,
                         footer,
                         watermark,
                         profile,
                         foldMarks);
  }

  /**
   * Returns a copy that renders another kind of PDF: {@link PdfProfile#PREVIEW} for a plain PDF to
   * look at, {@link PdfProfile#PDF_A_1B} for a PDF/A-1b document, e.g. as the visual part for
   * {@code Paniolo#toFacturX}, or {@link PdfProfile#PDF_A_3B}, the
   * default. The hybrid methods ({@code toFacturX}, {@code toOrderXPdf}, ...) reject a preview.
   *
   * @param profile the kind of PDF
   * @return the layout
   */
  public PdfLayout withProfile(PdfProfile profile) {
    return new PdfLayout(locale,
                         showSenderAddress,
                         logo,
                         showLogo,
                         logoPosition,
                         regularFont,
                         boldFont,
                         accentColor,
                         footer,
                         watermark,
                         profile,
                         foldMarks);
  }

  /**
   * Returns a copy that shows or hides the fold and hole marks. They are small lines at the left
   * edge of page 1, 105&nbsp;mm and 210&nbsp;mm from the top (fold) and 148.5&nbsp;mm (hole, a
   * little longer), as in DIN 5008 form B: folded at the marks, the address lies in the window of a
   * C6/5 (DL) envelope. Only documents with an address window have them. Hide them for letter paper
   * that already has the marks preprinted.
   *
   * @param show whether page 1 shows the fold and hole marks
   * @return the layout
   */
  public PdfLayout withFoldMarks(boolean show) {
    return new PdfLayout(locale,
                         showSenderAddress,
                         logo,
                         showLogo,
                         logoPosition,
                         regularFont,
                         boldFont,
                         accentColor,
                         footer,
                         watermark,
                         profile,
                         show);
  }

  /**
   * Returns true if the letterhead shows a logo: one is set and shown.
   *
   * @return true if a logo is set and shown
   */
  public boolean hasVisibleLogo() {
    return showLogo && logo != null;
  }

  /**
   * Returns a copy of the logo.
   *
   * @return the PNG or JPEG image, or null
   */
  @Override
  public byte[] logo() {
    return copy(logo);
  }

  /**
   * Returns a copy of the regular font.
   *
   * @return the TrueType font, or null for IBM Plex Sans
   */
  @Override
  public byte[] regularFont() {
    return copy(regularFont);
  }

  /**
   * Returns a copy of the bold font.
   *
   * @return the TrueType font, or null for IBM Plex Sans Bold without an own regular font and the
   *     emboldened regular font with one
   */
  @Override
  public byte[] boldFont() {
    return copy(boldFont);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof PdfLayout other &&
           locale.equals(other.locale) &&
           showSenderAddress == other.showSenderAddress &&
           Arrays.equals(logo,
                         other.logo) &&
           showLogo == other.showLogo &&
           logoPosition == other.logoPosition &&
           Arrays.equals(regularFont,
                         other.regularFont) &&
           Arrays.equals(boldFont,
                         other.boldFont) &&
           accentColor == other.accentColor &&
           Objects.equals(footer,
                          other.footer) &&
           Objects.equals(watermark,
                          other.watermark) &&
           profile == other.profile;
  }

  @Override
  public int hashCode() {
    return Objects.hash(locale,
                        showSenderAddress,
                        Arrays.hashCode(logo),
                        showLogo,
                        logoPosition,
                        Arrays.hashCode(regularFont),
                        Arrays.hashCode(boldFont),
                        accentColor,
                        footer,
                        watermark,
                        profile,
                        foldMarks);
  }

  @Override
  public String toString() {
    return "PdfLayout[locale=" +
           locale +
           ", showSenderAddress=" +
           showSenderAddress +
           ", logo=" +
           (logo == null ?
            "none" :
            logo.length + " bytes") +
           ", showLogo=" +
           showLogo +
           ", logoPosition=" +
           logoPosition +
           ", fonts=" +
           (regularFont == null ?
            "default" :
            "custom") +
           ", accentColor=" +
           String.format("#%06X",
                         accentColor) +
           ", footer=" +
           footer +
           ", watermark=" +
           watermark +
           ", profile=" +
           profile +
           "]";
  }

  /**
   * Side of the letterhead the logo is shown on.
   */
  public enum LogoPosition {
    /**
     * Top left; the sender's address is shown on the right.
     */
    LEFT,
    /**
     * Top right; the sender's address is shown on the left.
     */
    RIGHT
  }
}
