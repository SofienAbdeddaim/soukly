import { FormControl, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';
import { PriceMode } from '@soukly/listing-data';
import { parseInteger } from '@soukly/listing-data';
import { ERROR_KEY, FieldError } from '../details/details-form';

/** Bounds of ANN-01 (DraftEditor): whole dinars, the API stays the reference. */
export const PRICE_RANGE = { min: 1, max: 100_000_000 };

export type PriceLocationForm = FormGroup<{
  priceMode: FormControl<PriceMode | null>;
  /** Text of the price field, thousands separators allowed. */
  price: FormControl<string>;
  negotiable: FormControl<boolean>;
  governorateCode: FormControl<string | null>;
  delegationCode: FormControl<string | null>;
}>;

/** Codes with their own message (`publish.errors.<code>`); the others get the general one. */
export const PRICE_LOCATION_ERRORS = new Set([
  'price-mode.required',
  'price-mode.not-allowed',
  'price.required',
  'price.invalid',
  'price.out-of-range',
  'price.not-allowed',
  'governorate.required',
  'governorate.unknown',
  'delegation.unknown',
  'delegation.not-in-governorate',
]);

function invalid(code: string): ValidationErrors {
  return { [ERROR_KEY]: { code } satisfies FieldError };
}

export const priceModeValidator: ValidatorFn = (control) =>
  control.value ? null : invalid('price-mode.required');

/** The price is checked only with a fixed price: "on quote" has none. */
export const priceValidator: ValidatorFn = (control) => {
  if (control.parent?.get('priceMode')?.value === 'ON_QUOTE') {
    return null;
  }
  const price = parseInteger(String(control.value ?? ''));
  if (price === null) {
    return invalid('price.required');
  }
  if (Number.isNaN(price)) {
    return invalid('price.invalid');
  }
  return price < PRICE_RANGE.min || price > PRICE_RANGE.max ? invalid('price.out-of-range') : null;
};

export const governorateValidator: ValidatorFn = (control) =>
  control.value ? null : invalid('governorate.required');

/** Members of the draft this step edits, as the API has them. */
export interface PriceLocationValues {
  priceMode: PriceMode | null;
  price: number | null;
  negotiable: boolean;
  governorateCode: string | null;
  delegationCode: string | null;
}

/**
 * Values to send, from the controls that are valid: "on quote" clears the price and the
 * negotiation, which the API refuses together. Invalid fields are left out (undefined).
 */
export function toApiValues(form: PriceLocationForm): Partial<PriceLocationValues> {
  const { priceMode, price, negotiable, governorateCode, delegationCode } = form.controls;
  const values: Partial<PriceLocationValues> = {};
  if (priceMode.valid) {
    values.priceMode = priceMode.value;
  }
  if (priceMode.value === 'ON_QUOTE') {
    values.price = null;
    values.negotiable = false;
  } else {
    if (price.valid) {
      values.price = parseInteger(price.value);
    }
    values.negotiable = negotiable.value;
  }
  if (governorateCode.valid) {
    values.governorateCode = governorateCode.value;
  }
  values.delegationCode = delegationCode.value;
  return values;
}
