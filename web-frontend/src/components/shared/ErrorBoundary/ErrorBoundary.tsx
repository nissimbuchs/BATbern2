import { Component, ErrorInfo, ReactNode } from 'react';
import { Alert, Button, Box } from '@mui/material';

/**
 * Sanitize error message to prevent XSS attacks
 * Removes HTML tags and limits message length
 * @param message - Raw error message
 * @returns Sanitized error message safe for display
 */
function sanitizeErrorMessage(message: string): string {
  // Remove HTML tags
  const withoutTags = message.replace(/<[^>]*>/g, '');
  // Limit length to prevent DoS via large error messages
  return withoutTags.slice(0, 500);
}

interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: (props: { error: Error; resetError: () => void }) => ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

/**
 * ErrorBoundary Component
 * Catches React component errors and displays fallback UI
 *
 * @example
 * <ErrorBoundary>
 *   <MyComponent />
 * </ErrorBoundary>
 */
class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
    };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    // Update state so the next render will show the fallback UI
    return {
      hasError: true,
      error,
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    // Log error details to console
    try {
      const correlationId = (error as Error & { correlationId?: string }).correlationId;

      console.error('ErrorBoundary caught an error:', {
        message: error.message,
        stack: error.stack,
        correlationId,
        componentStack: errorInfo.componentStack,
      });

      // TODO: Integrate with Sentry or other error tracking service
      // if (window.Sentry) {
      //   window.Sentry.captureException(error, {
      //     contexts: {
      //       react: {
      //         componentStack: errorInfo.componentStack,
      //       },
      //     },
      //     tags: {
      //       correlationId,
      //     },
      //   });
      // }
    } catch (loggingError) {
      // Prevent logging errors from crashing the error boundary
      console.warn('Failed to log error:', loggingError);
    }
  }

  resetError = (): void => {
    this.setState({
      hasError: false,
      error: null,
    });
  };

  render(): ReactNode {
    const { hasError, error } = this.state;
    const { children, fallback } = this.props;

    if (hasError && error) {
      // If custom fallback provided, use it
      if (fallback) {
        return fallback({ error, resetError: this.resetError });
      }

      // Default fallback UI
      return (
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: '400px',
            padding: 3,
          }}
        >
          <Alert
            severity="error"
            role="alert"
            aria-live="assertive"
            sx={{
              maxWidth: '600px',
              width: '100%',
              marginBottom: 2,
            }}
          >
            <Box>
              <strong>Something went wrong</strong>
              {error.message && (
                <Box sx={{ marginTop: 1 }}>{sanitizeErrorMessage(error.message)}</Box>
              )}
            </Box>
          </Alert>
          <Button variant="contained" onClick={this.resetError} aria-label="Try again">
            Try Again
          </Button>
        </Box>
      );
    }

    return children;
  }
}

export default ErrorBoundary;
