/**
 * EventNewsletterTab (Story 10.7 — AC9, Story 10.14 — AC3-AC7)
 *
 * Newsletter management tab for organizers:
 * 1. Subscriber summary
 * 2. Compose & send section (template select, language, preview iframe, send/reminder buttons + confirm dialog)
 * 3. Retry of the newest send that left recipients out (the history is on the mail page, Story 16.3)
 */

import React, { useEffect, useRef, useState } from 'react';
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
  FormControlLabel,
  InputLabel,
  LinearProgress,
  Link,
  MenuItem,
  Paper,
  Select,
  Skeleton,
  Stack,
  Switch,
  Typography,
} from '@mui/material';
import { Email as EmailIcon } from '@mui/icons-material';
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
import { useBreakpoints } from '@/hooks/useBreakpoints';

interface EventNewsletterTabProps {
  eventCode: string;
  eventTitle: string;
  /** Story 16.4: called after a real (non-test) send was started; the mail page closes the panel. */
  onSent?: () => void;
}

type SendType = 'newsletter' | 'reminder';

export const EventNewsletterTab: React.FC<EventNewsletterTabProps> = ({
  eventCode,
  eventTitle,
  onSent,
}) => {
  const { t } = useTranslation(['events', 'organizer']);
  const { isMobile } = useBreakpoints();
  const [locale, setLocale] = useState<'de' | 'en'>('de');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pendingSendType, setPendingSendType] = useState<SendType | null>(null);
  const [previewHtml, setPreviewHtml] = useState<string | null>(null);
  const [selectedTemplateKey, setSelectedTemplateKey] = useState<string>('newsletter-event');
  const [testMode, setTestMode] = useState(false);
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

  // The newest send, if it ended PARTIAL or FAILED: offer to retry its failed recipients.
  const retryableSend = (() => {
    const newest = historyQuery.data?.[0];
    return newest && (newest.status === 'PARTIAL' || newest.status === 'FAILED') ? newest : null;
  })();

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
      ...(testMode && { testMode: true }),
    };
    sendMutation.mutate(request, {
      onSuccess: (data) => {
        setConfirmOpen(false);
        setPendingSendType(null);
        // Start polling the send-job status.
        if (data.id) {
          setActiveSendId(data.id);
        }
        // A test send stays in the panel so the real send can follow; the list shows progress.
        if (!testMode) {
          onSent?.();
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
      ...(testMode && { testMode: true }),
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

      {/* Section 2 — Compose & send (two-column 30/70 split).
          minmax(0, …) on both tracks so the send-history table (minWidth:max-content, scrolls
          in its own overflowX box) can't expand a track past the viewport on mobile. */}
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'minmax(300px, 34%) minmax(0, 1fr)' },
          gap: 3,
          alignItems: 'start',
        }}
      >
        {/* LEFT: compose form controls */}
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Typography variant="subtitle1" gutterBottom fontWeight="medium">
            {t('eventPage.newsletter.composeTitle')}
          </Typography>
          <Stack spacing={2}>
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

            {/* Test mode toggle — send only to organizer subscribers */}
            <FormControlLabel
              control={
                <Switch
                  checked={testMode}
                  onChange={(e) => setTestMode(e.target.checked)}
                  color="warning"
                  data-testid="newsletter-test-mode-switch"
                />
              }
              label={t('eventPage.newsletter.testMode', 'Test Mode (organizers only)')}
            />
            {testMode && (
              <Alert severity="warning" data-testid="newsletter-test-mode-warning">
                {t(
                  'eventPage.newsletter.testModeWarning',
                  'Test mode enabled — newsletter will only be sent to organizer subscribers.'
                )}
              </Alert>
            )}

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} flexWrap="wrap">
              <Button
                variant="outlined"
                startIcon={
                  previewMutation.isPending ? <CircularProgress size={16} /> : <EmailIcon />
                }
                onClick={handlePreview}
                disabled={previewMutation.isPending}
                data-testid="newsletter-preview-button"
              >
                {t('eventPage.newsletter.preview')}
              </Button>
              <Button
                variant="contained"
                onClick={() => openConfirm('newsletter')}
                disabled={sendMutation.isPending || isJobActive}
                data-testid="newsletter-send-button"
              >
                {t('eventPage.newsletter.sendNewsletter')}
              </Button>
              <Button
                variant="outlined"
                color="secondary"
                onClick={() => openConfirm('reminder')}
                disabled={sendMutation.isPending || isJobActive}
                data-testid="newsletter-send-reminder-button"
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
        </Paper>

        {/* RIGHT: preview + send history */}
        <Stack spacing={2}>
          {/* Preview iframe */}
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              {t('eventPage.newsletter.previewTitle', 'Email Preview')}
            </Typography>
            {previewHtml ? (
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
            ) : (
              <Typography variant="body2" color="text.secondary">
                {t('eventPage.newsletter.previewPlaceholder', 'Preview will appear here')}
              </Typography>
            )}
          </Paper>

          {/* Section 3 — Retry. The send history moved to the mail page ("What went out", Story
              16.3); the most recent send that left recipients out can still be retried here. */}
          {retryableSend && (
            <Alert
              severity="warning"
              data-testid="newsletter-retry-alert"
              action={
                <Button
                  size="small"
                  color="inherit"
                  onClick={() => {
                    retryMutation.mutate(retryableSend.id, {
                      onSuccess: (data) => {
                        if (data.id) setActiveSendId(data.id);
                      },
                    });
                  }}
                  disabled={retryMutation.isPending || isJobActive}
                >
                  {t('eventPage.newsletter.retryButton', 'Retry')}
                </Button>
              }
            >
              {t('eventPage.newsletter.retryHint', {
                date: new Date(retryableSend.sentAt).toLocaleString(),
                count: retryableSend.failedCount ?? 0,
              })}
            </Alert>
          )}
        </Stack>
      </Box>

      {/* Confirmation dialog */}
      <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)} fullScreen={isMobile}>
        <DialogTitle>{t('eventPage.newsletter.confirmSendTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {testMode
              ? t('eventPage.newsletter.confirmSendBodyTestMode', {
                  type: sendType,
                  eventTitle,
                  templateKey: selectedTemplateKey,
                  defaultValue: `Send {{type}} using '{{templateKey}}' to organizer subscribers only (test mode) for event «{{eventTitle}}»?`,
                })
              : t('eventPage.newsletter.confirmSendBody', {
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
            data-testid="newsletter-confirm-send-button"
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
