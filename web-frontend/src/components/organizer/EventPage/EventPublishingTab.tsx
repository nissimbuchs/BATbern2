/**
 * EventPublishingTab Component (Story 5.6)
 *
 * Publishing configuration, timeline, and quality checkpoints
 */

import React from 'react';
import {
  Paper,
  Typography,
  Button,
  Stack,
  Divider,
  Chip,
  Alert,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import {
  CheckCircle as CheckIcon,
  RadioButtonUnchecked as PendingIcon,
  Warning as WarningIcon,
  Visibility as PreviewIcon,
  Publish as PublishIcon,
  Email as EmailIcon,
  Settings as SettingsIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { format } from 'date-fns';
import { de, enUS } from 'date-fns/locale';
import type { Event, EventDetailUI } from '@/types/event.types';

interface EventPublishingTabProps {
  event: Event | EventDetailUI;
  eventCode: string;
}

interface TimelineItem {
  phase: string;
  date: string;
  status: 'completed' | 'pending' | 'upcoming';
  description: string;
}

interface QualityCheck {
  name: string;
  status: 'passed' | 'pending' | 'failed';
  message?: string;
}

export const EventPublishingTab: React.FC<EventPublishingTabProps> = ({ event, eventCode }) => {
  const { t, i18n } = useTranslation('events');
  const locale = i18n.language === 'de' ? de : enUS;

  // ⚠️ MOCK DATA - Publishing timeline (backend integration pending)
  const timeline: TimelineItem[] = [
    {
      phase: t('eventPage.publishing.topicPublished', 'Topic Published'),
      date: '2025-01-05',
      status: 'completed',
      description: t('eventPage.publishing.topicDesc', 'Immediate after topic selection'),
    },
    {
      phase: t('eventPage.publishing.speakersPublished', 'Speakers Published'),
      date: '2025-02-15',
      status: 'completed',
      description: t('eventPage.publishing.speakersDesc', '1 month before event'),
    },
    {
      phase: t('eventPage.publishing.finalAgenda', 'Final Agenda'),
      date: '2025-03-01',
      status: 'pending',
      description: t('eventPage.publishing.agendaDesc', '2 weeks before event'),
    },
    {
      phase: t('eventPage.publishing.eventDay', 'Event Day'),
      date: event.date || '2025-03-15',
      status: 'upcoming',
      description: t('eventPage.publishing.eventDesc', 'Event takes place'),
    },
    {
      phase: t('eventPage.publishing.postEvent', 'Post-Event Materials'),
      date: '2025-03-22',
      status: 'upcoming',
      description: t('eventPage.publishing.postDesc', '1 week after event'),
    },
  ];

  // ⚠️ MOCK DATA - Quality checkpoints (backend integration pending)
  const qualityChecks: QualityCheck[] = [
    {
      name: t('eventPage.publishing.abstractLength', 'Abstract length validation'),
      status: 'passed',
    },
    {
      name: t('eventPage.publishing.lessonsLearned', 'Lessons learned requirement'),
      status: 'passed',
    },
    {
      name: t('eventPage.publishing.materialsSubmitted', 'All materials submitted'),
      status: 'pending',
      message: '2 pending',
    },
    {
      name: t('eventPage.publishing.moderatorReview', 'Moderator review complete'),
      status: 'pending',
    },
  ];

  const getStatusIcon = (status: TimelineItem['status']) => {
    switch (status) {
      case 'completed':
        return <CheckIcon color="success" />;
      case 'pending':
        return <PendingIcon color="warning" />;
      case 'upcoming':
        return <PendingIcon color="disabled" />;
    }
  };

  const getCheckIcon = (status: QualityCheck['status']) => {
    switch (status) {
      case 'passed':
        return <CheckIcon color="success" fontSize="small" />;
      case 'pending':
        return <WarningIcon color="warning" fontSize="small" />;
      case 'failed':
        return <WarningIcon color="error" fontSize="small" />;
    }
  };

  const handlePreview = () => {
    window.open(`/events/${eventCode}`, '_blank');
  };

  const handleRepublish = () => {
    console.log('Republish event:', eventCode);
  };

  const handleNotifyAttendees = () => {
    console.log('Notify attendees:', eventCode);
  };

  return (
    <Stack spacing={3}>
      {/* MOCK DATA Warning */}
      <Alert severity="warning" icon={false}>
        <Stack direction="row" spacing={1} alignItems="center">
          <Typography variant="body2" fontWeight="bold">
            ⚠️ MOCK DATA FOR UI DEMONSTRATION
          </Typography>
        </Stack>
        <Typography variant="body2">
          All publishing timeline and quality checkpoint data shown below is mock data. Backend
          integration is pending.
        </Typography>
      </Alert>

      {/* Publishing Status */}
      <Paper sx={{ p: 3 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="h6">
              {t('eventPage.publishing.status', 'Publishing Status')}
            </Typography>
            <Chip label="MOCK DATA" size="small" color="warning" variant="outlined" />
          </Stack>
          <Button variant="outlined" startIcon={<SettingsIcon />} size="small">
            {t('eventPage.publishing.configure', 'Configure')}
          </Button>
        </Stack>
        <Divider sx={{ mb: 2 }} />

        <Stack spacing={2}>
          <Stack direction="row" spacing={2} alignItems="center">
            <Typography variant="body2" color="text.secondary">
              {t('eventPage.publishing.strategy', 'Strategy')}:
            </Typography>
            <Chip
              label={t('eventPage.publishing.progressive', 'Progressive Publishing')}
              color="primary"
              size="small"
            />
          </Stack>

          <Stack direction="row" spacing={2} alignItems="center">
            <Typography variant="body2" color="text.secondary">
              {t('eventPage.publishing.currentPhase', 'Current Phase')}:
            </Typography>
            <Typography variant="body1" fontWeight="medium">
              {t('eventPage.publishing.speakersPublished', 'Speakers Published')}
            </Typography>
          </Stack>
        </Stack>
      </Paper>

      {/* Publishing Timeline */}
      <Paper sx={{ p: 3 }}>
        <Stack direction="row" spacing={1} alignItems="center" mb={1}>
          <Typography variant="h6">
            {t('eventPage.publishing.timeline', 'Publishing Timeline')}
          </Typography>
          <Chip label="MOCK DATA" size="small" color="warning" variant="outlined" />
        </Stack>
        <Divider sx={{ mb: 2 }} />

        <List>
          {timeline.map((item, index) => (
            <ListItem
              key={index}
              sx={{
                borderLeft: '2px solid',
                borderColor:
                  item.status === 'completed'
                    ? 'success.main'
                    : item.status === 'pending'
                      ? 'warning.main'
                      : 'grey.300',
                ml: 1,
                py: 1.5,
              }}
            >
              <ListItemIcon sx={{ minWidth: 40 }}>{getStatusIcon(item.status)}</ListItemIcon>
              <ListItemText
                primary={
                  <Stack direction="row" spacing={2} alignItems="center">
                    <Typography variant="body1" fontWeight="medium">
                      {item.phase}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {format(new Date(item.date), 'MMM d, yyyy', { locale })}
                    </Typography>
                  </Stack>
                }
                secondary={item.description}
              />
            </ListItem>
          ))}
        </List>
      </Paper>

      {/* Quality Checkpoints */}
      <Paper sx={{ p: 3 }}>
        <Stack direction="row" spacing={1} alignItems="center" mb={1}>
          <Typography variant="h6">
            {t('eventPage.publishing.qualityCheckpoints', 'Quality Checkpoints')}
          </Typography>
          <Chip label="MOCK DATA" size="small" color="warning" variant="outlined" />
        </Stack>
        <Divider sx={{ mb: 2 }} />

        <Stack spacing={1}>
          {qualityChecks.map((check, index) => (
            <Stack
              key={index}
              direction="row"
              spacing={2}
              alignItems="center"
              sx={{
                py: 1,
                px: 2,
                borderRadius: 1,
                bgcolor:
                  check.status === 'passed'
                    ? 'success.light'
                    : check.status === 'pending'
                      ? 'warning.light'
                      : 'error.light',
                opacity: 0.8,
              }}
            >
              {getCheckIcon(check.status)}
              <Typography variant="body2" sx={{ flex: 1 }}>
                {check.name}
              </Typography>
              {check.message && (
                <Typography variant="caption" color="text.secondary">
                  ({check.message})
                </Typography>
              )}
            </Stack>
          ))}
        </Stack>

        {qualityChecks.some((c) => c.status !== 'passed') && (
          <Alert severity="info" sx={{ mt: 2 }}>
            {t(
              'eventPage.publishing.resolveCheckpoints',
              'Resolve all checkpoints before publishing final agenda.'
            )}
          </Alert>
        )}
      </Paper>

      {/* Actions */}
      <Paper sx={{ p: 3 }}>
        <Typography variant="h6" gutterBottom>
          {t('eventPage.publishing.actions', 'Actions')}
        </Typography>
        <Divider sx={{ mb: 2 }} />

        <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
          <Button variant="outlined" startIcon={<PreviewIcon />} onClick={handlePreview}>
            {t('eventPage.publishing.preview', 'Preview Public Page')}
          </Button>
          <Button variant="outlined" startIcon={<PublishIcon />} onClick={handleRepublish}>
            {t('eventPage.publishing.republish', 'Republish Event')}
          </Button>
          <Button variant="outlined" startIcon={<EmailIcon />} onClick={handleNotifyAttendees}>
            {t('eventPage.publishing.notifyAttendees', 'Notify Attendees')}
          </Button>
        </Stack>
      </Paper>
    </Stack>
  );
};

export default EventPublishingTab;
