/**
 * EventWrapupContainer (Epic 14, Phase A — interim)
 *
 * Consolidation container for the new "Wrap-up" tab. Stacks today's two
 * post-event surfaces — Photos and Appreciation (thank-you notes) — behind a
 * lightweight internal sub-tab switch, with NO change to their content or
 * behaviour (recompose, not rewrite; NFR9).
 *
 * The tab itself is locked (dimmed + 🔒, non-interactive) until the event is
 * EVENT_LIVE — that gating lives on the EventPage tab rail via the relevance
 * map, so this container only ever mounts when Wrap-up is active.
 *
 * Phase F (Story 14.F.1) refines the photo grid + thank-you-notes layout.
 */

import React, { useState } from 'react';
import { Box, Tabs, Tab } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { EventPhotosTab } from './EventPhotosTab';
import { EventAppreciationTab } from './EventAppreciationTab';

interface EventWrapupContainerProps {
  eventCode: string;
}

type WrapupSubView = 'photos' | 'appreciation';

export const EventWrapupContainer: React.FC<EventWrapupContainerProps> = ({ eventCode }) => {
  const { t } = useTranslation('events');
  const [subView, setSubView] = useState<WrapupSubView>('photos');

  return (
    <Box>
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs
          value={subView}
          onChange={(_e, v: WrapupSubView) => setSubView(v)}
          aria-label={t('eventPage.tabs.wrapup', 'Wrap-up')}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab
            value="photos"
            label={t('eventPage.tabs.photos', 'Photos')}
            data-testid="wrapup-subtab-photos"
          />
          <Tab
            value="appreciation"
            label={t('eventPage.tabs.appreciation', 'Appreciation')}
            data-testid="wrapup-subtab-appreciation"
          />
        </Tabs>
      </Box>

      {subView === 'photos' && <EventPhotosTab eventCode={eventCode} />}
      {subView === 'appreciation' && <EventAppreciationTab eventCode={eventCode} />}
    </Box>
  );
};

export default EventWrapupContainer;
