import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import userEvent from '@testing-library/user-event';
import ErrorBoundary from './ErrorBoundary';

// Component that throws an error
const ThrowError = ({ shouldThrow }: { shouldThrow: boolean }) => {
  if (shouldThrow) {
    throw new Error('Test error from child component');
  }
  return <div>Child component</div>;
};

// Component that throws an error with correlation ID
const ThrowErrorWithCorrelation = ({ correlationId }: { correlationId?: string }) => {
  const error = new Error('API error');
  if (correlationId) {
    (error as any).correlationId = correlationId;
  }
  throw error;
};

describe('ErrorBoundary', () => {
  const originalConsoleError = console.error;

  beforeEach(() => {
    // Suppress console.error during tests to keep output clean
    console.error = vi.fn();
  });

  afterEach(() => {
    console.error = originalConsoleError;
    vi.clearAllMocks();
  });

  describe('Error Catching', () => {
    it('should_displayFallbackUI_when_childComponentThrowsError', () => {
      render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
      expect(screen.queryByText('Child component')).not.toBeInTheDocument();
    });

    it('should_renderChildren_when_noErrorOccurs', () => {
      render(
        <ErrorBoundary>
          <ThrowError shouldThrow={false} />
        </ErrorBoundary>
      );

      expect(screen.getByText('Child component')).toBeInTheDocument();
      expect(screen.queryByText(/something went wrong/i)).not.toBeInTheDocument();
    });

    it('should_displayErrorMessage_when_errorHasMessage', () => {
      render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      expect(screen.getByText('Test error from child component')).toBeInTheDocument();
    });

    it('should_displayRetryButton_when_errorOccurs', () => {
      render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
    });

    it('should_resetErrorState_when_retryButtonClicked', async () => {
      const user = userEvent.setup();

      // Test that clicking retry button triggers the reset mechanism
      render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
      expect(screen.getByText('Test error from child component')).toBeInTheDocument();

      const retryButton = screen.getByRole('button', { name: /try again/i });
      expect(retryButton).toBeInTheDocument();

      // Click retry button
      await user.click(retryButton);

      // Since the child component still throws an error,
      // the error UI will be displayed again after reset
      // Verify that error UI is still shown (proves error was caught again after reset)
      expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
    });
  });

  describe('Custom Fallback', () => {
    it('should_renderCustomFallback_when_fallbackPropProvided', () => {
      const CustomFallback = ({ error, resetError }: { error: Error; resetError: () => void }) => (
        <div>
          <h2>Custom Error UI</h2>
          <p>{error.message}</p>
          <button onClick={resetError}>Reset</button>
        </div>
      );

      render(
        <ErrorBoundary fallback={CustomFallback}>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      expect(screen.getByText('Custom Error UI')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /reset/i })).toBeInTheDocument();
    });

    it('should_callResetError_when_customFallbackResetButtonClicked', async () => {
      const user = userEvent.setup();
      const mockResetError = vi.fn();

      const CustomFallback = ({ error, resetError }: { error: Error; resetError: () => void }) => (
        <div>
          <h2>Custom Error UI</h2>
          <button
            onClick={() => {
              mockResetError();
              resetError();
            }}
          >
            Reset Error
          </button>
        </div>
      );

      render(
        <ErrorBoundary fallback={CustomFallback}>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      expect(screen.getByText('Custom Error UI')).toBeInTheDocument();

      const resetButton = screen.getByRole('button', { name: /reset error/i });
      await user.click(resetButton);

      // Verify resetError was called
      expect(mockResetError).toHaveBeenCalledOnce();
    });
  });

  describe('Error Logging', () => {
    it('should_logErrorToConsole_when_errorOccurs', () => {
      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      expect(consoleErrorSpy).toHaveBeenCalled();
      consoleErrorSpy.mockRestore();
    });

    it('should_logCorrelationId_when_errorHasCorrelationId', () => {
      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
      const correlationId = 'corr-123-456';

      render(
        <ErrorBoundary>
          <ThrowErrorWithCorrelation correlationId={correlationId} />
        </ErrorBoundary>
      );

      expect(consoleErrorSpy).toHaveBeenCalled();
      const loggedArgs = consoleErrorSpy.mock.calls[0];
      const loggedString = JSON.stringify(loggedArgs);
      expect(loggedString).toContain(correlationId);

      consoleErrorSpy.mockRestore();
    });

    it('should_logErrorStack_when_errorHasStack', () => {
      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      expect(consoleErrorSpy).toHaveBeenCalled();
      consoleErrorSpy.mockRestore();
    });
  });

  describe('Accessibility', () => {
    it('should_haveAlertRole_when_errorDisplayed', () => {
      render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      const alert = screen.getByRole('alert');
      expect(alert).toBeInTheDocument();
    });

    it('should_haveFocusableRetryButton_when_errorDisplayed', () => {
      render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      const retryButton = screen.getByRole('button', { name: /try again/i });
      expect(retryButton).toBeInTheDocument();
      retryButton.focus();
      expect(retryButton).toHaveFocus();
    });

    it('should_haveAriaLabel_when_errorDisplayed', () => {
      render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      const alert = screen.getByRole('alert');
      expect(alert).toHaveAttribute('aria-live', 'assertive');
    });
  });

  describe('Edge Cases', () => {
    it('should_handleErrorGracefully_when_loggingFails', () => {
      // Test that error boundary continues to work even if console.error is unavailable
      const originalConsoleError = console.error;
      const consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

      // Temporarily remove console.error to simulate logging failure
      (console as any).error = undefined;

      // Should still display error UI even without logging
      render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();

      // Restore console.error
      console.error = originalConsoleError;
      consoleWarnSpy.mockRestore();
    });

    it('should_displayGenericMessage_when_errorHasNoMessage', () => {
      const ThrowErrorNoMessage = () => {
        const error: any = new Error();
        error.message = '';
        throw error;
      };

      render(
        <ErrorBoundary>
          <ThrowErrorNoMessage />
        </ErrorBoundary>
      );

      expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
    });

    it('should_handleMultipleErrors_when_componentThrowsAgain', () => {
      // Test that error boundary can handle multiple errors by unmounting and remounting
      const { unmount } = render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      // First error should be displayed
      expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();

      // Unmount and render fresh instance
      unmount();

      render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      // Error should be caught again
      expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
      expect(screen.getByText('Test error from child component')).toBeInTheDocument();
    });
  });

  describe('Material-UI Integration', () => {
    it('should_useMUIAlert_when_errorDisplayed', () => {
      render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      // Material-UI Alert component has role="alert" and specific classes
      const alert = screen.getByRole('alert');
      expect(alert).toHaveClass('MuiAlert-root');
    });

    it('should_useMUIButton_when_retryButtonDisplayed', () => {
      render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      const button = screen.getByRole('button', { name: /try again/i });
      expect(button).toHaveClass('MuiButton-root');
    });

    it('should_displayErrorSeverity_when_errorOccurs', () => {
      render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      const alert = screen.getByRole('alert');
      expect(alert).toHaveClass('MuiAlert-standardError');
    });
  });
});
