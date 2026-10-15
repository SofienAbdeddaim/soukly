// RTL guard: Soukly is rendered left-to-right (fr, en) and right-to-left (ar), so styles use
// logical properties only. Imported by the ESLint config of libs/ui and of every app that
// renders its components; see "Règles RTL" in libs/ui/README.md.
import css from '@eslint/css';

// Physical Tailwind utility (without its value) -> logical equivalent.
const TAILWIND_REPLACEMENTS = {
  ml: 'ms',
  mr: 'me',
  pl: 'ps',
  pr: 'pe',
  left: 'start',
  right: 'end',
  'scroll-ml': 'scroll-ms',
  'scroll-mr': 'scroll-me',
  'scroll-pl': 'scroll-ps',
  'scroll-pr': 'scroll-pe',
  'border-l': 'border-s',
  'border-r': 'border-e',
  'rounded-l': 'rounded-s',
  'rounded-r': 'rounded-e',
  'rounded-tl': 'rounded-ss',
  'rounded-tr': 'rounded-se',
  'rounded-bl': 'rounded-es',
  'rounded-br': 'rounded-ee',
  'text-left': 'text-start',
  'text-right': 'text-end',
  'float-left': 'float-start',
  'float-right': 'float-end',
  'clear-left': 'clear-start',
  'clear-right': 'clear-end',
};

// Utilities that need a value (ml-4, left-1/2, pr-[3px]) and those whose value is optional.
const WITH_VALUE = [
  'ml',
  'mr',
  'pl',
  'pr',
  'left',
  'right',
  'scroll-ml',
  'scroll-mr',
  'scroll-pl',
  'scroll-pr',
];
const OPTIONAL_VALUE = [
  'border-l',
  'border-r',
  'rounded-l',
  'rounded-r',
  'rounded-tl',
  'rounded-tr',
  'rounded-bl',
  'rounded-br',
];
const EXACT = ['text-left', 'text-right', 'float-left', 'float-right', 'clear-left', 'clear-right'];

const VALUE = String.raw`(?:\d+(?:\.\d+)?|\d+\/\d+|px|full|auto|xs|sm|md|lg|xl|\dxl|none|\[[^\]\s]+\]|\([^)\s]+\))`;
const alternatives = (names) => names.join('|');
// Optional variants (md:, rtl:, hover:) and important marker; a leading "-" for negative values.
const CLASS_PATTERN = new RegExp(
  String.raw`(?<=^|[\s'"\`])((?:[\w-]+:)*!?)(-?)(` +
    `(?:${alternatives(WITH_VALUE)})-${VALUE}` +
    `|(?:${alternatives(OPTIONAL_VALUE)})(?:-${VALUE}|-[a-z]+(?:-\\d+)?)?` +
    `|(?:${alternatives(EXACT)})` +
    String.raw`)(?=$|[\s'"\`!])`,
  'g',
);

/** Logical equivalent of a physical class, keeping variants and value: md:-ml-4 -> md:-ms-4. */
function logicalEquivalent(variants, negative, utility) {
  const name = Object.keys(TAILWIND_REPLACEMENTS)
    .filter((key) => utility === key || utility.startsWith(`${key}-`))
    .sort((a, b) => b.length - a.length)[0];
  return `${variants}${negative}${TAILWIND_REPLACEMENTS[name]}${utility.slice(name.length)}`;
}

/** Reports every physical class found in `text`, which starts at `offset` in the file. */
function reportPhysicalClasses(context, text, offset) {
  if (typeof text !== 'string' || text.length === 0) {
    return;
  }
  for (const match of text.matchAll(CLASS_PATTERN)) {
    const [found, variants, negative, utility] = match;
    const start = offset + match.index;
    context.report({
      loc: {
        start: context.sourceCode.getLocFromIndex(start),
        end: context.sourceCode.getLocFromIndex(start + found.length),
      },
      messageId: 'physicalClass',
      data: { found, logical: logicalEquivalent(variants, negative, utility) },
    });
  }
}

const noPhysicalTailwindClasses = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Forbid physical Tailwind classes (ml-, pr-, left-...): use logical ones.',
    },
    schema: [],
    messages: {
      physicalClass:
        'Physical Tailwind class "{{found}}" breaks RTL layouts: use the logical class "{{logical}}".',
    },
  },
  create(context) {
    const isTemplate = context.filename.endsWith('.html');
    if (isTemplate) {
      // Angular template AST (@angular-eslint/template-parser).
      return {
        TextAttribute(node) {
          if (node.name === 'class') {
            reportPhysicalClasses(context, node.value, node.valueSpan?.start.offset ?? 0);
          }
        },
        BoundAttribute(node) {
          // [class.ml-4]="cond": the class is the binding name.
          if (
            node.keySpan &&
            /^class\./.test(
              context.sourceCode.text.slice(node.keySpan.start.offset, node.keySpan.end.offset),
            )
          ) {
            reportPhysicalClasses(context, node.name, node.keySpan.start.offset + 'class.'.length);
          }
        },
        LiteralPrimitive(node) {
          // [class]="'ml-4'", [ngClass]="cond ? 'pl-2' : ''"
          if (typeof node.value === 'string' && node.sourceSpan) {
            const source = context.sourceCode.text;
            const start = source.indexOf(node.value, node.sourceSpan.start);
            reportPhysicalClasses(context, node.value, start < 0 ? node.sourceSpan.start : start);
          }
        },
      };
    }
    // TypeScript: host classes and class strings built in code.
    return {
      Literal(node) {
        if (typeof node.value === 'string') {
          reportPhysicalClasses(context, node.value, node.range[0] + 1);
        }
      },
      TemplateElement(node) {
        reportPhysicalClasses(context, node.value.cooked, node.range[0] + 1);
      },
    };
  },
};

export const rtlPlugin = {
  rules: { 'no-physical-tailwind-classes': noPhysicalTailwindClasses },
};

export default [
  {
    files: ['**/*.css'],
    ignores: ['**/styles/tailwind.css'], // Tailwind-only at-rules (@theme), no layout property
    language: 'css/css',
    plugins: { css },
    rules: {
      // Sizes and viewport units do not depend on the writing direction: only the
      // left/right properties and values are forbidden.
      'css/prefer-logical-properties': [
        'error',
        {
          allowProperties: [
            'width',
            'height',
            'min-width',
            'min-height',
            'max-width',
            'max-height',
          ],
          allowUnits: ['vh', 'vw', 'dvh', 'dvw', 'svh', 'svw', 'lvh', 'lvw', 'cqh', 'cqw'],
        },
      ],
    },
  },
  {
    files: ['**/*.html', '**/*.ts'],
    plugins: { 'soukly-rtl': rtlPlugin },
    rules: { 'soukly-rtl/no-physical-tailwind-classes': 'error' },
  },
];
