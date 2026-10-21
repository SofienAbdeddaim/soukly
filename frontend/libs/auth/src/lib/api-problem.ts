import { HttpErrorResponse } from '@angular/common/http';

const PROBLEM_TYPE_PREFIX = 'urn:soukly:problem:';

/** Invalid field of a `validation-failed` problem: `code` is a stable key the front end translates. */
export interface ApiFieldError {
  field: string;
  code: string;
}

/**
 * Slug of a Soukly `application/problem+json` error (`urn:soukly:problem:invalid-credentials` ->
 * `invalid-credentials`), or null for another error (network, proxy, unexpected body). The
 * `title` and `detail` of the API are never shown: the front end translates the slug.
 */
export function problemCode(error: unknown): string | null {
  const type = problemBody(error)?.['type'];
  return typeof type === 'string' && type.startsWith(PROBLEM_TYPE_PREFIX)
    ? type.slice(PROBLEM_TYPE_PREFIX.length)
    : null;
}

/** Field errors of a `validation-failed` problem; empty for any other error. */
export function problemFieldErrors(error: unknown): ApiFieldError[] {
  const errors = problemBody(error)?.['errors'];
  if (!Array.isArray(errors)) {
    return [];
  }
  return errors.filter(
    (item): item is ApiFieldError =>
      typeof item?.field === 'string' && typeof item?.code === 'string',
  );
}

/** `missingFields` of a `listing-incomplete` problem (ANN-03, ANN-05); empty for any other error. */
export function problemMissingFields(error: unknown): string[] {
  const fields = problemBody(error)?.['missingFields'];
  return Array.isArray(fields) ? fields.filter((field) => typeof field === 'string') : [];
}

/** Whole seconds of the `Retry-After` header of a `429`, or null. */
export function retryAfterSeconds(error: unknown): number | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const seconds = Number(error.headers.get('Retry-After'));
  return Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : null;
}

function problemBody(error: unknown): Record<string, unknown> | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const body: unknown = error.error;
  return body !== null && typeof body === 'object' ? (body as Record<string, unknown>) : null;
}
