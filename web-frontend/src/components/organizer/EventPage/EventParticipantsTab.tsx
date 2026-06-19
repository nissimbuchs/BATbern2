/**
 * Event Participants Tab Component (GREEN Phase)
 *
 * Container for the participants view within EventPage.
 * Story 3.3: Event Participants Tab - Task 8 (GREEN Phase)
 *
 * Features:
 * - Displays participant count badge
 * - Renders participant list for specific event
 * - Name-badge XLSX export (auto-participant-email-aliases-excel-export)
 * - Name-badge DOCX export (Avery L4784 + BAT logo, printable physical badges)
 */

import React, { useState } from 'react';
import {
  Box,
  Typography,
  Chip,
  Stack,
  LinearProgress,
  Button,
  Alert,
  Snackbar,
} from '@mui/material';
import {
  People as PeopleIcon,
  Download as DownloadIcon,
  Article as ArticleIcon,
  GroupAdd as GroupAddIcon,
  PersonAdd as PersonAddIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { Event } from '@/types/event.types';
import EventParticipantList from '@/components/organizer/EventPage/EventParticipantList';
import { AddParticipantDialog } from '@/components/organizer/EventPage/AddParticipantDialog';
import { eventApiClient } from '@/services/eventApiClient';
import { enrollStakeholders } from '@/services/api/eventRegistrationService';

interface EventParticipantsTabProps {
  event: Event;
}

const EventParticipantsTab: React.FC<EventParticipantsTabProps> = ({ event }) => {
  const { t } = useTranslation('events');
  const [isExporting, setIsExporting] = useState(false);
  const [isExportingDocx, setIsExportingDocx] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  // Add participant (organizer manually adds an existing user as confirmed).
  const [addOpen, setAddOpen] = useState(false);

  // Enrol organizers & partners (Epic 14 FR25 — moved here from the Overview).
  const [enrolling, setEnrolling] = useState(false);
  const [enrollSnackbar, setEnrollSnackbar] = useState<{
    open: boolean;
    message: string;
    severity: 'success' | 'error';
  }>({ open: false, message: '', severity: 'success' });

  const handleEnrollStakeholders = async (): Promise<void> => {
    setEnrolling(true);
    try {
      const result = await enrollStakeholders(event.eventCode);
      setEnrollSnackbar({
        open: true,
        message: t(
          'eventPage.overview.enrollStakeholdersSuccess',
          'Enrolled {{enrolled}} organizers/partners ({{skipped}} already registered)',
          { enrolled: result.enrolled, skipped: result.skipped }
        ),
        severity: 'success',
      });
    } catch {
      setEnrollSnackbar({
        open: true,
        message: t('eventPage.overview.enrollStakeholdersError', 'Failed to enroll stakeholders'),
        severity: 'error',
      });
    } finally {
      setEnrolling(false);
    }
  };

  const capacity = (event as { registrationCapacity?: number | null }).registrationCapacity ?? null;
  const confirmedCount = (event as { confirmedCount?: number }).confirmedCount ?? 0;
  const waitlistCount = (event as { waitlistCount?: number }).waitlistCount ?? 0;
  // Badge: total active registrations (registered + confirmed + waitlist, no cancelled)
  const activeTotal = confirmedCount + waitlistCount;

  const fillPct =
    capacity != null && capacity > 0 ? Math.min(100, (confirmedCount / capacity) * 100) : 0;
  const isFull = capacity != null && confirmedCount >= capacity;

  const handleExport = async (): Promise<void> => {
    setExportError(null);
    setIsExporting(true);
    let objectUrl: string | null = null;
    try {
      const blob = await eventApiClient.exportParticipantsXlsx(event.eventCode);
      objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = `${event.eventCode}-namensschilder.xlsx`;
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
    } catch (error) {
      const message = error instanceof Error ? error.message : t('event.participants.exportError');
      console.error('[EventParticipantsTab] Export failed:', error);
      setExportError(message);
    } finally {
      if (objectUrl) {
        // Slight delay so the browser has time to start the download before revoking.
        setTimeout(() => URL.revokeObjectURL(objectUrl as string), 100);
      }
      setIsExporting(false);
    }
  };

  const handleExportDocx = async (): Promise<void> => {
    setExportError(null);
    setIsExportingDocx(true);
    let objectUrl: string | null = null;
    try {
      const blob = await eventApiClient.exportParticipantsDocx(event.eventCode);
      objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = `${event.eventCode}-namensschilder.docx`;
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
    } catch (error) {
      const message = error instanceof Error ? error.message : t('event.participants.exportError');
      console.error('[EventParticipantsTab] DOCX export failed:', error);
      setExportError(message);
    } finally {
      if (objectUrl) {
        setTimeout(() => URL.revokeObjectURL(objectUrl as string), 100);
      }
      setIsExportingDocx(false);
    }
  };

  return (
    <Box sx={{ py: 3 }}>
      {/* Header: title row, then the action buttons on their OWN line below it — a single
          row on desktop (sm+), stacked full-width on mobile (xs). (Epic 14 follow-up: the 4th
          "Add participant" button no longer fits beside the title.) */}
      <Stack direction="column" spacing={2} sx={{ mb: 3 }}>
        <Stack direction="row" spacing={2} alignItems="center">
          <PeopleIcon sx={{ fontSize: 32, color: 'primary.main' }} />
          <Typography variant="h5" component="h2">
            {t('eventPage.participantsTab.title')}
          </Typography>
          <Chip label={activeTotal} color="primary" size="small" sx={{ fontWeight: 'bold' }} />
        </Stack>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} flexWrap="wrap" useFlexGap>
          <Button
            variant="contained"
            startIcon={<PersonAddIcon />}
            onClick={() => setAddOpen(true)}
            sx={{ width: { xs: '100%', sm: 'auto' } }}
            data-testid="add-participant-button"
          >
            {t('eventPage.participantsTab.addParticipant', 'Add participant')}
          </Button>
          <Button
            variant="outlined"
            startIcon={<DownloadIcon />}
            onClick={handleExport}
            disabled={isExporting || isExportingDocx}
            sx={{ width: { xs: '100%', sm: 'auto' } }}
            data-testid="participants-export-xlsx"
          >
            {t('event.participants.exportNameBadges')}
          </Button>
          <Button
            variant="outlined"
            startIcon={<ArticleIcon />}
            onClick={handleExportDocx}
            disabled={isExporting || isExportingDocx}
            sx={{ width: { xs: '100%', sm: 'auto' } }}
            data-testid="participants-export-docx"
          >
            {t('event.participants.exportNameBadgesDocx')}
          </Button>
          <Button
            variant="outlined"
            startIcon={<GroupAddIcon />}
            onClick={handleEnrollStakeholders}
            disabled={enrolling}
            sx={{ width: { xs: '100%', sm: 'auto' } }}
            data-testid="enroll-stakeholders-button"
          >
            {enrolling
              ? t('eventPage.overview.enrollStakeholdersLoading', 'Enrolling…')
              : t('eventPage.overview.enrollStakeholders', 'Enroll Organizers & Partners')}
          </Button>
        </Stack>
      </Stack>

      <AddParticipantDialog
        open={addOpen}
        onClose={() => setAddOpen(false)}
        eventCode={event.eventCode}
        onAdded={(name) =>
          setEnrollSnackbar({
            open: true,
            severity: 'success',
            message: t('eventPage.participantsTab.addParticipantSuccess', {
              name,
              defaultValue: 'Added {{name}} as a confirmed participant',
            }),
          })
        }
      />

      {exportError && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setExportError(null)}>
          {exportError}
        </Alert>
      )}

      {/* Story 10.11: Capacity progress bar (only when registrationCapacity is set) */}
      {capacity != null && (
        <Box sx={{ mb: 3 }}>
          <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.5 }}>
            <Typography variant="body2" color="text.secondary">
              {t('eventPage.participantsTab.capacityBar', {
                confirmed: confirmedCount,
                capacity,
                waitlist: waitlistCount,
              })}
            </Typography>
          </Stack>
          <LinearProgress
            variant="determinate"
            value={fillPct}
            color={isFull ? 'error' : 'primary'}
            sx={{ height: 8, borderRadius: 4 }}
          />
        </Box>
      )}

      {/* Participant List (the waitlist now lives inside this list via the
          "Waitlisted" filter — Epic 14 FR28, replacing the old WaitlistSection). */}
      <EventParticipantList eventCode={event.eventCode} />

      <Snackbar
        open={enrollSnackbar.open}
        autoHideDuration={5000}
        onClose={() => setEnrollSnackbar((s) => ({ ...s, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          severity={enrollSnackbar.severity}
          onClose={() => setEnrollSnackbar((s) => ({ ...s, open: false }))}
        >
          {enrollSnackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default EventParticipantsTab;
