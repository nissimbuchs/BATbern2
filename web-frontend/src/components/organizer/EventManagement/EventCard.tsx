/**
 * EventCard Component
 *
 * Story 2.5.3 - Task 8b (GREEN Phase)
 * AC: 1 (Event Dashboard Display)
 * Wireframe: docs/wireframes/story-1.16-event-management-dashboard.md v1.0
 *
 * Displays individual event card with:
 * - Progress bar (workflow completion %)
 * - Workflow step indicator (Step X/16)
 * - Event details (title, date, type)
 * - Quick actions (Edit, Topic Selection)
 * - Status badge
 * - Attendee information
 */

import React, { useState } from 'react';
import {
  Card,
  CardContent,
  CardActions,
  Typography,
  LinearProgress,
  IconButton,
  Box,
  Stack,
} from '@mui/material';
import {
  Edit as EditIcon,
  Event as EventIcon,
  People as PeopleIcon,
  Topic as TopicIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { format } from 'date-fns';
import { de, enUS } from 'date-fns/locale';
import type { Event, EventUI } from '@/types/event.types';
import {
  getWorkflowProgress,
  getProgressColor,
  getWorkflowStateLabel,
  getWorkflowStepNumber,
} from '@/utils/workflow/workflowState';

interface EventCardProps {
  event: Event;
  onEdit?: (eventCode: string) => void;
  onCardClick?: (eventCode: string) => void;
}

export const EventCard: React.FC<EventCardProps> = ({ event, onEdit, onCardClick }) => {
  const { t, i18n } = useTranslation('events');
  const navigate = useNavigate();
  const [isHovered, setIsHovered] = useState(false);

  const eventUI = event as EventUI;
  // Use new workflowState from Story 5.1a (fallback to 'CREATED')
  const workflowState = event.workflowState || 'CREATED';
  const progress = getWorkflowProgress(workflowState);
  const workflowLabel = getWorkflowStateLabel(workflowState, t);
  const workflowStep = getWorkflowStepNumber(workflowState);
  const capacity = eventUI.capacity || event.venueCapacity || 0;
  const attendeePercentage =
    capacity > 0 ? Math.round((event.currentAttendeeCount / capacity) * 100) : 0;

  const locale = i18n.language === 'de' ? de : enUS;
  const eventDate = eventUI.eventDate || event.date;
  const formattedDate = format(new Date(eventDate), 'dd MMM yyyy', { locale });

  const handleEdit = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    onEdit?.(event.eventCode);
  };

  const handleSelectTopic = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    const targetUrl = `/organizer/topics?eventCode=${event.eventCode}`;
    console.log('[EventCard] handleSelectTopic called', {
      eventCode: event.eventCode,
      targetUrl,
      event: event.title,
    });
    navigate(targetUrl);
  };

  const handleCardClick = () => {
    onCardClick?.(event.eventCode);
  };

  return (
    <Card
      data-testid={`event-card-${event.eventCode}`}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onClick={handleCardClick}
      tabIndex={0}
      aria-label={`Event card for ${event.title}`}
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        transition: 'all 0.3s ease',
        cursor: 'pointer',
        '&:hover': {
          boxShadow: 6,
          transform: 'translateY(-4px)',
        },
      }}
    >
      {/* Theme Image Banner (Story 2.5.3a) */}
      {event.themeImageUrl && (
        <Box
          component="img"
          src={event.themeImageUrl}
          alt={`${event.title} theme`}
          sx={{
            width: '100%',
            height: 160,
            objectFit: 'cover',
            borderTopLeftRadius: 'inherit',
            borderTopRightRadius: 'inherit',
          }}
        />
      )}

      <CardContent data-testid="event-card-content" sx={{ flexGrow: 1 }}>
        {/* Header with Event Code */}
        <Stack direction="row" justifyContent="flex-start" alignItems="center" mb={2}>
          <Typography variant="caption" color="text.secondary" fontWeight="medium">
            #{event.eventCode}
          </Typography>
        </Stack>

        {/* Event Title */}
        <Typography variant="h6" component="h3" gutterBottom noWrap>
          {event.title}
        </Typography>

        {/* Event Details */}
        <Stack spacing={1} mb={2}>
          <Stack direction="row" spacing={1} alignItems="center">
            <EventIcon fontSize="small" color="action" />
            <Typography variant="body2" color="text.secondary">
              {formattedDate}
              {eventUI.eventType && ` • ${t(`dashboard.eventType.${eventUI.eventType}`)}`}
            </Typography>
          </Stack>

          <Stack direction="row" spacing={1} alignItems="center">
            <PeopleIcon fontSize="small" color="action" />
            <Typography
              variant="body2"
              color={attendeePercentage === 100 ? 'error' : 'text.secondary'}
            >
              {event.currentAttendeeCount}/{capacity} ({attendeePercentage}% full)
            </Typography>
          </Stack>
        </Stack>

        {/* Workflow Progress (Story 5.1a) */}
        <Box>
          {/* Status and Step Indicator */}
          <Stack direction="row" justifyContent="space-between" alignItems="center" mb={0.5}>
            <Typography variant="caption" color="text.secondary">
              {workflowLabel}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {t('workflow.stepIndicator', { current: workflowStep, total: 16 })}
            </Typography>
          </Stack>
          {/* Progress Bar with Percentage */}
          <Stack direction="row" alignItems="center" spacing={1}>
            <LinearProgress
              variant="determinate"
              value={progress}
              color={getProgressColor(progress)}
              aria-label={`Workflow progress ${progress}%`}
              aria-valuenow={progress}
              sx={{ height: 8, borderRadius: 1, flex: 1 }}
            />
            <Typography variant="caption" fontWeight="bold" sx={{ minWidth: '35px' }}>
              {progress}%
            </Typography>
          </Stack>
        </Box>
      </CardContent>

      {/* Quick Actions */}
      <CardActions sx={{ justifyContent: 'flex-end', px: 2, pb: 2 }}>
        {isHovered && (
          <>
            <IconButton
              size="small"
              onClick={handleSelectTopic}
              aria-label={`Select topic for ${event.title}`}
              color="primary"
            >
              <TopicIcon />
            </IconButton>
            <IconButton
              size="small"
              onClick={handleEdit}
              aria-label={`Edit ${event.title}`}
              color="primary"
            >
              <EditIcon />
            </IconButton>
          </>
        )}
      </CardActions>
    </Card>
  );
};
