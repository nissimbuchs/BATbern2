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
  Divider,
  IconButton,
  Tooltip,
} from '@mui/material';
import { Add as AddIcon, Edit as EditIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSpeakerPool, useAddSpeakerToPool } from '@/hooks/useSpeakerPool';
import type { AddSpeakerToPoolRequest, SpeakerPoolEntry } from '@/types/speakerPool.types';
import { OrganizerSelect } from '@/components/shared/OrganizerSelect';
import { SpeakerEditModal } from './SpeakerEditModal';

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
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');

  // Edit modal state
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [selectedSpeaker, setSelectedSpeaker] = useState<SpeakerPoolEntry | null>(null);

  // Fetch speaker pool
  const { data: speakerPool, isLoading, isError } = useSpeakerPool(eventCode);

  // Mutation for adding speaker
  const addSpeakerMutation = useAddSpeakerToPool();

  // Handler for opening edit modal
  const handleSpeakerClick = (speaker: SpeakerPoolEntry) => {
    setSelectedSpeaker(speaker);
    setEditModalOpen(true);
  };

  const handleEditModalClose = () => {
    setEditModalOpen(false);
    setSelectedSpeaker(null);
  };

  const handleAddSpeaker = () => {
    if (!speakerName.trim() || !email.trim() || !phone.trim()) {
      return;
    }

    console.log('Form state:', {
      speakerName,
      company,
      expertise,
      assignedOrganizerId,
      notes,
      email,
      phone,
    });

    const request: AddSpeakerToPoolRequest = {
      speakerName: speakerName.trim(),
      company: company.trim() || undefined,
      expertise: expertise.trim() || undefined,
      assignedOrganizerId: assignedOrganizerId || undefined,
      notes: notes.trim() || undefined,
      email: email.trim(),
      phone: phone.trim(),
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
          setEmail('');
          setPhone('');
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
          inputProps={{ 'data-testid': 'speaker-name-field' }}
        />

        <TextField
          fullWidth
          size="small"
          label={t('speakerBrainstorm.form.company', 'Company')}
          value={company}
          onChange={(e) => setCompany(e.target.value)}
          disabled={addSpeakerMutation.isPending}
          inputProps={{ 'data-testid': 'speaker-company-field' }}
        />

        <TextField
          fullWidth
          size="small"
          label={t('speakerBrainstorm.form.expertise', 'Expertise')}
          value={expertise}
          onChange={(e) => setExpertise(e.target.value)}
          disabled={addSpeakerMutation.isPending}
          inputProps={{ 'data-testid': 'speaker-expertise-field' }}
        />

        <OrganizerSelect
          value={assignedOrganizerId}
          onChange={(organizerId) => setAssignedOrganizerId(organizerId)}
          organizers={organizers.length > 0 ? organizers : undefined}
          label={t('speakerBrainstorm.form.assignOrganizer', 'Assign to Organizer (Optional)')}
          size="small"
          fullWidth
          disabled={addSpeakerMutation.isPending}
          includeUnassigned={true}
          includeAllOption={false}
          data-testid="speaker-organizer-select"
        />

        <TextField
          fullWidth
          multiline
          rows={2}
          size="small"
          label={t('speakerBrainstorm.form.notes', 'Notes (Optional)')}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          disabled={addSpeakerMutation.isPending}
          inputProps={{ 'data-testid': 'speaker-notes-field' }}
        />

        <TextField
          required
          fullWidth
          size="small"
          type="email"
          label={t('speakerBrainstorm.form.email', 'Email')}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          disabled={addSpeakerMutation.isPending}
          inputProps={{ 'data-testid': 'speaker-email-field' }}
        />

        <TextField
          required
          fullWidth
          size="small"
          label={t('speakerBrainstorm.form.phone', 'Phone')}
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          disabled={addSpeakerMutation.isPending}
          inputProps={{ 'data-testid': 'speaker-phone-field' }}
        />

        <Button
          type="submit"
          variant="contained"
          color="primary"
          startIcon={<AddIcon />}
          disabled={
            !speakerName.trim() || !email.trim() || !phone.trim() || addSpeakerMutation.isPending
          }
          data-testid="add-to-pool-button"
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
                  secondaryAction={
                    <Tooltip title={t('speakerBrainstorm.pool.edit', 'Edit speaker')}>
                      <IconButton
                        edge="end"
                        aria-label="edit"
                        onClick={() => handleSpeakerClick(speaker)}
                        data-testid={`edit-speaker-${speaker.id}`}
                        sx={{
                          color: 'primary.main',
                          '&:hover': {
                            bgcolor: 'primary.light',
                            color: 'primary.contrastText',
                          },
                        }}
                      >
                        <EditIcon />
                      </IconButton>
                    </Tooltip>
                  }
                >
                  <ListItemText
                    primary={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Typography variant="subtitle2">{speaker.speakerName}</Typography>
                        {speaker.status && (
                          <Chip
                            label={speaker.status}
                            size="small"
                            color={speaker.status === 'IDENTIFIED' ? 'default' : 'primary'}
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
          <Button variant="outlined" onClick={() => onContinue()} data-testid="skip-for-now-button">
            {t('speakerBrainstorm.actions.skipForNow', 'Skip for Now')}
          </Button>
          <Button
            variant="contained"
            onClick={() => onContinue()}
            disabled={!speakerPool || speakerPool.length === 0}
            data-testid="proceed-to-outreach-button"
          >
            {t('speakerBrainstorm.actions.continue', 'Continue to Outreach')}
          </Button>
        </Box>
      )}

      {/* Edit Modal */}
      <SpeakerEditModal
        open={editModalOpen}
        onClose={handleEditModalClose}
        speaker={selectedSpeaker}
        eventCode={eventCode}
        organizers={organizers}
      />
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
