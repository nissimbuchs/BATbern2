/**
 * MailDetailDrawer (Story 16.3): one mail exactly as sent, its recipients and what came back.
 *
 * The body is rendered in a sandboxed iframe without scripts. Failed recipients are listed first
 * with the reason (AC4). Full screen on mobile.
 */

import React from 'react';
import {
  Alert,
  Box,
  Chip,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Skeleton,
  Stack,
  Typography,
} from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import { useMailMessage } from '@/hooks/useMailMessages/useMailMessages';
import type { MailRecipientEntry } from '@/services/mailMessageService';

interface MailDetailDrawerProps {
  eventCode: string;
  mailKey: string | null;
  onClose: () => void;
}

const STATUS_ORDER: Record<MailRecipientEntry['status'], number> = {
  FAILED: 0,
  SENT: 1,
  DELIVERED: 2,
};

export const MailDetailDrawer: React.FC<MailDetailDrawerProps> = ({
  eventCode,
  mailKey,
  onClose,
}) => {
  const { t, i18n } = useTranslation('events');
  const { isMobile } = useBreakpoints();
  const query = useMailMessage(eventCode, mailKey);
  const detail = query.data;
  const recipients = [...(detail?.recipients ?? [])].sort(
    (a, b) => STATUS_ORDER[a.status] - STATUS_ORDER[b.status]
  );

  return (
    <Drawer
      anchor="right"
      open={mailKey !== null}
      onClose={onClose}
      PaperProps={{ sx: { width: isMobile ? '100%' : 640 } }}
    >
      <Box sx={{ p: 2 }} data-testid="mail-detail">
        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
          <Typography variant="h6" sx={{ flex: 1, overflowWrap: 'anywhere' }}>
            {detail?.message.subject ?? detail?.message.templateKey ?? mailKey}
          </Typography>
          <IconButton onClick={onClose} aria-label={t('common:actions.close', 'Close')}>
            <CloseIcon />
          </IconButton>
        </Box>

        {query.isLoading && <Skeleton variant="rectangular" height={240} />}
        {query.isError && <Alert severity="error">{t('eventPage.mail.loadError')}</Alert>}

        {detail && (
          <Stack spacing={2}>
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
                <List dense disablePadding data-testid="mail-detail-recipients">
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

            <Divider />

            {detail.bodyHtml ? (
              <Box
                component="iframe"
                title={t('eventPage.mail.bodyTitle')}
                srcDoc={detail.bodyHtml}
                sandbox=""
                sx={{
                  width: '100%',
                  height: 560,
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: 1,
                }}
                data-testid="mail-detail-body"
              />
            ) : (
              <Typography variant="body2" color="text.secondary">
                {t('eventPage.mail.bodyNotRecorded')}
              </Typography>
            )}
          </Stack>
        )}
      </Box>
    </Drawer>
  );
};

export default MailDetailDrawer;
