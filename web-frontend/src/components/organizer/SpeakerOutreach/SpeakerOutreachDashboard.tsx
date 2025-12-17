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

import React, { useState, useMemo } from 'react';
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
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  CircularProgress,
  Alert,
  Checkbox,
  Grid,
  IconButton,
} from '@mui/material';
import { Add as AddIcon, ChevronRight } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSpeakerPool } from '../../../hooks/useSpeakerPool';
import { useEvent } from '../../../hooks/useEvents';
import { useUserList } from '../../../hooks/useUserManagement/useUserList';
import { Breadcrumbs } from '../../shared/Breadcrumbs';
import type { BreadcrumbItem } from '../../shared/Breadcrumbs';
import { SpeakerBrainstormingPanel } from '../../SpeakerBrainstormingPanel/SpeakerBrainstormingPanel';
import MarkContactedModal from './MarkContactedModal';
import SpeakerOutreachDetailsDrawer from './SpeakerOutreachDetailsDrawer';
import type { SpeakerPoolEntry } from '../../../types/speakerPool.types';

interface SpeakerOutreachDashboardProps {
  eventCode: string;
}

const SpeakerOutreachDashboard: React.FC<SpeakerOutreachDashboardProps> = ({ eventCode }) => {
  const { t } = useTranslation('organizer');
  const navigate = useNavigate();
  const { data: speakerPool, isLoading, isError } = useSpeakerPool(eventCode);
  const { data: eventData } = useEvent(eventCode);

  // Fetch users with ORGANIZER role
  const { data: organizersData } = useUserList({
    filters: { role: ['ORGANIZER'] },
    pagination: { page: 1, limit: 100 },
  });

  const [selectedOrganizer, setSelectedOrganizer] = useState<string>('all');
  const [selectedSpeakers, setSelectedSpeakers] = useState<Set<string>>(new Set());
  const [markContactedModalOpen, setMarkContactedModalOpen] = useState(false);
  const [detailsDrawerOpen, setDetailsDrawerOpen] = useState(false);
  const [currentSpeaker, setCurrentSpeaker] = useState<SpeakerPoolEntry | null>(null);
  const [showBrainstormPanel, setShowBrainstormPanel] = useState(false);

  // Calculate days since assignment
  const calculateDaysSinceAssignment = (createdAt: string): number => {
    const created = new Date(createdAt);
    const now = new Date();
    const diffTime = Math.abs(now.getTime() - created.getTime());
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    return diffDays;
  };

  // Transform organizers from API to format needed by components
  const organizers = React.useMemo(() => {
    if (!organizersData?.data) return [];
    return organizersData.data.map((user) => ({
      id: user.username,
      name: `${user.firstName} ${user.lastName}`.trim() || user.username,
    }));
  }, [organizersData]);

  // Filter speakers by organizer
  const filteredSpeakers = React.useMemo(() => {
    if (!speakerPool) return [];
    if (selectedOrganizer === 'all') return speakerPool;
    return speakerPool.filter((s) => s.assignedOrganizerId === selectedOrganizer);
  }, [speakerPool, selectedOrganizer]);

  // Build breadcrumb items
  const breadcrumbItems: BreadcrumbItem[] = useMemo(() => {
    if (eventData) {
      return [
        { label: t('speakerOutreach.breadcrumbs.home'), path: '/organizer/events' },
        { label: eventData.title, path: `/organizer/events/${eventCode}` },
        { label: t('speakerOutreach.breadcrumbs.speakerOutreach') },
      ];
    }
    return [
      { label: t('speakerOutreach.breadcrumbs.home'), path: '/organizer/events' },
      { label: t('speakerOutreach.breadcrumbs.speakerOutreach') },
    ];
  }, [eventData, eventCode, t]);

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
      <Box data-testid="speaker-outreach-dashboard">
        <Breadcrumbs items={breadcrumbItems} />
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
      {/* Breadcrumbs */}
      <Breadcrumbs items={breadcrumbItems} />

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
              <FormControl sx={{ minWidth: 200 }}>
                <InputLabel>{t('speakerOutreach.filterByOrganizer')}</InputLabel>
                <Select
                  value={selectedOrganizer}
                  onChange={(e) => setSelectedOrganizer(e.target.value)}
                  label={t('speakerOutreach.filterByOrganizer')}
                >
                  <MenuItem value="all">All Organizers</MenuItem>
                  {organizers.map((org) => (
                    <MenuItem key={org.id} value={org.id}>
                      {org.name}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Box>
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
