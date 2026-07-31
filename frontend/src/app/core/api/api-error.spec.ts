import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './api-error';

describe('ApiError', () => {
  it('preserves the clinical error returned by the server', () => {
    const error = ApiError.from(new HttpErrorResponse({
      status: 409,
      error: { code: 'VERSION_CONFLICT', error: 'La historia fue modificada por otro usuario.' }
    }));

    expect(error.status).toBe(409);
    expect(error.code).toBe('VERSION_CONFLICT');
    expect(error.message).toContain('modificada');
  });
});
