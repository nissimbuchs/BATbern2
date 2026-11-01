import React from 'react';
import { Box } from '@mui/material';
import type { ExtraFieldsProps } from '../types';
import { useVenueCoordinationConfig } from './useVenueCoordinationConfig';
import { VenueNotConfiguredAlert, VenueRecipientFields } from './VenueRecipientFields';

/** Venue: the role checkboxes and notes, with the not-configured warning. */
export const VenueFields: React.FC<ExtraFieldsProps> = ({ state, onChange }) => {
  const venue = useVenueCoordinationConfig();
  if (venue.isLoading) return null;
  return (
    <Box>
      {!venue.fullyConfigured && <VenueNotConfiguredAlert />}
      <VenueRecipientFields
        config={venue.config}
        venueConfigured={venue.venueConfigured}
        cateringConfigured={venue.cateringConfigured}
        roles={state.venueRecipients}
        onRolesChange={(venueRecipients) => onChange({ venueRecipients })}
        notes={state.venueNotes}
        onNotesChange={(venueNotes) => onChange({ venueNotes })}
      />
    </Box>
  );
};
