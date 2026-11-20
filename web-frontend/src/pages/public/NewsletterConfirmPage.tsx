/**
 * NewsletterConfirmPage (#1288, double opt-in)
 *
 * Public page at /newsletter/confirm?token={token}, the link of the confirmation mail. The
 * subscription becomes active when the visitor clicks the button, which posts the token. A click,
 * not an automatic post on load: mail security scanners open links (and some run scripts), and
 * they must not confirm a subscription the address's owner never asked for.
 */

import { useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import axios from 'axios';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/public/ui/card';
import { Button } from '@/components/public/ui/button';
import { useConfirmSubscription } from '@/hooks/useNewsletter/useNewsletter';

type PageState = 'ready' | 'confirmed' | 'invalid' | 'error';

export default function NewsletterConfirmPage() {
  const { t } = useTranslation('events');
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const [pageState, setPageState] = useState<PageState>(token ? 'ready' : 'invalid');
  const confirmMutation = useConfirmSubscription();

  function handleConfirm() {
    if (!token) {
      return;
    }
    confirmMutation.mutate(token, {
      onSuccess: () => setPageState('confirmed'),
      onError: (error) =>
        setPageState(
          axios.isAxiosError(error) && error.response?.status === 404 ? 'invalid' : 'error'
        ),
    });
  }

  return (
    <PublicLayout>
      <div className="flex min-h-[50vh] items-center justify-center px-4 py-12">
        <Card className="w-full max-w-md">
          <CardHeader>
            <CardTitle>{t('newsletter.confirm.title')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {pageState === 'ready' && (
              <>
                <p className="text-sm">{t('newsletter.confirm.message')}</p>
                <Button
                  onClick={handleConfirm}
                  disabled={confirmMutation.isPending}
                  className="w-full"
                >
                  {t('newsletter.confirm.button')}
                </Button>
              </>
            )}

            {pageState === 'confirmed' && (
              <div className="space-y-2">
                <p className="text-sm text-green-700 dark:text-green-400">
                  {t('newsletter.confirm.success')}
                </p>
                <Link to="/" className="text-sm text-primary underline">
                  {t('newsletter.confirm.homeLink')}
                </Link>
              </div>
            )}

            {pageState === 'invalid' && (
              <p className="text-sm text-destructive">{t('newsletter.confirm.invalidToken')}</p>
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
