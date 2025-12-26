/**
 * UnassignedSpeakersList Component (Story 5.7 - Task 4b GREEN Phase)
 *
 * Displays unassigned speakers list with filter controls
 * AC12: Show unassigned speakers list with real-time updates
 * AC5: Draggable speaker cards with grab handle
 * AC7: View preferences button per speaker
 */

import React from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  Chip,
  LinearProgress,
  Button,
  ButtonGroup,
  Stack,
  Skeleton,
} from '@mui/material';
import { DragIndicator, Visibility } from '@mui/icons-material';
import type { Session } from '@/types/event.types';

export interface UnassignedSpeakersListProps {
  sessions: Session[];
  totalSessions: number;
  onViewPreferences: (username: string) => void;
  activeFilter?: 'all' | 'assigned' | 'unassigned';
  onFilterChange?: (filter: 'all' | 'assigned' | 'unassigned') => void;
  isLoading?: boolean;
}

export const UnassignedSpeakersList: React.FC<UnassignedSpeakersListProps> = ({
  sessions,
  totalSessions,
  onViewPreferences,
  activeFilter = 'unassigned',
  onFilterChange,
  isLoading = false,
}) => {
  const assignedCount = totalSessions - sessions.length;
  const progressPercent = totalSessions > 0 ? Math.round((assignedCount / totalSessions) * 100) : 0;

  // Loading skeleton
  if (isLoading) {
    return (
      <Box data-testid="speaker-pool-sidebar" sx={{ height: '100%', p: 2 }}>
        <Skeleton data-testid="skeleton-loader" variant="rectangular" height={60} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" height={40} sx={{ mb: 2 }} />
        <Skeleton data-testid="skeleton-card" variant="rectangular" height={100} sx={{ mb: 1 }} />
        <Skeleton data-testid="skeleton-card" variant="rectangular" height={100} sx={{ mb: 1 }} />
        <Skeleton data-testid="skeleton-card" variant="rectangular" height={100} />
      </Box>
    );
  }

  return (
    <Box
      data-testid="speaker-pool-sidebar"
      sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}
    >
      {/* Header with Progress */}
      <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="h6" gutterBottom>
          Speaker Pool
        </Typography>

        {/* Progress Indicator */}
        <Box sx={{ mb: 2 }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {assignedCount} of {totalSessions} assigned ({progressPercent}%)
          </Typography>
          <LinearProgress
            variant="determinate"
            value={progressPercent}
            role="progressbar"
            sx={{ height: 8, borderRadius: 1 }}
          />
        </Box>

        {/* Unassigned Badge */}
        {sessions.length > 0 && (
          <Chip
            data-testid="unassigned-badge"
            label={`${sessions.length} Remaining`}
            color="warning"
            size="small"
            sx={{ mb: 2 }}
          />
        )}

        {/* Filter Controls */}
        <ButtonGroup size="small" fullWidth data-testid="filter-buttons">
          <Button
            data-testid="filter-all"
            onClick={() => onFilterChange?.('all')}
            variant={activeFilter === 'all' ? 'contained' : 'outlined'}
            className={activeFilter === 'all' ? 'filter-active' : ''}
          >
            All
          </Button>
          <Button
            data-testid="filter-assigned"
            onClick={() => onFilterChange?.('assigned')}
            variant={activeFilter === 'assigned' ? 'contained' : 'outlined'}
            className={activeFilter === 'assigned' ? 'filter-active' : ''}
          >
            Assigned
          </Button>
          <Button
            data-testid="filter-unassigned"
            onClick={() => onFilterChange?.('unassigned')}
            variant={activeFilter === 'unassigned' ? 'contained' : 'outlined'}
            className={activeFilter === 'unassigned' ? 'filter-active' : ''}
          >
            Unassigned
          </Button>
        </ButtonGroup>
      </Box>

      {/* Speaker Cards */}
      <Box sx={{ flex: 1, overflowY: 'auto', p: 2 }}>
        {sessions.length === 0 ? (
          // Empty State
          <Box
            data-testid="empty-state"
            sx={{
              textAlign: 'center',
              py: 4,
              px: 2,
              color: 'text.secondary',
            }}
          >
            <Typography variant="h6" color="success.main" gutterBottom>
              All sessions assigned!
            </Typography>
            <Typography variant="body2">
              Great work! All speakers have been assigned to time slots.
            </Typography>
          </Box>
        ) : (
          <Stack spacing={2}>
            {sessions.map((session) => {
              const speaker = session.speakers?.[0];
              const username = speaker?.username || '';
              const displayName = speaker
                ? `${speaker.firstName} ${speaker.lastName}`
                : 'Unknown Speaker';
              const companyName = speaker?.company || '';

              return (
                <Card
                  key={session.sessionSlug}
                  draggable
                  role="article"
                  aria-label={`Speaker: ${displayName}`}
                  tabIndex={0}
                  sx={{
                    cursor: 'grab',
                    '&:hover': {
                      boxShadow: 3,
                      '& .drag-handle': {
                        opacity: 1,
                      },
                    },
                    '&:active': {
                      cursor: 'grabbing',
                    },
                  }}
                  onMouseEnter={(e) => {
                    (e.currentTarget as HTMLElement).style.cursor = 'grab';
                  }}
                >
                  <CardContent
                    sx={{
                      display: 'flex',
                      alignItems: 'start',
                      gap: 1,
                      p: 2,
                      '&:last-child': { pb: 2 },
                    }}
                  >
                    {/* Drag Handle */}
                    <DragIndicator
                      className="drag-handle"
                      data-testid="drag-handle"
                      sx={{ color: 'text.secondary', opacity: 0.3, transition: 'opacity 0.2s' }}
                    />

                    {/* Speaker Info */}
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Typography variant="subtitle2" noWrap>
                        {session.title || `${displayName}${companyName ? ` - ${companyName}` : ''}`}
                      </Typography>
                    </Box>

                    {/* View Preferences Button */}
                    <Button
                      size="small"
                      startIcon={<Visibility />}
                      onClick={(e) => {
                        e.stopPropagation();
                        onViewPreferences(username);
                      }}
                      data-testid={`view-preferences-${username}`}
                      aria-label="View Preferences"
                      sx={{ flexShrink: 0 }}
                    >
                      Preferences
                    </Button>
                  </CardContent>
                </Card>
              );
            })}
          </Stack>
        )}
      </Box>
    </Box>
  );
};
