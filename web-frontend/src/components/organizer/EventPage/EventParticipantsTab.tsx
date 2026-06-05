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
import { Box, Typography, Chip, Stack, LinearProgress, Button, Alert } from '@mui/material';
import {
  People as PeopleIcon,
  Download as DownloadIcon,
  Article as ArticleIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { Event } from '@/types/event.types';
import EventParticipantList from '@/components/organizer/EventPage/EventParticipantList';
import WaitlistSection from '@/components/organizer/EventPage/WaitlistSection';
import { eventApiClient } from '@/services/eventApiClient';

interface EventParticipantsTabProps {
  event: Event;
}

const EventParticipantsTab: React.FC<EventParticipantsTabProps> = ({ event }) => {
  const { t } = useTranslation('events');
  const [isExporting, setIsExporting] = useState(false);
  const [isExportingDocx, setIsExportingDocx] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

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
      {/* Header with participant count + export button */}
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        alignItems={{ xs: 'stretch', md: 'center' }}
        justifyContent="space-between"
        spacing={2}
        sx={{ mb: 3 }}
      >
        <Stack direction="row" spacing={2} alignItems="center">
          <PeopleIcon sx={{ fontSize: 32, color: 'primary.main' }} />
          <Typography variant="h5" component="h2">
            {t('eventPage.participantsTab.title')}
          </Typography>
          <Chip label={activeTotal} color="primary" size="small" sx={{ fontWeight: 'bold' }} />
        </Stack>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
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
        </Stack>
      </Stack>

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

      {/* Participant List */}
      <EventParticipantList eventCode={event.eventCode} />

      {/* Story 10.11: Waitlist section (only when registrationCapacity is set) */}
      {capacity != null && (
        <WaitlistSection eventCode={event.eventCode} waitlistCount={waitlistCount} />
      )}
    </Box>
  );
};

export default EventParticipantsTab;
