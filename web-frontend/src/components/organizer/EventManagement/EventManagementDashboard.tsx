/**
 * EventManagementDashboard Component
 *
 * Story 2.5.3 - Task 8b (GREEN Phase)
 * AC: 1 (Event Dashboard Display), 2 (Event List & Filters)
 * Wireframe: docs/wireframes/story-1.16-event-management-dashboard.md v1.0
 *
 * Main organizer dashboard with:
 * - Active events pipeline
 * - Critical tasks
 * - Team activity feed
 * - Quick actions sidebar
 */

import React, { useState } from 'react';
import { Paper, Typography, Box, Stack, CircularProgress, Alert, Container } from '@mui/material';
import Grid from '@mui/material/Grid';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useEvents, useTeamActivity } from '@/hooks/useEvents';
import { useAuth } from '@/hooks/useAuth';
import { useEventStore } from '@/stores/eventStore';
import { EventList } from './EventList';
import { EventSearch } from './EventSearch';
import { TaskWidget } from '../Tasks/TaskWidget';
import { TeamActivityFeed } from './TeamActivityFeed';
import { EventForm } from './EventForm';
import { EventBatchImportModal } from '@/components/shared/Event/EventBatchImportModal';
import { SessionBatchImportModal } from '@/components/shared/Session/SessionBatchImportModal';
import { EventPagination } from './EventPagination';
import { QuickActions } from './QuickActions';
import type { EventFilters } from '@/types/event.types';

export const EventManagementDashboard: React.FC = () => {
  const { t } = useTranslation('events');
  const navigate = useNavigate();
  const { user } = useAuth();
  const {
    filters,
    setFilters,
    pagination,
    setPage,
    setLimit,
    isCreateModalOpen,
    openCreateModal,
    closeCreateModal,
    isEditModalOpen,
    selectedEventCode,
    openEditModal,
    closeEditModal,
  } = useEventStore();

  const [isBatchImportOpen, setIsBatchImportOpen] = useState(false);
  const [isSessionBatchImportOpen, setIsSessionBatchImportOpen] = useState(false);

  // Fetch data with React Query hooks
  // Include registrations to get actual registration counts from database
  const {
    data: eventsData,
    isLoading: isLoadingEvents,
    isError: isErrorEvents,
    error: eventsError,
  } = useEvents(pagination, filters, { expand: ['registrations'] });

  const {
    data: teamActivityData,
    isLoading: isLoadingActivity,
    refetch: refetchActivity,
  } = useTeamActivity(user?.username);

  const handleFiltersChange = (newFilters: EventFilters) => {
    setFilters(newFilters);
  };

  const handleEventEdit = (eventCode: string) => {
    openEditModal(eventCode);
  };

  const handleEventClick = (eventCode: string) => {
    navigate(`/organizer/events/${eventCode}`);
  };

  const handleNewEvent = () => {
    openCreateModal();
  };

  // Loading state
  if (isLoadingEvents && isLoadingActivity) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  // Error state
  if (isErrorEvents) {
    return (
      <Box p={3}>
        <Alert severity="error">
          {t('dashboard.errors.failedToLoadEvents')}: {eventsError?.message}
        </Alert>
      </Box>
    );
  }

  return (
    <Box component="main" role="main" sx={{ flexGrow: 1, p: 3 }} data-testid="dashboard-container">
      <Container maxWidth="xl">
        {/* Header */}
        <Box mb={3}>
          <Typography variant="h4" component="h1">
            {t('dashboard.title')}
          </Typography>
        </Box>

        {/* Search and Filters */}
        <Paper sx={{ p: 3, mb: 3 }}>
          <EventSearch onFiltersChange={handleFiltersChange} filters={filters} />
        </Paper>

        {/* Main Content Grid */}
        <Grid container spacing={3}>
          {/* Active Events (Main Column - 8/12) */}
          <Grid size={{ xs: 12, md: 8 }}>
            <Paper sx={{ p: 3 }}>
              <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
                <Typography variant="h5">{t('dashboard.activeEvents')}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {eventsData?.data?.length || 0}/{eventsData?.pagination?.totalItems || 0}{' '}
                  {(eventsData?.pagination?.totalItems || 0) === 1
                    ? t('dashboard.eventWord')
                    : t('dashboard.eventWord_plural')}
                </Typography>
              </Stack>
              <EventList
                events={eventsData?.data || []}
                isLoading={isLoadingEvents}
                onEventEdit={handleEventEdit}
                onEventClick={handleEventClick}
              />

              {/* Pagination */}
              {eventsData?.pagination && (
                <EventPagination
                  page={eventsData.pagination.page}
                  totalPages={eventsData.pagination.totalPages}
                  limit={eventsData.pagination.limit}
                  onPageChange={(newPage) => setPage(newPage)}
                  onLimitChange={(newLimit) => setLimit(newLimit)}
                />
              )}
            </Paper>
          </Grid>

          {/* Sidebar (4/12) */}
          <Grid size={{ xs: 12, md: 4 }}>
            <Stack spacing={3}>
              {/* Quick Actions - Story 5.1 */}
              <QuickActions
                onNewEvent={handleNewEvent}
                onBatchImport={() => setIsBatchImportOpen(true)}
                onBatchImportSessions={() => setIsSessionBatchImportOpen(true)}
              />

              {/* Critical Tasks - Story 5.5 Phase 6 */}
              <Paper sx={{ p: 3 }}>
                <TaskWidget organizerUsername={user?.username || ''} />
              </Paper>

              {/* Team Activity Feed */}
              <Paper sx={{ p: 3 }}>
                <TeamActivityFeed
                  activities={teamActivityData?.data || []}
                  isLoading={isLoadingActivity}
                  onReload={() => refetchActivity()}
                  limit={5}
                />
              </Paper>
            </Stack>
          </Grid>
        </Grid>

        {/* Create Event Modal */}
        <EventForm
          open={isCreateModalOpen}
          mode="create"
          onClose={closeCreateModal}
          onSuccess={() => {
            closeCreateModal();
            // Refetch events after successful creation
            // The useEvents hook will automatically refetch due to query invalidation
          }}
        />

        {/* Edit Event Modal */}
        {isEditModalOpen && selectedEventCode && (
          <EventForm
            open={isEditModalOpen}
            mode="edit"
            event={eventsData?.data?.find((e) => e.eventCode === selectedEventCode)}
            onClose={closeEditModal}
            onSuccess={() => {
              closeEditModal();
              // Refetch events after successful update
              // The useEvents hook will automatically refetch due to query invalidation
            }}
          />
        )}

        {/* Batch Import Modal */}
        <EventBatchImportModal
          open={isBatchImportOpen}
          onClose={() => setIsBatchImportOpen(false)}
          onImportComplete={(result) => {
            console.log('Import complete:', result);
            // The useEvents hook will automatically refetch due to query invalidation
          }}
        />

        {/* Session Batch Import Modal */}
        <SessionBatchImportModal
          open={isSessionBatchImportOpen}
          onClose={() => setIsSessionBatchImportOpen(false)}
          onImportComplete={(result) => {
            console.log('Session import complete:', result);
            // The useEvents hook will automatically refetch due to query invalidation
          }}
        />
      </Container>
    </Box>
  );
};
