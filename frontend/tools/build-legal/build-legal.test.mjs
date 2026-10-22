import assert from 'node:assert/strict';
import { mkdtempSync, readdirSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { after, describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import {
  buildLegal,
  CONTACT_EMAIL_TOKEN,
  LANGS,
  parseFrontMatter,
  renderPage,
  SLUGS,
} from './build-legal.mjs';

const TOOL_DIR = dirname(fileURLToPath(import.meta.url));
const WORKSPACE = join(TOOL_DIR, '..', '..');
const messages = (lang) => JSON.parse(readFileSync(join(TOOL_DIR, 'i18n', `${lang}.json`), 'utf8'));
const assets = { stylesheet: '/legal-assets/legal-0123ABCD.css' };

function page(body, { draft = true, lang = 'fr', slug = 'privacy', contactEmail } = {}) {
  const source = `---
title: Politique de confidentialité
version: 2026-10-22
effectiveDate: 2026-11-01
draft: ${draft}
---

${body}`;
  return renderPage({ lang, slug, source, messages: messages(lang), assets, contactEmail });
}

const SECTIONS = `## Données collectées {#data-collected}

Votre email.

## Vos droits {#rights}

Écrivez à {{contactEmail}}.
`;

describe('parseFrontMatter', () => {
  it('reads the four keys and turns draft into a boolean', () => {
    const { meta, body } = parseFrontMatter(
      '---\ntitle: Conditions\nversion: 2026-10-22\neffectiveDate: 2026-10-23\ndraft: false\n---\n\nText',
    );
    assert.deepEqual(meta, {
      title: 'Conditions',
      version: '2026-10-22',
      effectiveDate: '2026-10-23',
      draft: false,
    });
    assert.equal(body, '\nText');
  });

  it('rejects a missing front matter, an unknown key, a bad date or a bad draft flag', () => {
    assert.throws(() => parseFrontMatter('# Title'), /missing front matter/);
    const base = 'title: T\nversion: 2026-10-22\neffectiveDate: 2026-10-22\ndraft: true';
    assert.throws(() => parseFrontMatter(`---\n${base}\nauthor: x\n---\n`), /unknown/);
    assert.throws(
      () => parseFrontMatter(`---\n${base.replace('2026-10-22', '22/10/2026')}\n---\n`),
      /"version" must be a YYYY-MM-DD date/,
    );
    assert.throws(
      () => parseFrontMatter(`---\n${base.replace('draft: true', 'draft: yes')}\n---\n`),
      /"draft" must be true or false/,
    );
    assert.throws(() => parseFrontMatter(`---\n${base.replace('title: T', '')}\n---\n`), /title/);
  });
});

describe('renderPage', () => {
  it('renders a complete page with the title, the version and every section', () => {
    const html = page(SECTIONS);
    assert.match(html, /^<!doctype html>\n<html lang="fr" dir="ltr">/);
    assert.match(html, /<title>Politique de confidentialité · Soukly<\/title>/);
    assert.match(html, /<h1>Politique de confidentialité<\/h1>/);
    assert.match(html, /Version du 22 octobre 2026 · en vigueur depuis le 1 novembre 2026/);
    assert.match(html, /<h2 id="data-collected">Données collectées<\/h2>/);
    assert.match(html, /<h2 id="rights">Vos droits<\/h2>/);
    assert.match(html, /<link rel="stylesheet" href="\/legal-assets\/legal-0123ABCD.css" \/>/);
    assert.doesNotMatch(html, /<script/);
  });

  it('shows the draft banner only for a draft page', () => {
    assert.match(
      page(SECTIONS),
      /<p class="draft" role="note" data-testid="legal-draft">Texte provisoire, en cours de relecture juridique.<\/p>/,
    );
    assert.doesNotMatch(page(SECTIONS, { draft: false }), /legal-draft/);
  });

  it('lists every "##" section in the table of contents, linked to its anchor', () => {
    const html = page(`${SECTIONS}\n### Sub-section\n\nText.\n`);
    assert.match(
      html,
      /<details class="toc" open>\s*<summary>Sommaire<\/summary>\s*<ol><li><a href="#data-collected">Données collectées<\/a><\/li><li><a href="#rights">Vos droits<\/a><\/li><\/ol>/,
    );
    assert.doesNotMatch(html, /href="#sub-section"/);
  });

  it('requires a stable anchor on every section, unique in the page, and no "#" heading', () => {
    assert.throws(() => page('## Données collectées\n\nText.'), /needs a stable anchor/);
    assert.throws(() => page('## Données {#Données}\n\nText.'), /needs a stable anchor/);
    assert.throws(() => page('## A {#same}\n\n## B {#same}\n'), /share the same anchor/);
    assert.throws(() => page('# Title\n\n## A {#a}\n'), /no "#" heading/);
    assert.throws(() => page('Only text.'), /at least one "##" section/);
  });

  it('renders Arabic right to left, with links to the same page in the two other languages', () => {
    const html = page(SECTIONS, { lang: 'ar', slug: 'terms' });
    assert.match(html, /<html lang="ar" dir="rtl">/);
    assert.match(html, /نسخة 22 أكتوبر 2026/);
    assert.match(
      html,
      /<a class="lang-link" href="\/fr\/legal\/terms" lang="fr" hreflang="fr">Français<\/a>/,
    );
    assert.match(
      html,
      /<a class="lang-link" href="\/en\/legal\/terms" lang="en" hreflang="en">English<\/a>/,
    );
    assert.doesNotMatch(html, /href="\/ar\/legal\/terms" lang="ar"/);
    assert.match(html, /<span class="brand">سوقلي<\/span>/);
  });

  it('links the footer to the five pages in the current language, the current one marked', () => {
    const html = page(SECTIONS, { lang: 'en', slug: 'notice' });
    const links = [
      ...html.matchAll(/<li><a href="(\/en\/legal\/[a-z-]+)"( aria-current="page")?>/g),
    ];
    assert.deepEqual(
      links.map((link) => link[1]),
      SLUGS.map((slug) => `/en/legal/${slug}`),
    );
    assert.deepEqual(
      links.filter((link) => link[2]).map((link) => link[1]),
      ['/en/legal/notice'],
    );
  });

  it('escapes raw HTML and drops script links: only markdown-it tags reach the page', () => {
    const html = page(
      `## A {#a}\n\n<script>alert(1)</script> <img src=x onerror=alert(1)>\n\n[x](javascript:alert(1)) [y](/fr)\n`,
    );
    assert.doesNotMatch(html, /<script>|<img/);
    assert.match(html, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/);
    assert.doesNotMatch(html, /href="javascript:/);
    assert.match(html, /<a href="\/fr">y<\/a>/);
  });

  it('writes the contact address as a mailto link, the startup placeholder by default', () => {
    assert.match(
      page(SECTIONS),
      new RegExp(`<a href="mailto:${CONTACT_EMAIL_TOKEN}" dir="ltr">${CONTACT_EMAIL_TOKEN}</a>`),
    );
    assert.match(
      page(SECTIONS, { contactEmail: 'privacy@localhost' }),
      /<a href="mailto:privacy@localhost" dir="ltr">privacy@localhost<\/a>/,
    );
  });
});

describe('the pages of frontend/legal', () => {
  const out = mkdtempSync(join(tmpdir(), 'soukly-legal-'));
  after(() => rmSync(out, { recursive: true, force: true }));
  const pages = buildLegal({ srcDir: join(WORKSPACE, 'legal'), outDir: out });

  it('builds the 5 pages in the 3 languages, with hashed assets', () => {
    assert.equal(pages.length, 15);
    for (const lang of LANGS) {
      assert.deepEqual(
        readdirSync(join(out, lang, 'legal')).sort(),
        SLUGS.map((slug) => `${slug}.html`).sort(),
      );
    }
    const assetFiles = readdirSync(join(out, 'legal-assets'));
    assert.ok(assetFiles.some((file) => /^legal-[0-9A-F]{8}\.css$/.test(file)));
    assert.equal(assetFiles.filter((file) => /-[0-9A-F]{8}\.woff2$/.test(file)).length, 4);
    const css = readFileSync(
      join(
        out,
        'legal-assets',
        assetFiles.find((file) => file.endsWith('.css')),
      ),
      'utf8',
    );
    assert.doesNotMatch(css, /\.\.\/assets\/fonts/);
  });

  it('gives every page the same sections (same anchors) in the 3 languages', () => {
    for (const slug of SLUGS) {
      const anchors = LANGS.map((lang) =>
        [
          ...readFileSync(join(out, lang, 'legal', `${slug}.html`), 'utf8').matchAll(
            /<h2 id="([a-z0-9-]+)">/g,
          ),
        ].map((match) => match[1]),
      );
      assert.deepEqual(anchors[1], anchors[0], `${slug}: ar differs from fr`);
      assert.deepEqual(anchors[2], anchors[0], `${slug}: en differs from fr`);
    }
  });

  it('keeps the version of the terms aligned with LEGAL_TERMS_VERSION of user-service', () => {
    const versions = LANGS.map(
      (lang) =>
        parseFrontMatter(readFileSync(join(WORKSPACE, 'legal', lang, 'terms.md'), 'utf8')).meta
          .version,
    );
    assert.deepEqual(
      new Set(versions).size,
      1,
      'the terms have the same version in every language',
    );
    const config = readFileSync(
      join(
        WORKSPACE,
        '..',
        'backend',
        'user-service',
        'src',
        'main',
        'resources',
        'application.yml',
      ),
      'utf8',
    );
    assert.match(config, new RegExp(`terms-version: \\$\\{LEGAL_TERMS_VERSION:${versions[0]}\\}`));
  });

  it('gives the data deletion page the account deletion steps and the contact address (Meta)', () => {
    const html = readFileSync(join(out, 'fr', 'legal', 'data-deletion.html'), 'utf8');
    assert.match(html, /href="\/fr\/account\/profile"/);
    assert.match(html, /Supprimer mon compte/);
    assert.match(html, new RegExp(`mailto:${CONTACT_EMAIL_TOKEN}`));
  });
});
