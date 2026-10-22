import { HttpErrorResponse } from '@angular/common/http';
import { ReadonlyFieldTree, ValidationError } from '@angular/forms/signals';
import { problemCode, problemFieldErrors } from '@soukly/auth';

type FieldErrors = (ValidationError & { fieldTree: ReadonlyFieldTree<unknown> })[];

/**
 * Errors of the API that belong under a field of the form, keyed by the member of the request body:
 * the codes of a `validation-failed` answer, and `current-password-invalid` under the
 * `currentPassword` field (AUTH-05). Empty when none of them applies.
 */
export function apiFieldErrors(
  error: unknown,
  fields: Record<string, ReadonlyFieldTree<unknown>>,
): FieldErrors {
  const code = problemCode(error);
  if (code === 'current-password-invalid' && fields['currentPassword']) {
    return [{ fieldTree: fields['currentPassword'], kind: 'current-password.invalid' }];
  }
  if (code !== 'validation-failed') {
    return [];
  }
  return problemFieldErrors(error)
    .filter((item) => fields[item.field])
    .map((item) => ({ fieldTree: fields[item.field], kind: item.code }));
}

/** Translation key of an error shown on top of a form: too many attempts, or anything else. */
export function generalErrorKey(error: unknown): string {
  return error instanceof HttpErrorResponse && error.status === 429
    ? 'account.errors.too-many-attempts'
    : 'account.errors.unexpected';
}
