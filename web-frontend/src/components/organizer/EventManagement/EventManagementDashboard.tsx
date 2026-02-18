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

import React, { useState, useEffect } from 'react';
import { Paper, Typography, Box, Stack, Alert, Container } from '@mui/material';
import Grid from '@mui/material/Grid';
import { useTranslation } from 'react-i18next';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useEvents } from '@/hooks/useEvents';
import { useNotifications } from '@/hooks/useNotifications';
import { useAuth } from '@/hooks/useAuth';
import { useEventStore } from '@/stores/eventStore';
import { useNotificationWebSocket } from '@/hooks/useNotificationWebSocket';
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
  const queryClient = useQueryClient();
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

  // Fetch notifications for current organizer (unread notifications)
  const {
    data: notificationsData,
    isLoading: isLoadingNotifications,
    refetch: refetchNotifications,
  } = useNotifications(
    {
      username: user?.username || '',
      status: 'UNREAD', // UNREAD status = in-app unread notifications
    },
    { page: 1, limit: 10 }
  );

  // Real-time WebSocket notifications (Story BAT-7)
  const { onNotification, isConnected } = useNotificationWebSocket(user?.username);

  // Subscribe to real-time notification updates
  useEffect(() => {
    if (!isConnected) {
      return;
    }

    const unsubscribe = onNotification((notification) => {
      console.log('Real-time notification received:', notification);
      // Invalidate notifications query to refetch with new notification
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    });

    return unsubscribe;
  }, [isConnected, onNotification, queryClient]);

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
  if (isLoadingEvents && isLoadingNotifications) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <BATbernLoader size={48} />
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
    <Box sx={{ flexGrow: 1, p: 3 }} data-testid="dashboard-container">
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
              <Paper sx={{ p: 3 }} data-testid="critical-tasks-section">
                <TaskWidget organizerUsername={user?.username || ''} />
              </Paper>

              {/* Notifications Feed - EventBridge Integration */}
              <Paper sx={{ p: 3 }} data-testid="team-activity-section">
                <TeamActivityFeed
                  notifications={notificationsData?.data || []}
                  totalNotifications={notificationsData?.pagination?.totalItems}
                  isLoading={isLoadingNotifications}
                  onReload={() => refetchNotifications()}
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
            onSuccess={(updatedEvent) => {
              closeEditModal();
              // Redirect to event detail page if eventCode changed (e.g., eventNumber updated)
              if (updatedEvent && updatedEvent.eventCode !== selectedEventCode) {
                navigate(`/organizer/events/${updatedEvent.eventCode}`);
              }
              // Otherwise, refetch events to show updated data in list
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
