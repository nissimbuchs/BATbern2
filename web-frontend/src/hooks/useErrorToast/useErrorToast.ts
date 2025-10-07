import { create } from 'zustand';
import { formatErrorForDisplay, type FormattedError } from '@/utils/errorHandling';
import type { AxiosError } from 'axios';

interface ErrorToastState {
  error: FormattedError | null;
  showError: (error: string | Error | AxiosError | FormattedError) => void;
  clearError: () => void;
}

/**
 * Global error toast state store
 * Manages error display across the application
 */
const useErrorToastStore = create<ErrorToastState>((set) => ({
  error: null,

  showError: (error) => {
    let formattedError: FormattedError;

    // Handle different error types
    if (typeof error === 'string') {
      formattedError = {
        message: error,
      };
    } else if ('message' in error && 'statusCode' in error) {
      // Already a FormattedError
      formattedError = error as FormattedError;
    } else {
      // Error or AxiosError
      formattedError = formatErrorForDisplay(error);
    }

    // Log error to console
    console.error('Error occurred:', {
      message: formattedError.message,
      statusCode: formattedError.statusCode,
      correlationId: formattedError.correlationId,
      isNetworkError: formattedError.isNetworkError,
      isTimeoutError: formattedError.isTimeoutError,
      originalError: formattedError.originalError,
    });

    // Update state
    set({ error: formattedError });
  },

  clearError: () => {
    set({ error: null });
  },
}));

/**
 * Hook for displaying global error toasts
 *
 * @example
 * const { error, showError, clearError } = useErrorToast();
 *
 * // Show error from API
 * try {
 *   await apiClient.get('/users');
 * } catch (err) {
 *   showError(err);
 * }
 *
 * // Show custom error
 * showError('Invalid email format');
 *
 * // Clear error
 * clearError();
 */
export const useErrorToast = () => {
  return useErrorToastStore();
};
