/**
 * NeedsAnswerSection (Story 16.2a AC9–AC12): "Needs an answer", the first section of the event's
 * mail page. One row per conversation: status dot, counterpart, where it arrived, date, subject and
 * who answers. Open and waiting first, newest activity first; done below them, greyed. The badge
 * counts the open ones only. Empty: one line, no table (`docs/ux/mail-center-spec.md`, section 1).
 *
 * Same open/close rules as "What went out": one conversation open at a time, inline below its row
 * on desktop, a full-screen dialog on mobile; a second click closes it.
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
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth/useAuth';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import {
  useEventConversations,
  usePatchConversation,
} from '@/hooks/useMailConversations/useMailConversations';
import type { MailConversationSummary } from '@/services/mailConversationService';
import { ConversationDetail } from './ConversationDetail';
import { FullScreenDetailDialog } from './FullScreenDetailDialog';
import { initials } from './initials';

interface NeedsAnswerSectionProps {
  eventCode: string;
  /** The open conversation (e.g. from `?conversation=`), or null. */
  openKey: string | null;
  onOpenChange: (conversationKey: string | null) => void;
  /** Right side of the section header: the "+ New mail" button and its menu. */
  headerAction?: React.ReactNode;
}

const STATUS_COLOR: Record<MailConversationSummary['status'], string> = {
  OPEN: 'warning.main',
  WAITING: 'info.main',
  DONE: 'text.disabled',
};

export const NeedsAnswerSection: React.FC<NeedsAnswerSectionProps> = ({
  eventCode,
  openKey,
  onOpenChange,
  headerAction,
}) => {
  const { t } = useTranslation('events');
  const { isMobile } = useBreakpoints();
  const query = useEventConversations(eventCode);
  const rows = query.data?.data ?? [];
  const openCount = rows.filter((row) => row.status === 'OPEN').length;
  const openRow = rows.find((row) => row.conversationKey === openKey);
  const toggle = (key: string) => onOpenChange(openKey === key ? null : key);

  return (
    <Box data-testid="needs-answer">
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 2, gap: 2 }}>
        <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', gap: 2 }}>
          <Typography variant="h6">{t('eventPage.mail.conversations.title')}</Typography>
          {openCount > 0 && (
            <Chip
              size="small"
              color="warning"
              label={openCount}
              aria-label={t('eventPage.mail.conversations.openCount', { count: openCount })}
              data-testid="needs-answer-badge"
            />
          )}
        </Box>
        {headerAction}
      </Box>

      {query.isLoading ? (
        <Skeleton variant="rectangular" height={56} />
      ) : query.isError ? (
        <Typography color="error" variant="body2">
          {t('eventPage.mail.loadError')}
        </Typography>
      ) : rows.length === 0 ? (
        <Typography variant="body2" color="text.secondary" data-testid="needs-answer-empty">
          {t('eventPage.mail.conversations.nothingOpen')}
        </Typography>
      ) : (
        <List disablePadding data-testid="needs-answer-list">
          {rows.map((row) => (
            <ConversationRow
              key={row.conversationKey}
              row={row}
              eventCode={eventCode}
              isOpen={row.conversationKey === openKey}
              inline={!isMobile}
              onToggle={() => toggle(row.conversationKey)}
            />
          ))}
        </List>
      )}

      {isMobile && (
        <FullScreenDetailDialog
          open={openKey !== null}
          title={openRow?.subject ?? openKey ?? ''}
          onClose={() => onOpenChange(null)}
        >
          {openKey && <ConversationDetail eventCode={eventCode} conversationKey={openKey} />}
        </FullScreenDetailDialog>
      )}
    </Box>
  );
};

interface ConversationRowProps {
  row: MailConversationSummary;
  eventCode: string;
  isOpen: boolean;
  inline: boolean;
  onToggle: () => void;
}

const ConversationRow: React.FC<ConversationRowProps> = ({
  row,
  eventCode,
  isOpen,
  inline,
  onToggle,
}) => {
  const { t, i18n } = useTranslation('events');
  const { user } = useAuth();
  const patch = usePatchConversation(eventCode);
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
        sx={{ alignItems: 'center', opacity: done ? 0.55 : 1 }}
        data-testid={`conversation-row-${row.conversationKey}`}
        data-status={row.status}
      >
        <ListItemButton
          selected={isOpen}
          onClick={onToggle}
          aria-expanded={inline ? isOpen : undefined}
          sx={{ alignItems: 'flex-start', gap: 1.5, px: 1 }}
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
            <ConversationDetail eventCode={eventCode} conversationKey={row.conversationKey} />
          </Box>
        </Collapse>
      )}
    </>
  );
};

export default NeedsAnswerSection;
