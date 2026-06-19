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
  ToggleButton,
  ToggleButtonGroup,
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

  // Status options. `WAITLIST` (not `WAITLISTED`) is the canonical enum value the
  // API and the rest of the app use (Epic 14 FR28 — fixes the previously-dead
  // Waitlisted filter that sent an invalid status and returned nothing).
  const statusOptions = [
    { value: 'all', label: t('eventPage.participantFilters.status.all') },
    { value: 'CONFIRMED', label: t('eventPage.participantFilters.status.confirmed') },
    { value: 'REGISTERED', label: t('eventPage.participantFilters.status.registered') },
    { value: 'ATTENDED', label: t('eventPage.participantFilters.status.attended') },
    { value: 'CANCELLED', label: t('eventPage.participantFilters.status.cancelled') },
    { value: 'WAITLIST', label: t('eventPage.participantFilters.status.waitlisted') },
  ];

  const handleStatusChange = (_event: React.MouseEvent<HTMLElement>, status: string | null) => {
    // Exclusive ToggleButtonGroup yields null when the active button is re-clicked;
    // keep a selection rather than clearing it (clicking "All" is the way to reset).
    if (status === null) {
      return;
    }
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

        {/* Status Filter — segmented control (Epic 14 FR26 / UX-DR9) */}
        <FormControl component="fieldset">
          <FormLabel component="legend" id="participant-status-filter-label">
            {t('eventPage.participantFilters.status.label')}
          </FormLabel>
          <ToggleButtonGroup
            exclusive
            value={selectedStatus}
            onChange={handleStatusChange}
            size="small"
            aria-labelledby="participant-status-filter-label"
            sx={{ flexWrap: 'wrap', mt: 1 }}
            data-testid="participant-status-filter"
          >
            {statusOptions.map((option) => (
              <ToggleButton
                key={option.value}
                value={option.value}
                data-testid={`participant-status-${option.value}`}
              >
                {option.label}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>
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
