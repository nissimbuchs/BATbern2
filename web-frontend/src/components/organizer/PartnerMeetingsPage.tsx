/**
 * PartnerMeetingsPage
 * Story 8.3: Partner Meeting Coordination — AC1–5, 7, 8
 *
 * Organizer-only page for managing partner meetings.
 * Lists all meetings with event, type, date, location, invite status.
 * Clicking a row expands MeetingDetailPanel (agenda + notes + send invite).
 */

import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Collapse,
  Container,
  IconButton,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import {
  Add as AddIcon,
  CalendarMonth as CalendarMonthIcon,
  KeyboardArrowDown as ArrowDownIcon,
  KeyboardArrowUp as ArrowUpIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { usePartnerMeetings } from '@/hooks/usePartnerMeetings';
import CreateMeetingDialog from './CreateMeetingDialog';
import MeetingDetailPanel from './MeetingDetailPanel';
import type { PartnerMeetingDTO } from '@/services/api/partnerMeetingsApi';

const MEETING_TYPE_COLOR: Record<string, 'primary' | 'secondary'> = {
  SPRING: 'primary',
  AUTUMN: 'secondary',
};

const PartnerMeetingsPage: React.FC = () => {
  const { t } = useTranslation('partners');
  const { data: meetings, isLoading, isError } = usePartnerMeetings();
  const [createOpen, setCreateOpen] = useState(false);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const toggleRow = (id: string) => {
    setExpandedId((prev) => (prev === id ? null : id));
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

      {/* Meeting table */}
      {!meetings || meetings.length === 0 ? (
        <Typography color="text.secondary" data-testid="no-meetings-message">
          {t('meetings.noMeetings')}
        </Typography>
      ) : (
        <Table size="small" data-testid="partner-meetings-table">
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 40 }} />
              <TableCell>{t('common:labels.event')}</TableCell>
              <TableCell>{t('meetings.columns.type')}</TableCell>
              <TableCell>{t('common:labels.date')}</TableCell>
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
                t={t}
              />
            ))}
          </TableBody>
        </Table>
      )}

      <CreateMeetingDialog open={createOpen} onClose={() => setCreateOpen(false)} />
    </Container>
  );
};

// ─── Meeting row sub-component ────────────────────────────────────────────────

interface MeetingRowProps {
  meeting: PartnerMeetingDTO;
  expanded: boolean;
  onToggle: () => void;
  t: (key: string, opts?: Record<string, unknown>) => string;
}

const MeetingRow: React.FC<MeetingRowProps> = ({ meeting, expanded, onToggle, t }) => {
  const meetingDate = meeting.meetingDate
    ? new Date(meeting.meetingDate).toLocaleDateString()
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
        <TableCell />
      </TableRow>

      {/* Expanded detail panel */}
      <TableRow>
        <TableCell colSpan={7} sx={{ py: 0 }}>
          <Collapse in={expanded} timeout="auto" unmountOnExit>
            <MeetingDetailPanel meeting={meeting} />
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
};

export default PartnerMeetingsPage;
