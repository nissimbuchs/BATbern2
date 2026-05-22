/**
 * Unified history panel for the speaker detail drawer (Story 11.D.4 — AC7.3).
 *
 * Merges the status-change feed (`GET /events/{code}/speakers/{id}/status/history`,
 * `speakerStatusService.getStatusHistory`) and the outreach feed (`useSpeakerOutreachHistory`)
 * into a single chronologically-ordered list, newest first. Replaces the legacy
 * 3-tab drawer's separate "Activity" tab + the status timeline that lived elsewhere.
 *
 * Per plan §8.5 "unified History — status changes (with reason) + outreach attempts in
 * one chronological feed".
 */
import React, { useMemo } from 'react';
import {
  Alert,
  Avatar,
  Box,
  Chip,
  List,
  ListItem,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  SwapHoriz as StatusIcon,
  Email as EmailIcon,
  Phone as PhoneIcon,
  Person as PersonIcon,
  HelpOutline as UnknownIcon,
} from '@mui/icons-material';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { formatDateTime } from '@/utils/date';
import { speakerStatusService } from '@/services/speakerStatusService';
import { useSpeakerOutreachHistory } from '@/hooks/useSpeakerOutreach';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import type { components } from '@/types/generated/speakers-api.types';

type StatusHistoryItem = components['schemas']['StatusHistoryItem'];
type OutreachHistoryItem = components['schemas']['OutreachHistory'];
type ContactMethod = components['schemas']['ContactMethod'];

interface UnifiedHistoryPanelProps {
  speaker: SpeakerPoolEntry;
  eventCode: string;
}

interface InterleavedEntry {
  kind: 'status' | 'outreach';
  timestamp: string;
  payload: StatusHistoryItem | OutreachHistoryItem;
}

function contactMethodIcon(method: ContactMethod | string | undefined): React.ReactElement {
  switch (method) {
    case 'email':
      return <EmailIcon fontSize="small" />;
    case 'phone':
      return <PhoneIcon fontSize="small" />;
    case 'in_person':
      return <PersonIcon fontSize="small" />;
    default:
      return <UnknownIcon fontSize="small" />;
  }
}

export const UnifiedHistoryPanel: React.FC<UnifiedHistoryPanelProps> = ({ speaker, eventCode }) => {
  const { t, i18n } = useTranslation(['organizer', 'common']);
  const fmt = (iso: string) => formatDateTime(new Date(iso), i18n.language);

  const statusQuery = useQuery({
    queryKey: ['speakerStatusHistory', eventCode, speaker.id],
    queryFn: () => speakerStatusService.getStatusHistory(eventCode, speaker.id),
    enabled: !!speaker.id,
  });

  const outreachQuery = useSpeakerOutreachHistory(eventCode, speaker.id);

  const isLoading = statusQuery.isLoading || outreachQuery.isLoading;
  const isError = statusQuery.isError || outreachQuery.isError;

  const entries: InterleavedEntry[] = useMemo(() => {
    const statusItems: InterleavedEntry[] = (statusQuery.data ?? [])
      .filter((it): it is StatusHistoryItem & { changedAt: string } => !!it.changedAt)
      .map((it) => ({ kind: 'status', timestamp: it.changedAt, payload: it }));
    const outreachItems: InterleavedEntry[] = (outreachQuery.data ?? []).map((it) => ({
      kind: 'outreach',
      timestamp: it.contactDate,
      payload: it,
    }));
    return [...statusItems, ...outreachItems].sort((a, b) => {
      // Newest first; tolerate unparseable timestamps by treating them as oldest.
      const ta = new Date(a.timestamp).getTime();
      const tb = new Date(b.timestamp).getTime();
      const safeA = Number.isNaN(ta) ? 0 : ta;
      const safeB = Number.isNaN(tb) ? 0 : tb;
      return safeB - safeA;
    });
  }, [statusQuery.data, outreachQuery.data]);

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <BATbernLoader size={64} />
      </Box>
    );
  }

  if (isError) {
    return (
      <Box sx={{ p: 2 }}>
        <Alert severity="error">{t('organizer:speakerDrawer.history.loadError')}</Alert>
      </Box>
    );
  }

  if (entries.length === 0) {
    return (
      <Box sx={{ textAlign: 'center', py: 4, px: 2 }}>
        <Typography variant="body2" color="text.secondary">
          {t('organizer:speakerDrawer.history.noEntries')}
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 2, overflow: 'auto' }} data-testid="unified-history-panel">
      <List dense disablePadding>
        {entries.map((entry, idx) => {
          const key = `${entry.kind}-${idx}-${entry.timestamp}`;
          if (entry.kind === 'status') {
            const it = entry.payload as StatusHistoryItem;
            const fromLabel = it.previousStatus
              ? t(`organizer:speakerStatus.${it.previousStatus}`)
              : '—';
            const toLabel = it.newStatus ? t(`organizer:speakerStatus.${it.newStatus}`) : '—';
            return (
              <ListItem
                key={key}
                disableGutters
                data-testid="history-entry-status"
                sx={{
                  flexDirection: 'column',
                  alignItems: 'flex-start',
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: 1,
                  p: 1.5,
                  mb: 1,
                }}
              >
                <Stack direction="row" spacing={1} alignItems="center" sx={{ width: '100%' }}>
                  <Avatar sx={{ width: 24, height: 24, bgcolor: 'primary.main' }}>
                    <StatusIcon sx={{ fontSize: 16 }} />
                  </Avatar>
                  <Typography variant="body2" sx={{ flex: 1 }}>
                    {t('organizer:speakerDrawer.history.statusEntry', {
                      from: fromLabel,
                      to: toLabel,
                      actor: it.changedByUsername ?? t('common:labels.unknown', 'unknown'),
                    })}
                  </Typography>
                  <Tooltip title={fmt(entry.timestamp)}>
                    <Typography variant="caption" color="text.secondary">
                      {fmt(entry.timestamp)}
                    </Typography>
                  </Tooltip>
                </Stack>
                {it.changeReason && (
                  <Typography
                    variant="body2"
                    sx={{ mt: 0.5, ml: 4, color: 'text.secondary', whiteSpace: 'pre-wrap' }}
                  >
                    {it.changeReason}
                  </Typography>
                )}
              </ListItem>
            );
          }

          const it = entry.payload as OutreachHistoryItem;
          return (
            <ListItem
              key={key}
              disableGutters
              data-testid="history-entry-outreach"
              sx={{
                flexDirection: 'column',
                alignItems: 'flex-start',
                border: 1,
                borderColor: 'divider',
                borderRadius: 1,
                p: 1.5,
                mb: 1,
              }}
            >
              <Stack direction="row" spacing={1} alignItems="center" sx={{ width: '100%' }}>
                <Avatar sx={{ width: 24, height: 24, bgcolor: 'info.main' }}>
                  {contactMethodIcon(it.contactMethod)}
                </Avatar>
                <Typography variant="body2" sx={{ flex: 1 }}>
                  {t('organizer:speakerDrawer.history.outreachEntry', {
                    actor: it.organizerUsername ?? t('common:labels.unknown', 'unknown'),
                  })}
                </Typography>
                <Chip
                  size="small"
                  variant="outlined"
                  label={t(
                    `organizer:speakerOutreach.markContactedModal.method.${
                      it.contactMethod === 'in_person' ? 'inPerson' : it.contactMethod
                    }`
                  )}
                />
                <Tooltip title={fmt(entry.timestamp)}>
                  <Typography variant="caption" color="text.secondary">
                    {fmt(entry.timestamp)}
                  </Typography>
                </Tooltip>
              </Stack>
              {it.notes && (
                <Typography
                  variant="body2"
                  sx={{ mt: 0.5, ml: 4, color: 'text.secondary', whiteSpace: 'pre-wrap' }}
                >
                  {it.notes}
                </Typography>
              )}
            </ListItem>
          );
        })}
      </List>
    </Box>
  );
};
