/**
 * VenueCoordinationComposer
 *
 * Embedded in the Event Detail Venue tab. Lets the organizer:
 *   - pick a VENUE_COORDINATION email template
 *   - choose recipient(s): Venue / Catering (the singleton contacts from
 *     Administration → Venue & Catering)
 *   - add free-form notes
 *   - preview the rendered email (recipient-specific salutation)
 *   - send to selected recipients (real SES, Reply-To = configured coordinator)
 *
 * Contacts and coordinator are admin-managed singletons (app_setting key
 * `venue.coordination.config`). When unconfigured, the composer renders a
 * disabled state pointing the organizer to the admin page.
 */

import React, { useEffect, useMemo, useState } from 'react';
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
  MenuItem,
  Paper,
  Select,
  Snackbar,
  Stack,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import { Send as SendIcon, Preview as PreviewIcon } from '@mui/icons-material';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useEmailTemplates } from '@/hooks/useEmailTemplates';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import { venueCoordinationService } from '@/services/venueCoordinationService';
import {
  useVenueCoordinationConfig,
  type VenueRecipientRole,
} from './communications/composer/audiences/useVenueCoordinationConfig';
import {
  VenueNotConfiguredAlert,
  VenueRecipientFields,
} from './communications/composer/audiences/VenueRecipientFields';

interface VenueCoordinationComposerProps {
  eventCode: string;
  /** Story 16.4: called after a successful send; the mail page closes the panel and refreshes. */
  onSent?: () => void;
}

export const VenueCoordinationComposer: React.FC<VenueCoordinationComposerProps> = ({
  eventCode,
  onSent,
}) => {
  const { t } = useTranslation(['events', 'common']);
  const { isMobile } = useBreakpoints();

  // ── Admin config (contacts + coordinator) ───────────────────────────────
  const venueConfig = useVenueCoordinationConfig();
  const { config, venueConfigured, cateringConfigured, fullyConfigured } = venueConfig;

  // ── Templates ────────────────────────────────────────────────────────────
  const templatesQuery = useEmailTemplates({ category: 'VENUE_COORDINATION', isLayout: false });
  const [locale, setLocale] = useState<'de' | 'en'>('de');
  const localeTemplates = useMemo(
    () => (templatesQuery.data ?? []).filter((tpl) => tpl.locale === locale),
    [templatesQuery.data, locale]
  );
  const [templateKey, setTemplateKey] = useState<string>('');
  useEffect(() => {
    if (!templateKey && localeTemplates.length > 0) {
      setTemplateKey(localeTemplates[0].templateKey);
    }
  }, [localeTemplates, templateKey]);

  // ── Composer state ───────────────────────────────────────────────────────
  const [notes, setNotes] = useState('');
  const [selectedRecipients, setSelectedRecipients] = useState<VenueRecipientRole[]>([
    'VENUE',
    'CATERING',
  ]);
  const sendToVenue = selectedRecipients.includes('VENUE');
  const sendToCatering = selectedRecipients.includes('CATERING');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [snackbar, setSnackbar] = useState<{ message: string; error: boolean } | null>(null);

  const previewMutation = useMutation({
    mutationFn: () =>
      venueCoordinationService.preview(eventCode, {
        templateKey,
        recipients: selectedRecipients,
        locale,
        notes: notes.trim() || undefined,
      }),
  });

  const sendMutation = useMutation({
    mutationFn: () =>
      venueCoordinationService.send(eventCode, {
        templateKey,
        locale,
        notes: notes.trim() || undefined,
        recipients: selectedRecipients,
      }),
    onSuccess: (data) => {
      onSent?.();
      setSnackbar({
        message: t('eventPage.venueCoordination.sentSuccess', {
          count: data.sentTo.length,
          defaultValue: 'Sent to {{count}} recipient(s).',
        }),
        error: false,
      });
      setConfirmOpen(false);
    },
    onError: (err: unknown) => {
      const message = err instanceof Error ? err.message : String(err);
      setSnackbar({
        message: t('eventPage.venueCoordination.sentFailed', {
          message,
          defaultValue: 'Send failed: {{message}}',
        }),
        error: true,
      });
      setConfirmOpen(false);
    },
  });

  const canPreview = fullyConfigured && Boolean(templateKey);
  const canSend = canPreview && (sendToVenue || sendToCatering);

  // ── Render ───────────────────────────────────────────────────────────────

  if (venueConfig.isLoading) {
    return (
      <Paper sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'center' }}>
          <CircularProgress />
        </Box>
      </Paper>
    );
  }

  return (
    <Paper sx={{ p: 3 }} data-testid="venue-coordination-composer">
      <Typography variant="h6" gutterBottom>
        {t('eventPage.venueCoordination.title', 'Send to Venue / Catering')}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t(
          'eventPage.venueCoordination.subtitle',
          'Pick a template, optionally add notes, preview, then send to the venue contact, the catering contact, or both.'
        )}
      </Typography>

      {!fullyConfigured && <VenueNotConfiguredAlert />}

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', md: 'minmax(300px, 34%) 1fr' },
          gap: 3,
          alignItems: 'start',
        }}
      >
        {/* LEFT: compose form */}
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={2}>
            {/* Locale + template */}
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems="flex-start">
              <ToggleButtonGroup
                value={locale}
                exclusive
                onChange={(_, v) => v && setLocale(v)}
                size="small"
                sx={{ height: 'fit-content' }}
              >
                <ToggleButton value="de">DE</ToggleButton>
                <ToggleButton value="en">EN</ToggleButton>
              </ToggleButtonGroup>

              <FormControl fullWidth size="small" disabled={!fullyConfigured}>
                <InputLabel id="venue-coord-template-label">
                  {t('eventPage.venueCoordination.templateLabel', 'Template')}
                </InputLabel>
                <Select
                  labelId="venue-coord-template-label"
                  value={templateKey}
                  label={t('eventPage.venueCoordination.templateLabel', 'Template')}
                  onChange={(e) => setTemplateKey(e.target.value)}
                  data-testid="venue-coord-template-select"
                >
                  {localeTemplates.map((tpl) => (
                    <MenuItem key={tpl.templateKey} value={tpl.templateKey}>
                      {tpl.templateKey}
                    </MenuItem>
                  ))}
                  {localeTemplates.length === 0 && (
                    <MenuItem value="" disabled>
                      {t('eventPage.venueCoordination.noTemplates', 'No templates available')}
                    </MenuItem>
                  )}
                </Select>
              </FormControl>
            </Stack>

            <VenueRecipientFields
              config={config}
              venueConfigured={venueConfigured}
              cateringConfigured={cateringConfigured}
              roles={selectedRecipients}
              onRolesChange={setSelectedRecipients}
              notes={notes}
              onNotesChange={setNotes}
            />

            {/* Preview button */}
            <Box>
              <Button
                variant="outlined"
                startIcon={<PreviewIcon />}
                onClick={() => previewMutation.mutate()}
                disabled={!canPreview || previewMutation.isPending}
              >
                {previewMutation.isPending ? (
                  <CircularProgress size={16} />
                ) : (
                  t('eventPage.venueCoordination.previewButton', 'Preview')
                )}
              </Button>
            </Box>

            {/* Send button + confirm dialog */}
            <Box>
              <Button
                variant="contained"
                color="primary"
                startIcon={<SendIcon />}
                onClick={() => setConfirmOpen(true)}
                disabled={!canSend || sendMutation.isPending}
                data-testid="venue-coord-send-button"
              >
                {sendMutation.isPending ? (
                  <CircularProgress size={16} color="inherit" />
                ) : (
                  t('eventPage.venueCoordination.sendButton', 'Send')
                )}
              </Button>
            </Box>
          </Stack>
        </Paper>

        {/* RIGHT: preview */}
        <Paper variant="outlined" sx={{ p: 2 }}>
          {previewMutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {t('eventPage.venueCoordination.previewFailed', 'Preview failed:')}{' '}
              {previewMutation.error instanceof Error
                ? previewMutation.error.message
                : String(previewMutation.error)}
            </Alert>
          )}
          {previewMutation.data ? (
            <Stack spacing={1}>
              <Typography variant="caption" color="text.secondary">
                {t('eventPage.venueCoordination.to', 'To')}: {previewMutation.data.toEmail}
              </Typography>
              {previewMutation.data.ccEmails.length > 0 && (
                <Typography variant="caption" color="text.secondary">
                  {t('eventPage.venueCoordination.cc', 'Cc')}:{' '}
                  {previewMutation.data.ccEmails.join(', ')}
                </Typography>
              )}
              <Typography variant="caption" color="text.secondary">
                {t('eventPage.venueCoordination.replyTo', 'Reply-To')}:{' '}
                {previewMutation.data.replyToEmail}
              </Typography>
              <Typography variant="subtitle2">
                {t('eventPage.venueCoordination.subject', 'Subject')}:{' '}
                {previewMutation.data.subject}
              </Typography>
              <Box
                sx={{
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: 1,
                  bgcolor: 'background.default',
                  p: 1,
                  maxHeight: 480,
                  overflow: 'auto',
                }}
              >
                <iframe
                  title="Venue coordination preview"
                  srcDoc={previewMutation.data.htmlBody}
                  style={{ width: '100%', minHeight: 400, border: 'none' }}
                  data-testid="venue-coord-preview-iframe"
                />
              </Box>
            </Stack>
          ) : (
            !previewMutation.isError && (
              <Typography variant="body2" color="text.secondary">
                {t('eventPage.venueCoordination.previewPlaceholder', 'Preview will appear here')}
              </Typography>
            )
          )}
        </Paper>
      </Box>

      <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)} fullScreen={isMobile}>
        <DialogTitle>{t('eventPage.venueCoordination.confirmTitle', 'Send email?')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('eventPage.venueCoordination.confirmBody', {
              defaultValue:
                'This will send a real email to {{recipients}}. Reply-To is the configured venue coordinator.',
              recipients: [
                sendToVenue ? config?.venue.email : null,
                sendToCatering ? config?.catering.email : null,
              ]
                .filter(Boolean)
                .join(', '),
            })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOpen(false)}>
            {t('common:actions.cancel', 'Cancel')}
          </Button>
          <Button
            variant="contained"
            color="primary"
            onClick={() => sendMutation.mutate()}
            disabled={sendMutation.isPending}
          >
            {t('eventPage.venueCoordination.confirmSend', 'Yes, send')}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={Boolean(snackbar)}
        autoHideDuration={5000}
        onClose={() => setSnackbar(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {snackbar ? (
          <Alert severity={snackbar.error ? 'error' : 'success'} onClose={() => setSnackbar(null)}>
            {snackbar.message}
          </Alert>
        ) : (
          <span />
        )}
      </Snackbar>
    </Paper>
  );
};
