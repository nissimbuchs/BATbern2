/**
 * MailDetail (Story 16.3): one mail exactly as sent, its recipients and what came back.
 *
 * Desktop opens it inline below its row, the mail beside the facts, so the list keeps its place
 * and the mail gets the full page width (owner decision 2026-10-31, `docs/ux/mail-center-layouts.html`
 * variant A). Mobile opens it as a full-screen dialog, like the rest of the organizer area
 * (`docs/ux/mail-center-mobile.html`).
 *
 * The body is rendered in a sandboxed iframe without scripts. Failed recipients are listed first
 * with the reason (AC4).
 */

import React from 'react';
import {
  Alert,
  AppBar,
  Box,
  Chip,
  Dialog,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Skeleton,
  Stack,
  Toolbar,
  Typography,
} from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useMailMessage } from '@/hooks/useMailMessages/useMailMessages';
import type { MailRecipientEntry } from '@/services/mailMessageService';

interface MailDetailProps {
  eventCode: string;
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
  const query = useMailMessage(eventCode, mailKey);
  const detail = query.data;
  const recipients = [...(detail?.recipients ?? [])].sort(
    (a, b) => STATUS_ORDER[a.status] - STATUS_ORDER[b.status]
  );

  if (query.isLoading) return <Skeleton variant="rectangular" height={240} />;
  if (query.isError || !detail) {
    return <Alert severity="error">{t('eventPage.mail.loadError')}</Alert>;
  }

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
          <Box
            component="iframe"
            title={t('eventPage.mail.bodyTitle')}
            srcDoc={detail.bodyHtml}
            sandbox=""
            sx={{
              width: '100%',
              height: { xs: '70vh', md: 560 },
              border: 1,
              borderColor: 'divider',
              borderRadius: 1,
              bgcolor: 'background.paper',
            }}
            data-testid="mail-detail-body"
          />
        ) : (
          <Typography variant="body2" color="text.secondary">
            {t('eventPage.mail.bodyNotRecorded')}
          </Typography>
        )}
      </Box>

      <Stack spacing={2} sx={{ order: { xs: 1, md: 2 }, minWidth: 0 }}>
        <Typography variant="caption" color="text.secondary">
          {new Date(detail.message.sentAt).toLocaleString(i18n.language)} ·{' '}
          {detail.message.senderUsername ?? t('eventPage.mail.automatic')} ·{' '}
          {t('eventPage.mail.delivered', { count: detail.message.deliveredCount })}
          {detail.message.failedCount > 0 &&
            ` · ${t('eventPage.mail.failed', { count: detail.message.failedCount })}`}
        </Typography>

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
  eventCode: string;
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
}) => {
  const { t } = useTranslation('events');
  return (
    <Dialog
      fullScreen
      open={mailKey !== null}
      onClose={onClose}
      aria-labelledby="mail-detail-title"
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
            onClick={onClose}
            aria-label={t('common:actions.close', 'Close')}
          >
            <CloseIcon />
          </IconButton>
          <Typography id="mail-detail-title" variant="subtitle1" noWrap sx={{ flex: 1 }}>
            {title ?? mailKey}
          </Typography>
        </Toolbar>
      </AppBar>
      <Box sx={{ p: 2 }}>{mailKey && <MailDetail eventCode={eventCode} mailKey={mailKey} />}</Box>
    </Dialog>
  );
};

export default MailDetail;
