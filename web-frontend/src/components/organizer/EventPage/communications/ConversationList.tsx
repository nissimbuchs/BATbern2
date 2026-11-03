/**
 * ConversationList (Story 16.2a, extracted for Story 16.7): the rows of "Needs an answer". One row
 * per conversation: status dot, counterpart, where it arrived, date, subject and who answers. The
 * event page and the global Mail page render the same rows.
 *
 * One conversation open at a time, inline below its row on desktop, a full-screen dialog on mobile;
 * a second click closes it. Empty: one line, no table.
 *
 * Story 16.7: `showEvent` names the event on every row (the Mail page spans all events);
 * `assignable` puts an "Assign to event" select beside each row (the "No event" section).
 */

import React from 'react';
import {
  Avatar,
  Box,
  Button,
  Chip,
  Collapse,
  List,
  ListItem,
  ListItemButton,
  Skeleton,
  Tooltip,
  Typography,
} from '@mui/material';
import type { UseQueryResult } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth/useAuth';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import { usePatchConversation } from '@/hooks/useMailConversations/useMailConversations';
import type {
  MailConversationPage,
  MailConversationSummary,
} from '@/services/mailConversationService';
import { ConversationDetail } from './ConversationDetail';
import { EventSelect } from './EventSelect';
import { FullScreenDetailDialog } from './FullScreenDetailDialog';
import { initials } from './initials';

interface ConversationListProps {
  query: UseQueryResult<MailConversationPage, Error>;
  /** The open conversation, or null. */
  openKey: string | null;
  onOpenChange: (conversationKey: string | null) => void;
  emptyText: string;
  /** Prefix of the list's test ids (`{testId}-list`, `{testId}-empty`). */
  testId: string;
  /** Story 16.7: show the event (or "No event") on every row. */
  showEvent?: boolean;
  /** Story 16.7: an "Assign to event" select beside every row. */
  assignable?: boolean;
  /**
   * Mobile: the list opens the conversation in its own full-screen dialog. A page with several lists
   * (the Mail page) renders one dialog itself and sets this to false.
   */
  ownDialog?: boolean;
}

const STATUS_COLOR: Record<MailConversationSummary['status'], string> = {
  OPEN: 'warning.main',
  WAITING: 'info.main',
  DONE: 'text.disabled',
};

export const ConversationList: React.FC<ConversationListProps> = ({
  query,
  openKey,
  onOpenChange,
  emptyText,
  testId,
  showEvent = false,
  assignable = false,
  ownDialog = true,
}) => {
  const { t } = useTranslation('events');
  const { isMobile } = useBreakpoints();
  const rows = query.data?.data ?? [];
  const openRow = rows.find((row) => row.conversationKey === openKey);
  const toggle = (key: string) => onOpenChange(openKey === key ? null : key);

  return (
    <>
      {query.isLoading ? (
        <Skeleton variant="rectangular" height={56} />
      ) : query.isError ? (
        <Typography color="error" variant="body2">
          {t('eventPage.mail.loadError')}
        </Typography>
      ) : rows.length === 0 ? (
        <Typography variant="body2" color="text.secondary" data-testid={`${testId}-empty`}>
          {emptyText}
        </Typography>
      ) : (
        <List disablePadding data-testid={`${testId}-list`}>
          {rows.map((row) => (
            <ConversationRow
              key={row.conversationKey}
              row={row}
              isOpen={row.conversationKey === openKey}
              inline={!isMobile}
              onToggle={() => toggle(row.conversationKey)}
              showEvent={showEvent}
              assignable={assignable}
            />
          ))}
        </List>
      )}

      {isMobile && ownDialog && (
        <FullScreenDetailDialog
          open={openKey !== null}
          title={openRow?.subject ?? openKey ?? ''}
          onClose={() => onOpenChange(null)}
        >
          {openKey && <ConversationDetail conversationKey={openKey} />}
        </FullScreenDetailDialog>
      )}
    </>
  );
};

interface ConversationRowProps {
  row: MailConversationSummary;
  isOpen: boolean;
  inline: boolean;
  onToggle: () => void;
  showEvent: boolean;
  assignable: boolean;
}

const ConversationRow: React.FC<ConversationRowProps> = ({
  row,
  isOpen,
  inline,
  onToggle,
  showEvent,
  assignable,
}) => {
  const { t, i18n } = useTranslation('events');
  const { user } = useAuth();
  const patch = usePatchConversation();
  const done = row.status === 'DONE';
  // Story 16.2b AC9: a reply names the kind of mail it answers
  const replyKey =
    row.audience === 'REGISTRANTS'
      ? 'viaReplyRegistrants'
      : row.audience === 'NEWSLETTER'
        ? 'viaReplyNewsletter'
        : 'viaReply';
  const via =
    row.via === 'reply'
      ? t(`eventPage.mail.conversations.${replyKey}`)
      : t('eventPage.mail.conversations.via', { address: row.via });
  const lastAt = new Date(row.lastMessageAt).toLocaleString(i18n.language, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
  const canTake = !row.assigneeUsername && Boolean(user?.username);

  return (
    <>
      <ListItem
        disablePadding
        divider={!(inline && isOpen)}
        sx={{
          alignItems: 'center',
          opacity: done ? 0.55 : 1,
          flexWrap: assignable ? { xs: 'wrap', sm: 'nowrap' } : undefined,
        }}
        data-testid={`conversation-row-${row.conversationKey}`}
        data-status={row.status}
      >
        <ListItemButton
          selected={isOpen}
          onClick={onToggle}
          aria-expanded={inline ? isOpen : undefined}
          sx={{ alignItems: 'flex-start', gap: 1.5, px: 1, minWidth: 0 }}
          data-testid={`conversation-open-${row.conversationKey}`}
        >
          <Box
            component="span"
            role="img"
            aria-label={t(`eventPage.mail.conversations.status.${row.status}`)}
            sx={{
              width: 10,
              height: 10,
              borderRadius: '50%',
              bgcolor: STATUS_COLOR[row.status],
              flex: '0 0 auto',
              mt: 0.75,
            }}
          />
          <Box sx={{ flex: '1 1 auto', minWidth: 0 }}>
            <Typography variant="caption" color="text.secondary" component="div">
              {showEvent && (
                <Chip
                  size="small"
                  variant="outlined"
                  label={row.eventCode ?? t('mailCenter.noEventChip')}
                  sx={{ mr: 1, height: 18 }}
                  data-testid={`conversation-event-${row.conversationKey}`}
                />
              )}
              {lastAt} · {row.counterpartName ?? row.counterpartAddress} · {via}
              {row.messageCount > 1 &&
                ` · ${t('eventPage.mail.conversations.messages', { count: row.messageCount })}`}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              {row.subject}
            </Typography>
          </Box>
          {row.assigneeUsername && (
            <Tooltip title={row.assigneeUsername}>
              <Avatar
                sx={{ width: 28, height: 28, fontSize: 12, flex: '0 0 auto' }}
                data-testid="conversation-assignee-avatar"
              >
                {initials(row.assigneeUsername)}
              </Avatar>
            </Tooltip>
          )}
        </ListItemButton>
        {assignable && (
          // Story 16.7 AC1: beside the row, not inside it (a control inside a button is not operable)
          <EventSelect
            value={row.eventCode ?? ''}
            onChange={(eventCode) =>
              patch.mutate({
                key: row.conversationKey,
                patch: eventCode ? { eventCode } : { clearEvent: true },
              })
            }
            label={t('mailCenter.assignToEvent')}
            noneLabel={t('mailCenter.noEventOption')}
            disabled={patch.isPending}
            sx={{ minWidth: 200, flex: '0 0 auto', mx: 1, my: { xs: 1, sm: 0 } }}
            data-testid={`conversation-assign-${row.conversationKey}`}
          />
        )}
        {canTake && (
          // Beside the row, not inside it: a button inside a button is not operable.
          <Button
            size="small"
            onClick={() =>
              patch.mutate({
                key: row.conversationKey,
                patch: { assigneeUsername: user?.username },
              })
            }
            disabled={patch.isPending}
            sx={{ flex: '0 0 auto', mx: 1 }}
            data-testid={`conversation-take-${row.conversationKey}`}
          >
            {t('eventPage.mail.conversations.takeIt')}
          </Button>
        )}
      </ListItem>
      {inline && (
        <Collapse in={isOpen} unmountOnExit>
          <Box
            sx={{ p: 2, borderBottom: 1, borderColor: 'divider', bgcolor: 'action.hover' }}
            data-testid={`conversation-inline-${row.conversationKey}`}
          >
            <ConversationDetail conversationKey={row.conversationKey} />
          </Box>
        </Collapse>
      )}
    </>
  );
};

export default ConversationList;
