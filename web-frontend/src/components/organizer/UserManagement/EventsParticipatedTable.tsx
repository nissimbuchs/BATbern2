/**
 * Table showing events a user has participated in
 * Story BAT-15: Integration - Participant Batch Import
 */

import React, { useEffect, useState } from 'react';
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  CircularProgress,
  Alert,
  Box,
  Typography,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import axios from 'axios';
import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/events-api.types';

type Registration = components['schemas']['Registration'];

interface EventParticipation {
  eventCode: string;
  title: string;
  date: string;
  status: string;
}

interface EventsParticipatedTableProps {
  userId: string;
}

export const EventsParticipatedTable: React.FC<EventsParticipatedTableProps> = ({ userId }) => {
  const { t } = useTranslation('userManagement');
  const [events, setEvents] = useState<EventParticipation[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchEventParticipations = async () => {
      try {
        setIsLoading(true);
        setError(null);

        // Fetch registrations for this user using the new endpoint
        const response = await apiClient.get<Registration[]>(`/events/registrations`, {
          params: { attendeeUsername: userId },
        });

        // Transform response to table data with Swiss date format (de-CH)
        const participations: EventParticipation[] = response.data.map((reg) => ({
          eventCode: reg.eventCode || 'N/A',
          title: reg.eventTitle || reg.eventCode || t('userDetail.eventsTable.untitledEvent'),
          date: reg.eventDate
            ? new Date(reg.eventDate).toLocaleDateString('de-CH', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric',
              })
            : t('userDetail.eventsTable.dateTBD'),
          status: reg.status || 'REGISTERED',
        }));

        setEvents(participations);
      } catch (err) {
        console.error('Failed to fetch event participations:', err);
        if (axios.isAxiosError(err)) {
          setError(err.response?.data?.message || 'Failed to load event participations');
        } else {
          setError('Failed to load event participations');
        }
      } finally {
        setIsLoading(false);
      }
    };

    fetchEventParticipations();
  }, [userId]);

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight={200}>
        <CircularProgress aria-label={t('userDetail.eventsTable.loading')} />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error" sx={{ mb: 2 }}>
        {t('userDetail.eventsTable.loadError')}
      </Alert>
    );
  }

  if (events.length === 0) {
    return <Alert severity="info">{t('userDetail.eventsTable.noEvents')}</Alert>;
  }

  return (
    <TableContainer component={Paper}>
      <Table aria-label={t('userDetail.eventsTable.ariaLabel')}>
        <TableHead>
          <TableRow>
            <TableCell>{t('userDetail.eventsTable.eventCode')}</TableCell>
            <TableCell>{t('userDetail.eventsTable.title')}</TableCell>
            <TableCell>{t('userDetail.eventsTable.date')}</TableCell>
            <TableCell>{t('userDetail.eventsTable.status')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {events.map((event, index) => (
            <TableRow key={index}>
              <TableCell>
                <Typography variant="body2" fontWeight="medium">
                  {event.eventCode}
                </Typography>
              </TableCell>
              <TableCell>{event.title}</TableCell>
              <TableCell>{event.date}</TableCell>
              <TableCell>
                <Typography
                  variant="body2"
                  color={event.status === 'ATTENDED' ? 'success.main' : 'text.secondary'}
                >
                  {t(`userDetail.eventsTable.statusValues.${event.status}`, event.status)}
                </Typography>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
};
