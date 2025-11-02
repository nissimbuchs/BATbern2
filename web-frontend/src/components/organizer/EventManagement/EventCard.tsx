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
 * - Quick actions (Edit, View Details)
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
  Chip,
  Button,
  IconButton,
  Box,
  Stack,
} from '@mui/material';
import {
  Edit as EditIcon,
  Visibility as VisibilityIcon,
  Event as EventIcon,
  People as PeopleIcon,
} from '@mui/icons-material';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { format } from 'date-fns';
import { de, enUS } from 'date-fns/locale';
import type { Event, EventUI } from '@/types/event.types';

interface EventCardProps {
  event: Event;
  onEdit?: (eventCode: string) => void;
  onCardClick?: (eventCode: string) => void;
}

// Workflow state to step number mapping (16 steps total)
const WORKFLOW_STEPS: Record<string, number> = {
  topic_selection: 1,
  topic_approval: 2,
  venue_booking: 3,
  save_the_date: 4,
  speaker_research: 7,
  speaker_invitation: 8,
  abstracts_submission: 9,
  abstracts_moderation: 10,
  speaker_assignment: 11,
  speaker_materials: 12,
  registration_open: 13,
  event_promotion: 14,
  event_execution: 15,
  post_event: 16,
};

// Calculate progress percentage from workflow step
const getWorkflowProgress = (workflowState: string): number => {
  const step = WORKFLOW_STEPS[workflowState] || 1;
  return Math.round((step / 16) * 100);
};

// Get progress bar color based on completion
const getProgressColor = (progress: number): 'warning' | 'primary' | 'success' => {
  if (progress < 30) return 'warning';
  if (progress < 70) return 'primary';
  return 'success';
};

// Get status chip color
const getStatusColor = (
  status: string
): 'default' | 'primary' | 'success' | 'warning' | 'error' => {
  switch (status) {
    case 'published':
      return 'success';
    case 'active':
      return 'primary';
    case 'draft':
      return 'warning';
    case 'completed':
    case 'archived':
      return 'default';
    default:
      return 'default';
  }
};

export const EventCard: React.FC<EventCardProps> = ({ event, onEdit, onCardClick }) => {
  const { t, i18n } = useTranslation('events');
  const [isHovered, setIsHovered] = useState(false);

  const eventUI = event as EventUI;
  const workflowStep = WORKFLOW_STEPS[eventUI.workflowState || ''] || 1;
  const progress = getWorkflowProgress(eventUI.workflowState || '');
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
      <CardContent data-testid="event-card-content" sx={{ flexGrow: 1 }}>
        {/* Header with Status and Event Code */}
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
          <Chip
            label={t(`status.${event.status}`)}
            color={getStatusColor(event.status)}
            size="small"
          />
          <Typography variant="caption" color="text.secondary">
            #{event.eventNumber} • {event.eventCode}
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

        {/* Workflow Progress */}
        <Box mb={1}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" mb={0.5}>
            <Typography variant="caption" color="text.secondary">
              {t('dashboard.workflowProgress')}
            </Typography>
            <Typography variant="caption" fontWeight="bold">
              {progress}%
            </Typography>
          </Stack>
          <LinearProgress
            variant="determinate"
            value={progress}
            color={getProgressColor(progress)}
            aria-label={`Workflow progress ${progress}%`}
            aria-valuenow={progress}
            sx={{ height: 8, borderRadius: 1 }}
          />
        </Box>

        {/* Workflow Step */}
        <Typography variant="caption" color="text.secondary">
          {t('dashboard.workflowStep', { current: workflowStep, total: 16 })}
          {eventUI.workflowState && ` • ${t(`dashboard.workflowState.${eventUI.workflowState}`)}`}
        </Typography>
      </CardContent>

      {/* Quick Actions */}
      <CardActions sx={{ justifyContent: 'space-between', px: 2, pb: 2 }}>
        <Button
          component={Link}
          to={`/organizer/events/${event.eventCode}`}
          size="small"
          startIcon={<VisibilityIcon />}
          aria-label={`View details for ${event.title}`}
        >
          {t('dashboard.actions.viewDetails')}
        </Button>

        {isHovered && (
          <IconButton
            size="small"
            onClick={handleEdit}
            aria-label={`Edit ${event.title}`}
            color="primary"
          >
            <EditIcon />
          </IconButton>
        )}
      </CardActions>
    </Card>
  );
};
