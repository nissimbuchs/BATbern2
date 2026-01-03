/**
 * Event Participant Filters Component
 *
 * Filter controls for event participants list (search, status filter)
 * Story 3.3: Event Participants Tab
 */

import React, { useEffect, useState } from 'react';
import {
  Box,
  TextField,
  FormControl,
  FormLabel,
  RadioGroup,
  FormControlLabel,
  Radio,
  Button,
  Paper,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useEventParticipantStore } from '../../../stores/eventParticipantStore';
import type { RegistrationStatus } from '../../../types/eventParticipant.types';

const EventParticipantFilters: React.FC = () => {
  const { t } = useTranslation('events');
  const { filters, searchQuery, setFilters, setSearchQuery, resetFilters } =
    useEventParticipantStore();

  // Local state for debounced search
  const [searchValue, setSearchValue] = useState(searchQuery);

  // Debounced search effect
  useEffect(() => {
    const timer = setTimeout(() => {
      // Only update if the search value actually changed
      if (searchValue !== searchQuery) {
        setSearchQuery(searchValue);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [searchValue, searchQuery, setSearchQuery]);

  // Status options
  const statusOptions = [
    { value: 'all', label: t('eventPage.participantFilters.status.all') },
    { value: 'CONFIRMED', label: t('eventPage.participantFilters.status.confirmed') },
    { value: 'REGISTERED', label: t('eventPage.participantFilters.status.registered') },
    { value: 'ATTENDED', label: t('eventPage.participantFilters.status.attended') },
    { value: 'CANCELLED', label: t('eventPage.participantFilters.status.cancelled') },
    { value: 'WAITLISTED', label: t('eventPage.participantFilters.status.waitlisted') },
  ];

  const handleStatusChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const status = event.target.value;
    if (status === 'all') {
      setFilters({
        ...filters,
        status: undefined,
      });
    } else {
      setFilters({
        ...filters,
        status: [status as RegistrationStatus],
      });
    }
  };

  const handleClearFilters = () => {
    setSearchValue('');
    resetFilters();
  };

  // Determine selected status for radio group
  const selectedStatus = filters.status && filters.status.length > 0 ? filters.status[0] : 'all';

  return (
    <Paper sx={{ p: 2, mb: 3 }}>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {/* Search Input */}
        <TextField
          fullWidth
          label={t('eventPage.participantFilters.search.label')}
          variant="outlined"
          value={searchValue}
          onChange={(e) => setSearchValue(e.target.value)}
          placeholder={t('eventPage.participantFilters.search.placeholder')}
        />

        {/* Status Filter */}
        <FormControl component="fieldset">
          <FormLabel component="legend">{t('eventPage.participantFilters.status.label')}</FormLabel>
          <RadioGroup row value={selectedStatus} onChange={handleStatusChange}>
            {statusOptions.map((option) => (
              <FormControlLabel
                key={option.value}
                value={option.value}
                control={<Radio />}
                label={option.label}
              />
            ))}
          </RadioGroup>
        </FormControl>

        {/* Clear Filters Button */}
        <Button variant="outlined" onClick={handleClearFilters} sx={{ alignSelf: 'flex-start' }}>
          {t('eventPage.participantFilters.clearAll')}
        </Button>
      </Box>
    </Paper>
  );
};

export default EventParticipantFilters;
