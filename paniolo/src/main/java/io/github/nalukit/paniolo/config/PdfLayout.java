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
 * Settings of the visual PDF/A document that {@code paniolo-facturx} renders from an invoice,
 * order, or despatch advice, e.g. for {@code Paniolo#toPdf(Invoice, PdfLayout)} and
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
 * <p>For letter paper with a preprinted letterhead, hide the sender's address with
 * {@code withShowSenderAddress(false)}; the sender line above the address window and the footer
 * remain. Combined with {@code withShowLogo(true)}, the letterhead shows only the logo, on the side
 * chosen with {@code withLogoPosition}.
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
 * @param regularFont       TrueType font for regular text, or null for Liberation Sans (packaged
 *                          with PDFBox)
 * @param boldFont          TrueType font for bold text, or null to embolden the regular font
 * @param accentColor       color of rules and headings as RGB, e.g. {@code 0x019ABC}
 * @param footer            additional footer text on every page, e.g. management and register
 *                          court, or null
 * @param watermark         text printed diagonally across every page, e.g. {@code PREVIEW}, or
 *                          null; see {@link #withWatermark(String)}
 */
public record PdfLayout(Locale locale, boolean showSenderAddress, byte[] logo, boolean showLogo, LogoPosition logoPosition, byte[] regularFont, byte[] boldFont, int accentColor, String footer, String watermark) {
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
    if (watermark != null) {
      watermark = watermark.isBlank() ?
                  null :
                  watermark.strip();
    }
  }

  /**
   * The default layout: German labels, the sender's address in the letterhead, no logo,
   * Liberation Sans, the Paniolo accent color.
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
                         null);
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
                         watermark);
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
                         watermark);
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
                         watermark);
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
                         watermark);
  }

  /**
   * Returns a copy with own TrueType fonts, e.g. the corporate font. The fonts are embedded as
   * subsets, as PDF/A requires; their license must allow embedding.
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
                         watermark);
  }

  /**
   * Returns a copy with another accent color.
   *
   * @param rgb color as RGB, e.g. {@code 0x01244F}
   * @return the layout
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
                         watermark);
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
                         watermark);
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
                         watermark);
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
   * @return the TrueType font, or null for Liberation Sans
   */
  @Override
  public byte[] regularFont() {
    return copy(regularFont);
  }

  /**
   * Returns a copy of the bold font.
   *
   * @return the TrueType font, or null to embolden the regular font
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
                          other.watermark);
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
                        watermark);
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
