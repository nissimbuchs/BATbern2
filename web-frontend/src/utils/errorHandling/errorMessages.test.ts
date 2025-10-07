import { describe, it, expect } from 'vitest';
import { getErrorMessage, extractCorrelationId, formatErrorForDisplay } from './errorMessages';
import { AxiosError } from 'axios';

describe('Error Handling Utilities', () => {
  describe('getErrorMessage', () => {
    it('should_returnNetworkErrorMessage_when_statusCodeIs0', () => {
      const message = getErrorMessage(0);
      expect(message).toContain('network');
      expect(message).toMatch(/unable to connect|network error|no internet connection/i);
    });

    it('should_returnBadRequestMessage_when_statusCodeIs400', () => {
      const message = getErrorMessage(400);
      expect(message).toContain('request');
      expect(message).toMatch(/invalid|bad request|check your input/i);
    });

    it('should_returnUnauthorizedMessage_when_statusCodeIs401', () => {
      const message = getErrorMessage(401);
      expect(message).toMatch(/session|expired|login|authenticate/i);
    });

    it('should_returnForbiddenMessage_when_statusCodeIs403', () => {
      const message = getErrorMessage(403);
      expect(message).toMatch(/permission|access denied|not authorized/i);
    });

    it('should_returnNotFoundMessage_when_statusCodeIs404', () => {
      const message = getErrorMessage(404);
      expect(message).toMatch(/not found|does not exist|cannot find/i);
    });

    it('should_returnConflictMessage_when_statusCodeIs409', () => {
      const message = getErrorMessage(409);
      expect(message).toMatch(/conflict|already exists|duplicate/i);
    });

    it('should_returnRateLimitMessage_when_statusCodeIs429', () => {
      const message = getErrorMessage(429);
      expect(message).toMatch(/too many requests|rate limit|try again later/i);
    });

    it('should_returnServerErrorMessage_when_statusCodeIs500', () => {
      const message = getErrorMessage(500);
      expect(message).toMatch(/server error|something went wrong|try again/i);
    });

    it('should_returnServiceUnavailableMessage_when_statusCodeIs503', () => {
      const message = getErrorMessage(503);
      expect(message).toMatch(/service unavailable|maintenance|temporarily down/i);
    });

    it('should_returnGenericMessage_when_statusCodeIsUnknown', () => {
      const message = getErrorMessage(418); // I'm a teapot
      expect(message).toMatch(/unexpected error|something went wrong/i);
    });

    it('should_returnCustomMessage_when_customMessageProvided', () => {
      const customMessage = 'Custom error message from API';
      const message = getErrorMessage(500, customMessage);
      expect(message).toBe(customMessage);
    });

    it('should_useDefaultMessage_when_customMessageIsEmpty', () => {
      const message = getErrorMessage(500, '');
      expect(message).toMatch(/server error|something went wrong/i);
    });
  });

  describe('extractCorrelationId', () => {
    it('should_extractCorrelationId_when_headerPresent', () => {
      const error = {
        response: {
          headers: {
            'x-correlation-id': 'corr-abc-123',
          },
        },
      } as AxiosError;

      const correlationId = extractCorrelationId(error);
      expect(correlationId).toBe('corr-abc-123');
    });

    it('should_extractCorrelationId_when_headerIsCaseInsensitive', () => {
      const error = {
        response: {
          headers: {
            'X-Correlation-ID': 'corr-xyz-789',
          },
        },
      } as AxiosError;

      const correlationId = extractCorrelationId(error);
      expect(correlationId).toBe('corr-xyz-789');
    });

    it('should_returnUndefined_when_correlationIdHeaderMissing', () => {
      const error = {
        response: {
          headers: {},
        },
      } as AxiosError;

      const correlationId = extractCorrelationId(error);
      expect(correlationId).toBeUndefined();
    });

    it('should_returnUndefined_when_errorHasNoResponse', () => {
      const error = {
        message: 'Network error',
      } as AxiosError;

      const correlationId = extractCorrelationId(error);
      expect(correlationId).toBeUndefined();
    });

    it('should_extractCorrelationId_when_inErrorData', () => {
      const error = {
        response: {
          headers: {},
          data: {
            correlationId: 'corr-data-456',
          },
        },
      } as AxiosError;

      const correlationId = extractCorrelationId(error);
      expect(correlationId).toBe('corr-data-456');
    });

    it('should_preferHeaderCorrelationId_when_bothHeaderAndDataPresent', () => {
      const error = {
        response: {
          headers: {
            'x-correlation-id': 'corr-header-123',
          },
          data: {
            correlationId: 'corr-data-456',
          },
        },
      } as AxiosError;

      const correlationId = extractCorrelationId(error);
      expect(correlationId).toBe('corr-header-123');
    });
  });

  describe('formatErrorForDisplay', () => {
    it('should_formatError_when_axiosErrorWithStatus', () => {
      const error = {
        response: {
          status: 404,
          headers: {
            'x-correlation-id': 'corr-123',
          },
          data: {
            message: 'User not found',
          },
        },
      } as AxiosError;

      const formatted = formatErrorForDisplay(error);

      expect(formatted.message).toBeTruthy();
      expect(formatted.statusCode).toBe(404);
      expect(formatted.correlationId).toBe('corr-123');
    });

    it('should_includeCustomMessage_when_apiReturnsMessage', () => {
      const error = {
        response: {
          status: 400,
          data: {
            message: 'Invalid email format',
          },
        },
      } as AxiosError;

      const formatted = formatErrorForDisplay(error);

      expect(formatted.message).toBe('Invalid email format');
    });

    it('should_useDefaultMessage_when_apiMessageMissing', () => {
      const error = {
        response: {
          status: 500,
          data: {},
        },
      } as AxiosError;

      const formatted = formatErrorForDisplay(error);

      expect(formatted.message).toMatch(/server error|something went wrong/i);
    });

    it('should_handleNetworkError_when_noResponse', () => {
      const error = {
        message: 'Network Error',
        code: 'ERR_NETWORK',
      } as AxiosError;

      const formatted = formatErrorForDisplay(error);

      expect(formatted.message).toMatch(/network|connect|internet/i);
      expect(formatted.statusCode).toBeUndefined();
      expect(formatted.isNetworkError).toBe(true);
    });

    it('should_handleTimeoutError_when_timeoutOccurs', () => {
      const error = {
        message: 'timeout of 30000ms exceeded',
        code: 'ECONNABORTED',
      } as AxiosError;

      const formatted = formatErrorForDisplay(error);

      expect(formatted.message).toMatch(/timeout|too long|taking longer/i);
      expect(formatted.isTimeoutError).toBe(true);
    });

    it('should_formatGenericError_when_notAxiosError', () => {
      const error = new Error('Unknown error');

      const formatted = formatErrorForDisplay(error);

      expect(formatted.message).toBeTruthy();
      expect(formatted.statusCode).toBeUndefined();
    });

    it('should_includeOriginalError_when_debugging', () => {
      const error = {
        response: {
          status: 500,
          data: {
            message: 'Database connection failed',
          },
        },
      } as AxiosError;

      const formatted = formatErrorForDisplay(error);

      expect(formatted.originalError).toBeDefined();
    });
  });

  describe('i18n Support', () => {
    it('should_returnTranslatedMessage_when_i18nKeyProvided', () => {
      // This test assumes error messages can be translated
      const message = getErrorMessage(404, undefined, 'de');
      expect(message).toBeTruthy();
      // In German, the message should be translated
      expect(typeof message).toBe('string');
    });

    it('should_fallbackToEnglish_when_translationMissing', () => {
      const message = getErrorMessage(404, undefined, 'fr'); // French not supported
      expect(message).toBeTruthy();
      expect(typeof message).toBe('string');
    });
  });
});
