import React, { useState, useEffect } from 'react';
import {
  Box,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Button,
  Typography,
  Collapse,
  IconButton,
  Paper,
  Chip,
} from '@mui/material';
import {
  CheckCircle as CheckIcon,
  Warning as WarningIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PublishingPhase } from '@/types/event.types';

interface ValidationItem {
  isValid: boolean;
  errors: string[];
  assignedCount?: number;
  totalCount?: number;
  unassignedSessions?: Array<{ sessionSlug: string; title: string }>;
}

interface ValidationData {
  topic: ValidationItem;
  speakers: ValidationItem;
  sessions: ValidationItem;
}

export interface ValidationDashboardProps {
  eventCode: string;
  phase: PublishingPhase;
  validation: ValidationData;
}

export const ValidationDashboard: React.FC<ValidationDashboardProps> = ({
  eventCode,
  phase,
  validation,
}) => {
  const { t } = useTranslation('events');
  const navigate = useNavigate();
  const [expandedSessions, setExpandedSessions] = useState(false);
  const [statusAnnouncement, setStatusAnnouncement] = useState('');

  // Determine which items are required for the current phase
  const getRequiredItems = (currentPhase: PublishingPhase) => {
    switch (currentPhase) {
      case 'topic':
        return { topic: true, speakers: false, sessions: false };
      case 'speakers':
        return { topic: true, speakers: true, sessions: false };
      case 'agenda':
        return { topic: true, speakers: true, sessions: true };
      default:
        return { topic: false, speakers: false, sessions: false };
    }
  };

  const requiredItems = getRequiredItems(phase);

  // Calculate overall validation status
  const isOverallValid = () => {
    if (requiredItems.topic && !validation.topic.isValid) return false;
    if (requiredItems.speakers && !validation.speakers.isValid) return false;
    if (requiredItems.sessions && !validation.sessions.isValid) return false;
    return true;
  };

  const overallValid = isOverallValid();

  // Update screen reader announcement when validation changes
  useEffect(() => {
    setStatusAnnouncement(
      overallValid
        ? t('publishing.validation.accessibility.readyToPublish')
        : t('publishing.validation.accessibility.notReadyToPublish')
    );
  }, [overallValid, t]);

  const handleAssignTimingsClick = () => {
    navigate(`/organizer/events/${eventCode}/slot-assignment`);
  };

  const handleToggleExpand = () => {
    setExpandedSessions(!expandedSessions);
  };

  const renderValidationIcon = (item: keyof ValidationData, itemValidation: ValidationItem) => {
    if (itemValidation.isValid) {
      return <CheckIcon data-testid={`validation-check-${item}`} color="success" />;
    }
    return <WarningIcon data-testid={`validation-warning-${item}`} color="warning" />;
  };

  const renderSessionStatus = () => {
    const { assignedCount = 0, totalCount = 0, isValid } = validation.sessions;

    if (isValid) {
      return t('publishing.validation.ready', { assigned: assignedCount, total: totalCount });
    }
    return t('publishing.validation.incomplete', { assigned: assignedCount, total: totalCount });
  };

  return (
    <Paper sx={{ p: 2 }}>
      {/* Screen reader announcement */}
      <div role="status" aria-live="polite" style={{ position: 'absolute', left: '-10000px' }}>
        {statusAnnouncement}
      </div>

      {/* Overall Status */}
      <Box sx={{ mb: 3 }}>
        <Typography
          variant="h6"
          data-testid="overall-validation-status"
          sx={{ display: 'flex', alignItems: 'center', gap: 1 }}
        >
          {overallValid ? (
            <>
              <CheckIcon color="success" />
              {t('publishing.validation.readyToPublish')}
            </>
          ) : (
            <>
              <WarningIcon color="warning" />
              {t('publishing.validation.notReadyToPublish')}
            </>
          )}
        </Typography>
      </Box>

      {/* Validation Items List */}
      <List>
        {/* Topic Validation */}
        <ListItem
          data-testid="validation-item-topic"
          data-required={requiredItems.topic}
          aria-label={t('publishing.validation.accessibility.eventTopic')}
        >
          <ListItemIcon>{renderValidationIcon('topic', validation.topic)}</ListItemIcon>
          <ListItemText
            primary={t('publishing.validation.eventTopic')}
            secondary={
              requiredItems.topic ? (
                validation.topic.isValid ? (
                  t('publishing.validation.complete')
                ) : (
                  <span style={{ color: 'error.main' }}>{validation.topic.errors.join(', ')}</span>
                )
              ) : (
                <Chip
                  label={t('publishing.validation.notRequired')}
                  size="small"
                  sx={{ opacity: 0.6 }}
                />
              )
            }
            secondaryTypographyProps={{ component: 'div' }}
          />
        </ListItem>

        {/* Speakers Validation */}
        <ListItem
          data-testid="validation-item-speakers"
          data-required={requiredItems.speakers}
          aria-label={t('publishing.validation.accessibility.speakerLineup')}
        >
          <ListItemIcon>{renderValidationIcon('speakers', validation.speakers)}</ListItemIcon>
          <ListItemText
            primary={t('publishing.validation.speakerLineup')}
            secondary={
              requiredItems.speakers ? (
                validation.speakers.isValid ? (
                  t('publishing.validation.complete')
                ) : (
                  <span style={{ color: 'error.main' }}>
                    {validation.speakers.errors.join(', ')}
                  </span>
                )
              ) : (
                <Chip
                  label={t('publishing.validation.notRequired')}
                  size="small"
                  sx={{ opacity: 0.6 }}
                />
              )
            }
            secondaryTypographyProps={{ component: 'div' }}
          />
        </ListItem>

        {/* Sessions Validation */}
        <ListItem
          data-testid="validation-item-sessions"
          data-required={requiredItems.sessions}
          aria-label={t('publishing.validation.accessibility.sessionTimings')}
        >
          <ListItemIcon>{renderValidationIcon('sessions', validation.sessions)}</ListItemIcon>
          <ListItemText
            primary={
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <span>{t('publishing.validation.sessionTimings')}</span>
                {/* Show dropdown for unassigned sessions in speakers or agenda phase */}
                {(phase === 'speakers' || phase === 'agenda') &&
                  validation.sessions.unassignedSessions &&
                  validation.sessions.unassignedSessions.length > 0 && (
                    <IconButton
                      size="small"
                      onClick={handleToggleExpand}
                      data-testid="expand-sessions-button"
                      aria-label={
                        expandedSessions
                          ? t('publishing.validation.collapseUnassigned')
                          : t('publishing.validation.expandUnassigned')
                      }
                    >
                      {expandedSessions ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                    </IconButton>
                  )}
              </Box>
            }
            secondary={
              requiredItems.sessions ? (
                <>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                    <span>{renderSessionStatus()}</span>
                    {!validation.sessions.isValid && (
                      <Button
                        size="small"
                        variant="outlined"
                        onClick={handleAssignTimingsClick}
                        sx={{ ml: 1 }}
                      >
                        {t('publishing.validation.assignTimings')}
                      </Button>
                    )}
                  </Box>
                  {!validation.sessions.isValid && validation.sessions.unassignedSessions && (
                    <Collapse in={expandedSessions} timeout="auto" unmountOnExit>
                      <List dense sx={{ mt: 1 }}>
                        {validation.sessions.unassignedSessions.map((session) => (
                          <ListItem key={session.sessionSlug} sx={{ pl: 4 }}>
                            <ListItemText primary={session.title} />
                          </ListItem>
                        ))}
                      </List>
                    </Collapse>
                  )}
                </>
              ) : phase === 'speakers' &&
                validation.sessions.unassignedSessions &&
                validation.sessions.unassignedSessions.length > 0 ? (
                <>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                    <Chip
                      label={t('publishing.validation.notRequired')}
                      size="small"
                      sx={{ opacity: 0.6 }}
                    />
                    <span style={{ fontSize: '0.875rem', color: 'warning.main' }}>
                      ({validation.sessions.unassignedSessions.length} unassigned sessions)
                    </span>
                  </Box>
                  <Collapse in={expandedSessions} timeout="auto" unmountOnExit>
                    <List dense sx={{ mt: 1 }}>
                      {validation.sessions.unassignedSessions.map((session) => (
                        <ListItem key={session.sessionSlug} sx={{ pl: 4 }}>
                          <ListItemText primary={session.title} />
                        </ListItem>
                      ))}
                    </List>
                  </Collapse>
                </>
              ) : (
                <Chip
                  label={t('publishing.validation.notRequired')}
                  size="small"
                  sx={{ opacity: 0.6 }}
                />
              )
            }
            secondaryTypographyProps={{ component: 'div' }}
          />
        </ListItem>
      </List>
    </Paper>
  );
};
