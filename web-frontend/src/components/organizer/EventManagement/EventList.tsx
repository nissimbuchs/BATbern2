/**
 * EventList Component
 *
 * Story 2.5.3 - Task 8b (GREEN Phase)
 * AC: 1 (Event Dashboard Display), 2 (Event List & Filters)
 * Wireframe: docs/wireframes/story-1.16-event-management-dashboard.md v1.0
 *
 * Displays list of event cards in responsive grid layout
 */

import React from 'react';
import { Typography, Box, Skeleton } from '@mui/material';
import Grid from '@mui/material/Grid';
import { useTranslation } from 'react-i18next';
import { EventCard } from './EventCard';
import type { Event } from '@/types/event.types';

interface EventListProps {
  events: Event[];
  isLoading?: boolean;
  onEventEdit?: (eventCode: string) => void;
  onEventClick?: (eventCode: string) => void;
}

export const EventList: React.FC<EventListProps> = ({
  events,
  isLoading = false,
  onEventEdit,
  onEventClick,
}) => {
  const { t } = useTranslation('events');

  // Loading state with skeletons
  if (isLoading) {
    return (
      <Grid container spacing={3} data-testid="event-list-container">
        {[1, 2, 3].map((index) => (
          <Grid size={{ xs: 12, sm: 6, md: 4 }} key={index}>
            <Skeleton
              variant="rectangular"
              height={280}
              data-testid={`skeleton-card-${index}`}
              sx={{ borderRadius: 1 }}
            />
          </Grid>
        ))}
      </Grid>
    );
  }

  // Empty state
  if (events.length === 0) {
    return (
      <Box
        textAlign="center"
        py={8}
        aria-label={t('accessibility.noEventsFound')}
        data-testid="event-list-empty"
      >
        <Typography variant="h6" color="text.secondary" gutterBottom>
          {t('dashboard.noEventsFound')}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t('dashboard.tryAdjustingFilters')}
        </Typography>
      </Box>
    );
  }

  // Event grid
  return (
    <Box>
      {/* Event cards grid */}
      <Grid
        container
        spacing={3}
        data-testid="event-list-container"
        aria-label={t('accessibility.eventList')}
      >
        {events.map((event) => (
          <Grid size={{ xs: 12, sm: 6, md: 4 }} key={event.eventCode}>
            <Box data-testid={`event-card-${event.eventCode}`}>
              <EventCard event={event} onEdit={onEventEdit} onCardClick={onEventClick} />
            </Box>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
};
