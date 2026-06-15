/**
 * Event Participant List Component (GREEN Phase)
 *
 * Main container component for viewing event participants.
 * Story 3.3: Event Participants Tab - Task 7 (GREEN Phase)
 *
 * Features:
 * - Participant list table with sorting
 * - Search and status filters
 * - Server-side pagination with a "Showing X–Y of Z" counter (Epic 14 FR D.4 / NFR3)
 * - Waitlist-as-filter: the "Waitlisted" filter renders queue-ordered rows with an
 *   inline Promote action gated behind a confirm dialog (Epic 14 FR28 / NFR1)
 * - Loading and error states
 */

import React, { useState } from 'react';
import {
  Box,
  Typography,
  Alert,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Snackbar,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { useEventParticipantStore } from '../../../stores/eventParticipantStore';
import { useEventRegistrations } from '../../../hooks/useEventManagement/useEventRegistrations';
import { promoteFromWaitlist } from '@/services/api/eventRegistrationService';
import type { EventParticipant } from '@/types/eventParticipant.types';
import EventParticipantFilters from './EventParticipantFilters';
import EventParticipantTable from './EventParticipantTable';
import UserPagination from '../UserManagement/UserPagination';

interface EventParticipantListProps {
  eventCode: string;
}

const EventParticipantList: React.FC<EventParticipantListProps> = ({ eventCode }) => {
  const { t } = useTranslation('events');
  const queryClient = useQueryClient();
  const { filters, pagination, searchQuery, setPage, setLimit } = useEventParticipantStore();

  // Fetch participant registrations with React Query
  const { data, isLoading, error, refetch } = useEventRegistrations({
    eventCode,
    filters,
    pagination,
    search: searchQuery,
    enabled: true,
  });

  // Waitlist-as-filter mode (FR28): the "Waitlisted" filter is selected.
  const waitlistMode = filters.status?.length === 1 && filters.status[0] === 'WAITLIST';

  // Promote-with-confirm state (NFR1 — promotion sends a confirmation email).
  const [pendingPromote, setPendingPromote] = useState<EventParticipant | null>(null);
  const [promotingCode, setPromotingCode] = useState<string | null>(null);
  const [snackbar, setSnackbar] = useState<{
    open: boolean;
    severity: 'success' | 'error';
    message: string;
  }>({ open: false, severity: 'success', message: '' });

  const handlePromoteConfirm = async (): Promise<void> => {
    // Guard against a double dispatch in the same tick (before `disabled` applies).
    if (!pendingPromote || promotingCode) {
      return;
    }
    const code = pendingPromote.registrationCode;
    // If this is the only row on a page beyond the first, that page will be empty
    // after the promote — step back so we don't strand the user on a blank page.
    if ((data?.data.length ?? 0) === 1 && pagination.page > 1) {
      setPage(pagination.page - 1);
    }
    setPromotingCode(code);
    try {
      await promoteFromWaitlist(eventCode, code);
      // Invalidate waitlist + main lists + event counts so the queue re-numbers.
      queryClient.invalidateQueries({ queryKey: ['event-registrations', eventCode] });
      queryClient.invalidateQueries({ queryKey: ['events', eventCode] });
      queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
      setSnackbar({
        open: true,
        severity: 'success',
        message: t('eventPage.participantsTab.waitlistPromoteSuccess'),
      });
    } catch {
      setSnackbar({
        open: true,
        severity: 'error',
        message: t('eventPage.participantsTab.waitlistPromoteError'),
      });
    } finally {
      setPromotingCode(null);
      setPendingPromote(null);
    }
  };

  // Loading state
  if (isLoading) {
    return (
      <Box
        display="flex"
        flexDirection="column"
        alignItems="center"
        justifyContent="center"
        minHeight="400px"
      >
        <BATbernLoader size={96} />
        <Typography variant="body1" sx={{ mt: 2 }}>
          {t('eventPage.participantList.loading')}
        </Typography>
      </Box>
    );
  }

  // Error state
  if (error) {
    return (
      <Box sx={{ mt: 4 }}>
        <Alert severity="error" sx={{ mb: 2 }}>
          {t('eventPage.participantList.error.loadFailed')}
        </Alert>
        <Button variant="contained" onClick={() => refetch()}>
          {t('common:actions.retry')}
        </Button>
      </Box>
    );
  }

  const participants = data?.data || [];
  const totalPages = data?.pagination.totalPages || 0;
  const totalItems = data?.pagination.totalItems ?? 0;
  // Derive the offset from the FETCHED page (not the store) so the counter always
  // matches the rows actually on screen — avoids a transient over-count during a
  // page change (React Query keeps the previous page's data until the refetch
  // resolves). `shown === 0` (e.g. a stale page beyond range) shows the empty copy.
  const shownPage = data?.pagination.page ?? pagination.page;
  const shownLimit = data?.pagination.limit ?? pagination.limit;
  const pageOffset = (shownPage - 1) * shownLimit;
  const shown = participants.length;
  const from = shown === 0 ? 0 : pageOffset + 1;
  const to = shown === 0 ? 0 : pageOffset + shown;

  return (
    <Box>
      {/* Filters Panel */}
      <EventParticipantFilters />

      {/* Result counter (Epic 14 FR D.4) — handles empty / partial / last page. */}
      <Typography
        variant="body2"
        color="text.secondary"
        sx={{ mb: 1 }}
        data-testid="participants-result-count"
      >
        {totalItems === 0 || shown === 0
          ? t('eventPage.participantList.showingEmpty')
          : t('eventPage.participantList.showing', { from, to, total: totalItems })}
      </Typography>

      {/* Participant Table */}
      <EventParticipantTable
        participants={participants}
        isLoading={false}
        waitlistMode={waitlistMode}
        pageOffset={pageOffset}
        onPromote={waitlistMode ? setPendingPromote : undefined}
        promotingCode={promotingCode}
      />

      {/* Pagination */}
      {data && totalPages > 0 && (
        <UserPagination
          page={pagination.page}
          totalPages={totalPages}
          limit={pagination.limit}
          onPageChange={(newPage) => setPage(newPage)}
          onLimitChange={(newLimit) => setLimit(newLimit)}
        />
      )}

      {/* Promote-from-waitlist confirm dialog (NFR1 — consequential action) */}
      <Dialog
        open={!!pendingPromote}
        onClose={() => setPendingPromote(null)}
        data-testid="waitlist-promote-confirm"
      >
        <DialogTitle>{t('eventPage.participantsTab.promoteConfirmTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('eventPage.participantsTab.promoteConfirmMessage', {
              name: pendingPromote ? `${pendingPromote.firstName} ${pendingPromote.lastName}` : '',
            })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPendingPromote(null)}>{t('common:actions.cancel')}</Button>
          <Button
            variant="contained"
            color="success"
            disabled={!!promotingCode}
            onClick={handlePromoteConfirm}
            data-testid="waitlist-promote-confirm-button"
          >
            {t('eventPage.participantsTab.waitlistPromote')}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={() => setSnackbar((s) => ({ ...s, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          severity={snackbar.severity}
          onClose={() => setSnackbar((s) => ({ ...s, open: false }))}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default EventParticipantList;
