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
import { Paper, Typography, Button, Stack, Box, CircularProgress, Alert } from '@mui/material';
import Grid from '@mui/material/Grid';
import { Add as AddIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useEvents, useCriticalTasks, useTeamActivity } from '@/hooks/useEvents';
import { useAuthStore } from '@/stores/authStore';
import { useEventStore } from '@/stores/eventStore';
import { EventList } from './EventList';
import { EventSearch } from './EventSearch';
import { CriticalTasksList } from './CriticalTasksList';
import { TeamActivityFeed } from './TeamActivityFeed';
import { EventForm } from './EventForm';
import type { EventFilters } from '@/types/event.types';

export const EventManagementDashboard: React.FC = () => {
  const { t } = useTranslation('events');
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const {
    filters,
    setFilters,
    isCreateModalOpen,
    openCreateModal,
    closeCreateModal,
    isEditModalOpen,
    selectedEventCode,
    closeEditModal,
  } = useEventStore();

  const [pagination] = useState({ page: 1, limit: 20 });

  // Fetch data with React Query hooks
  const {
    data: eventsData,
    isLoading: isLoadingEvents,
    isError: isErrorEvents,
    error: eventsError,
  } = useEvents(pagination, filters);

  const { data: criticalTasksData, isLoading: isLoadingTasks } = useCriticalTasks(user?.username);

  const {
    data: teamActivityData,
    isLoading: isLoadingActivity,
    refetch: refetchActivity,
  } = useTeamActivity(user?.username);

  const handleFiltersChange = (newFilters: EventFilters) => {
    setFilters(newFilters);
  };

  const handleEventEdit = (eventCode: string) => {
    navigate(`/organizer/events/${eventCode}/edit`);
  };

  const handleEventClick = (eventCode: string) => {
    navigate(`/organizer/events/${eventCode}`);
  };

  const handleNewEvent = () => {
    openCreateModal();
  };

  const handleTaskAction = (taskId: string, actionId: string) => {
    console.log('Task action:', taskId, actionId);
    // TODO: Implement task action handling
  };

  // Loading state
  if (isLoadingEvents && isLoadingTasks && isLoadingActivity) {
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
    <Box data-testid="dashboard-container">
      {/* Header */}
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" component="h1">
          {t('dashboard.title')}
        </Typography>

        {/* Quick Actions */}
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={handleNewEvent}
          aria-label="Create new event"
        >
          {t('dashboard.actions.newEvent')}
        </Button>
      </Stack>

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
                {eventsData?.data?.length || 0}{' '}
                {t('dashboard.eventCount', { count: eventsData?.data?.length || 0 })}
              </Typography>
            </Stack>
            <EventList
              events={eventsData?.data || []}
              isLoading={isLoadingEvents}
              onEventEdit={handleEventEdit}
              onEventClick={handleEventClick}
            />
          </Paper>
        </Grid>

        {/* Sidebar (4/12) */}
        <Grid size={{ xs: 12, md: 4 }}>
          <Stack spacing={3}>
            {/* Critical Tasks */}
            <Paper sx={{ p: 3 }}>
              <CriticalTasksList
                tasks={criticalTasksData?.data || []}
                isLoading={isLoadingTasks}
                onAction={handleTaskAction}
              />
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
    </Box>
  );
};
