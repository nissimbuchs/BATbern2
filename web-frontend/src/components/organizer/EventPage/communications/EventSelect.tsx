/**
 * EventSelect (Story 16.7): pick an event, or "no event", for a conversation or a list filter.
 * The events come from the existing event list (`useEvents`, archived excluded), newest first.
 */

import React from 'react';
import { MenuItem, TextField } from '@mui/material';
import type { SxProps, Theme } from '@mui/material';
import { useEvents } from '@/hooks/useEvents';

interface EventSelectProps {
  /** The event code, or '' for none. */
  value: string;
  onChange: (eventCode: string) => void;
  label: string;
  /** Label of the empty choice ("— no event —" or "All events"). */
  noneLabel: string;
  disabled?: boolean;
  sx?: SxProps<Theme>;
  'data-testid'?: string;
}

export const EventSelect: React.FC<EventSelectProps> = ({
  value,
  onChange,
  label,
  noneLabel,
  disabled,
  sx,
  'data-testid': testId,
}) => {
  const { data } = useEvents({ page: 1, limit: 100 });
  const events = [...(data?.data ?? [])].sort((a, b) => b.eventNumber - a.eventNumber);
  // A value outside the list (e.g. an archived event) still shows as itself
  const known = value === '' || events.some((event) => event.eventCode === value);

  return (
    <TextField
      select
      size="small"
      label={label}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      disabled={disabled}
      sx={sx}
      inputProps={{ 'data-testid': testId }}
    >
      <MenuItem value="">
        <em>{noneLabel}</em>
      </MenuItem>
      {!known && <MenuItem value={value}>{value}</MenuItem>}
      {events.map((event) => (
        <MenuItem key={event.eventCode} value={event.eventCode}>
          {event.eventCode} · {event.title}
        </MenuItem>
      ))}
    </TextField>
  );
};

export default EventSelect;
