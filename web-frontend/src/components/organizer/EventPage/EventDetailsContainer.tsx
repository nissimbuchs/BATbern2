/**
 * EventDetailsContainer (Epic 14, Story 14.F.2) — the merged config-cluster tab.
 *
 * One "Details" tab with three sub-tabs — Info · Tasks · Settings — replacing
 * the former separate Details + Settings top-level tabs (rail 8 → 7) and giving
 * the previously modal-only Tasks surface a home. Matches the sub-tab pattern
 * used on Speakers / Communications / Wrap-up.
 */

import React, { useState } from 'react';
import { Box, Tabs, Tab } from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { Event, EventDetailUI } from '@/types/event.types';
import { EventInfoTab } from './EventInfoTab';
import { EventTasksLiveTab } from './EventTasksLiveTab';
import { EventSettingsTab } from './EventSettingsTab';

interface EventDetailsContainerProps {
  event: Event | EventDetailUI;
  eventCode: string;
}

type DetailsSubView = 'info' | 'tasks' | 'settings';

export const EventDetailsContainer: React.FC<EventDetailsContainerProps> = ({
  event,
  eventCode,
}) => {
  const { t } = useTranslation('events');
  const [subView, setSubView] = useState<DetailsSubView>('info');

  return (
    <Box>
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs
          value={subView}
          onChange={(_e, v: DetailsSubView) => setSubView(v)}
          aria-label={t('eventPage.tabs.details', 'Details')}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab
            value="info"
            label={t('eventPage.details.subtabs.info', 'Info')}
            data-testid="details-subtab-info"
          />
          <Tab
            value="tasks"
            label={t('eventPage.details.subtabs.tasks', 'Tasks')}
            data-testid="details-subtab-tasks"
          />
          <Tab
            value="settings"
            label={t('eventPage.details.subtabs.settings', 'Settings')}
            data-testid="details-subtab-settings"
          />
        </Tabs>
      </Box>

      {subView === 'info' && <EventInfoTab event={event} eventCode={eventCode} />}
      {subView === 'tasks' && <EventTasksLiveTab event={event} eventCode={eventCode} />}
      {subView === 'settings' && <EventSettingsTab event={event} eventCode={eventCode} />}
    </Box>
  );
};

export default EventDetailsContainer;
