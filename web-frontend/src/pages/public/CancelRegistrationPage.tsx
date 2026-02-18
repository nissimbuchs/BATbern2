/**
 * CancelRegistrationPage Component (with i18n support)
 *
 * Handles email cancellation link clicks.
 * Validates JWT token and cancels registration (permanently deletes from database).
 * Supports German and English languages.
 */

import { useEffect, useState, useRef } from 'react';
import { useSearchParams, useParams, Link, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Button } from '@/components/public/ui/button';
import { Card } from '@/components/public/ui/card';
import { CheckCircle2, XCircle, ArrowLeft } from 'lucide-react';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { eventApiClient } from '@/services/eventApiClient';

type CancellationState = 'loading' | 'success' | 'error';

const CancelRegistrationPage = () => {
  const { t } = useTranslation('registration');
  const { eventCode } = useParams<{ eventCode: string }>();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [state, setState] = useState<CancellationState>('loading');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const hasAttemptedCancellation = useRef(false);

  useEffect(() => {
    if (!token || !eventCode) {
      setState('error');
      setErrorMessage(t('cancellation.error.errors.invalid'));
      return;
    }

    // Prevent double execution in React Strict Mode (development)
    if (hasAttemptedCancellation.current) {
      return;
    }
    hasAttemptedCancellation.current = true;

    const cancelRegistration = async () => {
      try {
        const response = await eventApiClient.cancelRegistration(eventCode!, token);

        if (response.status === 'CANCELLED') {
          setState('success');

          // Clear pending registration from sessionStorage
          sessionStorage.removeItem('pendingRegistration');

          // Clear token from URL (security best practice)
          window.history.replaceState({}, '', `/events/${eventCode}/cancel-registration`);
        }
      } catch (error: unknown) {
        setState('error');

        const errorMessage = error instanceof Error ? error.message : '';
        if (errorMessage.includes('expired')) {
          setErrorMessage(t('cancellation.error.errors.expired'));
        } else if (errorMessage.includes('Invalid')) {
          setErrorMessage(t('cancellation.error.errors.invalid'));
        } else if (
          errorMessage.includes('not found') ||
          errorMessage.includes('already cancelled')
        ) {
          setErrorMessage(t('cancellation.error.errors.notFound'));
        } else {
          setErrorMessage(t('cancellation.error.errors.default'));
        }
      }
    };

    cancelRegistration();
  }, [token, eventCode, t]);

  // No token or eventCode -> redirect to home
  if (!token || !eventCode) {
    return <Navigate to="/" replace />;
  }

  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-12 max-w-3xl">
        {/* Loading State */}
        {state === 'loading' && (
          <div className="text-center py-24">
            <BATbernLoader size={64} />
            <h2 className="text-2xl font-light mb-2">{t('cancellation.loading.title')}</h2>
            <p className="text-zinc-400">{t('cancellation.loading.message')}</p>
          </div>
        )}

        {/* Success State */}
        {state === 'success' && (
          <>
            <div className="text-center mb-8">
              <CheckCircle2 className="h-16 w-16 text-green-400 mx-auto mb-4" />
              <h1 className="text-4xl font-light mb-2">{t('cancellation.success.title')}</h1>
              <p className="text-xl text-zinc-400">{t('cancellation.success.subtitle')}</p>
            </div>

            <Card className="p-8 mb-8">
              <h2 className="text-xl font-light mb-4">{t('cancellation.success.whatHappened')}</h2>
              <ul className="space-y-3 text-zinc-300">
                <li className="flex items-start gap-3">
                  <span>
                    <strong>{t('cancellation.success.permanentDeletion')}:</strong>{' '}
                    {t('cancellation.success.permanentDeletionText')}
                  </span>
                </li>
                <li className="flex items-start gap-3">
                  <span>
                    <strong>{t('cancellation.success.noFurtherAction')}:</strong>{' '}
                    {t('cancellation.success.noFurtherActionText')}
                  </span>
                </li>
                <li className="flex items-start gap-3">
                  <span>
                    <strong>{t('cancellation.success.registerAgain')}:</strong>{' '}
                    {t('cancellation.success.registerAgainText')}
                  </span>
                </li>
              </ul>
            </Card>

            <div className="flex flex-col sm:flex-row gap-4 justify-center">
              <Button asChild>
                <Link to="/">
                  <ArrowLeft className="h-4 w-4 mr-2" />
                  {t('cancellation.success.backToHome')}
                </Link>
              </Button>
              <Button asChild variant="outline">
                <Link to="/register">{t('cancellation.success.registerForAnother')}</Link>
              </Button>
            </div>
          </>
        )}

        {/* Error State */}
        {state === 'error' && (
          <>
            <div className="text-center mb-8">
              <XCircle className="h-16 w-16 text-red-400 mx-auto mb-4" />
              <h1 className="text-3xl font-light mb-2">{t('cancellation.error.title')}</h1>
              <p className="text-xl text-zinc-400">
                {errorMessage || t('cancellation.error.subtitle')}
              </p>
            </div>

            <Card className="p-6 mb-8">
              <h3 className="text-lg font-light mb-3">{t('cancellation.error.whatCanYouDo')}</h3>
              <ul className="space-y-2 text-sm text-zinc-400">
                <li>• {t('cancellation.error.checkLink')}</li>
                <li>• {t('cancellation.error.expired')}</li>
                <li>• {t('cancellation.error.alreadyCancelled')}</li>
                <li>• {t('cancellation.error.useLatestEmail')}</li>
                <li>• {t('cancellation.error.contactSupport')}</li>
              </ul>
            </Card>

            <div className="flex justify-center gap-4">
              <Button asChild variant="outline">
                <Link to="/">
                  <ArrowLeft className="h-4 w-4 mr-2" />
                  {t('cancellation.success.backToHome')}
                </Link>
              </Button>
            </div>
          </>
        )}
      </div>
    </PublicLayout>
  );
};

export default CancelRegistrationPage;
