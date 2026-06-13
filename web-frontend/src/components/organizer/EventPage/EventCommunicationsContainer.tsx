/**
 * EventCommunicationsContainer (Epic 14, Phase A — interim)
 *
 * Consolidation container for the new "Communications" tab. It stacks today's
 * three existing outbound-email surfaces behind a lightweight internal sub-tab
 * switch — Newsletter, Registrant Notices, and Venue & Caterer — with NO change
 * to their content or behaviour (recompose, not rewrite; NFR9).
 *
 * Phase E (Story 14.E.*) replaces this interim with the single adaptive
 * 4-audience compose surface.
 */

import React, { useState } from 'react';
import { Box, Tabs, Tab } from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { Event, EventDetailUI } from '@/types/event.types';
import { EventNewsletterTab } from './EventNewsletterTab';
import { EventRegistrantNoticesTab } from './EventRegistrantNoticesTab';
import { EventVenueTab } from './EventVenueTab';

interface EventCommunicationsContainerProps {
  event: Event | EventDetailUI;
  eventCode: string;
}

type CommSubView = 'newsletter' | 'registrant-notices' | 'venue';

export const EventCommunicationsContainer: React.FC<EventCommunicationsContainerProps> = ({
  event,
  eventCode,
}) => {
  const { t } = useTranslation('events');
  const [subView, setSubView] = useState<CommSubView>('newsletter');
  const eventTitle = event.title || '';

  return (
    <Box>
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs
          value={subView}
          onChange={(_e, v: CommSubView) => setSubView(v)}
          aria-label={t('eventPage.tabs.communications', 'Communications')}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab
            value="newsletter"
            label={t('eventPage.tabs.newsletter', 'Newsletter')}
            data-testid="comms-subtab-newsletter"
          />
          <Tab
            value="registrant-notices"
            label={t('eventPage.tabs.registrantNotices', 'Registrant Notices')}
            data-testid="comms-subtab-registrant-notices"
          />
          <Tab
            value="venue"
            label={t('eventPage.tabs.venue', 'Venue')}
            data-testid="comms-subtab-venue"
          />
        </Tabs>
      </Box>

      {subView === 'newsletter' && (
        <EventNewsletterTab eventCode={eventCode} eventTitle={eventTitle} />
      )}
      {subView === 'registrant-notices' && (
        <EventRegistrantNoticesTab eventCode={eventCode} eventTitle={eventTitle} />
      )}
      {subView === 'venue' && <EventVenueTab event={event} />}
    </Box>
  );
};

export default EventCommunicationsContainer;
