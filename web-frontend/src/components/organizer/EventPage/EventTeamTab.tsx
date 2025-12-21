/**
 * EventTeamTab Component (Story 5.6)
 *
 * Team assignments and outreach distribution
 */

import React from 'react';
import {
  Paper,
  Typography,
  Button,
  Stack,
  Divider,
  Avatar,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  IconButton,
} from '@mui/material';
import {
  PersonAdd as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { Event, EventDetailUI } from '@/types/event.types';

interface EventTeamTabProps {
  event: Event | EventDetailUI;
  eventCode: string;
}

// Mock team data (would come from API)
interface TeamMember {
  id: string;
  name: string;
  role: 'lead' | 'co-organizer' | 'moderator' | 'reviewer';
  email: string;
  speakersAssigned?: number;
  speakersContacted?: number;
}

export const EventTeamTab: React.FC<EventTeamTabProps> = ({ event, eventCode: _eventCode }) => {
  const { t } = useTranslation('events');

  // Mock team data
  const team: TeamMember[] = [
    {
      id: '1',
      name: event.organizerUsername || 'Lead Organizer',
      role: 'lead',
      email: 'lead@batbern.ch',
      speakersAssigned: 8,
      speakersContacted: 6,
    },
    {
      id: '2',
      name: 'Mark Thompson',
      role: 'co-organizer',
      email: 'mark@batbern.ch',
      speakersAssigned: 5,
      speakersContacted: 3,
    },
    {
      id: '3',
      name: 'Anna Weber',
      role: 'co-organizer',
      email: 'anna@batbern.ch',
      speakersAssigned: 5,
      speakersContacted: 4,
    },
  ];

  const getRoleColor = (role: TeamMember['role']) => {
    switch (role) {
      case 'lead':
        return 'primary';
      case 'co-organizer':
        return 'secondary';
      case 'moderator':
        return 'info';
      case 'reviewer':
        return 'warning';
      default:
        return 'default';
    }
  };

  const getRoleLabel = (role: TeamMember['role']) => {
    switch (role) {
      case 'lead':
        return t('eventPage.team.leadOrganizer', 'Lead Organizer');
      case 'co-organizer':
        return t('eventPage.team.coOrganizer', 'Co-Organizer');
      case 'moderator':
        return t('eventPage.team.moderator', 'Moderator');
      case 'reviewer':
        return t('eventPage.team.reviewer', 'Content Reviewer');
      default:
        return role;
    }
  };

  return (
    <Stack spacing={3}>
      {/* Team Assignments */}
      <Paper sx={{ p: 3 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">
            {t('eventPage.team.teamAssignments', 'Team Assignments')}
          </Typography>
          <Button variant="outlined" startIcon={<AddIcon />} size="small">
            {t('eventPage.team.addMember', 'Add Team Member')}
          </Button>
        </Stack>
        <Divider sx={{ mb: 2 }} />

        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>{t('eventPage.team.member', 'Team Member')}</TableCell>
                <TableCell>{t('eventPage.team.role', 'Role')}</TableCell>
                <TableCell>{t('eventPage.team.email', 'Email')}</TableCell>
                <TableCell align="right">{t('common.actions', 'Actions')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {team.map((member) => (
                <TableRow key={member.id}>
                  <TableCell>
                    <Stack direction="row" spacing={2} alignItems="center">
                      <Avatar sx={{ width: 32, height: 32, bgcolor: 'primary.main' }}>
                        {member.name.charAt(0)}
                      </Avatar>
                      <Typography variant="body2">{member.name}</Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={getRoleLabel(member.role)}
                      size="small"
                      color={getRoleColor(member.role)}
                      variant="outlined"
                    />
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">
                      {member.email}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <IconButton size="small">
                      <EditIcon fontSize="small" />
                    </IconButton>
                    {member.role !== 'lead' && (
                      <IconButton size="small" color="error">
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      {/* Outreach Distribution */}
      <Paper sx={{ p: 3 }}>
        <Typography variant="h6" gutterBottom>
          {t('eventPage.team.outreachDistribution', 'Speaker Outreach Distribution')}
        </Typography>
        <Divider sx={{ mb: 2 }} />

        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>{t('eventPage.team.organizer', 'Organizer')}</TableCell>
                <TableCell align="center">
                  {t('eventPage.team.assigned', 'Assigned')}
                </TableCell>
                <TableCell align="center">
                  {t('eventPage.team.contacted', 'Contacted')}
                </TableCell>
                <TableCell align="center">
                  {t('eventPage.team.pending', 'Pending')}
                </TableCell>
                <TableCell align="center">
                  {t('eventPage.team.progress', 'Progress')}
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {team
                .filter((m) => m.speakersAssigned !== undefined)
                .map((member) => {
                  const pending = (member.speakersAssigned || 0) - (member.speakersContacted || 0);
                  const progress =
                    member.speakersAssigned && member.speakersAssigned > 0
                      ? Math.round(((member.speakersContacted || 0) / member.speakersAssigned) * 100)
                      : 0;
                  return (
                    <TableRow key={member.id}>
                      <TableCell>
                        <Typography variant="body2">{member.name}</Typography>
                      </TableCell>
                      <TableCell align="center">
                        <Typography variant="body2">{member.speakersAssigned}</Typography>
                      </TableCell>
                      <TableCell align="center">
                        <Chip
                          label={member.speakersContacted}
                          size="small"
                          color="success"
                          variant="outlined"
                        />
                      </TableCell>
                      <TableCell align="center">
                        <Chip
                          label={pending}
                          size="small"
                          color={pending > 0 ? 'warning' : 'default'}
                          variant="outlined"
                        />
                      </TableCell>
                      <TableCell align="center">
                        <Typography
                          variant="body2"
                          color={progress >= 100 ? 'success.main' : 'text.secondary'}
                        >
                          {progress}%
                        </Typography>
                      </TableCell>
                    </TableRow>
                  );
                })}
            </TableBody>
          </Table>
        </TableContainer>

        <Stack direction="row" justifyContent="flex-end" mt={2}>
          <Button variant="outlined" size="small">
            {t('eventPage.team.reassignSpeakers', 'Reassign Speakers')}
          </Button>
        </Stack>
      </Paper>
    </Stack>
  );
};

export default EventTeamTab;
