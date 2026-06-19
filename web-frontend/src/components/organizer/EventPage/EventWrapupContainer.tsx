/**
 * EventWrapupContainer (Epic 14 — Wrap-up tab)
 *
 * The post-event Wrap-up surface. Photos and Thank-you notes are shown as two
 * stacked sections in one panel (per the redesign prototype's #p-wrapup), so an
 * organizer sees both at once — replacing the Phase A interim sub-tab switch.
 * The two children are reused unchanged (recompose, not rewrite; NFR9): each
 * already renders its own heading and owns its data/behaviour (FR36 photos;
 * FR37 thank-you notes + ★-feature-disabled-for-anonymous).
 *
 * The tab is locked (dimmed + 🔒, non-interactive) until the event is
 * EVENT_LIVE — that gating lives on the EventPage tab rail via the relevance
 * map, so this container only ever mounts when Wrap-up is active.
 */

import React from 'react';
import { Divider, Stack } from '@mui/material';
import { EventPhotosTab } from './EventPhotosTab';
import { EventAppreciationTab } from './EventAppreciationTab';

interface EventWrapupContainerProps {
  eventCode: string;
}

export const EventWrapupContainer: React.FC<EventWrapupContainerProps> = ({ eventCode }) => {
  return (
    <Stack spacing={4} data-testid="wrapup-panel">
      <EventPhotosTab eventCode={eventCode} />
      <Divider />
      <EventAppreciationTab eventCode={eventCode} />
    </Stack>
  );
};

export default EventWrapupContainer;
