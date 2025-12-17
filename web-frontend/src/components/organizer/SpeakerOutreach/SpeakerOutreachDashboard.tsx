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
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  CircularProgress,
  Alert,
  Checkbox,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useSpeakerPool } from '../../../hooks/useSpeakerPool';
import MarkContactedModal from './MarkContactedModal';
import SpeakerOutreachDetailsDrawer from './SpeakerOutreachDetailsDrawer';
import type { SpeakerPoolEntry } from '../../../types/speakerPool.types';

interface SpeakerOutreachDashboardProps {
  eventCode: string;
}

const SpeakerOutreachDashboard: React.FC<SpeakerOutreachDashboardProps> = ({ eventCode }) => {
  const { t } = useTranslation('organizer');
  const { data: speakerPool, isLoading, isError } = useSpeakerPool(eventCode);

  const [selectedOrganizer, setSelectedOrganizer] = useState<string>('all');
  const [selectedSpeakers, setSelectedSpeakers] = useState<Set<string>>(new Set());
  const [markContactedModalOpen, setMarkContactedModalOpen] = useState(false);
  const [detailsDrawerOpen, setDetailsDrawerOpen] = useState(false);
  const [currentSpeaker, setCurrentSpeaker] = useState<SpeakerPoolEntry | null>(null);

  // Calculate days since assignment
  const calculateDaysSinceAssignment = (createdAt: string): number => {
    const created = new Date(createdAt);
    const now = new Date();
    const diffTime = Math.abs(now.getTime() - created.getTime());
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    return diffDays;
  };

  // Get unique organizers for filter
  const organizers = React.useMemo(() => {
    if (!speakerPool) return [];
    const uniqueOrganizers = new Set(
      speakerPool.map((s) => s.assignedOrganizerId).filter((id): id is string => !!id)
    );
    return Array.from(uniqueOrganizers);
  }, [speakerPool]);

  // Filter speakers by organizer
  const filteredSpeakers = React.useMemo(() => {
    if (!speakerPool) return [];
    if (selectedOrganizer === 'all') return speakerPool;
    return speakerPool.filter((s) => s.assignedOrganizerId === selectedOrganizer);
  }, [speakerPool, selectedOrganizer]);

  // Handle speaker selection for bulk actions
  const handleSelectSpeaker = (speakerId: string) => {
    const newSelection = new Set(selectedSpeakers);
    if (newSelection.has(speakerId)) {
      newSelection.delete(speakerId);
    } else {
      newSelection.add(speakerId);
    }
    setSelectedSpeakers(newSelection);
  };

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

  // Handle modal close and success
  const handleModalSuccess = () => {
    setSelectedSpeakers(new Set()); // Clear selection after successful submission
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
      <Paper sx={{ p: 3 }}>
        <Typography variant="body1" color="text.secondary">
          {t('speakerBrainstorm.pool.empty')}
        </Typography>
      </Paper>
    );
  }

  return (
    <Box data-testid="speaker-outreach-dashboard">
      <Box mb={3} display="flex" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h5" gutterBottom>
            {t('speakerOutreach.title')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('speakerOutreach.subtitle')}
          </Typography>
        </Box>

        {/* Filter by Organizer */}
        <FormControl sx={{ minWidth: 200 }}>
          <InputLabel>{t('speakerOutreach.filterByOrganizer')}</InputLabel>
          <Select
            value={selectedOrganizer}
            onChange={(e) => setSelectedOrganizer(e.target.value)}
            label={t('speakerOutreach.filterByOrganizer')}
          >
            <MenuItem value="all">All Organizers</MenuItem>
            {organizers.map((org) => (
              <MenuItem key={org} value={org}>
                {org}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </Box>

      {/* Bulk Actions Bar */}
      {selectedSpeakers.size > 0 && (
        <Paper sx={{ p: 2, mb: 2, bgcolor: 'primary.50' }}>
          <Box display="flex" justifyContent="space-between" alignItems="center">
            <Typography variant="body2">
              {selectedSpeakers.size} speaker{selectedSpeakers.size > 1 ? 's' : ''} selected
            </Typography>
            <Button
              variant="contained"
              onClick={() => {
                // For now, just open modal for first selected speaker
                // In full implementation, would use bulk action
                const firstSpeakerId = Array.from(selectedSpeakers)[0];
                const speaker = speakerPool.find((s) => s.id === firstSpeakerId);
                if (speaker) handleMarkContacted(speaker);
              }}
            >
              {t('speakerOutreach.markContacted')}
            </Button>
          </Box>
        </Paper>
      )}

      {/* Speaker List Table */}
      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell padding="checkbox">
                <Checkbox
                  checked={selectedSpeakers.size === filteredSpeakers.length}
                  indeterminate={
                    selectedSpeakers.size > 0 && selectedSpeakers.size < filteredSpeakers.length
                  }
                  onChange={(e) => {
                    if (e.target.checked) {
                      setSelectedSpeakers(new Set(filteredSpeakers.map((s) => s.id)));
                    } else {
                      setSelectedSpeakers(new Set());
                    }
                  }}
                />
              </TableCell>
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
                  <TableCell padding="checkbox" onClick={(e) => e.stopPropagation()}>
                    <Checkbox
                      checked={selectedSpeakers.has(speaker.id)}
                      onChange={() => handleSelectSpeaker(speaker.id)}
                    />
                  </TableCell>
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
                      color={speaker.status === 'contacted' ? 'success' : 'default'}
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
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={(e) => handleMarkContacted(speaker, e)}
                    >
                      {t('speakerOutreach.markContacted')}
                    </Button>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Mark Contacted Modal */}
      {currentSpeaker && (
        <MarkContactedModal
          open={markContactedModalOpen}
          onClose={() => setMarkContactedModalOpen(false)}
          onSuccess={handleModalSuccess}
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
    </Box>
  );
};

export default SpeakerOutreachDashboard;
