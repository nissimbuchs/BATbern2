import { AxiosError } from 'axios';
import i18n from '@/i18n/config';

export interface FormattedError {
  message: string;
  statusCode?: number;
  correlationId?: string;
  isNetworkError?: boolean;
  isTimeoutError?: boolean;
  originalError?: any;
}

/**
 * Error message mapping for common HTTP status codes
 */
const ERROR_MESSAGES: Record<number, string> = {
  0: 'Unable to connect to the server. Please check your network connection.',
  400: 'Invalid request. Please check your input and try again.',
  401: 'Your session has expired. Please log in again.',
  403: 'You do not have permission to access this resource.',
  404: 'The requested resource was not found. It may have been deleted or does not exist.',
  409: 'Conflict: This item already exists. Please check and try again.',
  429: 'Too many requests. Rate limit exceeded. Please try again later.',
  500: 'Server error: Something went wrong. Please try again later.',
  503: 'Service temporarily unavailable due to maintenance. Please try again later.',
};

/**
 * Get user-friendly error message based on HTTP status code
 *
 * @param statusCode - HTTP status code
 * @param customMessage - Optional custom message from API
 * @param locale - Optional locale for i18n (defaults to current i18n language)
 * @returns User-friendly error message
 */
export function getErrorMessage(
  statusCode: number,
  customMessage?: string,
  locale?: string
): string {
  // If custom message provided and not empty, use it
  if (customMessage && customMessage.trim()) {
    return customMessage;
  }

  // Get message for status code or use generic message
  const message = ERROR_MESSAGES[statusCode] || 'An unexpected error occurred. Please try again.';

  // TODO: Implement i18n translation when error message translations are available
  // For now, return English messages
  // const translationKey = `errors.http.${statusCode}`;
  // return i18n.t(translationKey, { defaultValue: message });

  return message;
}

/**
 * Extract correlation ID from error response
 *
 * Checks both response headers and response data for correlation ID
 *
 * @param error - Axios error object
 * @returns Correlation ID if found, undefined otherwise
 */
export function extractCorrelationId(error: AxiosError): string | undefined {
  if (!error.response) {
    return undefined;
  }

  // Check headers first (case-insensitive)
  const headers = error.response.headers;
  if (headers) {
    const correlationId =
      headers['x-correlation-id'] || headers['X-Correlation-ID'] || headers['X-CORRELATION-ID'];

    if (correlationId) {
      return correlationId as string;
    }
  }

  // Check response data
  const data = error.response.data as any;
  if (data && data.correlationId) {
    return data.correlationId;
  }

  return undefined;
}

/**
 * Format error for display in UI
 *
 * Extracts relevant information from Error or AxiosError and formats it
 * for consistent display in the UI
 *
 * @param error - Error or AxiosError object
 * @returns Formatted error object
 */
export function formatErrorForDisplay(error: any): FormattedError {
  // Handle AxiosError (API errors)
  // Check for isAxiosError flag, response object, or axios-specific error codes
  const isAxiosLike =
    error.isAxiosError ||
    error.response ||
    error.code === 'ERR_NETWORK' ||
    error.code === 'ECONNABORTED';

  if (isAxiosLike) {
    const axiosError = error as AxiosError;

    // Network error (no response)
    if (!axiosError.response) {
      const isTimeout =
        axiosError.code === 'ECONNABORTED' ||
        (axiosError.message && axiosError.message.includes('timeout'));

      return {
        message: isTimeout
          ? 'Timeout: The request is taking longer than expected. Please try again.'
          : getErrorMessage(0),
        isNetworkError: isTimeout ? false : true,
        isTimeoutError: isTimeout ? true : false,
        originalError: error,
      };
    }

    // HTTP error with response
    const statusCode = axiosError.response.status;
    const customMessage = (axiosError.response.data as any)?.message;
    const correlationId = extractCorrelationId(axiosError);

    return {
      message: getErrorMessage(statusCode, customMessage),
      statusCode,
      correlationId,
      originalError: error,
    };
  }

  // Handle generic Error
  return {
    message: error.message || 'An unexpected error occurred. Please try again.',
    originalError: error,
  };
}
