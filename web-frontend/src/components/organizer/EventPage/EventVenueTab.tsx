/**
 * EventVenueTab Component (Story 5.6)
 *
 * Venue & Logistics tab showing venue details, catering, and schedule
 */

import React from 'react';
import { Box, Paper, Typography, Button, Stack, Divider, Chip, Alert } from '@mui/material';
import Grid from '@mui/material/Grid';
import {
  LocationOn as LocationIcon,
  LocalParking as ParkingIcon,
  Accessible as AccessibleIcon,
  Restaurant as CateringIcon,
  Edit as EditIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { VenueLogistics } from '@/components/organizer/EventManagement';
import { VenueCoordinationComposer } from './VenueCoordinationComposer';
import type { Event, EventDetailUI } from '@/types/event.types';

interface EventVenueTabProps {
  event: Event | EventDetailUI;
}

export const EventVenueTab: React.FC<EventVenueTabProps> = ({ event }) => {
  const { t } = useTranslation('events');
  // Convert Event to EventDetailUI, normalizing sessions: null -> undefined
  const eventUI: EventDetailUI = {
    ...event,
    sessions: event.sessions ?? undefined,
  } as EventDetailUI;

  return (
    <Stack spacing={3}>
      {/* Venue Information */}
      <Paper sx={{ p: 3 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">{t('eventPage.venue.venueTitle', 'Venue')}</Typography>
          <Button size="small" startIcon={<EditIcon />}>
            {t('eventPage.venue.changeVenue', 'Change Venue')}
          </Button>
        </Stack>
        <Divider sx={{ mb: 2 }} />

        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Stack direction="row" spacing={2} alignItems="flex-start">
              <LocationIcon color="primary" sx={{ mt: 0.5 }} />
              <Box>
                <Typography variant="h6">{event.venueName || '-'}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {event.venueAddress || t('eventPage.venue.noAddress', 'No address provided')}
                </Typography>
              </Box>
            </Stack>
          </Grid>

          <Grid size={{ xs: 12, md: 6 }}>
            <Stack spacing={1}>
              <Stack direction="row" spacing={2} alignItems="center">
                <Typography variant="body2">
                  {t('eventPage.venue.capacity', 'Capacity')}:{' '}
                  <strong>{event.venueCapacity || '-'}</strong>
                </Typography>
              </Stack>
              <Stack direction="row" spacing={1}>
                <Chip
                  icon={<ParkingIcon />}
                  label={t('eventPage.venue.parking', 'Parking')}
                  size="small"
                  color="success"
                  variant="outlined"
                />
                <Chip
                  icon={<AccessibleIcon />}
                  label={t('eventPage.venue.accessible', 'Accessible')}
                  size="small"
                  color="success"
                  variant="outlined"
                />
              </Stack>
            </Stack>
          </Grid>
        </Grid>

        {/* Booking Status */}
        {eventUI.booking ? (
          <Alert severity="success" sx={{ mt: 2 }}>
            ✓ {t('eventPage.venue.confirmed', 'Confirmed')} ({eventUI.booking.confirmationNumber})
          </Alert>
        ) : (
          <Alert severity="warning" sx={{ mt: 2 }}>
            {t('eventPage.venue.notBooked', 'Venue not yet booked')}
          </Alert>
        )}
      </Paper>

      {/* Catering */}
      <Paper sx={{ p: 3 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CateringIcon color="action" />
            <Typography variant="h6">{t('eventPage.venue.catering', 'Catering')}</Typography>
          </Stack>
          <Button size="small" startIcon={<EditIcon />}>
            {t('eventPage.venue.configureCatering', 'Configure')}
          </Button>
        </Stack>
        <Divider sx={{ mb: 2 }} />

        {eventUI.catering?.provider ? (
          <Stack spacing={1}>
            <Typography variant="body1">
              {t('eventPage.venue.provider', 'Provider')}: {eventUI.catering.provider}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {t('eventPage.venue.dietaryReq', 'Dietary Requirements')}:
            </Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap">
              {eventUI.catering.dietaryRequirements?.vegetarian && (
                <Chip
                  label={t('eventPage.venue.vegetarianCount', {
                    count: eventUI.catering.dietaryRequirements.vegetarian,
                  })}
                  size="small"
                />
              )}
              {eventUI.catering.dietaryRequirements?.vegan && (
                <Chip
                  label={t('eventPage.venue.veganCount', {
                    count: eventUI.catering.dietaryRequirements.vegan,
                  })}
                  size="small"
                />
              )}
              {eventUI.catering.dietaryRequirements?.glutenFree && (
                <Chip
                  label={t('eventPage.venue.glutenFreeCount', {
                    count: eventUI.catering.dietaryRequirements.glutenFree,
                  })}
                  size="small"
                />
              )}
            </Stack>
          </Stack>
        ) : (
          <Alert severity="info">
            {t('eventPage.venue.noCatering', 'Catering not yet configured')}
          </Alert>
        )}
      </Paper>

      {/* Venue & Catering email composer (real send path) */}
      <VenueCoordinationComposer eventCode={event.eventCode} />

      {/* Full Venue & Logistics Component */}
      <VenueLogistics event={eventUI} onUpdate={async () => {}} />
    </Stack>
  );
};

export default EventVenueTab;
