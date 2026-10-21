import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { problemCode, problemFieldErrors, retryAfterSeconds } from './api-problem';

function httpError(status: number, error: unknown, headers?: Record<string, string>) {
  return new HttpErrorResponse({ status, error, headers: new HttpHeaders(headers) });
}

describe('API problems', () => {
  it('reads the slug of a Soukly problem type', () => {
    expect(
      problemCode(httpError(401, { type: 'urn:soukly:problem:invalid-credentials', status: 401 })),
    ).toBe('invalid-credentials');
  });

  it('has no code for another body or error', () => {
    expect(problemCode(httpError(502, 'Bad gateway'))).toBeNull();
    expect(problemCode(httpError(400, { type: 'about:blank' }))).toBeNull();
    expect(problemCode(new Error('offline'))).toBeNull();
  });

  it('lists the well-formed field errors only', () => {
    const error = httpError(400, {
      type: 'urn:soukly:problem:validation-failed',
      errors: [{ field: 'phone', code: 'phone.invalid' }, { field: 'email' }, null],
    });
    expect(problemFieldErrors(error)).toEqual([{ field: 'phone', code: 'phone.invalid' }]);
    expect(problemFieldErrors(httpError(500, null))).toEqual([]);
  });

  it('reads Retry-After in whole seconds', () => {
    expect(retryAfterSeconds(httpError(429, null, { 'Retry-After': '90' }))).toBe(90);
    expect(retryAfterSeconds(httpError(429, null))).toBeNull();
    expect(retryAfterSeconds(httpError(429, null, { 'Retry-After': 'soon' }))).toBeNull();
  });
});
