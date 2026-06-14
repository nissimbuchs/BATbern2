/**
 * EventCommunicationsContainer (Epic 14 — Communications tab)
 *
 * The single Communications surface. An audience switch over four audiences —
 * 📰 Newsletter subscribers · 🎟️ Event registrants · 🎤 Speakers · 🏛️ Venue &
 * Caterer — each composing with its own existing surface (recompose, not
 * rewrite; NFR9). The Newsletter / Registrant / Venue children are unchanged
 * from Phase A; the Speakers audience (Story 14.E.3) surfaces the previously
 * unwired bulk-reminder hook.
 */

import React, { useState } from 'react';
import { Box, Tabs, Tab } from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { Event, EventDetailUI } from '@/types/event.types';
import { EventNewsletterTab } from './EventNewsletterTab';
import { EventRegistrantNoticesTab } from './EventRegistrantNoticesTab';
import { EventVenueTab } from './EventVenueTab';
import { SpeakerBulkComms } from './SpeakerBulkComms';

interface EventCommunicationsContainerProps {
  event: Event | EventDetailUI;
  eventCode: string;
}

type CommAudience = 'newsletter' | 'registrant-notices' | 'speakers' | 'venue';

export const EventCommunicationsContainer: React.FC<EventCommunicationsContainerProps> = ({
  event,
  eventCode,
}) => {
  const { t } = useTranslation('events');
  const [audience, setAudience] = useState<CommAudience>('newsletter');
  const eventTitle = event.title || '';

  return (
    <Box>
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs
          value={audience}
          onChange={(_e, v: CommAudience) => setAudience(v)}
          aria-label={t('eventPage.tabs.communications', 'Communications')}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab
            value="newsletter"
            label={`📰 ${t('eventPage.communications.audiences.newsletter', 'Newsletter subscribers')}`}
            data-testid="comms-subtab-newsletter"
          />
          <Tab
            value="registrant-notices"
            label={`🎟️ ${t('eventPage.communications.audiences.registrants', 'Event registrants')}`}
            data-testid="comms-subtab-registrant-notices"
          />
          <Tab
            value="speakers"
            label={`🎤 ${t('eventPage.communications.audiences.speakers', 'Speakers')}`}
            data-testid="comms-subtab-speakers"
          />
          <Tab
            value="venue"
            label={`🏛️ ${t('eventPage.communications.audiences.venue', 'Venue & Caterer')}`}
            data-testid="comms-subtab-venue"
          />
        </Tabs>
      </Box>

      {audience === 'newsletter' && (
        <EventNewsletterTab eventCode={eventCode} eventTitle={eventTitle} />
      )}
      {audience === 'registrant-notices' && (
        <EventRegistrantNoticesTab eventCode={eventCode} eventTitle={eventTitle} />
      )}
      {audience === 'speakers' && (
        <SpeakerBulkComms eventCode={eventCode} eventTitle={eventTitle} />
      )}
      {audience === 'venue' && <EventVenueTab event={event} />}
    </Box>
  );
};

export default EventCommunicationsContainer;
