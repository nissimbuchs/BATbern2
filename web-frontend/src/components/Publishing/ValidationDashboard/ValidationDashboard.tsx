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
    setStatusAnnouncement(overallValid ? 'Ready to publish' : 'Not ready to publish');
  }, [overallValid]);

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
      return `Ready (${assignedCount}/${totalCount} sessions assigned)`;
    }
    return `Incomplete (${assignedCount}/${totalCount} sessions assigned)`;
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
              Ready to Publish
            </>
          ) : (
            <>
              <WarningIcon color="warning" />
              Not Ready to Publish
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
          aria-label="Event Topic Validation"
        >
          <ListItemIcon>{renderValidationIcon('topic', validation.topic)}</ListItemIcon>
          <ListItemText
            primary="Event Topic"
            secondary={
              requiredItems.topic ? (
                validation.topic.isValid ? (
                  'Complete'
                ) : (
                  <span style={{ color: 'error.main' }}>{validation.topic.errors.join(', ')}</span>
                )
              ) : (
                <Chip label="Not Required" size="small" sx={{ opacity: 0.6 }} />
              )
            }
          />
        </ListItem>

        {/* Speakers Validation */}
        <ListItem
          data-testid="validation-item-speakers"
          data-required={requiredItems.speakers}
          aria-label="Speaker Lineup Validation"
        >
          <ListItemIcon>{renderValidationIcon('speakers', validation.speakers)}</ListItemIcon>
          <ListItemText
            primary="Speaker Lineup"
            secondary={
              requiredItems.speakers ? (
                validation.speakers.isValid ? (
                  'Complete'
                ) : (
                  <span style={{ color: 'error.main' }}>
                    {validation.speakers.errors.join(', ')}
                  </span>
                )
              ) : (
                <Chip label="Not Required" size="small" sx={{ opacity: 0.6 }} />
              )
            }
          />
        </ListItem>

        {/* Sessions Validation */}
        <ListItem
          data-testid="validation-item-sessions"
          data-required={requiredItems.sessions}
          aria-label="Session Timings Validation"
        >
          <ListItemIcon>{renderValidationIcon('sessions', validation.sessions)}</ListItemIcon>
          <ListItemText
            primary={
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <span>Session Timings</span>
                {!validation.sessions.isValid &&
                  validation.sessions.unassignedSessions &&
                  validation.sessions.unassignedSessions.length > 0 && (
                    <IconButton
                      size="small"
                      onClick={handleToggleExpand}
                      data-testid="expand-sessions-button"
                      aria-label={
                        expandedSessions
                          ? 'Collapse unassigned sessions'
                          : 'Expand unassigned sessions'
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
                        Assign Timings
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
              ) : (
                <Chip label="Not Required" size="small" sx={{ opacity: 0.6 }} />
              )
            }
          />
        </ListItem>
      </List>
    </Paper>
  );
};
