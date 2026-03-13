/**
 * NewsletterSubscriberList — Main list container
 *
 * Mirrors UserList.tsx but table-only (no grid/card view mode).
 *
 * Story 10.28: Newsletter Subscriber Management Page
 */

import React, { useCallback, useState } from 'react';
import { Alert, Box, Button, Snackbar } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useNewsletterSubscriberStore } from '@/stores/newsletterSubscriberStore';
import { useNewsletterSubscriberList } from '@/hooks/useNewsletterSubscribers';
import { BATbernLoader } from '@/components/shared/BATbernLoader';
import UserPagination from '@/components/organizer/UserManagement/UserPagination';
import NewsletterSubscriberFilters from './NewsletterSubscriberFilters';
import NewsletterSubscriberTable from './NewsletterSubscriberTable';
import UnsubscribeDialog from './UnsubscribeDialog';
import ResubscribeDialog from './ResubscribeDialog';
import DeleteSubscriberDialog from './DeleteSubscriberDialog';
import type { components } from '@/types/generated/events-api.types';

type SubscriberResponse = components['schemas']['SubscriberResponse'];

const NewsletterSubscriberList: React.FC = () => {
  const { t } = useTranslation('newsletterSubscribers');
  const { filters, pagination, setSort, setPage, setLimit } = useNewsletterSubscriberStore();

  const { data, isLoading, isError, refetch } = useNewsletterSubscriberList({
    filters,
    pagination,
  });

  // Dialog state
  const [unsubscribeTarget, setUnsubscribeTarget] = useState<SubscriberResponse | null>(null);
  const [resubscribeTarget, setResubscribeTarget] = useState<SubscriberResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<SubscriberResponse | null>(null);

  // Snackbar state
  const [snackbar, setSnackbar] = useState<{
    open: boolean;
    message: string;
    severity: 'success' | 'error';
  }>({ open: false, message: '', severity: 'success' });

  const handleAction = useCallback(
    (action: 'unsubscribe' | 'resubscribe' | 'delete', subscriber: SubscriberResponse) => {
      switch (action) {
        case 'unsubscribe':
          setUnsubscribeTarget(subscriber);
          break;
        case 'resubscribe':
          setResubscribeTarget(subscriber);
          break;
        case 'delete':
          setDeleteTarget(subscriber);
          break;
      }
    },
    []
  );

  const handleSortChange = useCallback(
    (field: string, dir: 'asc' | 'desc') => {
      setSort(field, dir);
    },
    [setSort]
  );

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
        <BATbernLoader />
      </Box>
    );
  }

  if (isError) {
    return (
      <Alert
        severity="error"
        action={
          <Button color="inherit" size="small" onClick={() => refetch()}>
            {t('error.retry', { defaultValue: 'Retry' })}
          </Button>
        }
      >
        {t('error.loadFailed')}
      </Alert>
    );
  }

  const subscribers = data?.data ?? [];
  const paginationMeta = data?.pagination;

  return (
    <>
      <NewsletterSubscriberFilters />

      <NewsletterSubscriberTable
        subscribers={subscribers}
        sortBy={filters.sortBy ?? 'subscribedAt'}
        sortDir={filters.sortDir ?? 'desc'}
        onSortChange={handleSortChange}
        onAction={handleAction}
      />

      {paginationMeta && (
        <UserPagination
          page={paginationMeta.page ?? 1}
          totalPages={paginationMeta.totalPages ?? 1}
          limit={pagination.limit}
          onPageChange={setPage}
          onLimitChange={setLimit}
        />
      )}

      <UnsubscribeDialog
        open={Boolean(unsubscribeTarget)}
        subscriber={unsubscribeTarget}
        onClose={() => setUnsubscribeTarget(null)}
        onSuccess={() =>
          setSnackbar({
            open: true,
            message: t('toast.unsubscribeSuccess', { email: unsubscribeTarget?.email }),
            severity: 'success',
          })
        }
      />

      <ResubscribeDialog
        open={Boolean(resubscribeTarget)}
        subscriber={resubscribeTarget}
        onClose={() => setResubscribeTarget(null)}
        onSuccess={() =>
          setSnackbar({
            open: true,
            message: t('toast.resubscribeSuccess', { email: resubscribeTarget?.email }),
            severity: 'success',
          })
        }
      />

      <DeleteSubscriberDialog
        open={Boolean(deleteTarget)}
        subscriber={deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onSuccess={() =>
          setSnackbar({
            open: true,
            message: t('toast.deleteSuccess', { email: deleteTarget?.email }),
            severity: 'success',
          })
        }
      />

      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={() => setSnackbar((s) => ({ ...s, open: false }))}
      >
        <Alert
          onClose={() => setSnackbar((s) => ({ ...s, open: false }))}
          severity={snackbar.severity}
          sx={{ width: '100%' }}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </>
  );
};

export default NewsletterSubscriberList;
