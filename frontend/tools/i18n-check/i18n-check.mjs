#!/usr/bin/env node
// Checks that every translation scope has the same keys in every language (SETUP-07).
//
// Usage: node tools/i18n-check/i18n-check.mjs <i18n folder>...
//
// An i18n folder holds the root scope (<lang>.json) and/or one folder per scope
// (<scope>/<lang>.json), for example apps/shell/public/i18n or apps/mfe-publish/public/i18n.
// For each scope, every language of LANGS must have a file, every file must be valid JSON whose
// leaves are non-empty strings, and a key present in one language must be present in all of them.
// Exits with code 1 and lists every problem otherwise.
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { pathToFileURL } from 'node:url';

/** Same list as SUPPORTED_LANGS in libs/i18n/src/lib/languages.ts. */
export const LANGS = ['fr', 'ar', 'en'];

/** { "a": { "b": "x" } } -> Map { "a.b" => "x" } */
export function flatten(value, prefix = '', keys = new Map()) {
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    for (const [key, child] of Object.entries(value)) {
      flatten(child, prefix ? `${prefix}.${key}` : key, keys);
    }
  } else {
    keys.set(prefix, value);
  }
  return keys;
}

/** Scopes of an i18n folder: [{ name, dir }], the root scope first when it has files. */
function scopesOf(folder) {
  const entries = readdirSync(folder);
  const scopes = [];
  if (entries.some((entry) => entry.endsWith('.json'))) {
    scopes.push({ name: '(root)', dir: folder });
  }
  for (const entry of entries.sort()) {
    if (statSync(join(folder, entry)).isDirectory()) {
      scopes.push({ name: entry, dir: join(folder, entry) });
    }
  }
  return scopes;
}

/** Problems of one scope, as readable lines. */
export function checkScope(scopeName, dir, cwd = process.cwd()) {
  const problems = [];
  const keysByLang = new Map();
  const where = (lang) => relative(cwd, join(dir, `${lang}.json`));

  for (const file of readdirSync(dir).filter((entry) => entry.endsWith('.json'))) {
    const lang = file.slice(0, -'.json'.length);
    if (!LANGS.includes(lang)) {
      problems.push(
        `scope "${scopeName}": ${where(lang)} is not a supported language (${LANGS.join(', ')})`,
      );
    }
  }

  for (const lang of LANGS) {
    const path = join(dir, `${lang}.json`);
    if (!existsSync(path)) {
      problems.push(`scope "${scopeName}": ${where(lang)} is missing`);
      continue;
    }
    let content;
    try {
      content = JSON.parse(readFileSync(path, 'utf8'));
    } catch (error) {
      problems.push(`scope "${scopeName}": ${where(lang)} is not valid JSON (${error.message})`);
      continue;
    }
    const keys = flatten(content);
    for (const [key, value] of keys) {
      if (typeof value !== 'string' || value.trim() === '') {
        problems.push(
          `scope "${scopeName}": key "${key}" is empty or not a text in ${where(lang)}`,
        );
      }
    }
    keysByLang.set(lang, keys);
  }

  const allKeys = new Set([...keysByLang.values()].flatMap((keys) => [...keys.keys()]));
  for (const key of [...allKeys].sort()) {
    const missingIn = [...keysByLang.keys()].filter((lang) => !keysByLang.get(lang).has(key));
    const presentIn = [...keysByLang.keys()].filter((lang) => keysByLang.get(lang).has(key));
    for (const lang of missingIn) {
      problems.push(
        `scope "${scopeName}": key "${key}" is missing in ${lang} (${where(lang)}), present in ${presentIn.join(', ')}`,
      );
    }
  }
  return problems;
}

/** Problems of all the given i18n folders. */
export function checkFolders(folders, cwd = process.cwd()) {
  const problems = [];
  for (const folder of folders) {
    if (!existsSync(folder) || !statSync(folder).isDirectory()) {
      problems.push(`${folder} is not a folder`);
      continue;
    }
    const scopes = scopesOf(folder);
    if (scopes.length === 0) {
      problems.push(`${folder} has no translation file`);
    }
    for (const { name, dir } of scopes) {
      problems.push(...checkScope(name, dir, cwd));
    }
  }
  return problems;
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const folders = process.argv.slice(2);
  if (folders.length === 0) {
    console.error('Usage: node tools/i18n-check/i18n-check.mjs <i18n folder>...');
    process.exit(2);
  }
  const problems = checkFolders(folders);
  if (problems.length > 0) {
    console.error(`i18n-check: ${problems.length} problem(s)`);
    for (const problem of problems) {
      console.error(`  - ${problem}`);
    }
    process.exit(1);
  }
  console.log(`i18n-check: ${folders.join(', ')}: every key exists in ${LANGS.join(', ')}`);
}
