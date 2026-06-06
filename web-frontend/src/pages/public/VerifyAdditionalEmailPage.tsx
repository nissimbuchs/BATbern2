/**
 * VerifyAdditionalEmailPage (Additional-email verification v2)
 *
 * Public page at /verify-email?token={token}.
 * GET-checks the token (no mutation), shows the masked email, and confirms on
 * button click (POST). Mirrors the newsletter UnsubscribePage verify→confirm
 * landing-page pattern.
 */

import { useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/public/ui/card';
import { Button } from '@/components/public/ui/button';
import {
  useCheckAdditionalEmailVerification,
  useConfirmAdditionalEmailVerification,
} from '@/hooks/useAdditionalEmailVerification/useAdditionalEmailVerification';

type PageState = 'verifying' | 'ready' | 'confirmed' | 'alreadyVerified' | 'invalid' | 'notFound';

export default function VerifyAdditionalEmailPage() {
  const { t } = useTranslation('userManagement');
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const [pageState, setPageState] = useState<PageState>('verifying');

  const checkQuery = useCheckAdditionalEmailVerification(token);
  const confirmMutation = useConfirmAdditionalEmailVerification();

  const checkIsSuccess = checkQuery.isSuccess;
  const checkIsError = checkQuery.isError;
  const checkVerified = checkQuery.data?.verified;

  // Drive the verifying → ready/alreadyVerified/invalid transition from an effect
  // (keyed on the check-query result) rather than from the render body, so we never
  // call setState during render and the behaviour is StrictMode double-invoke safe.
  useEffect(() => {
    if (pageState !== 'verifying') {
      return;
    }
    if (!token || checkIsError) {
      setPageState('invalid');
    } else if (checkIsSuccess) {
      setPageState(checkVerified ? 'alreadyVerified' : 'ready');
    }
  }, [pageState, token, checkIsSuccess, checkIsError, checkVerified]);

  function handleConfirm() {
    if (!token) {
      return;
    }
    confirmMutation.mutate(token, {
      onSuccess: (data) => setPageState(data.alreadyVerified ? 'alreadyVerified' : 'confirmed'),
      onError: (error) => {
        // 404 means the additional-email row was deleted (or deleted-then-re-added,
        // minting a fresh token) since this link was issued — "request a new link" is
        // misleading advice here, so branch to a distinct message. Everything else
        // (400 invalid/expired token, network errors) falls through to the generic
        // invalid state.
        if (isAxiosError(error) && error.response?.status === 404) {
          setPageState('notFound');
        } else {
          setPageState('invalid');
        }
      },
    });
  }

  const email = checkQuery.data?.email;

  return (
    <PublicLayout>
      <div className="flex min-h-[50vh] items-center justify-center px-4 py-12">
        <Card className="w-full max-w-md">
          <CardHeader>
            <CardTitle>{t('verifyEmail.title')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {pageState === 'verifying' && (
              <p className="text-sm text-muted-foreground" data-testid="verify-email-loading">
                {t('verifyEmail.loading')}
              </p>
            )}

            {pageState === 'ready' && email && (
              <>
                <p className="text-sm" data-testid="verify-email-confirm-message">
                  {t('verifyEmail.confirmMessage', { email })}
                </p>
                <Button
                  onClick={handleConfirm}
                  disabled={confirmMutation.isPending}
                  className="w-full"
                  data-testid="verify-email-confirm-button"
                >
                  {t('verifyEmail.confirmButton')}
                </Button>
              </>
            )}

            {pageState === 'confirmed' && (
              <div className="space-y-2">
                <p
                  className="text-sm text-green-700 dark:text-green-400"
                  data-testid="verify-email-success"
                >
                  {t('verifyEmail.success')}
                </p>
                <Link to="/" className="text-sm text-primary underline">
                  {t('verifyEmail.homeLink')}
                </Link>
              </div>
            )}

            {pageState === 'alreadyVerified' && (
              <div className="space-y-2">
                <p
                  className="text-sm text-green-700 dark:text-green-400"
                  data-testid="verify-email-already-verified"
                >
                  {t('verifyEmail.alreadyVerified')}
                </p>
                <Link to="/" className="text-sm text-primary underline">
                  {t('verifyEmail.homeLink')}
                </Link>
              </div>
            )}

            {pageState === 'invalid' && (
              <p className="text-sm text-destructive" data-testid="verify-email-invalid">
                {t('verifyEmail.invalidToken')}
              </p>
            )}

            {pageState === 'notFound' && (
              <p className="text-sm text-destructive" data-testid="verify-email-not-found">
                {t('verifyEmail.notFound')}
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    </PublicLayout>
  );
}
