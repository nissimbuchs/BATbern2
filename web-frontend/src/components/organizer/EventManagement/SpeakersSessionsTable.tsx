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
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import {
  Folder as FolderIcon,
  Delete as DeleteIcon,
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
  onViewMaterials: (sessionId: string) => void; // Kept for backwards compatibility
  onSessionUpdate: (sessionSlug: string, updates: SessionUpdateData) => Promise<void>;
  onSessionDelete?: (sessionSlug: string) => Promise<void>;
  isLoading?: boolean;
  error?: string;
}

export const SpeakersSessionsTable: React.FC<SpeakersSessionsTableProps> = ({
  sessions,
  eventCode: _eventCode, // eslint-disable-line @typescript-eslint/no-unused-vars
  eventDate,
  onViewMaterials: _onViewMaterials, // eslint-disable-line @typescript-eslint/no-unused-vars
  onSessionUpdate,
  onSessionDelete,
  isLoading = false,
  error,
}) => {
  const { t, i18n } = useTranslation('events');
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [selectedSession, setSelectedSession] = useState<SessionUI | null>(null);
  const [modalInitialTab, setModalInitialTab] = useState<number>(0); // Story 5.9 - AC2
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [sessionToDelete, setSessionToDelete] = useState<SessionUI | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

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

  // Materials status icon and color mapping (Story 5.9 - AC2)
  const getMaterialsStatusIcon = (session: SessionUI) => {
    // Story 5.9: Use materialsStatus from backend (NONE, PARTIAL, COMPLETE)
    const status: string = session.materialsStatus || 'NONE';

    if (status === 'NONE') {
      // ❌ Missing: No materials uploaded
      return <ErrorIcon color="error" fontSize="small" data-testid="materials-status-missing" />;
    }

    if (status === 'PARTIAL') {
      // ⚠️ Pending: Some materials but incomplete
      return (
        <WarningIcon color="warning" fontSize="small" data-testid="materials-status-pending" />
      );
    }

    // ✓ Complete: All materials uploaded
    return <CheckIcon color="success" fontSize="small" data-testid="materials-status-complete" />;
  };

  const getMaterialsStatusLabel = (session: SessionUI) => {
    // Story 5.9: Use materialsStatus from backend (NONE, PARTIAL, COMPLETE)
    const status: string = session.materialsStatus || 'NONE';

    if (status === 'NONE') {
      return t('speakers.materialsMissing');
    }

    if (status === 'PARTIAL') {
      return t('speakers.materialsPending');
    }

    return t('speakers.materialsComplete');
  };

  const handleRowClick = (session: SessionUI) => {
    setSelectedSession(session);
    setModalInitialTab(0); // Open on Details tab
    setEditModalOpen(true);
  };

  // Story 5.9 - AC2: Materials button opens SessionEditModal on Materials tab
  const handleMaterialsClick = (session: SessionUI) => {
    setSelectedSession(session);
    setModalInitialTab(1); // Open on Materials tab
    setEditModalOpen(true);
  };

  const handleEditModalClose = () => {
    setEditModalOpen(false);
    setSelectedSession(null);
    setModalInitialTab(0); // Reset to Details tab
  };

  const handleDeleteClick = (session: SessionUI) => {
    setSessionToDelete(session);
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = async () => {
    if (!sessionToDelete || !onSessionDelete) return;
    try {
      setDeleteLoading(true);
      await onSessionDelete(sessionToDelete.sessionSlug);
    } finally {
      setDeleteLoading(false);
      setDeleteDialogOpen(false);
      setSessionToDelete(null);
    }
  };

  const handleDeleteCancel = () => {
    setDeleteDialogOpen(false);
    setSessionToDelete(null);
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
                      {getMaterialsStatusIcon(session)}
                      <Typography variant="caption">{getMaterialsStatusLabel(session)}</Typography>
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
                      {getMaterialsStatusIcon(session)}
                      <Typography variant="caption">{getMaterialsStatusLabel(session)}</Typography>
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
                    handleMaterialsClick(session); // Story 5.9 - AC2
                  }}
                >
                  {t('speakers.materials')}
                </Button>
              )}
              {onSessionDelete && (
                <IconButton
                  size="small"
                  color="error"
                  aria-label={t('speakers.deleteSession')}
                  onClick={(e) => {
                    e.stopPropagation();
                    handleDeleteClick(session);
                  }}
                >
                  <DeleteIcon fontSize="small" />
                </IconButton>
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
                    {getMaterialsStatusIcon(session)}
                    <Typography variant="caption">{getMaterialsStatusLabel(session)}</Typography>
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
                          handleMaterialsClick(session); // Story 5.9 - AC2
                        }}
                      >
                        {t('speakers.materials')}
                      </Button>
                    )}
                    {onSessionDelete && (
                      <IconButton
                        size="small"
                        color="error"
                        aria-label={t('speakers.deleteSession')}
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDeleteClick(session);
                        }}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
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
        initialTab={modalInitialTab} // Story 5.9 - AC2: Materials button opens Materials tab
      />

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onClose={handleDeleteCancel}>
        <DialogTitle>{t('speakers.deleteSessionTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('speakers.deleteSessionMessage', { title: sessionToDelete?.title || sessionToDelete?.sessionSlug })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleDeleteCancel} disabled={deleteLoading}>
            {t('common.cancel', 'Cancel')}
          </Button>
          <Button onClick={handleDeleteConfirm} color="error" disabled={deleteLoading}>
            {t('speakers.deleteSession')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
