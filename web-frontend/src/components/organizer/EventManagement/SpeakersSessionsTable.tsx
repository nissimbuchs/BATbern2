/**
 * SpeakersSessionsTable Component (Task 13b - GREEN Phase)
 *
 * Story 2.5.3 - AC8: Speakers & Sessions Display
 * Wireframe: docs/wireframes/story-1.16-event-detail-edit.md v1.1 (lines 83-101)
 *
 * Features:
 * - Display 12 session slots with time, speaker, company, session title
 * - Materials status indicators (Complete ✓, Pending ⚠️, Missing ❌)
 * - Session-level actions (View Details, Edit Slot, Materials)
 * - Footer actions (View Full Agenda, Manage Speaker Assignments, Auto-Assign Speakers)
 * - Empty/Loading/Error states
 * - i18n support (German/English)
 * - Responsive design (table on desktop, cards on mobile)
 * - Accessibility (ARIA labels, keyboard navigation)
 */

import React, { useState } from 'react';
import {
  Box,
  Typography,
  Button,
  Card,
  CardContent,
  CardActions,
  Alert,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Stack,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import {
  Folder as FolderIcon,
  CheckCircle as CheckIcon,
  Warning as WarningIcon,
  Error as ErrorIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { UserAvatar } from '@/components/shared/UserAvatar';
import { SessionEditModal, type SessionUpdateData } from './SessionEditModal';
import type { SessionUI } from '@/types/event.types';

interface SpeakersSessionsTableProps {
  sessions: SessionUI[];
  eventCode: string;
  eventDate: string; // ISO 8601 date for time conversion
  onViewMaterials: (sessionId: string) => void;
  onSessionUpdate: (sessionSlug: string, updates: SessionUpdateData) => Promise<void>;
  isLoading?: boolean;
  error?: string;
}

export const SpeakersSessionsTable: React.FC<SpeakersSessionsTableProps> = ({
  sessions,
  eventCode: _eventCode, // eslint-disable-line @typescript-eslint/no-unused-vars
  eventDate,
  onViewMaterials,
  onSessionUpdate,
  isLoading = false,
  error,
}) => {
  const { t, i18n } = useTranslation('events');
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [selectedSession, setSelectedSession] = useState<SessionUI | null>(null);

  // Sort sessions by start time (unassigned sessions go to the end)
  const sortedSessions = React.useMemo(() => {
    return [...sessions].sort((a, b) => {
      // Sessions without start time go to the end
      if (!a.startTime && !b.startTime) return 0;
      if (!a.startTime) return 1;
      if (!b.startTime) return -1;

      // Parse ISO 8601 timestamps for comparison
      const timeA = new Date(a.startTime).getTime();
      const timeB = new Date(b.startTime).getTime();

      return timeA - timeB;
    });
  }, [sessions]);

  // Format ISO 8601 timestamp or simple time string (HH:mm) to localized time string
  const formatTime = (isoTimestamp: string | null | undefined): string => {
    if (!isoTimestamp) {
      return ''; // Session not yet assigned to time slot
    }

    // If it's already in HH:mm format (e.g., "09:00"), return as-is
    if (/^\d{2}:\d{2}$/.test(isoTimestamp)) {
      return isoTimestamp;
    }

    try {
      const date = new Date(isoTimestamp);
      // Check if date is valid
      if (isNaN(date.getTime())) {
        return isoTimestamp; // Fallback to raw string if invalid
      }
      return date.toLocaleTimeString(i18n.language, {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false, // Use 24-hour format
      });
    } catch {
      return isoTimestamp; // Fallback to raw string if parsing fails
    }
  };

  // Materials status icon and color mapping (QualityReviewStatus from architecture)
  const getMaterialsStatusIcon = (
    status?:
      | 'pending'
      | 'in_review'
      | 'approved'
      | 'requires_changes'
      | 'rejected'
      | 'revision_submitted'
  ) => {
    // Default to 'pending' if status is not available (Phase 2 feature)
    const effectiveStatus = status || 'pending';
    switch (effectiveStatus) {
      case 'approved':
        return (
          <CheckIcon color="success" fontSize="small" data-testid="materials-status-complete" />
        );
      case 'in_review':
      case 'revision_submitted':
      case 'pending':
        return (
          <WarningIcon color="warning" fontSize="small" data-testid="materials-status-pending" />
        );
      case 'rejected':
      case 'requires_changes':
        return <ErrorIcon color="error" fontSize="small" data-testid="materials-status-missing" />;
    }
  };

  const getMaterialsStatusLabel = (
    status?:
      | 'pending'
      | 'in_review'
      | 'approved'
      | 'requires_changes'
      | 'rejected'
      | 'revision_submitted'
  ) => {
    // Default to 'pending' if status is not available (Phase 2 feature)
    const effectiveStatus = status || 'pending';
    switch (effectiveStatus) {
      case 'approved':
        return t('speakers.materialsComplete');
      case 'in_review':
      case 'revision_submitted':
      case 'pending':
        return t('speakers.materialsPending');
      case 'rejected':
      case 'requires_changes':
        return t('speakers.materialsMissing');
      default:
        return '';
    }
  };

  const handleRowClick = (session: SessionUI) => {
    setSelectedSession(session);
    setEditModalOpen(true);
  };

  const handleEditModalClose = () => {
    setEditModalOpen(false);
    setSelectedSession(null);
  };

  // Loading state
  if (isLoading) {
    return (
      <Box data-testid="sessions-loading">
        <Typography variant="h6" gutterBottom>
          {t('speakers.sectionTitle', { count: 12 })}
        </Typography>
        {Array.from({ length: 12 }).map((_, index) => (
          <Skeleton key={index} variant="rectangular" height={80} sx={{ mb: 1 }} />
        ))}
      </Box>
    );
  }

  // Error state
  if (error) {
    return (
      <Box>
        <Typography variant="h6" gutterBottom>
          {t('speakers.sectionTitle', { count: 12 })}
        </Typography>
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
          <Button size="small" onClick={() => window.location.reload()}>
            {t('common.retry', 'Retry')}
          </Button>
        </Alert>
      </Box>
    );
  }

  // Empty state
  if (sessions.length === 0) {
    return (
      <Box>
        <Typography variant="h6" gutterBottom>
          {t('speakers.sectionTitle', { count: 0 })}
        </Typography>
        <Alert severity="info" sx={{ mb: 2 }}>
          {t('speakers.noSessionsScheduled')}
        </Alert>
      </Box>
    );
  }

  // Mobile card view
  if (isMobile) {
    return (
      <Box>
        <Typography variant="h6" gutterBottom>
          {t('speakers.sectionTitle', { count: sortedSessions.length })}
        </Typography>

        {sortedSessions.map((session) => (
          <Card
            key={session.sessionSlug}
            sx={{ mb: 2, cursor: 'pointer', '&:hover': { bgcolor: 'action.hover' } }}
            data-testid={`session-card-${session.sessionSlug}`}
            onClick={() => handleRowClick(session)}
          >
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="subtitle2" color="text.secondary">
                  {formatTime(session.startTime)}-{formatTime(session.endTime)}
                </Typography>

                {session.speakers && session.speakers.length > 0 ? (
                  <>
                    <Stack direction="column" spacing={1}>
                      {session.speakers.map((spk) => (
                        <UserAvatar
                          key={spk.username}
                          firstName={spk.firstName}
                          lastName={spk.lastName}
                          company={spk.company}
                          profilePictureUrl={spk.profilePictureUrl}
                          size={48}
                          showCompany={true}
                          horizontal={true}
                        />
                      ))}
                    </Stack>
                    <Typography variant="body2">{session.title}</Typography>
                    <Stack direction="row" spacing={1} alignItems="center">
                      {getMaterialsStatusIcon(session.materialsStatus)}
                      <Typography variant="caption">
                        {getMaterialsStatusLabel(session.materialsStatus)}
                      </Typography>
                    </Stack>
                  </>
                ) : session.speaker ? (
                  <>
                    <UserAvatar
                      name={session.speaker.name}
                      company={session.speaker.company}
                      profilePictureUrl={session.speaker.profilePictureUrl}
                      size={48}
                      showCompany={true}
                      horizontal={true}
                    />
                    <Typography variant="body2">{session.title}</Typography>
                    <Stack direction="row" spacing={1} alignItems="center">
                      {getMaterialsStatusIcon(session.materialsStatus)}
                      <Typography variant="caption">
                        {getMaterialsStatusLabel(session.materialsStatus)}
                      </Typography>
                    </Stack>
                  </>
                ) : (
                  <Typography variant="body2" color="text.secondary">
                    {t('speakers.notAssigned')}
                  </Typography>
                )}
              </Stack>
            </CardContent>

            <CardActions>
              {session.speaker && (
                <Button
                  size="small"
                  startIcon={<FolderIcon />}
                  onClick={(e) => {
                    e.stopPropagation();
                    onViewMaterials(session.sessionSlug);
                  }}
                >
                  {t('speakers.materials')}
                </Button>
              )}
            </CardActions>
          </Card>
        ))}
      </Box>
    );
  }

  // Desktop table view
  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        {t('speakers.sectionTitle', { count: sortedSessions.length })}
      </Typography>

      <TableContainer component={Paper} sx={{ mb: 3 }}>
        <Table aria-label="speakers and sessions table">
          <TableHead>
            <TableRow>
              <TableCell>{t('speakers.slotTime')}</TableCell>
              <TableCell>{t('speakers.speakerName')}</TableCell>
              <TableCell>{t('speakers.sessionTitle')}</TableCell>
              <TableCell>{t('speakers.materials')}</TableCell>
              <TableCell align="right">{t('common.actions', 'Actions')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {sortedSessions.map((session) => (
              <TableRow
                key={session.sessionSlug}
                data-testid={`session-row-${session.sessionSlug}`}
                hover
                onClick={() => handleRowClick(session)}
                sx={{ cursor: 'pointer' }}
              >
                <TableCell>
                  <Typography variant="body2">
                    {formatTime(session.startTime)}-{formatTime(session.endTime)}
                  </Typography>
                </TableCell>

                <TableCell>
                  {session.speakers && session.speakers.length > 0 ? (
                    <Stack direction="column" spacing={1}>
                      {session.speakers.map((spk) => (
                        <UserAvatar
                          key={spk.username}
                          firstName={spk.firstName}
                          lastName={spk.lastName}
                          company={spk.company}
                          profilePictureUrl={spk.profilePictureUrl}
                          size={40}
                          showCompany={true}
                          horizontal={true}
                        />
                      ))}
                    </Stack>
                  ) : session.speaker ? (
                    <UserAvatar
                      name={session.speaker.name}
                      company={session.speaker.company}
                      profilePictureUrl={session.speaker.profilePictureUrl}
                      size={40}
                      showCompany={true}
                      horizontal={true}
                    />
                  ) : (
                    <Typography variant="body2" color="text.secondary" fontStyle="italic">
                      {t('speakers.notAssigned')}
                    </Typography>
                  )}
                </TableCell>

                <TableCell>
                  {session.title ? (
                    <Typography variant="body2">{session.title}</Typography>
                  ) : (
                    <Typography variant="body2" color="text.secondary">
                      -
                    </Typography>
                  )}
                </TableCell>

                <TableCell>
                  <Stack direction="row" spacing={1} alignItems="center">
                    {getMaterialsStatusIcon(session.materialsStatus)}
                    <Typography variant="caption">
                      {getMaterialsStatusLabel(session.materialsStatus)}
                    </Typography>
                  </Stack>
                </TableCell>

                <TableCell align="right">
                  <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                    {session.speaker && (
                      <Button
                        size="small"
                        startIcon={<FolderIcon />}
                        onClick={(e) => {
                          e.stopPropagation();
                          onViewMaterials(session.sessionSlug);
                        }}
                      >
                        {t('speakers.materials')}
                      </Button>
                    )}
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Session Edit Modal */}
      <SessionEditModal
        open={editModalOpen}
        onClose={handleEditModalClose}
        session={selectedSession}
        eventDate={eventDate}
        onSave={onSessionUpdate}
      />
    </Box>
  );
};
