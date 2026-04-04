import React, { useState } from 'react';
import { Box, Typography, CircularProgress, Snackbar, Alert } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { OrganizerSelect } from '@/components/shared/OrganizerSelect/OrganizerSelect';
import { usePatchSpeakerPool } from '@/hooks/useSpeakerPool';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

interface AssignedOrganizerFieldProps {
  speaker: SpeakerPoolEntry;
  eventCode: string;
}

export const AssignedOrganizerField: React.FC<AssignedOrganizerFieldProps> = ({
  speaker,
  eventCode,
}) => {
  const { t } = useTranslation('organizer');
  const patchMutation = usePatchSpeakerPool();

  const [snackbar, setSnackbar] = useState<{
    open: boolean;
    message: string;
    severity: 'success' | 'error';
  }>({ open: false, message: '', severity: 'success' });

  const handleChange = (organizerId: string) => {
    patchMutation.mutate(
      {
        eventCode,
        speakerId: speaker.id,
        request: { assignedOrganizerId: organizerId || undefined },
      },
      {
        onSuccess: () => {
          setSnackbar({
            open: true,
            message: t('speakers.organizerUpdated', 'Organizer updated'),
            severity: 'success',
          });
        },
        onError: () => {
          setSnackbar({
            open: true,
            message: t('speakers.organizerUpdateFailed', 'Failed to update organizer'),
            severity: 'error',
          });
        },
      }
    );
  };

  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        {t('speakerBrainstorm.form.assignOrganizer')}
      </Typography>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <OrganizerSelect
          value={speaker.assignedOrganizerId ?? ''}
          onChange={handleChange}
          includeUnassigned={true}
          disabled={patchMutation.isPending}
          size="small"
          fullWidth
        />
        {patchMutation.isPending && <CircularProgress size={20} />}
      </Box>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={() => setSnackbar((s) => ({ ...s, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          onClose={() => setSnackbar((s) => ({ ...s, open: false }))}
          severity={snackbar.severity}
          sx={{ width: '100%' }}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};
