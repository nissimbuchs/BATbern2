/**
 * PartnerMeetingsPage
 * Story 8.3: Partner Meeting Coordination — AC1–5, 7, 8
 *
 * Organizer-only page for managing partner meetings.
 * Lists all meetings with event, type, date, location, invite status.
 * Clicking a row expands MeetingDetailPanel (agenda + notes + send invite).
 * Each row has Edit and Delete action buttons.
 */

import React, { useState } from 'react';
import type { TFunction } from 'i18next';
import {
  Alert,
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Chip,
  Collapse,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  IconButton,
  Skeleton,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import {
  Add as AddIcon,
  CalendarMonth as CalendarMonthIcon,
  Delete as DeleteIcon,
  Edit as EditIcon,
  KeyboardArrowDown as ArrowDownIcon,
  KeyboardArrowUp as ArrowUpIcon,
} from '@mui/icons-material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import { usePartnerMeetings } from '@/hooks/usePartnerMeetings';
import { deleteMeeting } from '@/services/api/partnerMeetingsApi';
import CreateMeetingDialog from './CreateMeetingDialog';
import EditMeetingDialog from './EditMeetingDialog';
import MeetingDetailPanel from './MeetingDetailPanel';
import type { PartnerMeetingDTO } from '@/services/api/partnerMeetingsApi';

const MEETING_TYPE_COLOR: Record<string, 'primary' | 'secondary'> = {
  SPRING: 'primary',
  AUTUMN: 'secondary',
};

const PartnerMeetingsPage: React.FC = () => {
  const { t } = useTranslation('partners');
  const { isMobile } = useBreakpoints();
  const { data: meetings, isLoading, isError } = usePartnerMeetings();
  const [createOpen, setCreateOpen] = useState(false);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [editMeeting, setEditMeeting] = useState<PartnerMeetingDTO | null>(null);
  const [deletingMeeting, setDeletingMeeting] = useState<PartnerMeetingDTO | null>(null);

  const queryClient = useQueryClient();

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteMeeting(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['partnerMeetings'] });
      setDeletingMeeting(null);
      if (expandedId === deletingMeeting?.id) setExpandedId(null);
    },
  });

  const toggleRow = (id: string) => {
    setExpandedId((prev) => (prev === id ? null : id));
  };

  const handleEditClick = (e: React.MouseEvent, meeting: PartnerMeetingDTO) => {
    e.stopPropagation();
    setEditMeeting(meeting);
  };

  const handleDeleteClick = (e: React.MouseEvent, meeting: PartnerMeetingDTO) => {
    e.stopPropagation();
    setDeletingMeeting(meeting);
  };

  const handleDeleteConfirm = () => {
    if (deletingMeeting) deleteMutation.mutate(deletingMeeting.id);
  };

  if (isLoading) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }} data-testid="partner-meetings-loading">
        <Skeleton variant="text" width={300} height={40} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" height={400} />
      </Container>
    );
  }

  if (isError) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Alert severity="error" data-testid="partner-meetings-error">
          {t('meetings.error')}
        </Alert>
      </Container>
    );
  }

  return (
    <Container maxWidth="xl" sx={{ py: 4 }} data-testid="partner-meetings-page">
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <CalendarMonthIcon color="primary" />
          <Typography variant="h5">{t('meetings.title')}</Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setCreateOpen(true)}
          data-testid="create-meeting-btn"
        >
          {t('meetings.create')}
        </Button>
      </Box>

      {/* Meeting list */}
      {!meetings || meetings.length === 0 ? (
        <Typography color="text.secondary" data-testid="no-meetings-message">
          {t('meetings.noMeetings')}
        </Typography>
      ) : isMobile ? (
        <Box data-testid="partner-meetings-cards">
          {meetings.map((meeting: PartnerMeetingDTO) => (
            <MeetingCard
              key={meeting.id}
              meeting={meeting}
              expanded={expandedId === meeting.id}
              onToggle={() => toggleRow(meeting.id)}
              onEdit={(e) => handleEditClick(e, meeting)}
              onDelete={(e) => handleDeleteClick(e, meeting)}
              t={t}
            />
          ))}
        </Box>
      ) : (
        <TableContainer component={Paper} sx={{ overflowX: 'auto' }}>
          <Table size="small" data-testid="partner-meetings-table" sx={{ minWidth: 800 }}>
            <TableHead>
              <TableRow>
                <TableCell sx={{ width: 40 }} />
                <TableCell>{t('common:labels.event')}</TableCell>
                <TableCell>{t('meetings.columns.type')}</TableCell>
                <TableCell>{t('common:labels.date')}</TableCell>
                <TableCell>{t('meetings.columns.time')}</TableCell>
                <TableCell>{t('meetings.columns.location')}</TableCell>
                <TableCell>{t('meetings.columns.inviteStatus')}</TableCell>
                <TableCell>{t('common:labels.actions')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {meetings.map((meeting: PartnerMeetingDTO) => (
                <MeetingRow
                  key={meeting.id}
                  meeting={meeting}
                  expanded={expandedId === meeting.id}
                  onToggle={() => toggleRow(meeting.id)}
                  onEdit={(e) => handleEditClick(e, meeting)}
                  onDelete={(e) => handleDeleteClick(e, meeting)}
                  t={t}
                />
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <CreateMeetingDialog open={createOpen} onClose={() => setCreateOpen(false)} />

      <EditMeetingDialog
        open={editMeeting !== null}
        meeting={editMeeting}
        onClose={() => setEditMeeting(null)}
      />

      {/* Delete confirmation dialog */}
      <Dialog
        open={deletingMeeting !== null}
        onClose={() => setDeletingMeeting(null)}
        fullScreen={isMobile}
        data-testid="delete-meeting-dialog"
      >
        <DialogTitle>{t('meetings.deleteConfirm.title')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {deletingMeeting?.inviteSentAt
              ? t('meetings.deleteConfirm.messageWithInvite', {
                  eventCode: deletingMeeting.eventCode,
                })
              : t('meetings.deleteConfirm.message', {
                  eventCode: deletingMeeting?.eventCode,
                })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeletingMeeting(null)} data-testid="delete-meeting-cancel">
            {t('common:actions.cancel')}
          </Button>
          <Button
            color="error"
            variant="contained"
            onClick={handleDeleteConfirm}
            disabled={deleteMutation.isPending}
            data-testid="delete-meeting-confirm"
          >
            {t('meetings.delete')}
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
};

// ─── Meeting row sub-component ────────────────────────────────────────────────

interface MeetingRowProps {
  meeting: PartnerMeetingDTO;
  expanded: boolean;
  onToggle: () => void;
  onEdit: (e: React.MouseEvent) => void;
  onDelete: (e: React.MouseEvent) => void;
  t: TFunction;
}

const MeetingRow: React.FC<MeetingRowProps> = ({
  meeting,
  expanded,
  onToggle,
  onEdit,
  onDelete,
  t,
}) => {
  const meetingDate = meeting.meetingDate
    ? new Date(meeting.meetingDate).toLocaleDateString()
    : '—';

  const meetingTime =
    meeting.startTime && meeting.endTime
      ? `${meeting.startTime.slice(0, 5)} – ${meeting.endTime.slice(0, 5)}`
      : meeting.startTime
        ? meeting.startTime.slice(0, 5)
        : '—';

  const inviteSentDate = meeting.inviteSentAt
    ? new Date(meeting.inviteSentAt).toLocaleDateString()
    : null;

  const typeKey =
    meeting.meetingType === 'SPRING'
      ? 'meetings.fields.type.spring'
      : 'meetings.fields.type.autumn';

  return (
    <>
      <TableRow
        hover
        onClick={onToggle}
        sx={{ cursor: 'pointer', '& > *': { borderBottom: expanded ? 0 : undefined } }}
        data-testid={`meeting-row-${meeting.id}`}
      >
        <TableCell>
          <IconButton size="small">{expanded ? <ArrowUpIcon /> : <ArrowDownIcon />}</IconButton>
        </TableCell>
        <TableCell>{meeting.eventCode}</TableCell>
        <TableCell>
          <Chip
            label={t(typeKey)}
            color={MEETING_TYPE_COLOR[meeting.meetingType] ?? 'default'}
            size="small"
          />
        </TableCell>
        <TableCell>{meetingDate}</TableCell>
        <TableCell>{meetingTime}</TableCell>
        <TableCell>{meeting.location ?? '—'}</TableCell>
        <TableCell>
          {inviteSentDate ? (
            <Chip
              label={t('meetings.inviteSent')}
              color="success"
              size="small"
              data-testid={`invite-sent-${meeting.id}`}
            />
          ) : (
            <Chip
              label={t('meetings.inviteNotSent')}
              variant="outlined"
              size="small"
              data-testid={`invite-not-sent-${meeting.id}`}
            />
          )}
        </TableCell>
        <TableCell onClick={(e) => e.stopPropagation()}>
          <IconButton
            size="small"
            onClick={onEdit}
            aria-label={t('meetings.edit')}
            data-testid={`edit-meeting-${meeting.id}`}
          >
            <EditIcon fontSize="small" />
          </IconButton>
          <IconButton
            size="small"
            color="error"
            onClick={onDelete}
            aria-label={t('meetings.delete')}
            data-testid={`delete-meeting-${meeting.id}`}
          >
            <DeleteIcon fontSize="small" />
          </IconButton>
        </TableCell>
      </TableRow>

      {/* Expanded detail panel */}
      <TableRow>
        <TableCell colSpan={8} sx={{ py: 0 }}>
          <Collapse in={expanded} timeout="auto" unmountOnExit>
            <MeetingDetailPanel meeting={meeting} />
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
};

// ─── Meeting card sub-component (mobile) ──────────────────────────────────────

const MeetingCard: React.FC<MeetingRowProps> = ({
  meeting,
  expanded,
  onToggle,
  onEdit,
  onDelete,
  t,
}) => {
  const meetingDate = meeting.meetingDate
    ? new Date(meeting.meetingDate).toLocaleDateString()
    : '—';

  const meetingTime =
    meeting.startTime && meeting.endTime
      ? `${meeting.startTime.slice(0, 5)} – ${meeting.endTime.slice(0, 5)}`
      : meeting.startTime
        ? meeting.startTime.slice(0, 5)
        : '—';

  const inviteSentDate = meeting.inviteSentAt
    ? new Date(meeting.inviteSentAt).toLocaleDateString()
    : null;

  const typeKey =
    meeting.meetingType === 'SPRING'
      ? 'meetings.fields.type.spring'
      : 'meetings.fields.type.autumn';

  return (
    <Card sx={{ mb: 2 }} data-testid={`meeting-card-${meeting.id}`}>
      <CardContent
        sx={{ cursor: 'pointer' }}
        onClick={onToggle}
        data-testid={`meeting-card-toggle-${meeting.id}`}
      >
        <Stack spacing={1}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
            <Typography variant="subtitle1" fontWeight={600}>
              {meeting.eventCode}
            </Typography>
            <Chip
              label={t(typeKey)}
              color={MEETING_TYPE_COLOR[meeting.meetingType] ?? 'default'}
              size="small"
            />
          </Stack>
          <Typography variant="body2" color="text.secondary">
            {meetingDate} · {meetingTime}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {meeting.location ?? '—'}
          </Typography>
          <Box>
            {inviteSentDate ? (
              <Chip
                label={t('meetings.inviteSent')}
                color="success"
                size="small"
                data-testid={`invite-sent-${meeting.id}`}
              />
            ) : (
              <Chip
                label={t('meetings.inviteNotSent')}
                variant="outlined"
                size="small"
                data-testid={`invite-not-sent-${meeting.id}`}
              />
            )}
          </Box>
        </Stack>
        <Collapse in={expanded} timeout="auto" unmountOnExit>
          <Box sx={{ mt: 2 }} onClick={(e) => e.stopPropagation()}>
            <MeetingDetailPanel meeting={meeting} />
          </Box>
        </Collapse>
      </CardContent>
      <CardActions>
        <IconButton
          size="small"
          onClick={onEdit}
          aria-label={t('meetings.edit')}
          data-testid={`edit-meeting-${meeting.id}`}
        >
          <EditIcon fontSize="small" />
        </IconButton>
        <IconButton
          size="small"
          color="error"
          onClick={onDelete}
          aria-label={t('meetings.delete')}
          data-testid={`delete-meeting-${meeting.id}`}
        >
          <DeleteIcon fontSize="small" />
        </IconButton>
        <Box sx={{ ml: 'auto' }}>
          <IconButton
            size="small"
            onClick={onToggle}
            aria-label={t('meetings.title', 'Meeting details')}
          >
            {expanded ? <ArrowUpIcon /> : <ArrowDownIcon />}
          </IconButton>
        </Box>
      </CardActions>
    </Card>
  );
};

export default PartnerMeetingsPage;
