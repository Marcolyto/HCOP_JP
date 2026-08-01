import { HttpErrorResponse } from '@angular/common/http';

export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) {
    super(message);
  }

  static from(error: unknown): ApiError {
    if (error instanceof ApiError) return error;
    if (error instanceof HttpErrorResponse) {
      const body = error.error as { code?: string; error?: string; message?: string } | null;
      return new ApiError(
        error.status,
        body?.code ?? 'HTTP_ERROR',
        body?.error ?? body?.message ?? (error.status === 0 ? 'No se pudo conectar con HCOP.' : 'La solicitud no pudo completarse.')
      );
    }
    return new ApiError(0, 'UNEXPECTED_ERROR', 'Ocurrió un error inesperado.');
  }
}
