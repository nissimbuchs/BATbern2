/**
 * UnsubscribePage (Story 10.7 — AC5)
 *
 * Public page at /unsubscribe?token={token}.
 * Verifies token, shows email being unsubscribed, and confirms on button click.
 */

import { useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/public/ui/card';
import { Button } from '@/components/public/ui/button';
import {
  useVerifyUnsubscribeToken,
  useUnsubscribeByToken,
} from '@/hooks/useNewsletter/useNewsletter';

type PageState = 'verifying' | 'ready' | 'confirmed' | 'invalid' | 'error';

export default function UnsubscribePage() {
  const { t } = useTranslation('events');
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const [pageState, setPageState] = useState<PageState>('verifying');

  const verifyQuery = useVerifyUnsubscribeToken(token);
  const unsubscribeMutation = useUnsubscribeByToken();

  // Transition from verifying to ready/invalid once query settles
  if (pageState === 'verifying') {
    if (verifyQuery.isSuccess) {
      // Delay state update to next render cycle
      setTimeout(() => setPageState('ready'), 0);
    } else if (verifyQuery.isError || !token) {
      setTimeout(() => setPageState('invalid'), 0);
    }
  }

  function handleConfirm() {
    if (!token) {
      return;
    }
    unsubscribeMutation.mutate(token, {
      onSuccess: () => setPageState('confirmed'),
      onError: () => setPageState('invalid'),
    });
  }

  const email = verifyQuery.data?.email;

  return (
    <PublicLayout>
      <div className="flex min-h-[50vh] items-center justify-center px-4 py-12">
        <Card className="w-full max-w-md">
          <CardHeader>
            <CardTitle>{t('newsletter.unsubscribe.title')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {pageState === 'verifying' && (
              <p className="text-sm text-muted-foreground">
                {t('newsletter.unsubscribe.loading', 'Loading…')}
              </p>
            )}

            {pageState === 'ready' && email && (
              <>
                <p className="text-sm">{t('newsletter.unsubscribe.confirmMessage', { email })}</p>
                <Button
                  onClick={handleConfirm}
                  disabled={unsubscribeMutation.isPending}
                  className="w-full"
                >
                  {t('newsletter.unsubscribe.confirmButton')}
                </Button>
              </>
            )}

            {pageState === 'confirmed' && (
              <div className="space-y-2">
                <p className="text-sm text-green-700 dark:text-green-400">
                  {t('newsletter.unsubscribe.success')}
                </p>
                <Link to="/" className="text-sm text-primary underline">
                  {t('newsletter.unsubscribe.resubscribeLink')}
                </Link>
              </div>
            )}

            {pageState === 'invalid' && (
              <p className="text-sm text-destructive">{t('newsletter.unsubscribe.invalidToken')}</p>
            )}

            {pageState === 'error' && (
              <p className="text-sm text-destructive">{t('newsletter.widget.error')}</p>
            )}
          </CardContent>
        </Card>
      </div>
    </PublicLayout>
  );
}
