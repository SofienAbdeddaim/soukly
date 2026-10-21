import {
  AbstractControl,
  FormControl,
  FormGroup,
  FormRecord,
  ValidationErrors,
  ValidatorFn,
} from '@angular/forms';
import { AttributeDefinition, AttributeValue, usesGrouping } from '@soukly/listing-data';

/** Bounds of ANN-01 (DraftEditor): the API stays the reference, these give an immediate answer. */
export const TITLE_LENGTH = { min: 5, max: 80 };
export const DESCRIPTION_LENGTH = { min: 20, max: 4000 };

/** Value of an attribute in the form: text of a TEXT or INTEGER input, ENUM code, BOOLEAN. */
export type AttributeInput = string | boolean | null;

export type DetailsForm = FormGroup<{
  title: FormControl<string>;
  description: FormControl<string>;
  attributes: FormRecord<FormControl<AttributeInput>>;
}>;

/**
 * Error of a field: the stable code of the API (`title.too-short`, `attribute.out-of-range`),
 * translated by the page with the bounds of the field. Set by the validators below and, for the
 * errors of the API, by the page under the same `soukly` key.
 */
export interface FieldError {
  code: string;
}

export const ERROR_KEY = 'soukly';

/** Codes with their own message (`publish.errors.<code>`); the others get the general one. */
export const KNOWN_ERRORS = new Set([
  ...['required', 'too-short', 'too-long', 'invalid'].flatMap((suffix) => [
    `title.${suffix}`,
    `description.${suffix}`,
  ]),
  ...[
    'required',
    'too-short',
    'too-long',
    'out-of-range',
    'invalid-type',
    'invalid-value',
    'unknown',
  ].map((suffix) => `attribute.${suffix}`),
]);

export function fieldError(control: AbstractControl): FieldError | null {
  return (control.errors?.[ERROR_KEY] as FieldError | undefined) ?? null;
}

function error(code: string): ValidationErrors {
  return { [ERROR_KEY]: { code } satisfies FieldError };
}

/** Length as the API counts it: Unicode code points. */
export function codePoints(value: string): number {
  return [...value].length;
}

/** A title on one line, as the API stores it: trimmed, runs of spaces made one. */
export function singleLine(value: string): string {
  return value.trim().replace(/\s+/gu, ' ');
}

function textValidator(
  prefix: string,
  min: number,
  max: number,
  normalize: (value: string) => string,
): ValidatorFn {
  return (control) => {
    const length = codePoints(normalize(String(control.value ?? '')));
    if (length === 0) {
      return error(`${prefix}.required`);
    }
    if (length < min) {
      return error(`${prefix}.too-short`);
    }
    return length > max ? error(`${prefix}.too-long`) : null;
  };
}

export const titleValidator = textValidator(
  'title',
  TITLE_LENGTH.min,
  TITLE_LENGTH.max,
  singleLine,
);
export const descriptionValidator = textValidator(
  'description',
  DESCRIPTION_LENGTH.min,
  DESCRIPTION_LENGTH.max,
  (value) => value.trim(),
);

/** Digits typed in a number field, thousands separators (spaces, dots, commas) allowed. */
const INTEGER_TEXT = /^[\d\s.,\u00a0\u202f\u066c]+$/u;

/** The whole number typed, null when empty, NaN when it is not a whole number. */
export function parseInteger(value: string): number | null {
  const text = value.trim();
  if (!text) {
    return null;
  }
  return INTEGER_TEXT.test(text) ? Number(text.replace(/\D/gu, '')) : Number.NaN;
}

/** "62000" -> "62 000" (fr), "62,000" (en), in Latin digits; numbers below 5 digits unchanged. */
export function formatInteger(
  value: number,
  attribute: AttributeDefinition,
  locale: string,
): string {
  return usesGrouping(attribute)
    ? new Intl.NumberFormat(`${locale}-u-nu-latn`, { useGrouping: true }).format(value)
    : String(value);
}

/** Validator built from the definition of the attribute (CAT-01): nothing is known by its code. */
export function attributeValidator(attribute: AttributeDefinition): ValidatorFn {
  return (control) => {
    const value = control.value as AttributeInput;
    switch (attribute.type) {
      case 'BOOLEAN':
        return null;
      case 'ENUM':
        return attribute.required && !value ? error('attribute.required') : null;
      case 'INTEGER': {
        const number = parseInteger(String(value ?? ''));
        if (number === null) {
          return attribute.required ? error('attribute.required') : null;
        }
        if (Number.isNaN(number)) {
          return error('attribute.invalid-type');
        }
        const tooSmall = attribute.minValue != null && number < attribute.minValue;
        const tooLarge = attribute.maxValue != null && number > attribute.maxValue;
        return tooSmall || tooLarge ? error('attribute.out-of-range') : null;
      }
      case 'TEXT': {
        const length = codePoints(singleLine(String(value ?? '')));
        if (length === 0) {
          return attribute.required ? error('attribute.required') : null;
        }
        if (attribute.minLength != null && length < attribute.minLength) {
          return error('attribute.too-short');
        }
        return attribute.maxLength != null && length > attribute.maxLength
          ? error('attribute.too-long')
          : null;
      }
    }
  };
}

/** Value of the API shown in the form. */
export function toInput(
  attribute: AttributeDefinition,
  value: AttributeValue | undefined,
  locale: string,
): AttributeInput {
  switch (attribute.type) {
    case 'BOOLEAN':
      return value === true;
    case 'INTEGER':
      return typeof value === 'number' ? formatInteger(value, attribute, locale) : '';
    case 'ENUM':
      return typeof value === 'string' ? value : null;
    case 'TEXT':
      return typeof value === 'string' ? value : '';
  }
}

/** Value of the form sent to the API; null clears the attribute. */
export function toApi(
  attribute: AttributeDefinition,
  input: AttributeInput,
): AttributeValue | null {
  switch (attribute.type) {
    case 'BOOLEAN':
      return input === true;
    case 'INTEGER':
      return parseInteger(String(input ?? ''));
    case 'ENUM':
      return typeof input === 'string' && input ? input : null;
    case 'TEXT': {
      const text = singleLine(String(input ?? ''));
      return text || null;
    }
  }
}

/** Title or description sent to the API: null when empty. */
export function textToApi(value: string, multiLine: boolean): string | null {
  const text = multiLine ? value.trim() : singleLine(value);
  return text || null;
}

/** Bounds given to the translation of an error of this attribute. */
export function errorParams(
  attribute: AttributeDefinition | null,
): Record<string, number | string> {
  if (!attribute) {
    return {};
  }
  return {
    min: attribute.minLength ?? attribute.minValue ?? '',
    max: attribute.maxLength ?? attribute.maxValue ?? '',
  };
}
