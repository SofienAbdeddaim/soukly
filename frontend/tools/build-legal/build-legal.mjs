#!/usr/bin/env node
// Builds the legal pages (LEG-01): static HTML generated from frontend/legal/<lang>/<slug>.md, readable
// without JavaScript, served by Caddy before the fallback to the shell (docker/Caddyfile).
//
// Usage: node tools/build-legal/build-legal.mjs [--out dist/legal] [--contact-email privacy@localhost]
//
// Output, laid out like the URLs so that a server only appends ".html":
//   <out>/<lang>/legal/<slug>.html             one page per language and per slug
//   <out>/legal-assets/legal-<HASH>.css         design tokens, fonts and base styles of libs/ui, plus legal.css
//   <out>/legal-assets/<font>-<HASH>.woff2      the self-hosted fonts of libs/ui
// The asset names carry a content hash, so Caddy caches them for good like the Angular bundles.
//
// Without --contact-email, the pages keep the CONTACT_EMAIL_TOKEN placeholder: the entrypoint of the
// front-end image replaces it at startup (LEGAL_CONTACT_EMAIL, else privacy@<host of PUBLIC_BASE_URL>),
// so that no domain is written into the image.
import { createHash } from 'node:crypto';
import { mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { parseArgs } from 'node:util';
import MarkdownIt from 'markdown-it';

const TOOL_DIR = dirname(fileURLToPath(import.meta.url));
const WORKSPACE = join(TOOL_DIR, '..', '..');
const UI_STYLES = join(WORKSPACE, 'libs', 'ui', 'src', 'styles');
const UI_FONTS = join(WORKSPACE, 'libs', 'ui', 'src', 'assets', 'fonts');

/** Same list and order as SUPPORTED_LANGS in libs/i18n/src/lib/languages.ts. */
export const LANGS = ['fr', 'ar', 'en'];
export const RTL_LANGS = ['ar'];
/** Pages, in the order of the footer. Their addresses are declared to Meta: never rename one. */
export const SLUGS = ['terms', 'privacy', 'posting-rules', 'notice', 'data-deletion'];
/** Same names as LANG_NAMES in libs/i18n, each written in its own language. */
const LANG_NAMES = { fr: 'Français', ar: 'العربية', en: 'English' };
/** Same locales as libs/i18n, with Latin digits in every language. */
const LOCALES = { fr: 'fr-TN', ar: 'ar-TN', en: 'en-TN' };

export const CONTACT_EMAIL_TOKEN = '__LEGAL_CONTACT_EMAIL__';
/** Written in the Markdown where the contact address goes; replaced by a mailto link. */
const CONTACT_EMAIL_MARKER = '{{contactEmail}}';
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const ANCHOR = /^[a-z][a-z0-9-]*$/;

/**
 * Raw HTML is disabled: "<" in the Markdown is escaped, so the output only holds the tags that
 * markdown-it itself produces, and its link validation drops javascript:, vbscript: and file: URLs.
 * No separate HTML sanitizer is needed for that.
 */
function createMarkdown() {
  const md = new MarkdownIt({ html: false, linkify: false, typographer: false });
  md.core.ruler.push('section_anchors', (state) => {
    const { tokens } = state;
    for (let i = 0; i < tokens.length; i++) {
      if (tokens[i].type !== 'heading_open') {
        continue;
      }
      if (tokens[i].tag === 'h1') {
        throw new Error('a page has no "#" heading: its title comes from the front matter');
      }
      if (tokens[i].tag !== 'h2') {
        continue;
      }
      const inline = tokens[i + 1];
      const match = /\s*\{#([^}]*)\}\s*$/.exec(inline.content);
      if (!match || !ANCHOR.test(match[1])) {
        throw new Error(
          `the section "${inline.content}" needs a stable anchor in lower case: "## Title {#anchor}"`,
        );
      }
      inline.content = inline.content.slice(0, match.index);
      const last = inline.children.at(-1);
      last.content = last.content.replace(/\s*\{#[^}]*\}\s*$/, '');
      tokens[i].attrSet('id', match[1]);
    }
  });
  return md;
}

const markdown = createMarkdown();

/** "---\nkey: value\n---\nbody" -> { meta, body }; only the keys of a legal page, all required. */
export function parseFrontMatter(source, where = 'page') {
  const match = /^---\r?\n([\s\S]*?)\r?\n---\r?\n?/.exec(source);
  if (!match) {
    throw new Error(`${where}: missing front matter (---)`);
  }
  const meta = {};
  for (const line of match[1].split(/\r?\n/)) {
    if (line.trim() === '') {
      continue;
    }
    const pair = /^([A-Za-z]+):\s*(.*)$/.exec(line);
    if (!pair) {
      throw new Error(`${where}: malformed front matter line "${line}"`);
    }
    meta[pair[1]] = pair[2].trim();
  }
  const unknown = Object.keys(meta).filter(
    (key) => !['title', 'version', 'effectiveDate', 'draft'].includes(key),
  );
  if (unknown.length > 0) {
    throw new Error(`${where}: unknown front matter key(s) ${unknown.join(', ')}`);
  }
  if (!meta.title) {
    throw new Error(`${where}: "title" is required`);
  }
  for (const key of ['version', 'effectiveDate']) {
    if (!DATE.test(meta[key] ?? '') || Number.isNaN(Date.parse(meta[key]))) {
      throw new Error(`${where}: "${key}" must be a YYYY-MM-DD date`);
    }
  }
  if (!['true', 'false'].includes(meta.draft)) {
    throw new Error(`${where}: "draft" must be true or false`);
  }
  return {
    meta: { ...meta, draft: meta.draft === 'true' },
    body: source.slice(match[0].length),
  };
}

/** Sections of the table of contents: every "##" heading, with its anchor. */
function sectionsOf(tokens) {
  const sections = [];
  tokens.forEach((token, i) => {
    if (token.type === 'heading_open' && token.tag === 'h2') {
      sections.push({ id: token.attrGet('id'), title: tokens[i + 1].content });
    }
  });
  return sections;
}

function escapeHtml(text) {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function formatDate(date, lang) {
  return new Intl.DateTimeFormat(`${LOCALES[lang]}-u-nu-latn`, {
    dateStyle: 'long',
    timeZone: 'UTC',
  }).format(new Date(`${date}T00:00:00Z`));
}

function message(messages, key, params = {}) {
  const value = key.split('.').reduce((node, part) => node?.[part], messages);
  if (typeof value !== 'string') {
    throw new Error(`missing message "${key}"`);
  }
  return value.replace(/\{(\w+)\}/g, (_, name) => params[name]);
}

/**
 * Renders one page. `assets` holds the published paths of the stylesheet; `contactEmail` replaces
 * the {{contactEmail}} marker (the placeholder token by default).
 */
export function renderPage({ lang, slug, source, messages, assets, contactEmail }) {
  const where = `${lang}/${slug}.md`;
  const { meta, body } = parseFrontMatter(source, where);
  let tokens;
  try {
    tokens = markdown.parse(body, {});
  } catch (error) {
    throw new Error(`${where}: ${error.message}`);
  }
  const sections = sectionsOf(tokens);
  if (sections.length === 0) {
    throw new Error(`${where}: a page needs at least one "##" section`);
  }
  const anchors = new Set(sections.map((section) => section.id));
  if (anchors.size !== sections.length) {
    throw new Error(`${where}: two sections share the same anchor`);
  }

  const email = escapeHtml(contactEmail ?? CONTACT_EMAIL_TOKEN);
  const content = markdown.renderer
    .render(tokens, markdown.options, {})
    .replaceAll(CONTACT_EMAIL_MARKER, `<a href="mailto:${email}" dir="ltr">${email}</a>`);
  const t = (key, params) => escapeHtml(message(messages, key, params));
  const dir = RTL_LANGS.includes(lang) ? 'rtl' : 'ltr';
  const title = escapeHtml(meta.title);
  const version = t('version', { version: formatDate(meta.version, lang) });
  const effective = t('effective', { date: formatDate(meta.effectiveDate, lang) });

  const languageLinks = LANGS.filter((other) => other !== lang)
    .map(
      (other) =>
        `<li><a class="lang-link" href="/${other}/legal/${slug}" lang="${other}" hreflang="${other}">${LANG_NAMES[other]}</a></li>`,
    )
    .join('');
  const tocLinks = sections
    .map((section) => `<li><a href="#${section.id}">${escapeHtml(section.title)}</a></li>`)
    .join('');
  const footerLinks = SLUGS.map((other) => {
    const current = other === slug ? ' aria-current="page"' : '';
    return `<li><a href="/${lang}/legal/${other}"${current}>${t(`footer.${other}`)}</a></li>`;
  }).join('');

  return `<!doctype html>
<html lang="${lang}" dir="${dir}">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>${title} · ${t('brand')}</title>
    <link rel="icon" href="data:," />
    <link rel="stylesheet" href="${assets.stylesheet}" />
  </head>
  <body>
    <a class="skip-link" href="#content">${t('skip-link')}</a>
    <header class="site-header">
      <div class="container site-header__inner">
        <a class="brand" href="/${lang}">${t('brand')}</a>
        <nav aria-label="${t('languages')}">
          <ul class="lang-list">${languageLinks}</ul>
        </nav>
      </div>
    </header>
    <main id="content" tabindex="-1">
      <div class="container legal">
        <nav class="breadcrumb" aria-label="${t('breadcrumb.label')}">
          <ol>
            <li><a href="/${lang}">${t('breadcrumb.home')}</a></li>
            <li>${t('breadcrumb.legal')}</li>
          </ol>
        </nav>
        <h1>${title}</h1>
        <p class="legal__version">${version} · ${effective}</p>
        <details class="toc" open>
          <summary>${t('toc')}</summary>
          <ol>${tocLinks}</ol>
        </details>
${meta.draft ? `        <p class="draft" role="note" data-testid="legal-draft">${t('draft')}</p>\n` : ''}        <div class="prose">
${content}        </div>
      </div>
    </main>
    <footer class="site-footer">
      <div class="container site-footer__inner">
        <span class="brand">${t('brand')}</span>
        <nav aria-label="${t('footer.label')}">
          <ul class="footer-list">${footerLinks}</ul>
        </nav>
      </div>
    </footer>
  </body>
</html>
`;
}

/** First 8 hex digits of the SHA-256, in upper case: the hash format the Caddyfile caches for good. */
function contentHash(content) {
  return createHash('sha256').update(content).digest('hex').slice(0, 8).toUpperCase();
}

/** Writes the fonts and the stylesheet; returns the published path of the stylesheet. */
function buildAssets(outDir) {
  const assetsDir = join(outDir, 'legal-assets');
  mkdirSync(assetsDir, { recursive: true });
  let fonts = readFileSync(join(UI_STYLES, 'fonts.css'), 'utf8');
  for (const font of readdirSync(UI_FONTS).filter((file) => file.endsWith('.woff2'))) {
    const content = readFileSync(join(UI_FONTS, font));
    const name = font.replace(/\.woff2$/, `-${contentHash(content)}.woff2`);
    writeFileSync(join(assetsDir, name), content);
    fonts = fonts.replaceAll(`../assets/fonts/${font}`, name);
  }
  const css = [
    fonts,
    readFileSync(join(UI_STYLES, 'tokens.css'), 'utf8'),
    readFileSync(join(UI_STYLES, 'base.css'), 'utf8'),
    readFileSync(join(TOOL_DIR, 'legal.css'), 'utf8'),
  ].join('\n');
  const name = `legal-${contentHash(css)}.css`;
  writeFileSync(join(assetsDir, name), css);
  return { stylesheet: `/legal-assets/${name}` };
}

/** Builds every page of `srcDir` (one folder per language) into `outDir`, emptied first. */
export function buildLegal({ srcDir, outDir, contactEmail }) {
  rmSync(outDir, { recursive: true, force: true });
  const assets = buildAssets(outDir);
  const pages = [];
  for (const lang of LANGS) {
    const messages = JSON.parse(readFileSync(join(TOOL_DIR, 'i18n', `${lang}.json`), 'utf8'));
    const files = readdirSync(join(srcDir, lang)).filter((file) => file.endsWith('.md'));
    const extra = files.filter((file) => !SLUGS.includes(file.slice(0, -'.md'.length)));
    if (extra.length > 0) {
      throw new Error(
        `${lang}: unexpected page(s) ${extra.join(', ')} (known: ${SLUGS.join(', ')})`,
      );
    }
    mkdirSync(join(outDir, lang, 'legal'), { recursive: true });
    for (const slug of SLUGS) {
      if (!files.includes(`${slug}.md`)) {
        throw new Error(`${lang}/${slug}.md is missing`);
      }
      const source = readFileSync(join(srcDir, lang, `${slug}.md`), 'utf8');
      const html = renderPage({ lang, slug, source, messages, assets, contactEmail });
      const target = join(outDir, lang, 'legal', `${slug}.html`);
      writeFileSync(target, html);
      pages.push(target);
    }
  }
  return pages;
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const { values } = parseArgs({
    options: {
      src: { type: 'string', default: join(WORKSPACE, 'legal') },
      out: { type: 'string', default: join(WORKSPACE, 'dist', 'legal') },
      'contact-email': { type: 'string' },
    },
  });
  try {
    const pages = buildLegal({
      srcDir: values.src,
      outDir: values.out,
      contactEmail: values['contact-email'],
    });
    console.log(
      `build-legal: ${pages.length} pages written to ${relative(process.cwd(), values.out) || '.'}`,
    );
  } catch (error) {
    console.error(`build-legal: ${error.message}`);
    process.exit(1);
  }
}
