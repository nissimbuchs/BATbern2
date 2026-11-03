/**
 * ConversationDetail (Story 16.2a AC11, AC12): every message of a conversation, oldest first, with
 * sender, time, text and attachment names; above them the status and who answers.
 *
 * Story 16.6: the answer box under the messages (`ConversationReplyBox`). Story 16.7: the event it
 * belongs to can be changed here.
 *
 * Bodies render in the same sandboxed frame as "What went out" (`MailBodyFrame`). Story 16.6:
 * attachments up to 25 MB are kept and open through a presigned link; larger ones show their name.
 */

import React from 'react';
import {
  Alert,
  Box,
  Button,
  FormControl,
  InputLabel,
  Link,
  MenuItem,
  Select,
  Skeleton,
  Stack,
  Typography,
} from '@mui/material';
import { AttachFile as AttachmentIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { OrganizerSelect } from '@/components/shared/OrganizerSelect/OrganizerSelect';
import { useAuth } from '@/hooks/useAuth/useAuth';
import {
  useConversation,
  usePatchConversation,
} from '@/hooks/useMailConversations/useMailConversations';
import {
  getConversationAttachmentDownloadUrl,
  type MailConversationStatus,
} from '@/services/mailConversationService';
import { ConversationReplyBox } from './ConversationReplyBox';
import { EventSelect } from './EventSelect';
import { MailBodyFrame } from './MailBodyFrame';

const CONVERSATION_STATUSES: MailConversationStatus[] = ['OPEN', 'WAITING', 'DONE'];

interface ConversationDetailProps {
  conversationKey: string;
}

export const ConversationDetail: React.FC<ConversationDetailProps> = ({ conversationKey }) => {
  const { t, i18n } = useTranslation('events');
  const { user } = useAuth();
  const query = useConversation(conversationKey);
  const [downloadFailed, setDownloadFailed] = React.useState(false);
  const patch = usePatchConversation();
  const detail = query.data;

  if (query.isLoading) return <Skeleton variant="rectangular" height={240} />;
  if (query.isError || !detail) {
    return <Alert severity="error">{t('eventPage.mail.loadError')}</Alert>;
  }
  const { conversation, messages } = detail;
  const me = user?.username;
  const save = (change: Parameters<typeof patch.mutate>[0]['patch']) =>
    patch.mutate({ key: conversationKey, patch: change });
  // Story 16.6: same flow as the session materials: fetch a presigned URL, open it in a new tab.
  const download = async (mailKey: string, index: number) => {
    setDownloadFailed(false);
    try {
      const url = await getConversationAttachmentDownloadUrl(conversationKey, mailKey, index);
      window.open(url, '_blank', 'noopener,noreferrer');
    } catch {
      setDownloadFailed(true);
    }
  };

  return (
    <Stack spacing={2} data-testid="conversation-detail">
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        alignItems={{ xs: 'stretch', sm: 'center' }}
      >
        <FormControl size="small" sx={{ minWidth: 160 }}>
          <InputLabel id={`status-${conversationKey}`}>
            {t('eventPage.mail.conversations.statusLabel')}
          </InputLabel>
          <Select
            labelId={`status-${conversationKey}`}
            label={t('eventPage.mail.conversations.statusLabel')}
            value={conversation.status}
            onChange={(e) => save({ status: e.target.value as MailConversationStatus })}
            disabled={patch.isPending}
            data-testid="conversation-status"
          >
            {CONVERSATION_STATUSES.map((status) => (
              <MenuItem key={status} value={status} data-testid={`conversation-status-${status}`}>
                {t(`eventPage.mail.conversations.status.${status}`)}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <OrganizerSelect
          value={conversation.assigneeUsername ?? ''}
          onChange={(username) =>
            save(username ? { assigneeUsername: username } : { clearAssignee: true })
          }
          label={t('eventPage.mail.conversations.assignee')}
          disabled={patch.isPending}
          sx={{ minWidth: 220 }}
          data-testid="conversation-assignee"
        />
        {/* Story 16.7 AC1: which event the conversation belongs to */}
        <EventSelect
          value={conversation.eventCode ?? ''}
          onChange={(eventCode) => save(eventCode ? { eventCode } : { clearEvent: true })}
          label={t('mailCenter.event')}
          noneLabel={t('mailCenter.noEventOption')}
          disabled={patch.isPending}
          sx={{ minWidth: 200 }}
          data-testid="conversation-event"
        />
        {!conversation.assigneeUsername && me && (
          <Button
            variant="outlined"
            size="small"
            onClick={() => save({ assigneeUsername: me })}
            disabled={patch.isPending}
            data-testid="conversation-take-detail"
          >
            {t('eventPage.mail.conversations.takeIt')}
          </Button>
        )}
      </Stack>

      {downloadFailed && (
        <Alert
          severity="error"
          onClose={() => setDownloadFailed(false)}
          data-testid="conversation-attachment-error"
        >
          {t('eventPage.mail.conversations.downloadError')}
        </Alert>
      )}

      {patch.isError && (
        <Alert severity="error" onClose={() => patch.reset()}>
          {t('eventPage.mail.conversations.patchError')}
        </Alert>
      )}

      {messages.map(({ message, bodyHtml }) => {
        const sender =
          message.direction === 'IN'
            ? (message.fromName ?? message.fromAddress ?? '')
            : (message.senderUsername ?? t('eventPage.mail.automatic'));
        const attachments = message.attachments ?? [];
        return (
          <Box key={message.mailKey} data-testid={`conversation-message-${message.mailKey}`}>
            <Typography variant="caption" color="text.secondary" component="div">
              {sender}
              {message.direction === 'IN' && message.fromName && message.fromAddress
                ? ` <${message.fromAddress}>`
                : ''}{' '}
              · {new Date(message.sentAt).toLocaleString(i18n.language)}
            </Typography>
            <Typography variant="body2" sx={{ mb: 1, overflowWrap: 'anywhere' }}>
              {message.subject}
            </Typography>
            {bodyHtml && (
              <MailBodyFrame html={bodyHtml} title={t('eventPage.mail.bodyTitle')} height={280} />
            )}
            {attachments.length > 0 && (
              <Typography
                variant="caption"
                color="text.secondary"
                component="div"
                sx={{ mt: 0.5, display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 0.5 }}
                data-testid="conversation-attachments"
              >
                <AttachmentIcon fontSize="inherit" />
                {t('eventPage.mail.conversations.attachmentsLabel')}
                {attachments.map((a, index) =>
                  a.kept ? (
                    <Link
                      key={`${a.name}-${index}`}
                      component="button"
                      variant="caption"
                      onClick={() => download(message.mailKey, index)}
                      data-testid={`conversation-attachment-${message.mailKey}-${index}`}
                    >
                      {a.name}
                    </Link>
                  ) : (
                    <span
                      key={`${a.name}-${index}`}
                      title={t('eventPage.mail.conversations.notKept')}
                    >
                      {a.name} ({t('eventPage.mail.conversations.nameOnly')})
                    </span>
                  )
                )}
              </Typography>
            )}
          </Box>
        );
      })}

      <ConversationReplyBox conversationKey={conversationKey} />
    </Stack>
  );
};

export default ConversationDetail;
