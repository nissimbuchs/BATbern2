/**
 * DeregistrationPage (Story 10.12 — AC8)
 *
 * Public page at /deregister?token={uuid}.
 * Verifies token, shows event/attendee info, and cancels on button click.
 *
 * States: verifying → ready → confirmed | invalid | alreadyCancelled
 * Mirrors UnsubscribePage.tsx structure exactly.
 */

import { useState, useEffect } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/public/ui/card';
import { Button } from '@/components/public/ui/button';
import { useVerifyDeregistrationToken, useDeregisterByToken } from '@/hooks/useDeregistration';

type PageState = 'verifying' | 'ready' | 'confirmed' | 'invalid' | 'alreadyCancelled';

export default function DeregistrationPage() {
  const { t } = useTranslation('registration');
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const [pageState, setPageState] = useState<PageState>('verifying');

  const verifyQuery = useVerifyDeregistrationToken(token);
  const deregisterMutation = useDeregisterByToken(verifyQuery.data?.eventCode);

  // Transition from verifying to ready/invalid once query settles (useEffect avoids setTimeout-in-render anti-pattern)
  useEffect(() => {
    if (pageState !== 'verifying') return;
    if (verifyQuery.isSuccess) {
      setPageState('ready');
    } else if (verifyQuery.isError || !token) {
      setPageState('invalid');
    }
  }, [verifyQuery.isSuccess, verifyQuery.isError, token, pageState]);

  function handleConfirm() {
    if (!token) return;
    deregisterMutation.mutate(token, {
      onSuccess: () => setPageState('confirmed'),
      onError: (err) => {
        // 409 = already cancelled
        if (err instanceof Error && err.message.includes('409')) {
          setPageState('alreadyCancelled');
        } else {
          setPageState('invalid');
        }
      },
    });
  }

  const verifyData = verifyQuery.data;

  // Format event date for display
  const formattedDate = verifyData?.eventDate
    ? new Date(verifyData.eventDate).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      })
    : '';

  return (
    <PublicLayout>
      <div
        className="flex min-h-[50vh] items-center justify-center px-4 py-12"
        data-testid="deregistration-page"
      >
        <Card className="w-full max-w-md">
          <CardHeader>
            <CardTitle>{t('deregistration.page.title')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {pageState === 'verifying' && (
              <p className="text-sm text-muted-foreground">{t('deregistration.page.verifying')}</p>
            )}

            {pageState === 'ready' && verifyData && (
              <>
                <p className="text-sm">
                  {t('deregistration.page.confirmQuestion', {
                    name: verifyData.attendeeFirstName,
                    eventTitle: verifyData.eventTitle,
                    eventDate: formattedDate,
                  })}
                </p>
                <Button
                  onClick={handleConfirm}
                  disabled={deregisterMutation.isPending}
                  className="w-full bg-destructive text-destructive-foreground hover:bg-destructive/90"
                >
                  {t('deregistration.page.confirmButton')}
                </Button>
                <div className="text-center">
                  <Link to="/" className="text-sm text-muted-foreground underline">
                    {t('deregistration.page.goBack')}
                  </Link>
                </div>
              </>
            )}

            {pageState === 'confirmed' && (
              <div className="space-y-2">
                <p className="text-sm font-semibold text-green-700 dark:text-green-400">
                  {t('deregistration.page.successTitle')}
                </p>
                <p className="text-sm text-muted-foreground">
                  {t('deregistration.page.successBody')}
                </p>
                <Link to="/" className="text-sm text-primary underline">
                  {t('deregistration.page.goBack')}
                </Link>
              </div>
            )}

            {pageState === 'invalid' && (
              <p className="text-sm text-destructive">{t('deregistration.page.invalidToken')}</p>
            )}

            {pageState === 'alreadyCancelled' && (
              <p className="text-sm text-muted-foreground">
                {t('deregistration.page.alreadyCancelled')}
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    </PublicLayout>
  );
}
