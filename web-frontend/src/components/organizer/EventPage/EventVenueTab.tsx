/**
 * EventVenueTab Component
 *
 * Hosts the venue/catering coordination composer for the event. The legacy mock
 * "Venue", "Catering", and VenueLogistics cards were removed once the composer
 * landed — venue & address are already shown in the public hero + the Settings
 * tab, and the dietary / booking-status mocks were never backed by real data.
 */

import React from 'react';
import { Stack } from '@mui/material';
import { VenueCoordinationComposer } from './VenueCoordinationComposer';
import type { Event, EventDetailUI } from '@/types/event.types';

interface EventVenueTabProps {
  event: Event | EventDetailUI;
}

export const EventVenueTab: React.FC<EventVenueTabProps> = ({ event }) => {
  return (
    <Stack spacing={3}>
      <VenueCoordinationComposer eventCode={event.eventCode} />
    </Stack>
  );
};

export default EventVenueTab;
