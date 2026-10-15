import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import { checkFolders, flatten } from './i18n-check.mjs';

const script = fileURLToPath(new URL('./i18n-check.mjs', import.meta.url));

function write(dir, lang, content) {
  mkdirSync(dir, { recursive: true });
  writeFileSync(
    join(dir, `${lang}.json`),
    typeof content === 'string' ? content : JSON.stringify(content),
  );
}

describe('i18n-check', () => {
  let root;

  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), 'i18n-check-'));
  });

  afterEach(() => rmSync(root, { recursive: true, force: true }));

  function complete(dir) {
    write(dir, 'fr', { 'coming-soon': { title: 'Bientôt', text: 'Texte' } });
    write(dir, 'ar', { 'coming-soon': { title: 'قريبًا', text: 'نص' } });
    write(dir, 'en', { 'coming-soon': { title: 'Soon', text: 'Text' } });
  }

  it('flattens nested keys', () => {
    assert.deepEqual(
      [...flatten({ a: { b: 'x', c: { d: 'y' } }, e: 'z' }).keys()],
      ['a.b', 'a.c.d', 'e'],
    );
  });

  it('accepts a root scope and scope folders with the same keys in every language', () => {
    complete(root);
    complete(join(root, 'catalog'));

    assert.deepEqual(checkFolders([root], root), []);
  });

  it('names the key, the scope and the language when a key is missing', () => {
    complete(join(root, 'catalog'));
    write(join(root, 'catalog'), 'ar', { 'coming-soon': { title: 'قريبًا' } });

    assert.deepEqual(checkFolders([root], root), [
      'scope "catalog": key "coming-soon.text" is missing in ar (catalog/ar.json), present in fr, en',
    ]);
  });

  it('reports a missing file, invalid JSON, an empty text and an unsupported language', () => {
    write(root, 'fr', { title: 'Titre' });
    write(root, 'ar', '{ "title": ');
    write(root, 'de', { title: 'Titel' });
    write(join(root, 'publish'), 'fr', { title: '' });
    write(join(root, 'publish'), 'ar', { title: 'عنوان' });
    write(join(root, 'publish'), 'en', { title: 'Title' });

    const problems = checkFolders([root], root);

    assert.ok(
      problems.includes('scope "(root)": de.json is not a supported language (fr, ar, en)'),
    );
    assert.ok(problems.some((p) => p.startsWith('scope "(root)": ar.json is not valid JSON')));
    assert.ok(problems.includes('scope "(root)": en.json is missing'));
    assert.ok(
      problems.includes('scope "publish": key "title" is empty or not a text in publish/fr.json'),
    );
  });

  it('exits with code 1 and lists the problems, 0 when everything is complete', () => {
    complete(join(root, 'catalog'));
    assert.match(execFileSync('node', [script, root], { encoding: 'utf8' }), /every key exists/);

    write(join(root, 'catalog'), 'ar', { 'coming-soon': { title: 'قريبًا' } });
    assert.throws(
      () => execFileSync('node', [script, root], { encoding: 'utf8', stdio: 'pipe' }),
      (error) => error.status === 1 && /key "coming-soon.text" is missing in ar/.test(error.stderr),
    );
  });
});
