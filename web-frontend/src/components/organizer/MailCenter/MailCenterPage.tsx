/**
 * MailCenterPage (Story 16.7): the global "Mail" page, `/organizer/mail`.
 *
 * Sections, in this order (AC8, AC9):
 *  1. Open: OPEN and WAITING conversations of every event, each row names its event.
 *  2. No event: mail to info@ / support@ that belongs to no event; each row has "Assign to event".
 *  3. Spam (collapsed, count in the header): mail SES flagged as spam, "Not spam" per row.
 *  4. What went out (all events, collapsed): the event page's list across events, with an event filter.
 *
 * The rows and inline details are the event page's (`ConversationList`, `MailSentList`,
 * `MailDetail`): inline below the row on desktop, a full-screen dialog on mobile.
 * `?conversation={key}` opens a conversation directly (the 16.6 notification links here for mail
 * without event). Empty sections show one line.
 */

import React, { useState } from 'react';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Chip,
  Collapse,
  List,
  ListItem,
  ListItemButton,
  Paper,
  Skeleton,
  Typography,
} from '@mui/material';
import { ExpandMore as ExpandMoreIcon } from '@mui/icons-material';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import {
  useConversationCounts,
  useConversations,
} from '@/hooks/useMailConversations/useMailConversations';
import { useMailMessages, useMarkNotSpam } from '@/hooks/useMailMessages/useMailMessages';
import type { MailMessageSummary } from '@/services/mailMessageService';
import { ConversationDetail } from '@/components/organizer/EventPage/communications/ConversationDetail';
import { ConversationList } from '@/components/organizer/EventPage/communications/ConversationList';
import { EventSelect } from '@/components/organizer/EventPage/communications/EventSelect';
import { FullScreenDetailDialog } from '@/components/organizer/EventPage/communications/FullScreenDetailDialog';
import {
  MailDetail,
  MailDetailDialog,
} from '@/components/organizer/EventPage/communications/MailDetail';
import { MailSentList } from '@/components/organizer/EventPage/communications/MailSentList';

const SPAM_PAGE_SIZE = 50;

export const MailCenterPage: React.FC = () => {
  const { t } = useTranslation('events');
  const { isMobile } = useBreakpoints();
  const [searchParams, setSearchParams] = useSearchParams();
  const openKey = searchParams.get('conversation');
  const setOpenKey = (key: string | null) =>
    setSearchParams(
      (params) => {
        const next = new URLSearchParams(params);
        if (key) next.set('conversation', key);
        else next.delete('conversation');
        return next;
      },
      { replace: true }
    );
  const [sentEvent, setSentEvent] = useState('');

  const counts = useConversationCounts();
  const open = useConversations({ status: ['OPEN', 'WAITING'], noEvent: false });
  const noEvent = useConversations({ noEvent: true });
  const shown = [...(open.data?.data ?? []), ...(noEvent.data?.data ?? [])];
  const openRow = shown.find((row) => row.conversationKey === openKey);
  // A linked conversation that is in neither list (e.g. done, with an event) opens on top
  const loaded = open.data !== undefined && noEvent.data !== undefined;
  const orphan = openKey !== null && loaded && openRow === undefined;

  return (
    <Box sx={{ maxWidth: 1100, mx: 'auto', px: { xs: 0, sm: 2 }, py: 2 }} data-testid="mail-center">
      <Typography variant="h4" component="h1" sx={{ mb: 3 }}>
        {t('mailCenter.title')}
      </Typography>

      {orphan && !isMobile && (
        <Paper variant="outlined" sx={{ p: 2, mb: 3 }} data-testid="mail-center-linked">
          <ConversationDetail conversationKey={openKey} />
        </Paper>
      )}

      <Paper variant="outlined" sx={{ p: { xs: 1, sm: 2 }, mb: 3 }}>
        <SectionHeader title={t('mailCenter.open')} count={counts.data?.open} warning />
        <ConversationList
          query={open}
          openKey={openKey}
          onOpenChange={setOpenKey}
          emptyText={t('mailCenter.nothingOpen')}
          testId="mail-center-open"
          showEvent
          ownDialog={false}
        />
      </Paper>

      <Paper variant="outlined" sx={{ p: { xs: 1, sm: 2 }, mb: 3 }}>
        <SectionHeader
          title={t('mailCenter.noEvent')}
          subtitle={t('mailCenter.noEventHint')}
          count={counts.data?.noEvent}
        />
        <ConversationList
          query={noEvent}
          openKey={openKey}
          onOpenChange={setOpenKey}
          emptyText={t('mailCenter.nothingWithoutEvent')}
          testId="mail-center-no-event"
          assignable
          ownDialog={false}
        />
      </Paper>

      <Accordion variant="outlined" disableGutters sx={{ mb: 3 }} data-testid="mail-center-spam">
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <SectionHeader title={t('mailCenter.spam')} count={counts.data?.spam} inline />
        </AccordionSummary>
        <AccordionDetails>
          <SpamList />
        </AccordionDetails>
      </Accordion>

      <Accordion variant="outlined" disableGutters data-testid="mail-center-sent">
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle1">{t('mailCenter.whatWentOut')}</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <EventSelect
            value={sentEvent}
            onChange={setSentEvent}
            label={t('mailCenter.event')}
            noneLabel={t('mailCenter.allEvents')}
            sx={{ minWidth: 240, mb: 2 }}
            data-testid="mail-center-sent-event"
          />
          <MailSentList
            key={sentEvent}
            listParams={sentEvent ? { eventCode: sentEvent } : undefined}
          />
        </AccordionDetails>
      </Accordion>

      {isMobile && (
        <FullScreenDetailDialog
          open={openKey !== null}
          title={openRow?.subject ?? openKey ?? ''}
          onClose={() => setOpenKey(null)}
        >
          {openKey && <ConversationDetail conversationKey={openKey} />}
        </FullScreenDetailDialog>
      )}
    </Box>
  );
};

interface SectionHeaderProps {
  title: string;
  subtitle?: string;
  count?: number;
  /** Open conversations: the count is a warning chip, like the event page's badge. */
  warning?: boolean;
  /** Inside an accordion summary: no bottom margin. */
  inline?: boolean;
}

const SectionHeader: React.FC<SectionHeaderProps> = ({
  title,
  subtitle,
  count,
  warning,
  inline,
}) => (
  <Box sx={{ mb: inline ? 0 : 2 }}>
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
      <Typography variant={inline ? 'subtitle1' : 'h6'} component="h2">
        {title}
      </Typography>
      {count !== undefined && count > 0 && (
        <Chip size="small" color={warning ? 'warning' : 'default'} label={count} />
      )}
    </Box>
    {subtitle && (
      <Typography variant="body2" color="text.secondary">
        {subtitle}
      </Typography>
    )}
  </Box>
);

/**
 * AC3: mail SES flagged as spam, newest first. A click shows the mail inline (desktop) or in a
 * dialog (mobile); "Not spam" threads it like a new arrival. Deleted after 30 days (AC7).
 */
const SpamList: React.FC = () => {
  const { t } = useTranslation('events');
  const { isMobile } = useBreakpoints();
  const [openKey, setOpenKey] = useState<string | null>(null);
  const query = useMailMessages(null, {
    direction: 'IN',
    spam: true,
    grouped: false,
    limit: SPAM_PAGE_SIZE,
  });
  const notSpam = useMarkNotSpam();
  const rows = query.data?.data ?? [];
  const openRow = rows.find((row) => row.mailKey === openKey);

  if (query.isLoading) return <Skeleton variant="rectangular" height={56} />;
  if (query.isError) {
    return (
      <Typography color="error" variant="body2">
        {t('eventPage.mail.loadError')}
      </Typography>
    );
  }
  if (rows.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary" data-testid="mail-center-spam-empty">
        {t('mailCenter.noSpam')}
      </Typography>
    );
  }
  return (
    <>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        {t('mailCenter.spamHint')}
      </Typography>
      {notSpam.isError && (
        <Alert severity="error" sx={{ mb: 1 }} onClose={() => notSpam.reset()}>
          {t('mailCenter.notSpamError')}
        </Alert>
      )}
      <List disablePadding data-testid="mail-center-spam-list">
        {rows.map((row) => (
          <SpamRow
            key={row.mailKey}
            row={row}
            isOpen={row.mailKey === openKey}
            inline={!isMobile}
            onToggle={() => setOpenKey((current) => (current === row.mailKey ? null : row.mailKey))}
            onNotSpam={() => notSpam.mutate(row.mailKey)}
            pending={notSpam.isPending}
          />
        ))}
      </List>
      {isMobile && (
        <MailDetailDialog
          mailKey={openKey}
          title={openRow?.subject ?? undefined}
          onClose={() => setOpenKey(null)}
        />
      )}
    </>
  );
};

interface SpamRowProps {
  row: MailMessageSummary;
  isOpen: boolean;
  inline: boolean;
  onToggle: () => void;
  onNotSpam: () => void;
  pending: boolean;
}

const SpamRow: React.FC<SpamRowProps> = ({ row, isOpen, inline, onToggle, onNotSpam, pending }) => {
  const { t, i18n } = useTranslation('events');
  const at = new Date(row.sentAt).toLocaleString(i18n.language, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
  return (
    <>
      <ListItem
        disablePadding
        divider={!(inline && isOpen)}
        sx={{ alignItems: 'center' }}
        data-testid={`spam-row-${row.mailKey}`}
      >
        <ListItemButton
          selected={isOpen}
          onClick={onToggle}
          aria-expanded={inline ? isOpen : undefined}
          sx={{ px: 1, minWidth: 0 }}
        >
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="caption" color="text.secondary" component="div">
              {at} · {row.fromName ?? row.fromAddress}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              {row.subject}
            </Typography>
          </Box>
        </ListItemButton>
        {/* Beside the row, not inside it: a button inside a button is not operable. */}
        <Button
          size="small"
          onClick={onNotSpam}
          disabled={pending}
          sx={{ flex: '0 0 auto', mx: 1 }}
          data-testid={`spam-not-spam-${row.mailKey}`}
        >
          {t('mailCenter.notSpam')}
        </Button>
      </ListItem>
      {inline && (
        <Collapse in={isOpen} unmountOnExit>
          <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider', bgcolor: 'action.hover' }}>
            <MailDetail mailKey={row.mailKey} />
          </Box>
        </Collapse>
      )}
    </>
  );
};

export default MailCenterPage;
