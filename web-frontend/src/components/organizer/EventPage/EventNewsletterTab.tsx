/**
 * EventNewsletterTab (Story 10.7 — AC9, Story 10.14 — AC3-AC7)
 *
 * Newsletter management tab for organizers:
 * 1. Subscriber summary
 * 2. Send history table
 * 3. Compose & send section (template select, language, preview iframe, send/reminder buttons + confirm dialog)
 */

import React, { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControl,
  InputLabel,
  Link,
  MenuItem,
  Paper,
  Select,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { Email as EmailIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import {
  useSubscriberCount,
  useNewsletterHistory,
  useNewsletterPreview,
  useSendNewsletter,
} from '@/hooks/useNewsletter/useNewsletter';
import type { NewsletterSendRequest } from '@/services/newsletterService';
import { useEmailTemplates } from '@/hooks/useEmailTemplates';
import type { EmailTemplateResponse } from '@/services/emailTemplateService';

interface EventNewsletterTabProps {
  eventCode: string;
  eventTitle: string;
}

type SendType = 'newsletter' | 'reminder';

export const EventNewsletterTab: React.FC<EventNewsletterTabProps> = ({
  eventCode,
  eventTitle,
}) => {
  const { t } = useTranslation(['events', 'organizer']);
  const [locale, setLocale] = useState<'de' | 'en'>('de');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pendingSendType, setPendingSendType] = useState<SendType | null>(null);
  const [previewHtml, setPreviewHtml] = useState<string | null>(null);
  const [selectedTemplateKey, setSelectedTemplateKey] = useState<string>('newsletter-event');

  const subscriberCountQuery = useSubscriberCount();
  const historyQuery = useNewsletterHistory(eventCode);
  const previewMutation = useNewsletterPreview();
  const sendMutation = useSendNewsletter(eventCode);
  const newsletterTemplatesQuery = useEmailTemplates({ category: 'NEWSLETTER' });

  const filteredTemplates: EmailTemplateResponse[] = React.useMemo(
    () => (newsletterTemplatesQuery.data ?? []).filter((tpl) => tpl.locale === locale),
    [newsletterTemplatesQuery.data, locale]
  );

  useEffect(() => {
    const defaultKey = 'newsletter-event';
    const hasDefault = filteredTemplates.some((tpl) => tpl.templateKey === defaultKey);
    setSelectedTemplateKey(
      hasDefault ? defaultKey : (filteredTemplates[0]?.templateKey ?? defaultKey)
    );
  }, [locale, filteredTemplates]);

  function openConfirm(sendType: SendType) {
    setPendingSendType(sendType);
    setConfirmOpen(true);
  }

  function handleConfirmSend() {
    if (!pendingSendType) {
      return;
    }
    const request: NewsletterSendRequest = {
      isReminder: pendingSendType === 'reminder',
      locale,
      templateKey: selectedTemplateKey,
    };
    sendMutation.mutate(request, {
      onSuccess: () => {
        setConfirmOpen(false);
        setPendingSendType(null);
      },
      onError: () => {
        setConfirmOpen(false);
      },
    });
  }

  function handlePreview() {
    const request: NewsletterSendRequest = {
      isReminder: false,
      locale,
      templateKey: selectedTemplateKey,
    };
    previewMutation.mutate(
      { eventCode, request },
      {
        onSuccess: (data) => setPreviewHtml(data.htmlPreview),
      }
    );
  }

  const activeCount = subscriberCountQuery.data?.totalActive ?? 0;
  const sendType =
    pendingSendType === 'reminder'
      ? t('eventPage.newsletter.sendReminder')
      : t('eventPage.newsletter.sendNewsletter');

  return (
    <Stack spacing={4}>
      {/* Section 1 — Subscriber count */}
      <Box>
        <Typography variant="h6" gutterBottom>
          {t('eventPage.newsletter.title')}
        </Typography>
        {subscriberCountQuery.isLoading ? (
          <Skeleton width={200} />
        ) : (
          <Typography variant="body1">
            {t('eventPage.newsletter.subscriberCount', { count: activeCount })}
          </Typography>
        )}
      </Box>

      {/* Section 2 — Send history */}
      <Box>
        <Typography variant="subtitle1" gutterBottom fontWeight="medium">
          {t('eventPage.newsletter.sendHistory')}
        </Typography>
        {historyQuery.isLoading ? (
          <Skeleton variant="rectangular" height={100} />
        ) : historyQuery.data && historyQuery.data.length > 0 ? (
          <Paper variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>{t('common:labels.date')}</TableCell>
                  <TableCell>{t('eventPage.newsletter.historyType', 'Type')}</TableCell>
                  <TableCell align="right">
                    {t('eventPage.newsletter.historyRecipients', 'Recipients')}
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {historyQuery.data.map((send) => (
                  <TableRow key={send.id}>
                    <TableCell>{new Date(send.sentAt).toLocaleString()}</TableCell>
                    <TableCell>
                      {send.isReminder
                        ? t('eventPage.newsletter.typeReminder', 'Reminder')
                        : t('eventPage.newsletter.typeNewsletter', 'Newsletter')}
                    </TableCell>
                    <TableCell align="right">{send.recipientCount}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        ) : (
          <Typography variant="body2" color="text.secondary">
            {t('eventPage.newsletter.noHistory')}
          </Typography>
        )}
      </Box>

      {/* Section 3 — Compose & send */}
      <Box>
        <Typography variant="subtitle1" gutterBottom fontWeight="medium">
          {t('eventPage.newsletter.composeTitle')}
        </Typography>
        <Stack spacing={2} maxWidth={400}>
          <FormControl size="small">
            <InputLabel>{t('eventPage.newsletter.locale')}</InputLabel>
            <Select
              value={locale}
              label={t('eventPage.newsletter.locale')}
              onChange={(e) => setLocale(e.target.value as 'de' | 'en')}
            >
              <MenuItem value="de">Deutsch</MenuItem>
              <MenuItem value="en">English</MenuItem>
            </Select>
          </FormControl>

          {/* Template selector */}
          <FormControl size="small">
            <InputLabel>{t('organizer:newsletter.templateSelect.label')}</InputLabel>
            <Select
              value={selectedTemplateKey}
              label={t('organizer:newsletter.templateSelect.label')}
              onChange={(e) => setSelectedTemplateKey(e.target.value)}
              disabled={newsletterTemplatesQuery.isLoading}
              inputProps={{ 'data-testid': 'newsletter-template-select' }}
            >
              {filteredTemplates.map((tpl) => (
                <MenuItem key={tpl.templateKey} value={tpl.templateKey}>
                  {tpl.templateKey}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          {/* Create new template link */}
          <Link
            href="/organizer/admin?tab=email-templates"
            variant="caption"
            color="text.secondary"
            underline="hover"
          >
            {t('organizer:newsletter.templateSelect.createNew')} ↗
          </Link>

          <Stack direction="row" spacing={1} flexWrap="wrap">
            <Button
              variant="outlined"
              startIcon={previewMutation.isPending ? <CircularProgress size={16} /> : <EmailIcon />}
              onClick={handlePreview}
              disabled={previewMutation.isPending}
            >
              {t('eventPage.newsletter.preview')}
            </Button>
            <Button
              variant="contained"
              onClick={() => openConfirm('newsletter')}
              disabled={sendMutation.isPending}
            >
              {t('eventPage.newsletter.sendNewsletter')}
            </Button>
            <Button
              variant="outlined"
              color="secondary"
              onClick={() => openConfirm('reminder')}
              disabled={sendMutation.isPending}
            >
              {t('eventPage.newsletter.sendReminder')}
            </Button>
          </Stack>

          {sendMutation.isSuccess && (
            <Alert severity="success">
              {t('eventPage.newsletter.sendSuccess', {
                count: sendMutation.data?.recipientCount ?? 0,
              })}
            </Alert>
          )}
          {sendMutation.isError && (
            <Alert severity="error">
              {t('eventPage.newsletter.sendError', 'Failed to send. Please try again.')}
            </Alert>
          )}
        </Stack>

        {/* Preview iframe */}
        {previewHtml && (
          <Box mt={3}>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              {t('eventPage.newsletter.previewTitle', 'Email Preview')}
            </Typography>
            <Box
              component="iframe"
              srcDoc={previewHtml}
              sx={{
                width: '100%',
                height: 600,
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 1,
              }}
              title="newsletter-preview"
              sandbox="allow-same-origin"
            />
          </Box>
        )}
      </Box>

      {/* Confirmation dialog */}
      <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)}>
        <DialogTitle>{t('eventPage.newsletter.confirmSendTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('eventPage.newsletter.confirmSendBody', {
              type: sendType,
              count: activeCount,
              eventTitle,
              templateKey: selectedTemplateKey,
            })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOpen(false)} disabled={sendMutation.isPending}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            onClick={handleConfirmSend}
            variant="contained"
            disabled={sendMutation.isPending}
            autoFocus
          >
            {sendMutation.isPending ? (
              <CircularProgress size={20} />
            ) : (
              t('common.confirm', 'Confirm')
            )}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
};
