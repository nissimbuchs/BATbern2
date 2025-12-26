/**
 * ConflictDetectionAlert Component (Story 5.7 - Task 4b GREEN Phase)
 *
 * Displays conflict detection modal with resolution options
 * AC9: Warn if speaker has conflicting commitment at same time
 * Conflict types: room_overlap, speaker_double_booked, speaker_unavailable
 */

import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Chip,
  Alert,
  Stack,
  Paper,
} from '@mui/material';
import { Warning, Error as ErrorIcon, Info } from '@mui/icons-material';
import type { TimingConflictError } from '@/types/event.types';

export interface ConflictDetectionAlertProps {
  conflict: TimingConflictError | null;
  isOpen: boolean;
  onClose: () => void;
  onResolve: () => void;
}

export const ConflictDetectionAlert: React.FC<ConflictDetectionAlertProps> = ({
  conflict,
  isOpen,
  onClose,
  onResolve,
}) => {
  if (!conflict || !isOpen) {
    return null;
  }

  const firstConflict = conflict.conflicts[0];
  const conflictTypeLabels = {
    room_overlap: 'Room Overlap',
    speaker_double_booked: 'Speaker Double-Booked',
    speaker_unavailable: 'Speaker Unavailable',
  };

  const conflictSeverity = firstConflict.type === 'speaker_unavailable' ? 'warning' : 'error';

  const getConflictIcon = () => {
    if (conflictSeverity === 'error') {
      return <ErrorIcon color="error" />;
    }
    return <Warning color="warning" />;
  };

  return (
    <Dialog open={isOpen} onClose={onClose} maxWidth="md" fullWidth role="dialog">
      <DialogTitle>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {getConflictIcon()}
          <Typography variant="h6">Timing Conflict Detected</Typography>
        </Box>
      </DialogTitle>

      <DialogContent>
        <Stack spacing={3}>
          {/* Conflict Message */}
          <Alert severity={conflictSeverity}>
            <Typography variant="body1" sx={{ fontWeight: 500 }}>
              {conflict.message}
            </Typography>
          </Alert>

          {/* Conflict Details */}
          {conflict.conflicts.map((conf, index) => (
            <Paper key={index} sx={{ p: 2, bgcolor: 'background.default' }}>
              <Box sx={{ mb: 2 }}>
                <Chip
                  data-testid="conflict-type-badge"
                  label={conflictTypeLabels[conf.type]}
                  color={conflictSeverity === 'error' ? 'error' : 'warning'}
                  size="small"
                  sx={{ mb: 1 }}
                />
                <Chip
                  data-testid="conflict-severity"
                  label={conflictSeverity.toUpperCase()}
                  className={`severity-${conflictSeverity}`}
                  size="small"
                  sx={{ ml: 1 }}
                />
              </Box>

              <Typography variant="body2" color="text.secondary" gutterBottom>
                {conf.details}
              </Typography>

              {/* Visual Timeline */}
              <Box
                data-testid="conflict-timeline"
                sx={{
                  mt: 2,
                  p: 2,
                  bgcolor: 'background.paper',
                  borderRadius: 1,
                  border: 1,
                  borderColor: conflictSeverity === 'error' ? 'error.light' : 'warning.light',
                }}
              >
                <Typography variant="caption" color="text.secondary" gutterBottom>
                  Conflict Time Range
                </Typography>
                <Box sx={{ display: 'flex', gap: 2, mt: 1 }}>
                  <Box>
                    <Typography variant="caption" color="text.secondary">
                      Start
                    </Typography>
                    <Typography variant="body2" fontWeight="medium">
                      {new Date(conf.conflictingTimeRange.start).toLocaleTimeString('en-US', {
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">
                      End
                    </Typography>
                    <Typography variant="body2" fontWeight="medium">
                      {new Date(conf.conflictingTimeRange.end).toLocaleTimeString('en-US', {
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </Typography>
                  </Box>
                </Box>
              </Box>

              {/* Conflicting Session Info */}
              {conf.conflictingSessionSlug && (
                <Box
                  data-testid="conflicting-slot"
                  sx={{
                    mt: 2,
                    p: 1.5,
                    bgcolor: 'error.lighter',
                    borderRadius: 1,
                    border: 1,
                    borderColor: 'error.light',
                  }}
                  className="conflicting-slot-highlight"
                >
                  <Typography variant="caption" color="text.secondary">
                    Conflicting Session
                  </Typography>
                  <Typography variant="body2" fontWeight="medium">
                    {conf.conflictingSessionSlug}
                  </Typography>
                </Box>
              )}
            </Paper>
          ))}

          {/* Resolution Options */}
          <Box>
            <Typography variant="subtitle2" gutterBottom>
              Resolution Options
            </Typography>
            <Stack spacing={1}>
              <Alert severity="info" icon={<Info />}>
                <Typography variant="body2">
                  Choose a different time slot or room to resolve this conflict.
                </Typography>
              </Alert>
            </Stack>
          </Box>
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} variant="outlined">
          Cancel
        </Button>
        <Button onClick={onResolve} variant="contained" color="primary">
          Choose Different Slot
        </Button>
      </DialogActions>
    </Dialog>
  );
};
