import React, { useEffect, useState } from 'react';
import { Snackbar, Alert, AlertColor, Box, Typography, IconButton } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { useErrorToast } from '@/hooks/useErrorToast';

interface ErrorToastProps {
  /**
   * Duration in ms before auto-hiding the toast (default: 6000ms)
   * Set to null to disable auto-hide
   */
  autoHideDuration?: number | null;

  /**
   * Position of the toast (default: top-center)
   */
  anchorOrigin?: {
    vertical: 'top' | 'bottom';
    horizontal: 'left' | 'center' | 'right';
  };
}

/**
 * Global error toast component
 * Displays error messages with automatic dismissal and user-friendly formatting
 *
 * @example
 * <ErrorToast />
 * <ErrorToast autoHideDuration={3000} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }} />
 */
const ErrorToast: React.FC<ErrorToastProps> = ({
  autoHideDuration = 6000,
  anchorOrigin = { vertical: 'top', horizontal: 'center' },
}) => {
  const { error, clearError } = useErrorToast();
  const [isPaused, setIsPaused] = useState(false);

  /**
   * Determine severity level based on status code
   */
  const getSeverity = (): AlertColor => {
    if (!error?.statusCode) {
      return 'error';
    }

    // 401 Unauthorized → info (session expired)
    if (error.statusCode === 401) {
      return 'info';
    }

    // 4xx Client errors → warning
    if (error.statusCode >= 400 && error.statusCode < 500) {
      return 'warning';
    }

    // 5xx Server errors → error
    if (error.statusCode >= 500) {
      return 'error';
    }

    return 'error';
  };

  /**
   * Check if error is critical (should not auto-close)
   */
  const isCritical = (): boolean => {
    return (error as typeof error & { severity?: string })?.severity === 'critical';
  };

  /**
   * Handle close button click
   */
  const handleClose = (_event?: React.SyntheticEvent | Event, reason?: string) => {
    // Don't close on clickaway
    if (reason === 'clickaway') {
      return;
    }
    clearError();
  };

  /**
   * Handle mouse enter (pause auto-close)
   */
  const handleMouseEnter = () => {
    setIsPaused(true);
  };

  /**
   * Handle mouse leave (resume auto-close)
   */
  const handleMouseLeave = () => {
    setIsPaused(false);
  };

  /**
   * Reset pause state when error changes
   */
  useEffect(() => {
    setIsPaused(false);
  }, [error]);

  if (!error) {
    return null;
  }

  const severity = getSeverity();
  const critical = isCritical();

  // Auto-hide duration: null if critical, otherwise use provided value (or default 6000ms)
  // Also disable auto-hide when paused
  const effectiveAutoHideDuration = critical || isPaused ? null : autoHideDuration;

  return (
    <Snackbar
      open={!!error}
      autoHideDuration={effectiveAutoHideDuration}
      onClose={handleClose}
      anchorOrigin={anchorOrigin}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      <Alert
        severity={severity}
        role="alert"
        aria-live="assertive"
        onClose={handleClose}
        sx={{
          width: '100%',
          minWidth: '300px',
          maxWidth: '600px',
        }}
        action={
          <IconButton size="small" aria-label="close" color="inherit" onClick={handleClose}>
            <CloseIcon fontSize="small" />
          </IconButton>
        }
      >
        <Box>
          <Typography variant="body1" component="div">
            {error.message}
          </Typography>
          {error.correlationId && (
            <Typography variant="caption" component="div" sx={{ marginTop: 1, opacity: 0.8 }}>
              Correlation ID: {error.correlationId}
            </Typography>
          )}
        </Box>
      </Alert>
    </Snackbar>
  );
};

export default ErrorToast;
