/**
 * Speaker Brainstorming Panel Component (Story 5.2 - AC9-13)
 *
 * Speaker pool management during event planning:
 * - Add potential speakers (name, company, expertise)
 * - Assign speakers to organizers for outreach
 * - Add notes for each speaker
 * - View speaker pool list
 * - Initial status: IDENTIFIED
 */

import React, { useState } from 'react';
import {
  Paper,
  Typography,
  Box,
  TextField,
  Button,
  List,
  ListItem,
  ListItemText,
  Chip,
  Alert,
  CircularProgress,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Divider,
} from '@mui/material';
import { Add as AddIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSpeakerPool, useAddSpeakerToPool } from '@/hooks/useSpeakerPool';
import type { AddSpeakerToPoolRequest } from '@/types/speakerPool.types';

export interface SpeakerBrainstormingPanelProps {
  eventCode: string;
  organizers?: Array<{ id: string; name: string }>; // List of available organizers
  onContinue?: () => void; // Callback when workflow should continue
  showPoolList?: boolean; // Whether to show the speaker pool list (default: true)
  showHeader?: boolean; // Whether to show Paper wrapper and header (default: true)
}

export const SpeakerBrainstormingPanel: React.FC<SpeakerBrainstormingPanelProps> = ({
  eventCode,
  organizers = [],
  onContinue,
  showPoolList = true,
  showHeader = true,
}) => {
  const { t } = useTranslation('organizer');

  // Form state
  const [speakerName, setSpeakerName] = useState('');
  const [company, setCompany] = useState('');
  const [expertise, setExpertise] = useState('');
  const [assignedOrganizerId, setAssignedOrganizerId] = useState<string>('');
  const [notes, setNotes] = useState('');

  // Fetch speaker pool
  const { data: speakerPool, isLoading, isError } = useSpeakerPool(eventCode);

  // Mutation for adding speaker
  const addSpeakerMutation = useAddSpeakerToPool();

  const handleAddSpeaker = () => {
    if (!speakerName.trim()) {
      return;
    }

    console.log('Form state:', {
      speakerName,
      company,
      expertise,
      assignedOrganizerId,
      notes,
    });

    const request: AddSpeakerToPoolRequest = {
      speakerName: speakerName.trim(),
      company: company.trim() || undefined,
      expertise: expertise.trim() || undefined,
      assignedOrganizerId: assignedOrganizerId || undefined,
      notes: notes.trim() || undefined,
    };

    console.log('Request payload:', request);

    addSpeakerMutation.mutate(
      { eventCode, request },
      {
        onSuccess: () => {
          // Clear form on success
          setSpeakerName('');
          setCompany('');
          setExpertise('');
          setAssignedOrganizerId('');
          setNotes('');
        },
      }
    );
  };

  const formContent = (
    <>
      {/* Add Speaker Form */}
      <Box
        component="form"
        sx={{ display: 'flex', flexDirection: 'column', gap: 2, mb: 3 }}
        onSubmit={(e) => {
          e.preventDefault();
          handleAddSpeaker();
        }}
      >
        <TextField
          required
          fullWidth
          size="small"
          label={t('speakerBrainstorm.form.speakerName', 'Speaker Name')}
          value={speakerName}
          onChange={(e) => setSpeakerName(e.target.value)}
          disabled={addSpeakerMutation.isPending}
        />

        <TextField
          fullWidth
          size="small"
          label={t('speakerBrainstorm.form.company', 'Company')}
          value={company}
          onChange={(e) => setCompany(e.target.value)}
          disabled={addSpeakerMutation.isPending}
        />

        <TextField
          fullWidth
          size="small"
          label={t('speakerBrainstorm.form.expertise', 'Expertise')}
          value={expertise}
          onChange={(e) => setExpertise(e.target.value)}
          disabled={addSpeakerMutation.isPending}
        />

        <FormControl fullWidth size="small">
          <InputLabel>
            {t('speakerBrainstorm.form.assignOrganizer', 'Assign to Organizer (Optional)')}
          </InputLabel>
          <Select
            value={assignedOrganizerId}
            onChange={(e) => setAssignedOrganizerId(e.target.value)}
            label={t('speakerBrainstorm.form.assignOrganizer', 'Assign to Organizer (Optional)')}
            disabled={addSpeakerMutation.isPending || organizers.length === 0}
          >
            <MenuItem value="">
              <em>{t('speakerBrainstorm.form.unassigned', 'Unassigned')}</em>
            </MenuItem>
            {organizers.map((org) => (
              <MenuItem key={org.id} value={org.id}>
                {org.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <TextField
          fullWidth
          multiline
          rows={2}
          size="small"
          label={t('speakerBrainstorm.form.notes', 'Notes (Optional)')}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          disabled={addSpeakerMutation.isPending}
        />

        <Button
          type="submit"
          variant="contained"
          color="primary"
          startIcon={<AddIcon />}
          disabled={!speakerName.trim() || addSpeakerMutation.isPending}
        >
          {addSpeakerMutation.isPending
            ? t('speakerBrainstorm.form.adding', 'Adding...')
            : t('speakerBrainstorm.form.addButton', 'Add to Pool')}
        </Button>

        {addSpeakerMutation.isError && (
          <Alert severity="error">
            {t('speakerBrainstorm.form.error', 'Failed to add speaker')}:{' '}
            {addSpeakerMutation.error?.message}
          </Alert>
        )}
      </Box>

      {showPoolList && (
        <>
          <Divider sx={{ my: 2 }} />

          {/* Speaker Pool List */}
          <Typography variant="subtitle1" gutterBottom>
            {t('speakerBrainstorm.pool.title', 'Speaker Pool')} ({speakerPool?.length || 0})
          </Typography>

          {isLoading && (
            <Box display="flex" justifyContent="center" alignItems="center" minHeight={100}>
              <CircularProgress />
            </Box>
          )}

          {isError && (
            <Alert severity="error">
              {t('speakerBrainstorm.pool.error', 'Failed to load speaker pool')}
            </Alert>
          )}

          {speakerPool && speakerPool.length === 0 && (
            <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 2 }}>
              {t('speakerBrainstorm.pool.empty', 'No speakers in pool yet. Add some above!')}
            </Typography>
          )}

          {speakerPool && speakerPool.length > 0 && (
            <List>
              {speakerPool.map((speaker) => (
                <ListItem
                  key={speaker.id}
                  sx={{
                    border: 1,
                    borderColor: 'divider',
                    borderRadius: 1,
                    mb: 1,
                  }}
                >
                  <ListItemText
                    primary={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Typography variant="subtitle2">{speaker.speakerName}</Typography>
                        {speaker.status && (
                          <Chip
                            label={speaker.status}
                            size="small"
                            color={speaker.status === 'identified' ? 'default' : 'primary'}
                          />
                        )}
                      </Box>
                    }
                    secondary={
                      <>
                        {speaker.company && (
                          <Typography component="span" variant="body2" color="text.secondary">
                            {speaker.company}
                          </Typography>
                        )}
                        {speaker.expertise && (
                          <>
                            <br />
                            <Typography component="span" variant="body2" color="text.secondary">
                              {t('speakerBrainstorm.pool.expertise', 'Expertise')}:{' '}
                              {speaker.expertise}
                            </Typography>
                          </>
                        )}
                        {speaker.assignedOrganizerId && (
                          <>
                            <br />
                            <Typography component="span" variant="body2" color="text.secondary">
                              {t('speakerBrainstorm.pool.assigned', 'Assigned to')}:{' '}
                              {organizers.find((o) => o.id === speaker.assignedOrganizerId)?.name ||
                                speaker.assignedOrganizerId}
                            </Typography>
                          </>
                        )}
                        {speaker.notes && (
                          <>
                            <br />
                            <Typography component="span" variant="body2" color="text.secondary">
                              {t('speakerBrainstorm.pool.notes', 'Notes')}: {speaker.notes}
                            </Typography>
                          </>
                        )}
                      </>
                    }
                  />
                </ListItem>
              ))}
            </List>
          )}
        </>
      )}

      {/* Action Buttons */}
      {onContinue && (
        <Box sx={{ mt: 3, display: 'flex', gap: 2, justifyContent: 'flex-end' }}>
          <Button variant="outlined" onClick={() => onContinue()}>
            {t('speakerBrainstorm.actions.skipForNow', 'Skip for Now')}
          </Button>
          <Button
            variant="contained"
            onClick={() => onContinue()}
            disabled={!speakerPool || speakerPool.length === 0}
          >
            {t('speakerBrainstorm.actions.continue', 'Continue to Outreach')}
          </Button>
        </Box>
      )}
    </>
  );

  if (!showHeader) {
    return formContent;
  }

  return (
    <Paper sx={{ p: 3 }}>
      <Typography variant="h6" gutterBottom>
        {t('speakerBrainstorm.title', 'Speaker Brainstorming')}
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        {t(
          'speakerBrainstorm.subtitle',
          'Add potential speakers to the event pool and assign them to organizers for outreach'
        )}
      </Typography>
      {formContent}
    </Paper>
  );
};

export default SpeakerBrainstormingPanel;
