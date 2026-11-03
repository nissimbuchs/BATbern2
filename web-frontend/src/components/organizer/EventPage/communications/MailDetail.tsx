/**
 * MailDetail (Story 16.3): one mail exactly as sent, its recipients and what came back.
 *
 * Desktop opens it inline below its row, the mail beside the facts, so the list keeps its place
 * and the mail gets the full page width (owner decision 2026-10-31, `docs/ux/mail-center-layouts.html`
 * variant A). Mobile opens it as a full-screen dialog, like the rest of the organizer area
 * (`docs/ux/mail-center-mobile.html`).
 *
 * The body is rendered in a sandboxed iframe without scripts. Failed recipients are listed first
 * with the reason (AC4). Story 16.7/16.10: without an event it loads the mail by its key; a received
 * mail shows its sender and a link to its conversation.
 */

import React from 'react';
import {
  Alert,
  Box,
  Chip,
  Link,
  List,
  ListItem,
  ListItemText,
  Skeleton,
  Stack,
  Typography,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router-dom';
import { useMailMessage } from '@/hooks/useMailMessages/useMailMessages';
import type { MailRecipientEntry } from '@/services/mailMessageService';
import { FullScreenDetailDialog } from './FullScreenDetailDialog';
import { MailBodyFrame } from './MailBodyFrame';

interface MailDetailProps {
  /** Without it the mail is loaded by its key alone (`GET /mails/{mailKey}`, Story 16.7). */
  eventCode?: string;
  mailKey: string;
}

const STATUS_ORDER: Record<MailRecipientEntry['status'], number> = {
  FAILED: 0,
  SENT: 1,
  DELIVERED: 2,
};

/** The mail and its facts. Two columns from `md` up, stacked below. */
export const MailDetail: React.FC<MailDetailProps> = ({ eventCode, mailKey }) => {
  const { t, i18n } = useTranslation('events');
  const query = useMailMessage(eventCode ?? null, mailKey);
  const detail = query.data;
  const recipients = [...(detail?.recipients ?? [])].sort(
    (a, b) => STATUS_ORDER[a.status] - STATUS_ORDER[b.status]
  );

  if (query.isLoading) return <Skeleton variant="rectangular" height={240} />;
  if (query.isError || !detail) {
    return <Alert severity="error">{t('eventPage.mail.loadError')}</Alert>;
  }

  const received = detail.message.direction === 'IN';
  const conversationKey = detail.message.conversationKey;
  const conversationLink = !conversationKey
    ? null
    : detail.message.eventCode
      ? `/organizer/events/${encodeURIComponent(detail.message.eventCode)}?tab=communications&conversation=${encodeURIComponent(conversationKey)}`
      : `/organizer/mail?conversation=${encodeURIComponent(conversationKey)}`;

  return (
    <Box
      data-testid="mail-detail"
      sx={{
        display: 'grid',
        gap: 2,
        gridTemplateColumns: { xs: '1fr', md: '2fr 1fr' },
        alignItems: 'start',
      }}
    >
      <Box sx={{ order: { xs: 2, md: 1 }, minWidth: 0 }}>
        {detail.bodyHtml ? (
          <MailBodyFrame
            html={detail.bodyHtml}
            title={t('eventPage.mail.bodyTitle')}
            data-testid="mail-detail-body"
          />
        ) : (
          <Typography variant="body2" color="text.secondary">
            {t('eventPage.mail.bodyNotRecorded')}
          </Typography>
        )}
      </Box>

      <Stack spacing={2} sx={{ order: { xs: 1, md: 2 }, minWidth: 0 }}>
        {received ? (
          // Story 16.10 AC5: a received mail names its sender and links to its conversation
          <Stack spacing={0.5}>
            <Typography variant="caption" color="text.secondary">
              {new Date(detail.message.sentAt).toLocaleString(i18n.language)} ·{' '}
              {t('mailCenter.receivedFrom', {
                sender: detail.message.fromName
                  ? `${detail.message.fromName} <${detail.message.fromAddress ?? ''}>`
                  : (detail.message.fromAddress ?? ''),
              })}
            </Typography>
            {conversationLink && (
              <Link
                component={RouterLink}
                to={conversationLink}
                variant="body2"
                data-testid="mail-detail-conversation-link"
              >
                {t('mailCenter.openConversation')}
              </Link>
            )}
          </Stack>
        ) : (
          <Typography variant="caption" color="text.secondary">
            {new Date(detail.message.sentAt).toLocaleString(i18n.language)} ·{' '}
            {detail.message.senderUsername ?? t('eventPage.mail.automatic')} ·{' '}
            {t('eventPage.mail.delivered', { count: detail.message.deliveredCount })}
            {detail.message.failedCount > 0 &&
              ` · ${t('eventPage.mail.failed', { count: detail.message.failedCount })}`}
          </Typography>
        )}

        {recipients.length > 0 && (
          <Box>
            <Typography variant="subtitle2">
              {detail.message.newsletterSendId
                ? t('eventPage.mail.failedRecipients')
                : t('eventPage.mail.recipientsHeading')}
            </Typography>
            <List
              dense
              disablePadding
              data-testid="mail-detail-recipients"
              sx={{ maxHeight: { md: 480 }, overflowY: { md: 'auto' } }}
            >
              {recipients.map((r) => (
                <ListItem key={`${r.kind}-${r.address}`} disableGutters>
                  <ListItemText
                    primary={r.address}
                    secondary={r.failureReason ?? undefined}
                    primaryTypographyProps={{ sx: { overflowWrap: 'anywhere' } }}
                  />
                  <Chip
                    size="small"
                    label={t(`eventPage.mail.status.${r.status}`)}
                    color={
                      r.status === 'FAILED'
                        ? 'error'
                        : r.status === 'DELIVERED'
                          ? 'success'
                          : 'default'
                    }
                    variant="outlined"
                  />
                </ListItem>
              ))}
            </List>
          </Box>
        )}
      </Stack>
    </Box>
  );
};

interface MailDetailDialogProps {
  eventCode?: string;
  mailKey: string | null;
  title?: string;
  onClose: () => void;
}

/** Mobile: the same detail as a full-screen dialog with the subject in the top bar. */
export const MailDetailDialog: React.FC<MailDetailDialogProps> = ({
  eventCode,
  mailKey,
  title,
  onClose,
}) => (
  <FullScreenDetailDialog open={mailKey !== null} title={title ?? mailKey ?? ''} onClose={onClose}>
    {mailKey && <MailDetail eventCode={eventCode} mailKey={mailKey} />}
  </FullScreenDetailDialog>
);

export default MailDetail;
