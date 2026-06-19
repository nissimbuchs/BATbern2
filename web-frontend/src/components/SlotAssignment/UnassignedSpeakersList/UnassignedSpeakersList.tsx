/**
 * UnassignedSpeakersList Component (Story 5.7 - Task 4b GREEN Phase)
 *
 * Displays unassigned speakers list with filter controls
 * AC12: Show unassigned speakers list with real-time updates
 * AC5: Draggable speaker cards with grab handle
 * AC7: View preferences button per speaker
 */

import React, { useEffect, useRef } from 'react';
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
import { useTranslation } from 'react-i18next';
import type { Session } from '@/types/event.types';

export interface UnassignedSpeakersListProps {
  sessions: Session[];
  totalSessions: number;
  onViewPreferences: (username: string) => void;
  onDragStart?: (session: Session) => (e: React.DragEvent) => void;
  activeFilter?: 'all' | 'assigned' | 'unassigned';
  onFilterChange?: (filter: 'all' | 'assigned' | 'unassigned') => void;
  isLoading?: boolean;
  /**
   * 14.C.5: when set, the matching session card is visually highlighted and
   * scrolled into view (best-effort — no-op if no session matches).
   */
  focusSessionSlug?: string | null;
  /**
   * 14.G.3 (mobile tap-to-assign): when provided, tapping a card selects it
   * (drag-drop is hostile on touch). The selected card is highlighted via
   * `selectedSessionSlug`.
   */
  onSessionTap?: (session: Session) => void;
  selectedSessionSlug?: string | null;
}

export const UnassignedSpeakersList: React.FC<UnassignedSpeakersListProps> = ({
  sessions,
  totalSessions,
  onViewPreferences,
  onDragStart,
  activeFilter = 'unassigned',
  onFilterChange,
  isLoading = false,
  focusSessionSlug = null,
  onSessionTap,
  selectedSessionSlug = null,
}) => {
  const focusedCardRef = useRef<HTMLDivElement | null>(null);

  // Best-effort scroll the focused card into view when it (or the target) changes.
  useEffect(() => {
    if (focusSessionSlug && focusedCardRef.current) {
      focusedCardRef.current.scrollIntoView({ block: 'nearest' });
    }
  }, [focusSessionSlug, sessions]);

  const { t } = useTranslation('events');
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
          {t('slotAssignment.speakerPool.title')}
        </Typography>

        {/* Progress Indicator */}
        <Box sx={{ mb: 2 }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {t('slotAssignment.speakerPool.assigned', {
              count: assignedCount,
              total: totalSessions,
              percent: progressPercent,
            })}
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
            label={t('slotAssignment.speakerPool.remaining', { count: sessions.length })}
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
            {t('slotAssignment.speakerPool.filters.all')}
          </Button>
          <Button
            data-testid="filter-assigned"
            onClick={() => onFilterChange?.('assigned')}
            variant={activeFilter === 'assigned' ? 'contained' : 'outlined'}
            className={activeFilter === 'assigned' ? 'filter-active' : ''}
          >
            {t('slotAssignment.speakerPool.filters.assigned')}
          </Button>
          <Button
            data-testid="filter-unassigned"
            onClick={() => onFilterChange?.('unassigned')}
            variant={activeFilter === 'unassigned' ? 'contained' : 'outlined'}
            className={activeFilter === 'unassigned' ? 'filter-active' : ''}
          >
            {t('slotAssignment.speakerPool.filters.unassigned')}
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
              {t('slotAssignment.speakerPool.emptyState.title')}
            </Typography>
            <Typography variant="body2">
              {t('slotAssignment.speakerPool.emptyState.message')}
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

              const isFocused = !!focusSessionSlug && session.sessionSlug === focusSessionSlug;
              const isSelected =
                !!selectedSessionSlug && session.sessionSlug === selectedSessionSlug;

              return (
                <Card
                  key={session.sessionSlug}
                  ref={isFocused ? focusedCardRef : undefined}
                  data-testid={
                    isFocused
                      ? `focused-session-${session.sessionSlug}`
                      : `tray-session-${session.sessionSlug}`
                  }
                  draggable
                  onDragStart={onDragStart?.(session)}
                  onClick={onSessionTap ? () => onSessionTap(session) : undefined}
                  aria-pressed={onSessionTap ? isSelected : undefined}
                  role="article"
                  aria-label={`${t('common:role.speaker')}: ${displayName}`}
                  tabIndex={0}
                  sx={{
                    cursor: onSessionTap ? 'pointer' : 'grab',
                    ...(isSelected && {
                      borderLeft: 4,
                      borderColor: 'secondary.main',
                      boxShadow: 6,
                      bgcolor: 'action.selected',
                    }),
                    ...(isFocused &&
                      !isSelected && {
                        borderLeft: 4,
                        borderColor: 'primary.main',
                        boxShadow: 4,
                      }),
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
                      <Typography variant="subtitle2" fontWeight="bold" noWrap>
                        {session.title}
                      </Typography>
                      <Typography variant="caption" display="block" color="text.secondary" noWrap>
                        {displayName}
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
                      aria-label={t('slotAssignment.speakerPool.viewPreferences')}
                      sx={{ flexShrink: 0 }}
                    >
                      {t('slotAssignment.speakerPool.viewPreferences')}
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
