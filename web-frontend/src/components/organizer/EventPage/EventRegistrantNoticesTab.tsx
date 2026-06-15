/**
 * EventRegistrantNoticesTab (Story 7.3 hardening)
 *
 * Organizer tab to send a REGISTRANT-targeted notice (e.g. the "slides are online" mail) to an
 * event's ACTIVE REGISTRANTS — never the global newsletter-subscriber pool. Mirrors the Newsletter
 * tab UX (template select, language, preview iframe, send + confirm) but is wired to the dedicated
 * registrant-notice endpoints.
 *
 * The language selector controls the PREVIEW only — the actual send resolves each registrant's own
 * language (de-anything to German, en to English, otherwise German fallback).
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
  Stack,
  Typography,
} from '@mui/material';
import { Email as EmailIcon } from '@mui/icons-material';
import axios from 'axios';
import { useTranslation } from 'react-i18next';
import { useEmailTemplates } from '@/hooks/useEmailTemplates';
import type { EmailTemplateResponse } from '@/services/emailTemplateService';
import {
  usePreviewRegistrantNotice,
  useSendRegistrantNotice,
} from '@/hooks/useRegistrantNotice/useRegistrantNotice';
import { useSendStatus } from '@/hooks/useNewsletter/useNewsletter';
import { useBreakpoints } from '@/hooks/useBreakpoints';

interface EventRegistrantNoticesTabProps {
  eventCode: string;
  eventTitle: string;
}

export const EventRegistrantNoticesTab: React.FC<EventRegistrantNoticesTabProps> = ({
  eventCode,
  eventTitle,
}) => {
  const { t } = useTranslation(['events', 'organizer', 'common']);
  const { isMobile } = useBreakpoints();
  const [locale, setLocale] = useState<'de' | 'en'>('de');
  const [selectedTemplateKey, setSelectedTemplateKey] = useState<string>('');
  const [previewHtml, setPreviewHtml] = useState<string | null>(null);
  const [recipientCount, setRecipientCount] = useState<number | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  /** sendId of the most recently triggered send — polled for its final status. */
  const [activeSendId, setActiveSendId] = useState<string | null>(null);

  const templatesQuery = useEmailTemplates({ category: 'REGISTRANT_NOTICE' });
  const previewMutation = usePreviewRegistrantNotice(eventCode);
  const sendMutation = useSendRegistrantNotice(eventCode);
  // Reuse the template-agnostic newsletter send-status endpoint (polls until terminal).
  const sendStatusQuery = useSendStatus(eventCode, activeSendId);

  const filteredTemplates: EmailTemplateResponse[] = React.useMemo(
    () => (templatesQuery.data ?? []).filter((tpl) => tpl.locale === locale),
    [templatesQuery.data, locale]
  );

  // Keep a valid template selected as the locale/template list changes.
  useEffect(() => {
    if (filteredTemplates.length === 0) {
      setSelectedTemplateKey('');
      return;
    }
    if (!filteredTemplates.some((tpl) => tpl.templateKey === selectedTemplateKey)) {
      setSelectedTemplateKey(filteredTemplates[0].templateKey);
    }
  }, [filteredTemplates, selectedTemplateKey]);

  function handlePreview() {
    if (!selectedTemplateKey) return;
    setActiveSendId(null);
    previewMutation.mutate(
      { templateKey: selectedTemplateKey, locale },
      {
        onSuccess: (data) => {
          setPreviewHtml(data.htmlPreview);
          setRecipientCount(data.recipientCount);
        },
      }
    );
  }

  // Open the confirm dialog with a real recipient count. If the organizer hasn't previewed yet,
  // fetch the count first so the dialog never shows a misleading "0".
  function handleOpenConfirm() {
    if (!selectedTemplateKey) return;
    if (recipientCount !== null) {
      setConfirmOpen(true);
      return;
    }
    previewMutation.mutate(
      { templateKey: selectedTemplateKey, locale },
      {
        onSuccess: (data) => {
          setPreviewHtml(data.htmlPreview);
          setRecipientCount(data.recipientCount);
          setConfirmOpen(true);
        },
      }
    );
  }

  function handleConfirmSend() {
    if (!selectedTemplateKey) return;
    sendMutation.mutate(selectedTemplateKey, {
      onSuccess: (data) => {
        setConfirmOpen(false);
        setActiveSendId(data.sendId); // begin polling for the final result
      },
      onError: () => setConfirmOpen(false),
    });
  }

  const noTemplates = !templatesQuery.isLoading && filteredTemplates.length === 0;

  // Surface the API error. The 409 "already sent / in progress" guard is an expected outcome the
  // organizer must understand (shown as an info, not a red failure); other errors show the API
  // message when present, else a generic fallback.
  const apiError: { severity: 'info' | 'error'; message: string } | null = (() => {
    const err = sendMutation.error ?? previewMutation.error;
    if (!err) return null;
    if (axios.isAxiosError(err)) {
      const status = err.response?.status;
      const apiMsg = (err.response?.data as { message?: string } | undefined)?.message;
      if (status === 409) {
        return {
          severity: 'info',
          message: t('eventPage.registrantNotices.alreadySent', {
            defaultValue:
              apiMsg ??
              'This notice has already been sent (or a send is in progress) for this event.',
          }),
        };
      }
      if (apiMsg) return { severity: 'error', message: apiMsg };
    }
    return {
      severity: 'error',
      message: t('eventPage.registrantNotices.error', 'Something went wrong. Please try again.'),
    };
  })();

  return (
    <Stack spacing={4}>
      <Box>
        <Typography variant="h6" gutterBottom>
          {t('eventPage.registrantNotices.title', 'Registrant Notices')}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t(
            'eventPage.registrantNotices.description',
            'Send a one-off notice (e.g. “the slides are online”) to this event’s registrants. Recipients are the active registrants — not newsletter subscribers — and each gets the mail in their own language.'
          )}
        </Typography>
      </Box>

      {activeSendId &&
        sendStatusQuery.data &&
        (() => {
          const s = sendStatusQuery.data;
          const sending = s.status === 'PENDING' || s.status === 'IN_PROGRESS';
          if (sending) {
            return (
              <Alert severity="info" data-testid="rn-send-progress">
                {t('eventPage.registrantNotices.sending', {
                  sent: s.sentCount,
                  total: s.totalCount,
                  defaultValue: `Sending… ${s.sentCount} / ${s.totalCount}`,
                })}
              </Alert>
            );
          }
          // Terminal: skipped = registrants with no resolvable email on file.
          const skipped = Math.max(0, s.totalCount - s.sentCount - s.failedCount);
          return (
            <Alert
              severity={s.sentCount > 0 ? 'success' : 'warning'}
              onClose={() => setActiveSendId(null)}
              data-testid="rn-send-success"
            >
              {t('eventPage.registrantNotices.sendResult', {
                sent: s.sentCount,
                total: s.totalCount,
                defaultValue: `Sent to ${s.sentCount} of ${s.totalCount} registrants.`,
              })}
              {skipped > 0 &&
                ' ' +
                  t('eventPage.registrantNotices.sendResultSkipped', {
                    count: skipped,
                    defaultValue: `${skipped} had no email address on file (skipped).`,
                  })}
              {s.failedCount > 0 &&
                ' ' +
                  t('eventPage.registrantNotices.sendResultFailed', {
                    count: s.failedCount,
                    defaultValue: `${s.failedCount} failed.`,
                  })}
            </Alert>
          );
        })()}

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', md: 'minmax(300px, 34%) 1fr' },
          gap: 3,
          alignItems: 'start',
        }}
      >
        <Paper variant="outlined" sx={{ p: 2 }}>
          {/* LEFT: the form controls */}
          <Stack spacing={2}>
            {/* Preview language */}
            <FormControl size="small">
              <InputLabel>{t('eventPage.registrantNotices.locale', 'Preview language')}</InputLabel>
              <Select
                value={locale}
                label={t('eventPage.registrantNotices.locale', 'Preview language')}
                onChange={(e) => {
                  setLocale(e.target.value as 'de' | 'en');
                  setPreviewHtml(null);
                }}
                SelectDisplayProps={
                  { 'data-testid': 'rn-locale-select' } as React.HTMLAttributes<HTMLDivElement>
                }
              >
                <MenuItem value="de">Deutsch</MenuItem>
                <MenuItem value="en">English</MenuItem>
              </Select>
            </FormControl>

            {/* Template selector — REGISTRANT_NOTICE category only */}
            <FormControl size="small">
              <InputLabel>{t('eventPage.registrantNotices.templateSelect', 'Template')}</InputLabel>
              <Select
                value={selectedTemplateKey}
                label={t('eventPage.registrantNotices.templateSelect', 'Template')}
                onChange={(e) => {
                  setSelectedTemplateKey(e.target.value);
                  setPreviewHtml(null);
                }}
                disabled={templatesQuery.isLoading || noTemplates}
                SelectDisplayProps={
                  { 'data-testid': 'rn-template-select' } as React.HTMLAttributes<HTMLDivElement>
                }
              >
                {filteredTemplates.map((tpl) => (
                  <MenuItem key={tpl.templateKey} value={tpl.templateKey}>
                    {tpl.templateKey}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            {noTemplates && (
              <Alert severity="info" data-testid="rn-no-templates">
                {t(
                  'eventPage.registrantNotices.noTemplates',
                  'No registrant-notice templates for this language yet.'
                )}{' '}
                <Link href="/organizer/admin?tab=email-templates" underline="hover">
                  {t('organizer:newsletter.templateSelect.createNew', 'Create one')} ↗
                </Link>
              </Alert>
            )}

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} flexWrap="wrap">
              <Button
                variant="outlined"
                startIcon={
                  previewMutation.isPending ? <CircularProgress size={16} /> : <EmailIcon />
                }
                onClick={handlePreview}
                disabled={previewMutation.isPending || !selectedTemplateKey}
                data-testid="rn-preview-button"
              >
                {t('eventPage.registrantNotices.preview', 'Preview')}
              </Button>
              <Button
                variant="contained"
                onClick={handleOpenConfirm}
                disabled={
                  sendMutation.isPending || previewMutation.isPending || !selectedTemplateKey
                }
                data-testid="rn-send-button"
              >
                {t('eventPage.registrantNotices.send', 'Send to registrants')}
              </Button>
            </Stack>

            {recipientCount !== null && (
              <Typography variant="caption" color="text.secondary" data-testid="rn-recipient-count">
                {t('eventPage.registrantNotices.recipientCount', {
                  count: recipientCount,
                  defaultValue: `${recipientCount} active registrants`,
                })}
              </Typography>
            )}

            {apiError && (
              <Alert severity={apiError.severity} data-testid="rn-error">
                {apiError.message}
              </Alert>
            )}
          </Stack>
        </Paper>

        <Paper variant="outlined" sx={{ p: 2 }}>
          {/* RIGHT: preview */}
          <Typography variant="body2" color="text.secondary" gutterBottom data-testid="sec-label">
            {t('eventPage.registrantNotices.previewTitle', 'Email Preview')}
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
              title="registrant-notice-preview"
              sandbox="allow-same-origin"
              data-testid="rn-preview-iframe"
            />
          ) : (
            <Typography variant="body2" color="text.secondary">
              Preview will appear here
            </Typography>
          )}
        </Paper>
      </Box>

      {/* Confirmation dialog */}
      <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)} fullScreen={isMobile}>
        <DialogTitle>
          {t('eventPage.registrantNotices.confirmTitle', 'Send to registrants?')}
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('eventPage.registrantNotices.confirmBody', {
              templateKey: selectedTemplateKey,
              eventTitle,
              count: recipientCount ?? 0,
              defaultValue:
                recipientCount !== null
                  ? `Send '${selectedTemplateKey}' to ${recipientCount} active registrants of «${eventTitle}»? Each receives it in their own language.`
                  : `Send '${selectedTemplateKey}' to the active registrants of «${eventTitle}»? Each receives it in their own language.`,
            })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOpen(false)} disabled={sendMutation.isPending}>
            {t('common:actions.cancel', 'Cancel')}
          </Button>
          <Button
            onClick={handleConfirmSend}
            variant="contained"
            disabled={sendMutation.isPending}
            autoFocus
            data-testid="rn-confirm-send"
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
