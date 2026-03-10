/**
 * EventNewsletterTab (Story 10.7 — AC9, Story 10.14 — AC3-AC7)
 *
 * Newsletter management tab for organizers:
 * 1. Subscriber summary
 * 2. Compose & send section (template select, language, preview iframe, send/reminder buttons + confirm dialog)
 * 3. Send history table (collapsible, at the bottom)
 */

import React, { useEffect, useRef, useState } from 'react';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
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
  LinearProgress,
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
  Tooltip,
  Typography,
} from '@mui/material';
import { Email as EmailIcon, ExpandMore as ExpandMoreIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import {
  useSubscriberCount,
  useNewsletterHistory,
  useNewsletterPreview,
  useSendNewsletter,
  useSendStatus,
  useRetryFailedRecipients,
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
  /** sendId of the most recently triggered send — used to poll status. */
  const [activeSendId, setActiveSendId] = useState<string | null>(null);

  const subscriberCountQuery = useSubscriberCount();
  const historyQuery = useNewsletterHistory(eventCode);
  const previewMutation = useNewsletterPreview();
  const sendMutation = useSendNewsletter(eventCode);
  const sendStatusQuery = useSendStatus(eventCode, activeSendId);
  const retryMutation = useRetryFailedRecipients(eventCode);

  const isJobActive =
    sendStatusQuery.data?.status === 'PENDING' || sendStatusQuery.data?.status === 'IN_PROGRESS';
  const newsletterTemplatesQuery = useEmailTemplates({ category: 'NEWSLETTER' });

  // On mount (or when history loads), resume polling if there's an active send in the history.
  // This handles navigating away and back while a send is in progress.
  useEffect(() => {
    if (activeSendId) return; // already tracking one
    const activeRow = historyQuery.data?.find(
      (s) => s.status === 'PENDING' || s.status === 'IN_PROGRESS'
    );
    if (activeRow) {
      setActiveSendId(activeRow.id);
    }
  }, [historyQuery.data, activeSendId]);

  // Refetch history when the active send job reaches a terminal state.
  const prevStatusRef = useRef<string | undefined>(undefined);
  useEffect(() => {
    const status = sendStatusQuery.data?.status;
    const wasActive =
      prevStatusRef.current === 'PENDING' || prevStatusRef.current === 'IN_PROGRESS';
    const isTerminal = status === 'COMPLETED' || status === 'PARTIAL' || status === 'FAILED';
    if (wasActive && isTerminal) {
      historyQuery.refetch();
    }
    prevStatusRef.current = status;
  }, [sendStatusQuery.data?.status, historyQuery]);

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
      onSuccess: (data) => {
        setConfirmOpen(false);
        setPendingSendType(null);
        // Start polling the send-job status.
        if (data.id) {
          setActiveSendId(data.id);
        }
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

      {/* Send progress indicator — visible while PENDING or IN_PROGRESS */}
      {activeSendId && sendStatusQuery.data && (
        <Box>
          {isJobActive ? (
            <Stack spacing={1}>
              <Typography variant="body2" color="text.secondary">
                {t('eventPage.newsletter.sendInProgress', 'Sending newsletter...')}{' '}
                <strong>
                  {sendStatusQuery.data.sentCount} / {sendStatusQuery.data.totalCount}
                </strong>{' '}
                ({sendStatusQuery.data.percentComplete}%)
              </Typography>
              <LinearProgress
                variant="determinate"
                value={sendStatusQuery.data.percentComplete}
                data-testid="newsletter-send-progress"
              />
            </Stack>
          ) : sendStatusQuery.data.status === 'COMPLETED' ? (
            <Alert
              severity="success"
              onClose={() => setActiveSendId(null)}
              data-testid="newsletter-send-completed"
            >
              {t('eventPage.newsletter.sendCompleted', {
                count: sendStatusQuery.data.sentCount,
                defaultValue: `Newsletter sent to ${sendStatusQuery.data.sentCount} recipients.`,
              })}
            </Alert>
          ) : sendStatusQuery.data.status === 'PARTIAL' ? (
            <Alert
              severity="warning"
              onClose={() => setActiveSendId(null)}
              data-testid="newsletter-send-partial"
            >
              {t('eventPage.newsletter.sendPartial', {
                sent: sendStatusQuery.data.sentCount,
                failed: sendStatusQuery.data.failedCount,
                defaultValue: `Sent to ${sendStatusQuery.data.sentCount} recipients. ${sendStatusQuery.data.failedCount} failed — use Retry to resend.`,
              })}
            </Alert>
          ) : sendStatusQuery.data.status === 'FAILED' ? (
            <Alert
              severity="error"
              onClose={() => setActiveSendId(null)}
              data-testid="newsletter-send-failed"
            >
              {t(
                'eventPage.newsletter.sendFailed',
                'Send failed. Please use Retry on the send history row.'
              )}
            </Alert>
          ) : null}
        </Box>
      )}

      {/* Section 2 — Compose & send */}
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
              SelectDisplayProps={
                {
                  'data-testid': 'newsletter-template-select',
                } as React.HTMLAttributes<HTMLDivElement>
              }
            >
              {filteredTemplates.length === 0 && !newsletterTemplatesQuery.isLoading ? (
                <MenuItem value={selectedTemplateKey} disabled>
                  {selectedTemplateKey}
                </MenuItem>
              ) : (
                filteredTemplates.map((tpl) => (
                  <MenuItem key={tpl.templateKey} value={tpl.templateKey}>
                    {tpl.templateKey}
                  </MenuItem>
                ))
              )}
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
              disabled={sendMutation.isPending || isJobActive}
            >
              {t('eventPage.newsletter.sendNewsletter')}
            </Button>
            <Button
              variant="outlined"
              color="secondary"
              onClick={() => openConfirm('reminder')}
              disabled={sendMutation.isPending || isJobActive}
            >
              {t('eventPage.newsletter.sendReminder')}
            </Button>
          </Stack>

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

      {/* Section 3 — Send history (collapsible) */}
      <Accordion variant="outlined" disableGutters>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle1" fontWeight="medium">
            {t('eventPage.newsletter.sendHistory')}
            {historyQuery.data && historyQuery.data.length > 0 && (
              <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                ({historyQuery.data.length})
              </Typography>
            )}
          </Typography>
        </AccordionSummary>
        <AccordionDetails sx={{ p: 0 }}>
          {historyQuery.isLoading ? (
            <Box p={2}>
              <Skeleton variant="rectangular" height={100} />
            </Box>
          ) : historyQuery.data && historyQuery.data.length > 0 ? (
            <Paper variant="outlined" sx={{ border: 0 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>{t('common:labels.date')}</TableCell>
                    <TableCell>{t('eventPage.newsletter.historyType', 'Type')}</TableCell>
                    <TableCell align="right">
                      {t('eventPage.newsletter.historyRecipients', 'Recipients')}
                    </TableCell>
                    <TableCell align="center">
                      {t('eventPage.newsletter.historyStatus', 'Status')}
                    </TableCell>
                    <TableCell />
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
                      <TableCell align="right">
                        {send.id === activeSendId && isJobActive
                          ? `${sendStatusQuery.data?.sentCount ?? send.sentCount ?? 0} / ${send.recipientCount}`
                          : send.recipientCount}
                      </TableCell>
                      <TableCell align="center">
                        <Typography
                          variant="caption"
                          color={
                            send.status === 'COMPLETED'
                              ? 'success.main'
                              : send.status === 'PARTIAL'
                                ? 'warning.main'
                                : send.status === 'FAILED'
                                  ? 'error.main'
                                  : 'text.secondary'
                          }
                        >
                          {send.status ?? 'COMPLETED'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        {(send.status === 'PARTIAL' || send.status === 'FAILED') && (
                          <Tooltip
                            title={t(
                              'eventPage.newsletter.retryTooltip',
                              'Retry failed recipients'
                            )}
                          >
                            <span>
                              <Button
                                size="small"
                                variant="text"
                                color="warning"
                                onClick={() => {
                                  retryMutation.mutate(send.id, {
                                    onSuccess: (data) => {
                                      if (data.id) setActiveSendId(data.id);
                                    },
                                  });
                                }}
                                disabled={retryMutation.isPending || isJobActive}
                              >
                                {t('eventPage.newsletter.retryButton', 'Retry')}
                              </Button>
                            </span>
                          </Tooltip>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Paper>
          ) : (
            <Box p={2}>
              <Typography variant="body2" color="text.secondary">
                {t('eventPage.newsletter.noHistory')}
              </Typography>
            </Box>
          )}
        </AccordionDetails>
      </Accordion>

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
