/**
 * OrganizerChip — the canonical "assigned organizer" display used across the app
 * (speaker kanban, Cockpit attention cards, task cards/lists).
 *
 * Renders an outlined chip with an initials avatar + the organizer's "First Last"
 * display name, resolved from the organizer directory where `id === username`
 * (see OrganizerSelect). Falls back to the raw username when the directory has no
 * match, and renders nothing when no username is given.
 *
 * Self-resolving by default (calls `useOrganizers`); pass an `organizers` array to
 * reuse a directory the parent already fetched.
 */

import React from 'react';
import { Avatar, Chip } from '@mui/material';
import type { ChipProps } from '@mui/material';
import { useOrganizers, type Organizer } from '@/components/shared/OrganizerSelect';

export interface OrganizerChipProps {
  /** Organizer username (id === username in the directory). */
  username?: string | null;
  /** Optional pre-fetched directory to avoid a duplicate fetch. */
  organizers?: Organizer[];
  size?: ChipProps['size'];
  sx?: ChipProps['sx'];
  'data-testid'?: string;
}

/** First-letter-of-up-to-two name parts; handles "First Last" and "first.last". */
function initials(text: string): string {
  const parts = text.split(/[.\s_-]+/).filter(Boolean);
  return (parts[0]?.[0] ?? '').concat(parts[1]?.[0] ?? '').toUpperCase() || '?';
}

export const OrganizerChip: React.FC<OrganizerChipProps> = ({
  username,
  organizers,
  size = 'small',
  sx,
  'data-testid': testId,
}) => {
  const { organizers: fetched } = useOrganizers();
  if (!username) return null;

  const directory = organizers ?? fetched;
  const name = directory.find((o) => o.id === username)?.name ?? username;

  return (
    <Chip
      size={size}
      variant="outlined"
      label={name}
      avatar={<Avatar sx={{ width: 18, height: 18, fontSize: '0.6rem' }}>{initials(name)}</Avatar>}
      data-testid={testId}
      sx={{ height: 22, '& .MuiChip-label': { fontSize: '0.7rem', px: 0.75 }, ...sx }}
    />
  );
};

export default OrganizerChip;
