/**
 * Event Participant List Component (GREEN Phase)
 *
 * Main container component for viewing event participants.
 * Story 3.3: Event Participants Tab - Task 7 (GREEN Phase)
 *
 * Features:
 * - Participant list table with sorting
 * - Search and status filters
 * - Pagination
 * - Loading and error states
 */

import React from 'react';
import { Box, Typography, Alert, Button } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { useEventParticipantStore } from '../../../stores/eventParticipantStore';
import { useEventRegistrations } from '../../../hooks/useEventManagement/useEventRegistrations';
import EventParticipantFilters from './EventParticipantFilters';
import EventParticipantTable from './EventParticipantTable';
import UserPagination from '../UserManagement/UserPagination';

interface EventParticipantListProps {
  eventCode: string;
}

const EventParticipantList: React.FC<EventParticipantListProps> = ({ eventCode }) => {
  const { t } = useTranslation('events');
  const { filters, pagination, searchQuery, setPage, setLimit } = useEventParticipantStore();

  // Fetch participant registrations with React Query
  const { data, isLoading, error, refetch } = useEventRegistrations({
    eventCode,
    filters,
    pagination,
    search: searchQuery,
    enabled: true,
  });

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

  return (
    <Box>
      {/* Filters Panel */}
      <EventParticipantFilters />

      {/* Participant Table */}
      <EventParticipantTable participants={participants} isLoading={false} />

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
    </Box>
  );
};

export default EventParticipantList;
