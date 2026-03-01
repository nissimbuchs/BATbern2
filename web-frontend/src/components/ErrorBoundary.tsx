/**
 * Error Boundary Component
 * Story 4.1.7: API Optimization & Performance - Task 4
 *
 * Catches JavaScript errors anywhere in the child component tree,
 * logs errors, and displays a fallback UI instead of crashing the app.
 */

import { Component, ErrorInfo, ReactNode } from 'react';
import i18next from 'i18next';
import { Button } from '@/components/public/ui/button';
import { AlertTriangle } from 'lucide-react';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    // Update state so the next render will show the fallback UI
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // Log error to console for development
    console.error('Error boundary caught:', error, errorInfo);

    // TODO: Send to monitoring service (Sentry, DataDog, etc.)
    // Example: logErrorToService(error, errorInfo);
  }

  handleReset = () => {
    // Reset error state
    this.setState({ hasError: false, error: null });

    // Reload the page to recover
    window.location.reload();
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex items-center justify-center min-h-screen bg-zinc-950 text-zinc-100 p-4">
          <div className="max-w-md text-center space-y-6">
            <div className="flex justify-center">
              <AlertTriangle className="h-16 w-16 text-yellow-500" />
            </div>

            <h1 className="text-2xl font-light">{i18next.t('common:errors.somethingWentWrong')}</h1>

            <p className="text-zinc-400">{i18next.t('common:errors.unexpectedError')}</p>

            {/* Show error message in development */}
            {import.meta.env.DEV && this.state.error && (
              <div className="mt-4 p-4 bg-zinc-900 border border-zinc-800 rounded-lg text-left">
                <p className="text-xs text-zinc-500 mb-2">
                  {i18next.t('common:errors.devDetails')}
                </p>
                <pre className="text-xs text-red-400 overflow-x-auto">
                  {this.state.error.message}
                </pre>
              </div>
            )}

            <div className="flex justify-center gap-4">
              <Button onClick={this.handleReset} className="bg-blue-600 hover:bg-blue-700">
                {i18next.t('common:errors.refreshPage')}
              </Button>

              <Button
                onClick={() => (window.location.href = '/')}
                variant="outline"
                className="border-zinc-700 hover:bg-zinc-900"
              >
                {i18next.t('common:errors.goHome')}
              </Button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
