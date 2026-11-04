/**
 * #1183 D — the link from the profile-claim mail. Opened while logged in as the person who asked
 * for it (the route is protected), it merges the earlier profile into their account. POST only on
 * the button, so a mail scanner opening the link changes nothing.
 */

import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/public/ui/card';
import { Button } from '@/components/public/ui/button';
import { confirmProfileClaim } from '@/services/api/userAccountApi';

type PageState = 'ready' | 'merged' | 'alreadyMerged' | 'otherAccount' | 'invalid' | 'failed';

export default function ClaimProfilePage() {
  const { t } = useTranslation('userManagement');
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const queryClient = useQueryClient();
  const [pageState, setPageState] = useState<PageState>(token ? 'ready' : 'invalid');

  const confirm = useMutation({
    mutationFn: (value: string) => confirmProfileClaim(value),
    onSuccess: async (data) => {
      setPageState(data.merged ? 'merged' : 'alreadyMerged');
      await queryClient.invalidateQueries();
    },
    onError: (error) => {
      const status = isAxiosError(error) ? error.response?.status : undefined;
      if (status === 403) {
        setPageState('otherAccount');
      } else if (status === 400) {
        setPageState('invalid');
      } else {
        setPageState('failed');
      }
    },
  });

  const message: Record<Exclude<PageState, 'ready'>, string> = {
    merged: t('claim.merged'),
    alreadyMerged: t('claim.alreadyMerged'),
    otherAccount: t('claim.otherAccount'),
    invalid: t('claim.invalid'),
    failed: t('claim.failed'),
  };

  return (
    <PublicLayout>
      <div className="flex min-h-[50vh] items-center justify-center px-4 py-12">
        <Card className="w-full max-w-md">
          <CardHeader>
            <CardTitle>{t('claim.title')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {pageState === 'ready' ? (
              <>
                <p className="text-sm" data-testid="claim-confirm-message">
                  {t('claim.confirmMessage')}
                </p>
                <Button
                  onClick={() => token && confirm.mutate(token)}
                  disabled={confirm.isPending}
                  className="w-full"
                  data-testid="claim-confirm-button"
                >
                  {t('claim.confirmButton')}
                </Button>
              </>
            ) : (
              <div className="space-y-2">
                <p
                  className={
                    pageState === 'merged' || pageState === 'alreadyMerged'
                      ? 'text-sm text-green-700 dark:text-green-400'
                      : 'text-sm text-destructive'
                  }
                  data-testid={`claim-${pageState}`}
                >
                  {message[pageState]}
                </p>
                <Link to="/dashboard" className="text-sm text-primary underline">
                  {t('claim.dashboardLink')}
                </Link>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </PublicLayout>
  );
}
