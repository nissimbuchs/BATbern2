/**
 * EventCommunicationsContainer (Epic 16, Stories 16.3 + 16.4): the event's mail page.
 *
 * One column, newest first: "What went out" (every recorded send, the newsletter included, with
 * delivered/failed counts) and "Handled automatically" (collapsed, counts only). Section 1,
 * "Needs an answer", is added by Story 16.2 above them.
 *
 * "+ New mail" opens the existing compose components in a side panel (full screen on mobile);
 * after a send the panel closes and the list refreshes. 16.8 replaces the composers with one.
 */

import React, { useRef, useState } from 'react';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Button,
  Drawer,
  IconButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Paper,
  Snackbar,
  Typography,
} from '@mui/material';
import {
  Add as AddIcon,
  Close as CloseIcon,
  ConfirmationNumberOutlined as RegistrantsIcon,
  ExpandMore as ExpandMoreIcon,
  Handshake as PartnersIcon,
  MailOutline as NewsletterIcon,
  MicNone as SpeakersIcon,
  StorefrontOutlined as VenueIcon,
} from '@mui/icons-material';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import { MAIL_MESSAGE_QUERY_KEYS, useMailCounts } from '@/hooks/useMailMessages/useMailMessages';
import type { Event, EventDetailUI } from '@/types/event.types';
import { EventNewsletterTab } from './EventNewsletterTab';
import { EventRegistrantNoticesTab } from './EventRegistrantNoticesTab';
import { EventVenueTab } from './EventVenueTab';
import { SpeakerBulkComms } from './SpeakerBulkComms';
import { MailSentList } from './communications/MailSentList';
import { MailDetailDrawer } from './communications/MailDetailDrawer';
import { PartnerMeetingsCompose } from './communications/PartnerMeetingsCompose';

interface EventCommunicationsContainerProps {
  event: Event | EventDetailUI;
  eventCode: string;
}

type ComposeAudience = 'newsletter' | 'registrants' | 'speakers' | 'venue' | 'partners';

export const EventCommunicationsContainer: React.FC<EventCommunicationsContainerProps> = ({
  event,
  eventCode,
}) => {
  const { t } = useTranslation('events');
  const { isMobile } = useBreakpoints();
  const queryClient = useQueryClient();
  const newMailButton = useRef<HTMLButtonElement>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [compose, setCompose] = useState<ComposeAudience | null>(null);
  const [openMailKey, setOpenMailKey] = useState<string | null>(null);
  const [sentToast, setSentToast] = useState(false);
  const counts = useMailCounts(eventCode);
  const eventTitle = event.title || '';

  const audiences: Array<{ value: ComposeAudience; icon: React.ReactNode; label: string }> = [
    {
      value: 'newsletter',
      icon: <NewsletterIcon />,
      label: t('eventPage.mail.compose.newsletter'),
    },
    {
      value: 'registrants',
      icon: <RegistrantsIcon />,
      label: t('eventPage.mail.compose.registrants'),
    },
    { value: 'speakers', icon: <SpeakersIcon />, label: t('eventPage.mail.compose.speakers') },
    { value: 'venue', icon: <VenueIcon />, label: t('eventPage.mail.compose.venue') },
    { value: 'partners', icon: <PartnersIcon />, label: t('eventPage.mail.compose.partners') },
  ];

  const closeCompose = () => setCompose(null);

  // Story 16.4 AC3: a successful send closes the panel and the new row appears on top.
  const handleSent = () => {
    void queryClient.invalidateQueries({ queryKey: MAIL_MESSAGE_QUERY_KEYS.event(eventCode) });
    closeCompose();
    setSentToast(true);
  };

  return (
    <Box sx={{ maxWidth: 900 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 2, gap: 2 }}>
        <Typography variant="h6" sx={{ flex: 1 }}>
          {t('eventPage.mail.whatWentOut')}
        </Typography>
        <Button
          ref={newMailButton}
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setMenuOpen(true)}
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          aria-controls={menuOpen ? 'new-mail-menu' : undefined}
          data-testid="new-mail-button"
        >
          {t('eventPage.mail.newMail')}
        </Button>
        <Menu
          id="new-mail-menu"
          anchorEl={newMailButton.current}
          open={menuOpen}
          onClose={() => setMenuOpen(false)}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
          transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        >
          {audiences.map((a) => (
            <MenuItem
              key={a.value}
              onClick={() => {
                setMenuOpen(false);
                setCompose(a.value);
              }}
              data-testid={`new-mail-${a.value}`}
            >
              <ListItemIcon>{a.icon}</ListItemIcon>
              <ListItemText>{a.label}</ListItemText>
            </MenuItem>
          ))}
        </Menu>
      </Box>

      <Paper variant="outlined" sx={{ p: { xs: 1, sm: 2 }, mb: 3 }}>
        <MailSentList eventCode={eventCode} onOpen={setOpenMailKey} />
      </Paper>

      <Accordion variant="outlined" disableGutters data-testid="mail-automatic">
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle1">{t('eventPage.mail.handledAutomatically')}</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <Typography variant="body2" data-testid="mail-automatic-failed">
            {t('eventPage.mail.failedDeliveries', { count: counts.data?.failedDeliveries ?? 0 })}
          </Typography>
        </AccordionDetails>
      </Accordion>

      <MailDetailDrawer
        eventCode={eventCode}
        mailKey={openMailKey}
        onClose={() => setOpenMailKey(null)}
      />

      {/* The Drawer is a modal: it traps focus and closes on Esc (AC5). */}
      <Drawer
        anchor="right"
        open={compose !== null}
        onClose={closeCompose}
        // AC5: focus returns to "+ New mail" once the panel is gone. MUI would restore it to the
        // menu item that opened the panel, which no longer exists.
        disableRestoreFocus
        slotProps={{ transition: { onExited: () => newMailButton.current?.focus() } }}
        // Wide enough for the composers' two-column layout (they switch on the viewport width).
        PaperProps={{ sx: { width: isMobile ? '100%' : 'min(1100px, 92vw)' } }}
      >
        <Box sx={{ p: 2 }} data-testid={compose ? `compose-panel-${compose}` : undefined}>
          <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
            <Typography variant="h6" sx={{ flex: 1 }}>
              {audiences.find((a) => a.value === compose)?.label}
            </Typography>
            <IconButton onClick={closeCompose} aria-label={t('common:actions.close', 'Close')}>
              <CloseIcon />
            </IconButton>
          </Box>
          {compose === 'newsletter' && (
            <EventNewsletterTab eventCode={eventCode} eventTitle={eventTitle} onSent={handleSent} />
          )}
          {compose === 'registrants' && (
            <EventRegistrantNoticesTab
              eventCode={eventCode}
              eventTitle={eventTitle}
              onSent={handleSent}
            />
          )}
          {compose === 'speakers' && (
            <SpeakerBulkComms eventCode={eventCode} eventTitle={eventTitle} onSent={handleSent} />
          )}
          {compose === 'venue' && <EventVenueTab event={event} onSent={handleSent} />}
          {compose === 'partners' && (
            <PartnerMeetingsCompose eventCode={eventCode} onSent={handleSent} />
          )}
        </Box>
      </Drawer>

      <Snackbar
        open={sentToast}
        autoHideDuration={5000}
        onClose={() => setSentToast(false)}
        message={t('eventPage.mail.sentToast')}
        data-testid="mail-sent-toast"
      />
    </Box>
  );
};

export default EventCommunicationsContainer;
