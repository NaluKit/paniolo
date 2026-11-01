# Paniolo website

A standalone English static website. No JavaScript, package installation, build step,
backend, external fonts, or CDN is required to serve it.

## Preview

From the repository root:

```sh
python3 -m http.server 8080 --directory docs/site
```

Open http://localhost:8080. You can also open `index.html` directly.

## Deploy

Upload **the contents of this directory**, keeping `assets/` beside the HTML pages,
to any static web server. Configure `index.html` as the default document.
All local links are relative, so deployment at a domain root or a subdirectory works.
No SPA rewrites are needed. Serve `.html` as `text/html`, `.css` as `text/css`,
and `.svg` as `image/svg+xml`. No application runtime is required.

For GitHub Pages, upload `docs/site` as the Pages artifact in your deployment workflow.
For nginx, Apache, Netlify, or Cloudflare Pages, use this directory as the static
publish directory and leave the build command empty.

## Pages

- `index.html`: overview, introduction, and main features
- `quickstart.html`: requirements, installation, and quickstart entry point
- `formats.html`: capability matrix, module selection, formats, and limitations
- `api.html`: complete rendered versioned reference and reproducible quickstart
- `assets/`: local styles and existing Paniolo branding
- `LICENSE.txt`: Apache 2.0 license

The API page is a rendered copy of `../api/HEAD-SNAPSHOT-6c2d2dc169af.md`,
with heading anchors and source links pinned to that reference's commit.
When updating the documented source baseline, update the HTML reference and its
version labels (the source baseline in the sidebars and the note on the quickstart
page) together. HEAD-SNAPSHOT and local quickstart build coordinates
are not presented as published releases. The API page includes the command to
generate the complete JavaDoc; generated JavaDoc is not bundled here.

The website uses the branding in `../branding`. It processes no invoices and
collects no visitor data. GitHub links open the public project repository.
