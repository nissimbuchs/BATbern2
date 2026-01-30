/**
 * EventTypeSelector Component (Story 5.1 - Task 3b)
 *
 * Dropdown selector for event types with preview information
 * Features:
 * - Displays three event types (FULL_DAY, AFTERNOON, EVENING)
 * - Shows slot range and duration for each type
 * - onChange callback for parent components
 * - Disabled state support
 * - i18n compliance (all text uses react-i18next)
 * - Generated types from OpenAPI spec (ADR-006)
 */

import React from 'react';
import {
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Typography,
  Box,
  CircularProgress,
  FormHelperText,
} from '@mui/material';
import type { SelectChangeEvent } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useEventTypes } from '@/hooks/useEventTypes';
import type { components } from '@/types/generated/events-api.types';

// Import generated types from OpenAPI spec (ADR-006 compliance)
type EventType = components['schemas']['EventType'];

interface EventTypeSelectorProps {
  value: EventType;
  onChange: (type: EventType) => void;
  disabled?: boolean;
  error?: boolean;
  helperText?: string;
}

/**
 * Helper function to format event type names for display
 */
function formatEventTypeName(type: EventType, t: (key: string) => string): string {
  const typeMap: Record<EventType, string> = {
    FULL_DAY: t('form.eventTypes.fullDay'),
    AFTERNOON: t('form.eventTypes.afternoon'),
    EVENING: t('form.eventTypes.evening'),
  };
  return typeMap[type] || type;
}

export const EventTypeSelector: React.FC<EventTypeSelectorProps> = ({
  value,
  onChange,
  disabled = false,
  error = false,
  helperText,
}) => {
  const { t } = useTranslation('events');
  const { data: eventTypes, isLoading, isError } = useEventTypes();

  const handleChange = (event: SelectChangeEvent<EventType>) => {
    onChange(event.target.value as EventType);
  };

  // Handle loading state
  if (isLoading) {
    return (
      <FormControl fullWidth disabled>
        <InputLabel id="event-type-label">{t('form.eventType')}</InputLabel>
        <Select
          labelId="event-type-label"
          id="event-type-selector"
          value=""
          label={t('form.eventType')}
          displayEmpty
          data-testid="event-type-selector-loading"
        >
          <MenuItem disabled>
            <CircularProgress size={20} />
          </MenuItem>
        </Select>
      </FormControl>
    );
  }

  // Handle error state
  if (isError || !eventTypes || eventTypes.length === 0) {
    return (
      <FormControl fullWidth disabled error>
        <InputLabel id="event-type-label">{t('form.eventType')}</InputLabel>
        <Select
          labelId="event-type-label"
          id="event-type-selector"
          value=""
          label={t('form.eventType')}
          displayEmpty
        >
          <MenuItem disabled>{t('form.errors.failedToLoadEventTypes')}</MenuItem>
        </Select>
        <FormHelperText>{t('form.errors.failedToLoadEventTypes')}</FormHelperText>
      </FormControl>
    );
  }

  return (
    <FormControl fullWidth disabled={disabled} error={error}>
      <InputLabel id="event-type-label">{t('form.eventType')}</InputLabel>
      <Select
        labelId="event-type-label"
        id="event-type-selector"
        value={value}
        label={t('form.eventType')}
        onChange={handleChange}
        data-testid="event-type-selector"
      >
        {eventTypes?.map((config) => (
          <MenuItem
            key={config.type}
            value={config.type}
            data-testid={`event-type-option-${config.type.toLowerCase()}`}
          >
            <Box sx={{ display: 'flex', justifyContent: 'space-between', width: '100%', gap: 2 }}>
              <Typography variant="body1">{formatEventTypeName(config.type, t)}</Typography>
              <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
                {config.minSlots}-{config.maxSlots} {t('slotPreview.slots')} • {config.slotDuration}{' '}
                min each
              </Typography>
            </Box>
          </MenuItem>
        ))}
      </Select>
      {helperText && <FormHelperText>{helperText}</FormHelperText>}
    </FormControl>
  );
};
