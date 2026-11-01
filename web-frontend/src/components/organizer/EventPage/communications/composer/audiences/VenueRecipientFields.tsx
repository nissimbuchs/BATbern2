/**
 * Venue & catering recipients and notes (extracted from VenueCoordinationComposer, Story 16.8b).
 *
 * The contacts are admin-managed singletons (app_setting `venue.coordination.config`). Used by the
 * old venue composer until 16.8c removes it, and by the shared composer's venue audience.
 */
import React from 'react';
import {
  Alert,
  Box,
  Checkbox,
  FormControlLabel,
  Link,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router-dom';
import type { VenueCoordinationConfig } from '@/components/organizer/Admin/VenueCateringContactsTab';
import type { VenueRecipientRole } from './useVenueCoordinationConfig';

/** Warning with a link to Administration → Venue & Catering. */
export const VenueNotConfiguredAlert: React.FC = () => {
  const { t } = useTranslation(['events', 'common']);
  return (
    <Alert severity="warning" sx={{ mb: 2 }}>
      {t(
        'eventPage.venueCoordination.notConfigured',
        'Venue or catering contacts are not configured.'
      )}{' '}
      <Link component={RouterLink} to="/organizer/admin?tab=8">
        {t('eventPage.venueCoordination.openAdmin', 'Open Administration → Venue & Catering')}
      </Link>
    </Alert>
  );
};

interface VenueRecipientFieldsProps {
  config: VenueCoordinationConfig | null;
  venueConfigured: boolean;
  cateringConfigured: boolean;
  roles: VenueRecipientRole[];
  onRolesChange: (roles: VenueRecipientRole[]) => void;
  notes: string;
  onNotesChange: (notes: string) => void;
}

/** Role checkboxes (first checked role is To, the other Cc) and the free-form notes. */
export const VenueRecipientFields: React.FC<VenueRecipientFieldsProps> = ({
  config,
  venueConfigured,
  cateringConfigured,
  roles,
  onRolesChange,
  notes,
  onNotesChange,
}) => {
  const { t } = useTranslation(['events', 'common']);
  const toggle = (role: VenueRecipientRole, checked: boolean) => {
    const next = new Set(roles);
    if (checked) next.add(role);
    else next.delete(role);
    // Keep the order the backend expects: venue first, catering second.
    onRolesChange((['VENUE', 'CATERING'] as const).filter((r) => next.has(r)));
  };
  const label = (role: VenueRecipientRole) => {
    const name =
      role === 'VENUE'
        ? t('eventPage.venueCoordination.venueRole', 'Venue')
        : t('eventPage.venueCoordination.cateringRole', 'Catering');
    const contact = role === 'VENUE' ? config?.venue : config?.catering;
    const configured = role === 'VENUE' ? venueConfigured : cateringConfigured;
    return configured && contact ? `${name} — ${contact.name} <${contact.email}>` : name;
  };

  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="subtitle2" sx={{ mb: 1 }}>
          {t('eventPage.venueCoordination.recipients', 'Recipients')}
        </Typography>
        <Stack direction="column" spacing={1}>
          {(['VENUE', 'CATERING'] as const).map((role) => (
            <FormControlLabel
              key={role}
              control={
                <Checkbox
                  checked={roles.includes(role)}
                  onChange={(e) => toggle(role, e.target.checked)}
                  disabled={role === 'VENUE' ? !venueConfigured : !cateringConfigured}
                  data-testid={
                    role === 'VENUE' ? 'venue-coord-send-venue' : 'venue-coord-send-catering'
                  }
                />
              }
              label={label(role)}
              sx={{ '& .MuiFormControlLabel-label': { wordBreak: 'break-word' } }}
            />
          ))}
        </Stack>
      </Box>
      <TextField
        label={t('eventPage.venueCoordination.notesLabel', 'Notes (optional)')}
        placeholder={t(
          'eventPage.venueCoordination.notesPlaceholder',
          'e.g. Reserve a lunch table for 8 at 12:30 in the back room.'
        )}
        value={notes}
        onChange={(e) => onNotesChange(e.target.value)}
        multiline
        minRows={3}
        fullWidth
        inputProps={{ maxLength: 4000 }}
      />
    </Stack>
  );
};
