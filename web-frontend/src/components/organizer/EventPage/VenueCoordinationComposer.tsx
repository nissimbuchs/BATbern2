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
  Checkbox,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControl,
  FormControlLabel,
  InputLabel,
  Link,
  MenuItem,
  Paper,
  Select,
  Snackbar,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import { Send as SendIcon, Preview as PreviewIcon } from '@mui/icons-material';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router-dom';
import { useEmailTemplates } from '@/hooks/useEmailTemplates';
import { getAdminSetting } from '@/services/adminSettingsService';
import {
  venueCoordinationService,
  type VenueRecipientRole,
} from '@/services/venueCoordinationService';
import {
  VENUE_COORDINATION_CONFIG_KEY,
  type VenueCoordinationConfig,
} from '@/components/organizer/Admin/VenueCateringContactsTab';

interface VenueCoordinationComposerProps {
  eventCode: string;
}

function parseConfig(raw: string | null | undefined): VenueCoordinationConfig | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as VenueCoordinationConfig;
  } catch {
    return null;
  }
}

export const VenueCoordinationComposer: React.FC<VenueCoordinationComposerProps> = ({
  eventCode,
}) => {
  const { t } = useTranslation(['events', 'common']);

  // ── Admin config (contacts + coordinator) ───────────────────────────────
  const configQuery = useQuery({
    queryKey: ['admin-settings', VENUE_COORDINATION_CONFIG_KEY],
    queryFn: () => getAdminSetting(VENUE_COORDINATION_CONFIG_KEY),
  });
  const config = useMemo(() => parseConfig(configQuery.data?.value), [configQuery.data]);
  const venueConfigured = Boolean(config?.venue?.name && config?.venue?.email);
  const cateringConfigured = Boolean(config?.catering?.name && config?.catering?.email);
  const coordinatorConfigured = Boolean(config?.coordinatorUsername);
  const fullyConfigured = venueConfigured && cateringConfigured && coordinatorConfigured;

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
  const [sendToVenue, setSendToVenue] = useState(true);
  const [sendToCatering, setSendToCatering] = useState(true);
  const [previewRole, setPreviewRole] = useState<VenueRecipientRole>('VENUE');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [snackbar, setSnackbar] = useState<{ message: string; error: boolean } | null>(null);

  // Auto-flip preview role if the configured one is no longer selected.
  useEffect(() => {
    if (previewRole === 'VENUE' && !sendToVenue && sendToCatering) setPreviewRole('CATERING');
    if (previewRole === 'CATERING' && !sendToCatering && sendToVenue) setPreviewRole('VENUE');
  }, [previewRole, sendToVenue, sendToCatering]);

  const previewMutation = useMutation({
    mutationFn: () =>
      venueCoordinationService.preview(eventCode, {
        templateKey,
        recipientRole: previewRole,
        locale,
        notes: notes.trim() || undefined,
      }),
  });

  const sendMutation = useMutation({
    mutationFn: () => {
      const recipients: VenueRecipientRole[] = [];
      if (sendToVenue) recipients.push('VENUE');
      if (sendToCatering) recipients.push('CATERING');
      return venueCoordinationService.send(eventCode, {
        templateKey,
        locale,
        notes: notes.trim() || undefined,
        recipients,
      });
    },
    onSuccess: (data) => {
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

  if (configQuery.isLoading) {
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

      {!fullyConfigured && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {t(
            'eventPage.venueCoordination.notConfigured',
            'Venue or catering contacts are not configured.'
          )}{' '}
          <Link component={RouterLink} to="/organizer/admin?tab=8">
            {t('eventPage.venueCoordination.openAdmin', 'Open Administration → Venue & Catering')}
          </Link>
        </Alert>
      )}

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

        {/* Recipient checkboxes */}
        <Box>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            {t('eventPage.venueCoordination.recipients', 'Recipients')}
          </Typography>
          <Stack direction="row" spacing={3}>
            <FormControlLabel
              control={
                <Checkbox
                  checked={sendToVenue}
                  onChange={(e) => setSendToVenue(e.target.checked)}
                  disabled={!venueConfigured}
                  data-testid="venue-coord-send-venue"
                />
              }
              label={
                venueConfigured && config
                  ? `${t('eventPage.venueCoordination.venueRole', 'Venue')} — ${config.venue.name} <${config.venue.email}>`
                  : t('eventPage.venueCoordination.venueRole', 'Venue')
              }
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={sendToCatering}
                  onChange={(e) => setSendToCatering(e.target.checked)}
                  disabled={!cateringConfigured}
                  data-testid="venue-coord-send-catering"
                />
              }
              label={
                cateringConfigured && config
                  ? `${t('eventPage.venueCoordination.cateringRole', 'Catering')} — ${config.catering.name} <${config.catering.email}>`
                  : t('eventPage.venueCoordination.cateringRole', 'Catering')
              }
            />
          </Stack>
        </Box>

        {/* Notes */}
        <TextField
          label={t('eventPage.venueCoordination.notesLabel', 'Notes (optional)')}
          placeholder={t(
            'eventPage.venueCoordination.notesPlaceholder',
            'e.g. Reserve a lunch table for 8 at 12:30 in the back room.'
          )}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          multiline
          minRows={3}
          fullWidth
        />

        {/* Preview controls */}
        <Stack direction="row" spacing={2} alignItems="center">
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
          {previewMutation.isSuccess && (
            <ToggleButtonGroup
              value={previewRole}
              exclusive
              onChange={(_, v) => {
                if (v) {
                  setPreviewRole(v);
                  setTimeout(() => previewMutation.mutate(), 0);
                }
              }}
              size="small"
            >
              <ToggleButton value="VENUE" disabled={!venueConfigured}>
                {t('eventPage.venueCoordination.previewAsVenue', 'as Venue')}
              </ToggleButton>
              <ToggleButton value="CATERING" disabled={!cateringConfigured}>
                {t('eventPage.venueCoordination.previewAsCatering', 'as Catering')}
              </ToggleButton>
            </ToggleButtonGroup>
          )}
        </Stack>

        {/* Preview pane */}
        {previewMutation.isError && (
          <Alert severity="error">
            {t('eventPage.venueCoordination.previewFailed', 'Preview failed:')}{' '}
            {previewMutation.error instanceof Error
              ? previewMutation.error.message
              : String(previewMutation.error)}
          </Alert>
        )}
        {previewMutation.data && (
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Stack spacing={1}>
              <Typography variant="caption" color="text.secondary">
                {t('eventPage.venueCoordination.to', 'To')}: {previewMutation.data.toName} &lt;
                {previewMutation.data.toEmail}&gt;
              </Typography>
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
          </Paper>
        )}

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

      <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)}>
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
