/**
 * Speaker Outreach Dashboard Component (Story 5.3 - Task 4b)
 *
 * Main dashboard for tracking speaker outreach activities
 * Features:
 * - Display speaker pool with outreach status
 * - Filter by assigned organizer
 * - Show days since assignment with overdue highlighting
 * - Mark speakers as contacted
 * - View contact history
 * - Bulk actions
 */

import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Paper,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Button,
  Chip,
  CircularProgress,
  Alert,
  Grid,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
} from '@mui/material';
import { Add as AddIcon, ChevronRight, Delete as DeleteIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSpeakerPool, useDeleteSpeakerFromPool } from '../../../hooks/useSpeakerPool';
import { SpeakerBrainstormingPanel } from '../../SpeakerBrainstormingPanel/SpeakerBrainstormingPanel';
import MarkContactedModal from './MarkContactedModal';
import SpeakerOutreachDetailsDrawer from './SpeakerOutreachDetailsDrawer';
import type { SpeakerPoolEntry } from '../../../types/speakerPool.types';
import { OrganizerSelect, useOrganizers } from '@/components/shared/OrganizerSelect';

interface SpeakerOutreachDashboardProps {
  eventCode: string;
}

const SpeakerOutreachDashboard: React.FC<SpeakerOutreachDashboardProps> = ({ eventCode }) => {
  const { t } = useTranslation('organizer');
  const navigate = useNavigate();
  const { data: speakerPool, isLoading, isError } = useSpeakerPool(eventCode);

  // Fetch organizers using shared hook
  const { organizers } = useOrganizers();

  const [selectedOrganizer, setSelectedOrganizer] = useState<string>('all');
  const [markContactedModalOpen, setMarkContactedModalOpen] = useState(false);
  const [detailsDrawerOpen, setDetailsDrawerOpen] = useState(false);
  const [currentSpeaker, setCurrentSpeaker] = useState<SpeakerPoolEntry | null>(null);
  const [showBrainstormPanel, setShowBrainstormPanel] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [speakerToDelete, setSpeakerToDelete] = useState<SpeakerPoolEntry | null>(null);

  // Delete speaker mutation
  const deleteSpeakerMutation = useDeleteSpeakerFromPool();

  // Calculate days since assignment
  const calculateDaysSinceAssignment = (createdAt: string): number => {
    const created = new Date(createdAt);
    const now = new Date();
    const diffTime = Math.abs(now.getTime() - created.getTime());
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    return diffDays;
  };

  // Filter speakers by organizer
  const filteredSpeakers = React.useMemo(() => {
    if (!speakerPool) return [];
    if (selectedOrganizer === 'all') return speakerPool;
    return speakerPool.filter((s) => s.assignedOrganizerId === selectedOrganizer);
  }, [speakerPool, selectedOrganizer]);

  // Handle mark contacted button click
  const handleMarkContacted = (speaker: SpeakerPoolEntry, event: React.MouseEvent) => {
    event.stopPropagation(); // Prevent row click from triggering
    setCurrentSpeaker(speaker);
    setMarkContactedModalOpen(true);
  };

  // Handle row click to view details
  const handleRowClick = (speaker: SpeakerPoolEntry) => {
    setCurrentSpeaker(speaker);
    setDetailsDrawerOpen(true);
  };

  // Handle delete button click
  const handleDeleteClick = (speaker: SpeakerPoolEntry, event: React.MouseEvent) => {
    event.stopPropagation(); // Prevent row click from triggering
    setSpeakerToDelete(speaker);
    setDeleteConfirmOpen(true);
  };

  // Handle delete confirmation
  const handleDeleteConfirm = () => {
    if (speakerToDelete) {
      deleteSpeakerMutation.mutate(
        { eventCode, speakerId: speakerToDelete.id },
        {
          onSuccess: () => {
            setDeleteConfirmOpen(false);
            setSpeakerToDelete(null);
          },
        }
      );
    }
  };

  // Handle delete cancel
  const handleDeleteCancel = () => {
    setDeleteConfirmOpen(false);
    setSpeakerToDelete(null);
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  if (isError) {
    return <Alert severity="error">{t('speakerBrainstorm.pool.error')}</Alert>;
  }

  if (!speakerPool || speakerPool.length === 0) {
    return (
      <Box data-testid="speaker-outreach-dashboard">
        <Box sx={{ p: 3, textAlign: 'center' }}>
          <Alert severity="info" sx={{ mb: 2 }}>
            {t(
              'speakerOutreach.emptyPool.message',
              'You need to add speakers to the pool before starting outreach.'
            )}
          </Alert>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t(
              'speakerOutreach.emptyPool.hint',
              'Go back to the event planning stage to add potential speakers.'
            )}
          </Typography>
          <Button
            variant="contained"
            color="primary"
            onClick={() => navigate(`/organizer/topics?eventCode=${eventCode}`)}
          >
            {t('speakerOutreach.emptyPool.addSpeakers', 'Add Speakers to Pool')}
          </Button>
        </Box>
      </Box>
    );
  }

  return (
    <Box data-testid="speaker-outreach-dashboard">
      <Grid container spacing={3}>
        {/* Main Content Area */}
        <Grid size={{ xs: 12, md: showBrainstormPanel ? 8 : 12 }}>
          <Box mb={3} display="flex" justifyContent="space-between" alignItems="center">
            <Box>
              <Typography variant="h5" gutterBottom>
                {t('speakerOutreach.title')}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {t('speakerOutreach.subtitle')}
              </Typography>
            </Box>

            {/* Toggle brainstorm panel button */}
            <Box display="flex" gap={2} alignItems="center">
              <Button
                variant="outlined"
                startIcon={showBrainstormPanel ? <ChevronRight /> : <AddIcon />}
                onClick={() => setShowBrainstormPanel(!showBrainstormPanel)}
              >
                {showBrainstormPanel
                  ? t('speakerOutreach.hideBrainstorm', 'Hide Add Speakers')
                  : t('speakerOutreach.showBrainstorm', 'Add Speakers')}
              </Button>

              {/* Filter by Organizer */}
              <OrganizerSelect
                value={selectedOrganizer}
                onChange={(organizerId) => setSelectedOrganizer(organizerId)}
                organizers={organizers}
                label={t('speakerOutreach.filterByOrganizer')}
                sx={{ minWidth: 200 }}
                includeUnassigned={false}
                includeAllOption={true}
              />
            </Box>
          </Box>

          {/* Speaker List Table */}
          <TableContainer component={Paper}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>{t('speakerBrainstorm.form.speakerName')}</TableCell>
                  <TableCell>{t('speakerBrainstorm.form.company')}</TableCell>
                  <TableCell>{t('speakerBrainstorm.pool.assigned')}</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>{t('speakerOutreach.daysSinceAssignment')}</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredSpeakers.map((speaker) => {
                  const daysSince = calculateDaysSinceAssignment(speaker.createdAt);
                  const isOverdue = daysSince > 7;

                  return (
                    <TableRow
                      key={speaker.id}
                      hover
                      onClick={() => handleRowClick(speaker)}
                      sx={{ cursor: 'pointer' }}
                      data-testid="speaker-row"
                    >
                      <TableCell>
                        <Typography variant="body2" fontWeight="medium">
                          {speaker.speakerName}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" color="text.secondary">
                          {speaker.company || '-'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" color="text.secondary">
                          {speaker.assignedOrganizerId || t('speakerBrainstorm.form.unassigned')}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={speaker.status}
                          size="small"
                          color={speaker.status === 'CONTACTED' ? 'success' : 'default'}
                          data-testid="outreach-status"
                        />
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={`${daysSince} days`}
                          size="small"
                          color={isOverdue ? 'error' : 'default'}
                          data-testid="days-since-assignment"
                        />
                      </TableCell>
                      <TableCell align="right">
                        <Box display="flex" gap={1} justifyContent="flex-end">
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={(e) => handleMarkContacted(speaker, e)}
                          >
                            {t('speakerOutreach.markContacted')}
                          </Button>
                          <IconButton
                            size="small"
                            color="error"
                            onClick={(e) => handleDeleteClick(speaker, e)}
                            title="Delete speaker"
                          >
                            <DeleteIcon />
                          </IconButton>
                        </Box>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </Grid>

        {/* Collapsible Speaker Brainstorming Panel */}
        {showBrainstormPanel && (
          <Grid size={{ xs: 12, md: 4 }}>
            <Paper sx={{ p: 2, position: 'sticky', top: 16 }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                <Typography variant="h6">{t('speakerBrainstorm.title')}</Typography>
                <IconButton size="small" onClick={() => setShowBrainstormPanel(false)}>
                  <ChevronRight />
                </IconButton>
              </Box>
              <SpeakerBrainstormingPanel
                eventCode={eventCode}
                organizers={organizers}
                showPoolList={false}
                showHeader={false}
              />
            </Paper>
          </Grid>
        )}
      </Grid>

      {/* Mark Contacted Modal */}
      {currentSpeaker && (
        <MarkContactedModal
          open={markContactedModalOpen}
          onClose={() => setMarkContactedModalOpen(false)}
          onSuccess={() => {}}
          eventCode={eventCode}
          speakerId={currentSpeaker.id}
          speakerName={currentSpeaker.speakerName}
        />
      )}

      {/* Speaker Outreach Details Drawer */}
      <SpeakerOutreachDetailsDrawer
        open={detailsDrawerOpen}
        onClose={() => setDetailsDrawerOpen(false)}
        speaker={currentSpeaker}
        eventCode={eventCode}
      />

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteConfirmOpen} onClose={handleDeleteCancel}>
        <DialogTitle>Delete Speaker</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to delete {speakerToDelete?.speakerName} from the speaker pool?
            This action cannot be undone.
          </DialogContentText>
          {deleteSpeakerMutation.isError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              Failed to delete speaker. Please try again.
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleDeleteCancel} disabled={deleteSpeakerMutation.isPending}>
            Cancel
          </Button>
          <Button
            onClick={handleDeleteConfirm}
            color="error"
            variant="contained"
            disabled={deleteSpeakerMutation.isPending}
          >
            {deleteSpeakerMutation.isPending ? 'Deleting...' : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default SpeakerOutreachDashboard;
