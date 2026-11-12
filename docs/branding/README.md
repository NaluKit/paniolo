# Paniolo branding

Each variant exists as SVG (vector, preferred) and PNG (raster, e.g. for the GitHub social preview,
which needs PNG or JPG).

| File                               | Use                                                |
|------------------------------------|----------------------------------------------------|
| `paniolo-banner.svg` / `.png`      | Horizontal logo for light backgrounds, e.g. README |
| `paniolo-banner-dark.svg` / `.png` | Horizontal logo for dark backgrounds               |
| `paniolo-logo.svg` / `.png`        | Stacked logo (figure above wordmark), light        |
| `paniolo-logo-dark.svg` / `.png`   | Stacked logo, dark                                 |
| `paniolo-icon.svg` / `.png`        | Figure only, square, e.g. avatar                   |
| `paniolo-icon-dark.svg` / `.png`   | Figure only, dark                                  |
| `paniolo-mark.svg`                 | Small mark: "p" of the wordmark with the teal dot  |
| `paniolo-mark-dark.svg`            | Small mark, dark                                   |
| `paniolo-favicon.svg`              | Small mark on a navy tile, for any background      |
| `paniolo-favicon-32.png`           | Favicon PNG                                        |
| `paniolo-favicon-180.png`          | Apple touch icon                                   |
| `paniolo-favicon-512.png`          | Web app manifest, avatar                           |
| `favicon.ico`                      | Favicon with 16, 32 and 48 px                      |

At 16–32 px rider and lasso are no longer recognizable; use the mark or the favicon there. The
favicon tile keeps its contrast on light and dark browser tabs:

```html
<link rel="icon" href="paniolo-favicon.svg" type="image/svg+xml">
<link rel="icon" href="favicon.ico" sizes="16x16 32x32 48x48">
<link rel="apple-touch-icon" href="paniolo-favicon-180.png">
```

Colors: navy `#01244F`, teal `#019ABC`; on dark backgrounds the navy parts are `#E2ECF5`. In the
SVGs each color is one `<g fill="...">`, so a variant in other colors only needs a different fill.

The variants are derived from the concept: stray semi-transparent pixels removed, split into figure
and wordmark, and each color traced to paths with VTracer. The PNGs are cropped, scaled and
reduced to 64 colors. Use the GitHub `<picture>` element to switch by color scheme:

```html
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/branding/paniolo-banner-dark.svg">
  <img src="docs/branding/paniolo-banner.svg" alt="Paniolo" width="480">
</picture>
```

## Origin and rights

The original concept (not part of this repository) was created for the Paniolo
project, on behalf of Frank Hossfeld. All other files in this folder and the logo files in
`src/content/paniolo/assets/` of the website repository
[NaluKit/nalukit-web-site](https://github.com/NaluKit/nalukit-web-site) are derived from it as
described above (cleaned up, split, traced with VTracer, cropped, scaled). The wordmark is part of the concept and was traced from it;
it is not set in a font.

The logo is not offered under the Apache License 2.0 of the code; it identifies the
Paniolo project. Use it unchanged to refer to Paniolo; do not use it for other products or in a
way that suggests endorsement by the project or Frank Hossfeld.

Open:

- The traced wordmark has slight unevenness from the concept; setting it in the matching font, or
  redrawing it by hand, would give exact outlines; the mark takes its "p" from the traced wordmark.
