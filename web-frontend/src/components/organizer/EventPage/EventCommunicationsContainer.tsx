/**
 * EventCommunicationsContainer (Epic 16, Stories 16.3, 16.4, 16.8b): the event's mail page.
 *
 * One column, newest first: "What went out" (every recorded send, the newsletter included, with
 * delivered/failed counts) and "Handled automatically" (collapsed, counts only). Section 1,
 * "Needs an answer", is added by Story 16.2 above them.
 *
 * "+ New mail" opens the composer inline at the top of the page on desktop and as a full-screen
 * dialog on mobile (owner decision 2026-10-31, `docs/ux/mail-center-layouts.html` variant A and
 * `docs/ux/mail-center-mobile.html`); a sent mail likewise opens below its row. Newsletter,
 * registrants, speakers and venue share one MailComposer (16.8b); Partners keeps
 * PartnerMeetingsCompose until 16.9. After a send the composer closes, the list refreshes and the
 * new row is highlighted on top.
 */

import React, { useEffect, useRef, useState } from 'react';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  AppBar,
  Box,
  Button,
  Collapse,
  Dialog,
  IconButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Paper,
  Snackbar,
  Toolbar,
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
import type { ComposeAudience, MailSendResponse } from '@/services/mailMessageService';
import type { Event, EventDetailUI } from '@/types/event.types';
import { MailSentList } from './communications/MailSentList';
import { PartnerMeetingsCompose } from './communications/PartnerMeetingsCompose';
import { MailComposer } from './communications/composer/MailComposer';

interface EventCommunicationsContainerProps {
  event: Event | EventDetailUI;
  eventCode: string;
}

type PickerAudience = 'newsletter' | 'registrants' | 'speakers' | 'venue' | 'partners';

/** The picker entries the shared composer serves; Partners is not among them yet (AC15). */
const COMPOSER_AUDIENCE: Partial<Record<PickerAudience, ComposeAudience>> = {
  newsletter: 'NEWSLETTER',
  registrants: 'REGISTRANTS',
  speakers: 'SPEAKERS',
  venue: 'VENUE',
};

export const EventCommunicationsContainer: React.FC<EventCommunicationsContainerProps> = ({
  eventCode,
}) => {
  const { t } = useTranslation('events');
  const { isMobile } = useBreakpoints();
  const queryClient = useQueryClient();
  const newMailButton = useRef<HTMLButtonElement>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [compose, setCompose] = useState<PickerAudience | null>(null);
  const composeHeading = useRef<HTMLHeadingElement>(null);
  const [sentToast, setSentToast] = useState(false);
  const [highlightKeys, setHighlightKeys] = useState<string[]>([]);
  const counts = useMailCounts(eventCode);

  const audiences: Array<{ value: PickerAudience; icon: React.ReactNode; label: string }> = [
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

  // AC5: focus returns to "+ New mail" once the composer is gone. The dialog would restore it to
  // the menu item that opened it, which no longer exists, so both layouts do it here.
  const closeCompose = () => {
    setCompose(null);
    if (!isMobile) newMailButton.current?.focus();
  };

  // Desktop: move focus into the inline composer, so keyboard and screen reader users land there.
  useEffect(() => {
    if (compose && !isMobile) composeHeading.current?.focus();
  }, [compose, isMobile]);

  // Story 16.4 AC3, 16.8b AC13: a successful send closes the composer and the new row appears
  // on top, highlighted (the shared composer returns its key(s); the partner invite does not).
  const handleSent = (result?: MailSendResponse) => {
    void queryClient.invalidateQueries({ queryKey: MAIL_MESSAGE_QUERY_KEYS.event(eventCode) });
    setHighlightKeys(result?.mailKeys ?? []);
    closeCompose();
    setSentToast(true);
  };

  const composeLabel = audiences.find((a) => a.value === compose)?.label;
  const composerAudience = compose ? COMPOSER_AUDIENCE[compose] : undefined;
  const composer = (
    <>
      {composerAudience && (
        <MailComposer
          key={composerAudience}
          eventCode={eventCode}
          audience={composerAudience}
          onSent={handleSent}
        />
      )}
      {compose === 'partners' && (
        <PartnerMeetingsCompose eventCode={eventCode} onSent={() => handleSent()} />
      )}
    </>
  );

  return (
    <Box sx={{ maxWidth: 1100 }}>
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

      {!isMobile && (
        <Collapse in={compose !== null} unmountOnExit>
          <Paper
            variant="outlined"
            sx={{ p: 2, mb: 3, borderColor: 'primary.main' }}
            onKeyDown={(e) => {
              if (e.key === 'Escape') closeCompose();
            }}
            data-testid={compose ? `compose-panel-${compose}` : undefined}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
              <Typography
                ref={composeHeading}
                tabIndex={-1}
                variant="h6"
                component="h3"
                sx={{ flex: 1, outline: 'none' }}
              >
                {composeLabel}
              </Typography>
              <IconButton onClick={closeCompose} aria-label={t('common:actions.close', 'Close')}>
                <CloseIcon />
              </IconButton>
            </Box>
            {composer}
          </Paper>
        </Collapse>
      )}

      <Paper variant="outlined" sx={{ p: { xs: 1, sm: 2 }, mb: 3 }}>
        <MailSentList eventCode={eventCode} highlightKeys={highlightKeys} />
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

      {isMobile && (
        // The Dialog is a modal: it traps focus and closes on Esc (AC5).
        <Dialog
          fullScreen
          open={compose !== null}
          onClose={closeCompose}
          disableRestoreFocus
          TransitionProps={{ onExited: () => newMailButton.current?.focus() }}
          aria-labelledby="compose-title"
        >
          <AppBar
            position="sticky"
            color="default"
            elevation={0}
            sx={{ borderBottom: 1, borderColor: 'divider' }}
          >
            <Toolbar sx={{ gap: 1 }}>
              <IconButton
                edge="start"
                onClick={closeCompose}
                aria-label={t('common:actions.close', 'Close')}
              >
                <CloseIcon />
              </IconButton>
              <Typography id="compose-title" variant="subtitle1" noWrap sx={{ flex: 1 }}>
                {composeLabel}
              </Typography>
            </Toolbar>
          </AppBar>
          <Box sx={{ p: 2 }} data-testid={compose ? `compose-panel-${compose}` : undefined}>
            {composer}
          </Box>
        </Dialog>
      )}

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
