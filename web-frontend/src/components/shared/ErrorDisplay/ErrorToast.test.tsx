import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import userEvent from '@testing-library/user-event';
import ErrorToast from './ErrorToast';
import { useErrorToast } from '@/hooks/useErrorToast';

// Mock the useErrorToast hook
vi.mock('@/hooks/useErrorToast');

describe('ErrorToast', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Display Behavior', () => {
    it('should_notDisplay_when_noErrorPresent', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: null,
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast />);

      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('should_displaySnackbar_when_errorPresent', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Server error occurred',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast />);

      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(screen.getByText('Server error occurred')).toBeInTheDocument();
    });

    it('should_displayErrorMessage_when_errorHasMessage', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Invalid email format',
          statusCode: 400,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast />);

      expect(screen.getByText('Invalid email format')).toBeInTheDocument();
    });

    it('should_displayCorrelationId_when_errorHasCorrelationId', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Server error',
          statusCode: 500,
          correlationId: 'corr-abc-123',
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast />);

      expect(screen.getByText(/corr-abc-123/i)).toBeInTheDocument();
    });

    it('should_notDisplayCorrelationId_when_correlationIdMissing', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Server error',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast />);

      expect(screen.queryByText(/correlation/i)).not.toBeInTheDocument();
    });
  });

  describe('User Interaction', () => {
    it('should_closeSnackbar_when_closeButtonClicked', async () => {
      const user = userEvent.setup();
      const clearError = vi.fn();

      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Test error',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError,
      });

      render(<ErrorToast />);

      const closeButton = screen.getByRole('button', { name: /close/i });
      await user.click(closeButton);

      expect(clearError).toHaveBeenCalledOnce();
    });

    it('should_autoClose_when_timeoutReached', async () => {
      const clearError = vi.fn();

      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Test error',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError,
      });

      render(<ErrorToast autoHideDuration={1000} />);

      await waitFor(
        () => {
          expect(clearError).toHaveBeenCalledOnce();
        },
        { timeout: 2000 }
      );
    });

    it('should_notAutoClose_when_errorIsCritical', async () => {
      const clearError = vi.fn();

      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Critical server error',
          statusCode: 500,
          severity: 'critical',
        },
        showError: vi.fn(),
        clearError,
      });

      render(<ErrorToast autoHideDuration={1000} />);

      await new Promise((resolve) => setTimeout(resolve, 1500));

      expect(clearError).not.toHaveBeenCalled();
    });

    it('should_pauseAutoClose_when_mouseEnters', async () => {
      const user = userEvent.setup();
      const clearError = vi.fn();

      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Test error',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError,
      });

      render(<ErrorToast autoHideDuration={1000} />);

      const snackbar = screen.getByRole('alert').closest('.MuiSnackbar-root');
      if (snackbar) {
        await user.hover(snackbar);
      }

      await new Promise((resolve) => setTimeout(resolve, 1500));

      // Should not auto-close when mouse is hovering
      expect(clearError).not.toHaveBeenCalled();
    });
  });

  describe('Severity Levels', () => {
    it('should_displayErrorSeverity_when_statusCodeIs5xx', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Server error',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast />);

      const alert = screen.getByRole('alert');
      expect(alert).toHaveClass('MuiAlert-standardError');
    });

    it('should_displayWarningSeverity_when_statusCodeIs4xx', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Invalid request',
          statusCode: 400,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast />);

      const alert = screen.getByRole('alert');
      expect(alert).toHaveClass('MuiAlert-standardWarning');
    });

    it('should_displayInfoSeverity_when_statusCodeIs401', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Session expired',
          statusCode: 401,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast />);

      const alert = screen.getByRole('alert');
      expect(alert).toHaveClass('MuiAlert-standardInfo');
    });
  });

  describe('Position', () => {
    it('should_displayAtTopCenter_when_defaultPosition', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Test error',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast />);

      const snackbar = screen.getByRole('alert').closest('.MuiSnackbar-root');
      expect(snackbar).toHaveClass('MuiSnackbar-anchorOriginTopCenter');
    });

    it('should_displayAtCustomPosition_when_positionProvided', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Test error',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }} />);

      const snackbar = screen.getByRole('alert').closest('.MuiSnackbar-root');
      expect(snackbar).toHaveClass('MuiSnackbar-anchorOriginBottomRight');
    });
  });

  describe('Material-UI Integration', () => {
    it('should_useMUISnackbar_when_displayed', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Test error',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast />);

      const snackbar = screen.getByRole('alert').closest('.MuiSnackbar-root');
      expect(snackbar).toBeInTheDocument();
    });

    it('should_useMUIAlert_when_displayed', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Test error',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast />);

      const alert = screen.getByRole('alert');
      expect(alert).toHaveClass('MuiAlert-root');
    });

    it('should_haveCloseButton_when_displayed', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Test error',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast />);

      expect(screen.getByRole('button', { name: /close/i })).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should_haveAlertRole_when_displayed', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Test error',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast />);

      expect(screen.getByRole('alert')).toBeInTheDocument();
    });

    it('should_haveAriaLive_when_displayed', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Test error',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast />);

      const alert = screen.getByRole('alert');
      expect(alert).toHaveAttribute('aria-live', 'assertive');
    });

    it('should_beFocusable_when_displayed', () => {
      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Test error',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      render(<ErrorToast />);

      const closeButton = screen.getByRole('button', { name: /close/i });
      closeButton.focus();
      expect(closeButton).toHaveFocus();
    });

    it('should_supportKeyboardClose_when_escapePressed', async () => {
      const user = userEvent.setup();
      const clearError = vi.fn();

      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Test error',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError,
      });

      render(<ErrorToast />);

      await user.keyboard('{Escape}');

      expect(clearError).toHaveBeenCalledOnce();
    });
  });

  describe('Multiple Errors', () => {
    it('should_displayLatestError_when_multipleErrorsOccur', () => {
      const { rerender } = render(<ErrorToast />);

      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'First error',
          statusCode: 400,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      rerender(<ErrorToast />);

      expect(screen.getByText('First error')).toBeInTheDocument();

      vi.mocked(useErrorToast).mockReturnValue({
        error: {
          message: 'Second error',
          statusCode: 500,
        },
        showError: vi.fn(),
        clearError: vi.fn(),
      });

      rerender(<ErrorToast />);

      expect(screen.queryByText('First error')).not.toBeInTheDocument();
      expect(screen.getByText('Second error')).toBeInTheDocument();
    });
  });
});
