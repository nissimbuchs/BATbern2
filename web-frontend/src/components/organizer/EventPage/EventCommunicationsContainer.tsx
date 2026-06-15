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
import {
  MailOutline as NewsletterIcon,
  ConfirmationNumberOutlined as RegistrantsIcon,
  MicNone as SpeakersIcon,
  StorefrontOutlined as VenueIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useBreakpoints } from '@/hooks/useBreakpoints';
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
  const { isMobile } = useBreakpoints();
  const [audience, setAudience] = useState<CommAudience>('newsletter');
  const eventTitle = event.title || '';

  // Each audience tab shows icon + label on desktop; on mobile (Phase G) it collapses to
  // icon-only (the four labelled tabs are far too wide for a phone). The label stays in
  // aria-label for screen readers regardless.
  const audiences = [
    {
      value: 'newsletter' as const,
      icon: <NewsletterIcon />,
      label: t('eventPage.communications.audiences.newsletter', 'Newsletter subscribers'),
    },
    {
      value: 'registrant-notices' as const,
      icon: <RegistrantsIcon />,
      label: t('eventPage.communications.audiences.registrants', 'Event registrants'),
    },
    {
      value: 'speakers' as const,
      icon: <SpeakersIcon />,
      label: t('eventPage.communications.audiences.speakers', 'Speakers'),
    },
    {
      value: 'venue' as const,
      icon: <VenueIcon />,
      label: t('eventPage.communications.audiences.venue', 'Venue & Caterer'),
    },
  ];

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
          {audiences.map((a) => (
            <Tab
              key={a.value}
              value={a.value}
              icon={a.icon}
              iconPosition="start"
              label={isMobile ? undefined : a.label}
              aria-label={a.label}
              data-testid={`comms-subtab-${a.value}`}
              sx={isMobile ? { minWidth: 0 } : undefined}
            />
          ))}
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
