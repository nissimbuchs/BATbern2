/**
 * Event Participant Table Component (GREEN Phase)
 *
 * Displays event participants in a sortable table.
 * Story 3.3: Event Participants Tab - Task 6 (GREEN Phase)
 *
 * Features:
 * - Sortable columns (name, email, company, status, date)
 * - Status chips with color coding
 * - Row click to view details (optional)
 * - Loading skeleton states
 */

import React, { useState } from 'react';
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  Avatar,
  Box,
  Typography,
  TableSortLabel,
  Skeleton,
} from '@mui/material';
import { People as PeopleIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { EventParticipant, RegistrationStatus } from '../../../types/eventParticipant.types';
import CompanyCell from '../UserManagement/CompanyCell';

interface EventParticipantTableProps {
  participants: EventParticipant[];
  isLoading: boolean;
  onRowClick?: (participant: EventParticipant) => void;
}

type SortField = 'name' | 'email' | 'company' | 'status' | 'registrationDate';
type SortDirection = 'asc' | 'desc';

const EventParticipantTable: React.FC<EventParticipantTableProps> = ({
  participants,
  isLoading,
  onRowClick,
}) => {
  const { t } = useTranslation('events');
  const [sortField, setSortField] = useState<SortField>('name');
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc');

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortDirection('asc');
    }
  };

  const sortedParticipants = [...participants].sort((a, b) => {
    let aValue: string | number = '';
    let bValue: string | number = '';

    switch (sortField) {
      case 'name':
        aValue = `${a.firstName} ${a.lastName}`;
        bValue = `${b.firstName} ${b.lastName}`;
        break;
      case 'email':
        aValue = a.email;
        bValue = b.email;
        break;
      case 'company':
        aValue = a.company?.id || '';
        bValue = b.company?.id || '';
        break;
      case 'status':
        aValue = a.status;
        bValue = b.status;
        break;
      case 'registrationDate':
        aValue = new Date(a.registrationDate).getTime();
        bValue = new Date(b.registrationDate).getTime();
        break;
    }

    if (typeof aValue === 'number' && typeof bValue === 'number') {
      return sortDirection === 'asc' ? aValue - bValue : bValue - aValue;
    }

    const comparison = aValue.toString().localeCompare(bValue.toString());
    return sortDirection === 'asc' ? comparison : -comparison;
  });

  const getStatusChipColor = (
    status: RegistrationStatus
  ): 'default' | 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'info' => {
    switch (status) {
      case 'CONFIRMED':
        return 'success';
      case 'REGISTERED':
        return 'primary';
      case 'ATTENDED':
        return 'info';
      case 'CANCELLED':
        return 'error';
      case 'WAITLISTED':
        return 'warning';
      default:
        return 'default';
    }
  };

  const formatDate = (dateString: string): string => {
    const date = new Date(dateString);
    return date.toLocaleDateString('de-CH', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  };

  // Loading skeleton
  if (isLoading) {
    return (
      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>{t('eventPage.participantTable.headers.name')}</TableCell>
              <TableCell>{t('eventPage.participantTable.headers.email')}</TableCell>
              <TableCell>{t('eventPage.participantTable.headers.company')}</TableCell>
              <TableCell>{t('eventPage.participantTable.headers.status')}</TableCell>
              <TableCell>{t('eventPage.participantTable.headers.registrationDate')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {[...Array(5)].map((_, index) => (
              <TableRow key={index} data-testid="participant-skeleton">
                <TableCell>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Skeleton variant="circular" width={32} height={32} />
                    <Skeleton width={120} />
                  </Box>
                </TableCell>
                <TableCell>
                  <Skeleton width={200} />
                </TableCell>
                <TableCell>
                  <Skeleton width={150} />
                </TableCell>
                <TableCell>
                  <Skeleton width={100} />
                </TableCell>
                <TableCell>
                  <Skeleton width={100} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    );
  }

  // Empty state
  if (participants.length === 0) {
    return (
      <Paper sx={{ p: 4, textAlign: 'center' }}>
        <PeopleIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
        <Typography variant="h6" color="text.secondary">
          {t('eventPage.participantTable.empty')}
        </Typography>
      </Paper>
    );
  }

  return (
    <TableContainer component={Paper}>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>
              <TableSortLabel
                active={sortField === 'name'}
                direction={sortField === 'name' ? sortDirection : 'asc'}
                onClick={() => handleSort('name')}
              >
                {t('eventPage.participantTable.headers.name')}
              </TableSortLabel>
            </TableCell>
            <TableCell>
              <TableSortLabel
                active={sortField === 'email'}
                direction={sortField === 'email' ? sortDirection : 'asc'}
                onClick={() => handleSort('email')}
              >
                {t('eventPage.participantTable.headers.email')}
              </TableSortLabel>
            </TableCell>
            <TableCell>
              <TableSortLabel
                active={sortField === 'company'}
                direction={sortField === 'company' ? sortDirection : 'asc'}
                onClick={() => handleSort('company')}
              >
                {t('eventPage.participantTable.headers.company')}
              </TableSortLabel>
            </TableCell>
            <TableCell>
              <TableSortLabel
                active={sortField === 'status'}
                direction={sortField === 'status' ? sortDirection : 'asc'}
                onClick={() => handleSort('status')}
              >
                {t('eventPage.participantTable.headers.status')}
              </TableSortLabel>
            </TableCell>
            <TableCell>
              <TableSortLabel
                active={sortField === 'registrationDate'}
                direction={sortField === 'registrationDate' ? sortDirection : 'asc'}
                onClick={() => handleSort('registrationDate')}
              >
                {t('eventPage.participantTable.headers.registrationDate')}
              </TableSortLabel>
            </TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {sortedParticipants.map((participant) => (
            <TableRow
              key={participant.registrationCode}
              hover
              onClick={() => onRowClick?.(participant)}
              sx={{ cursor: onRowClick ? 'pointer' : 'default' }}
            >
              <TableCell>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Avatar sx={{ width: 32, height: 32 }}>
                    {participant.firstName[0]}
                    {participant.lastName[0]}
                  </Avatar>
                  <Typography variant="body2">
                    {participant.firstName} {participant.lastName}
                  </Typography>
                </Box>
              </TableCell>
              <TableCell>
                <Typography variant="body2" color="text.secondary">
                  {participant.email}
                </Typography>
              </TableCell>
              <TableCell>
                <CompanyCell companyId={participant.company?.id} />
              </TableCell>
              <TableCell>
                <Chip
                  label={participant.status}
                  size="small"
                  color={getStatusChipColor(participant.status)}
                />
              </TableCell>
              <TableCell>
                <Typography variant="body2" color="text.secondary">
                  {formatDate(participant.registrationDate)}
                </Typography>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
};

export default EventParticipantTable;
