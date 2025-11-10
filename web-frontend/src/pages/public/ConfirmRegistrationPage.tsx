/**
 * ConfirmRegistrationPage Component
 *
 * Handles email confirmation link clicks.
 * Validates JWT token and confirms registration status change from PENDING to CONFIRMED.
 */

import { useEffect, useState } from 'react';
import { useSearchParams, Link, Navigate } from 'react-router-dom';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Button } from '@/components/public/ui/button';
import { Card } from '@/components/public/ui/card';
import { CheckCircle2, XCircle, Loader2, ArrowLeft, Calendar } from 'lucide-react';
import { eventApiClient } from '@/services/eventApiClient';

type ConfirmationState = 'loading' | 'success' | 'error' | 'already_confirmed';

const ConfirmRegistrationPage = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [state, setState] = useState<ConfirmationState>('loading');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setState('error');
      setErrorMessage('No confirmation token provided');
      return;
    }

    const confirmRegistration = async () => {
      try {
        const response = await eventApiClient.confirmRegistration(token);

        if (response.status === 'CONFIRMED') {
          // Check if it was already confirmed
          if (response.message?.includes('already confirmed')) {
            setState('already_confirmed');
          } else {
            setState('success');
          }

          // Clear token from URL (security best practice)
          window.history.replaceState({}, '', '/confirm-registration');
        }
      } catch (error: unknown) {
        setState('error');

        const errorMessage = error instanceof Error ? error.message : '';
        if (errorMessage.includes('expired')) {
          setErrorMessage('Confirmation link has expired. Please register again.');
        } else if (errorMessage.includes('Invalid')) {
          setErrorMessage(
            'Invalid confirmation link. Please check your email for the correct link.'
          );
        } else {
          setErrorMessage('Failed to confirm registration. Please try again or contact support.');
        }
      }
    };

    confirmRegistration();
  }, [token]);

  // No token -> redirect to home
  if (!token) {
    return <Navigate to="/" replace />;
  }

  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-12 max-w-3xl">
        {/* Loading State */}
        {state === 'loading' && (
          <div className="text-center py-24">
            <Loader2 className="h-16 w-16 text-blue-400 mx-auto mb-4 animate-spin" />
            <h2 className="text-2xl font-light mb-2">Confirming Your Registration...</h2>
            <p className="text-zinc-400">Please wait while we process your confirmation.</p>
          </div>
        )}

        {/* Success State */}
        {state === 'success' && (
          <>
            <div className="text-center mb-8">
              <CheckCircle2 className="h-16 w-16 text-green-400 mx-auto mb-4" />
              <h1 className="text-4xl font-light mb-2">Registration Confirmed!</h1>
              <p className="text-xl text-zinc-400">You're all set for the event</p>
            </div>

            <Card className="p-8 mb-8">
              <h2 className="text-xl font-light mb-4">What's Next?</h2>
              <ul className="space-y-3 text-zinc-300">
                <li className="flex items-start gap-3">
                  <Calendar className="h-5 w-5 text-blue-400 flex-shrink-0 mt-0.5" />
                  <span>
                    <strong>Check-in at the Event:</strong> Present your ID at the event desk for
                    check-in. We'll have your confirmed registration on file.
                  </span>
                </li>
                <li className="flex items-start gap-3">
                  <Calendar className="h-5 w-5 text-blue-400 flex-shrink-0 mt-0.5" />
                  <span>
                    <strong>Event Details:</strong> You'll receive event updates and reminders via
                    email.
                  </span>
                </li>
              </ul>
            </Card>

            <div className="flex flex-col sm:flex-row gap-4 justify-center">
              <Button asChild>
                <Link to="/">
                  <ArrowLeft className="h-4 w-4 mr-2" />
                  Back to Home
                </Link>
              </Button>
              <Button asChild variant="outline">
                <Link to="/auth/register">Create Account to Manage Registrations</Link>
              </Button>
            </div>
          </>
        )}

        {/* Already Confirmed State */}
        {state === 'already_confirmed' && (
          <>
            <div className="text-center mb-8">
              <CheckCircle2 className="h-16 w-16 text-blue-400 mx-auto mb-4" />
              <h1 className="text-3xl font-light mb-2">Already Confirmed</h1>
              <p className="text-xl text-zinc-400">This registration has already been confirmed</p>
            </div>

            <Card className="p-6 mb-8 text-center">
              <p className="text-zinc-300">
                Your registration was previously confirmed. No further action is needed.
              </p>
            </Card>

            <div className="flex justify-center">
              <Button asChild variant="outline">
                <Link to="/">
                  <ArrowLeft className="h-4 w-4 mr-2" />
                  Back to Home
                </Link>
              </Button>
            </div>
          </>
        )}

        {/* Error State */}
        {state === 'error' && (
          <>
            <div className="text-center mb-8">
              <XCircle className="h-16 w-16 text-red-400 mx-auto mb-4" />
              <h1 className="text-3xl font-light mb-2">Confirmation Failed</h1>
              <p className="text-xl text-zinc-400">
                {errorMessage || 'Unable to confirm your registration'}
              </p>
            </div>

            <Card className="p-6 mb-8">
              <h3 className="text-lg font-light mb-3">What can you do?</h3>
              <ul className="space-y-2 text-sm text-zinc-400">
                <li>• Check if you clicked the correct link from your email</li>
                <li>• Confirmation links expire after 48 hours - you may need to register again</li>
                <li>• Make sure you're using the latest confirmation email</li>
                <li>• Contact support if the problem persists</li>
              </ul>
            </Card>

            <div className="flex justify-center gap-4">
              <Button asChild variant="outline">
                <Link to="/">
                  <ArrowLeft className="h-4 w-4 mr-2" />
                  Back to Home
                </Link>
              </Button>
              <Button asChild>
                <Link to="/register">Register Again</Link>
              </Button>
            </div>
          </>
        )}
      </div>
    </PublicLayout>
  );
};

export default ConfirmRegistrationPage;
