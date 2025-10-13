import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useErrorToast } from './useErrorToast';
import { AxiosError } from 'axios';

describe('useErrorToast', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Initial State', () => {
    it('should_haveNoError_when_initialized', () => {
      const { result } = renderHook(() => useErrorToast());

      expect(result.current.error).toBeNull();
    });

    it('should_provideFunctions_when_initialized', () => {
      const { result } = renderHook(() => useErrorToast());

      expect(typeof result.current.showError).toBe('function');
      expect(typeof result.current.clearError).toBe('function');
    });
  });

  describe('Show Error', () => {
    it('should_setError_when_showErrorCalled', () => {
      const { result } = renderHook(() => useErrorToast());

      act(() => {
        result.current.showError({
          message: 'Test error message',
          statusCode: 500,
        });
      });

      expect(result.current.error).toEqual({
        message: 'Test error message',
        statusCode: 500,
      });
    });

    it('should_extractCorrelationId_when_errorHasCorrelationId', () => {
      const { result } = renderHook(() => useErrorToast());

      const axiosError = {
        response: {
          status: 500,
          headers: {
            'x-correlation-id': 'corr-123',
          },
          data: {
            message: 'Server error',
          },
        },
      } as AxiosError;

      act(() => {
        result.current.showError(axiosError);
      });

      expect(result.current.error?.correlationId).toBe('corr-123');
    });

    it('should_formatAxiosError_when_axiosErrorProvided', () => {
      const { result } = renderHook(() => useErrorToast());

      const axiosError = {
        response: {
          status: 404,
          data: {
            message: 'User not found',
          },
        },
      } as AxiosError;

      act(() => {
        result.current.showError(axiosError);
      });

      expect(result.current.error?.statusCode).toBe(404);
      expect(result.current.error?.message).toBe('User not found');
    });

    it('should_useDefaultMessage_when_axiosErrorHasNoMessage', () => {
      const { result } = renderHook(() => useErrorToast());

      const axiosError = {
        response: {
          status: 500,
          data: {},
        },
      } as AxiosError;

      act(() => {
        result.current.showError(axiosError);
      });

      expect(result.current.error?.message).toMatch(/server error|something went wrong/i);
    });

    it('should_handleNetworkError_when_noResponse', () => {
      const { result } = renderHook(() => useErrorToast());

      const axiosError = {
        message: 'Network Error',
        code: 'ERR_NETWORK',
      } as AxiosError;

      act(() => {
        result.current.showError(axiosError);
      });

      expect(result.current.error?.message).toMatch(/network|connect|internet/i);
      expect(result.current.error?.isNetworkError).toBe(true);
    });

    it('should_handleStringError_when_stringProvided', () => {
      const { result } = renderHook(() => useErrorToast());

      act(() => {
        result.current.showError('Simple error message');
      });

      expect(result.current.error?.message).toBe('Simple error message');
    });

    it('should_handleGenericError_when_errorObjectProvided', () => {
      const { result } = renderHook(() => useErrorToast());

      const error = new Error('Generic error');

      act(() => {
        result.current.showError(error);
      });

      expect(result.current.error?.message).toBe('Generic error');
    });
  });

  describe('Clear Error', () => {
    it('should_clearError_when_clearErrorCalled', () => {
      const { result } = renderHook(() => useErrorToast());

      act(() => {
        result.current.showError({
          message: 'Test error',
          statusCode: 500,
        });
      });

      expect(result.current.error).not.toBeNull();

      act(() => {
        result.current.clearError();
      });

      expect(result.current.error).toBeNull();
    });

    it('should_doNothing_when_clearErrorCalledWithNoError', () => {
      const { result } = renderHook(() => useErrorToast());

      expect(() => {
        act(() => {
          result.current.clearError();
        });
      }).not.toThrow();

      expect(result.current.error).toBeNull();
    });
  });

  describe('Multiple Errors', () => {
    it('should_replaceError_when_multipleErrorsShown', () => {
      const { result } = renderHook(() => useErrorToast());

      act(() => {
        result.current.showError({
          message: 'First error',
          statusCode: 400,
        });
      });

      expect(result.current.error?.message).toBe('First error');

      act(() => {
        result.current.showError({
          message: 'Second error',
          statusCode: 500,
        });
      });

      expect(result.current.error?.message).toBe('Second error');
    });

    it('should_queueErrors_when_multipleErrorsOccurRapidly', async () => {
      const { result } = renderHook(() => useErrorToast());

      act(() => {
        result.current.showError({ message: 'Error 1', statusCode: 400 });
        result.current.showError({ message: 'Error 2', statusCode: 500 });
        result.current.showError({ message: 'Error 3', statusCode: 503 });
      });

      // Should show the last error
      expect(result.current.error?.message).toBe('Error 3');
    });
  });

  describe('Error Logging', () => {
    it('should_logErrorToConsole_when_errorShown', () => {
      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      const { result } = renderHook(() => useErrorToast());

      act(() => {
        result.current.showError({
          message: 'Test error',
          statusCode: 500,
        });
      });

      expect(consoleErrorSpy).toHaveBeenCalled();
      consoleErrorSpy.mockRestore();
    });

    it('should_logCorrelationId_when_present', () => {
      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      const { result } = renderHook(() => useErrorToast());

      act(() => {
        result.current.showError({
          message: 'Test error',
          statusCode: 500,
          correlationId: 'corr-abc-123',
        });
      });

      expect(consoleErrorSpy).toHaveBeenCalled();
      const loggedArgs = consoleErrorSpy.mock.calls[0];
      const loggedString = JSON.stringify(loggedArgs);
      expect(loggedString).toContain('corr-abc-123');

      consoleErrorSpy.mockRestore();
    });

    it('should_includeStatusCode_when_logging', () => {
      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      const { result } = renderHook(() => useErrorToast());

      act(() => {
        result.current.showError({
          message: 'Test error',
          statusCode: 404,
        });
      });

      expect(consoleErrorSpy).toHaveBeenCalled();
      const loggedArgs = consoleErrorSpy.mock.calls[0];
      const loggedString = JSON.stringify(loggedArgs);
      expect(loggedString).toContain('404');

      consoleErrorSpy.mockRestore();
    });
  });

  describe('Integration with API Client', () => {
    it('should_handleAPIError_when_401Received', () => {
      const { result } = renderHook(() => useErrorToast());

      const axiosError = {
        response: {
          status: 401,
          data: {
            message: 'Session expired',
          },
        },
      } as AxiosError;

      act(() => {
        result.current.showError(axiosError);
      });

      expect(result.current.error?.statusCode).toBe(401);
      expect(result.current.error?.message).toMatch(/session|expired|login/i);
    });

    it('should_handleAPIError_when_429Received', () => {
      const { result } = renderHook(() => useErrorToast());

      const axiosError = {
        response: {
          status: 429,
          data: {
            message: 'Too many requests',
          },
        },
      } as AxiosError;

      act(() => {
        result.current.showError(axiosError);
      });

      expect(result.current.error?.statusCode).toBe(429);
      expect(result.current.error?.message).toMatch(/too many|rate limit|try again/i);
    });
  });
});
