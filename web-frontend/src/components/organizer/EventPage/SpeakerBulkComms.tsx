/**
 * SpeakerBulkComms (Epic 14, Phase E — Story 14.E.3)
 *
 * The 🎤 Speakers audience of the Communications tab. Surfaces the previously
 * unwired `useSendReminder` hook as a bulk, confirm-gated reminder send.
 *
 * Scope note (NFR9 — frontend-only, no backend): `useSendReminder` is 1:1
 * (per speakerPoolId) and supports only the RESPONSE and CONTENT reminder
 * types. So this surface offers exactly two reminders, sent by looping the 1:1
 * hook over the matching speakers client-side:
 *   - Response deadline → speakers still INVITED (awaiting a yes/no)
 *   - Content  deadline → speakers ACCEPTED   (awaiting their materials)
 * "Logistics & arrival" and "thank-you" speaker comms (FR32) would need a new
 * backend reminder type + bulk endpoint and are deferred (see deferred-work.md).
 *
 * NFR1: a send opens a confirm dialog FIRST and the Send button is disabled
 * while in flight, so a drag/double-click can never silently double-send.
 */

import React, { useMemo, useState } from 'react';
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
  Select,
  Stack,
  Typography,
} from '@mui/material';
import { NotificationsActive as ReminderIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSpeakerPool, useSendReminder } from '@/hooks/useSpeakerPool';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

interface SpeakerBulkCommsProps {
  eventCode: string;
  eventTitle: string;
}

type ReminderType = 'RESPONSE' | 'CONTENT';

/** Which speaker state each reminder targets. */
const TARGET_STATUS: Record<ReminderType, SpeakerPoolEntry['status']> = {
  RESPONSE: 'INVITED',
  CONTENT: 'ACCEPTED',
};

export const SpeakerBulkComms: React.FC<SpeakerBulkCommsProps> = ({ eventCode, eventTitle }) => {
  const { t } = useTranslation(['events', 'common']);
  const { isMobile } = useBreakpoints();
  const poolQuery = useSpeakerPool(eventCode);
  const sendReminder = useSendReminder(eventCode);

  const [reminderType, setReminderType] = useState<ReminderType>('RESPONSE');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<{ sent: number; failed: SpeakerPoolEntry[] } | null>(null);

  const recipients = useMemo<SpeakerPoolEntry[]>(
    () => (poolQuery.data ?? []).filter((s) => s.status === TARGET_STATUS[reminderType]),
    [poolQuery.data, reminderType]
  );

  const typeLabel =
    reminderType === 'RESPONSE'
      ? t('eventPage.speakerComms.typeResponse', 'Response deadline (invited speakers)')
      : t('eventPage.speakerComms.typeContent', 'Content deadline (accepted speakers)');

  // Loop the 1:1 reminder over the chosen speakers, collecting per-speaker outcomes
  // so a partial failure is reported (and retryable) rather than swallowed.
  async function runSend(targets: SpeakerPoolEntry[]) {
    // The recipient set can refetch to empty while the confirm dialog is open;
    // never report a misleading "sent 0" success for a no-op confirm.
    if (targets.length === 0) {
      setConfirmOpen(false);
      return;
    }
    setSending(true);
    setConfirmOpen(false);
    let sent = 0;
    const failed: SpeakerPoolEntry[] = [];
    try {
      for (const s of targets) {
        try {
          await sendReminder.mutateAsync({ speakerPoolId: s.id, request: { reminderType } });
          sent += 1;
        } catch {
          failed.push(s);
        }
      }
      setResult({ sent, failed });
    } finally {
      // Guarantee the button is re-enabled even if something throws outside the per-item catch.
      setSending(false);
    }
  }

  if (poolQuery.isLoading) {
    return (
      <Box display="flex" justifyContent="center" py={6}>
        <CircularProgress />
      </Box>
    );
  }

  if (poolQuery.isError) {
    return (
      <Alert
        severity="error"
        data-testid="speaker-comms-load-error"
        action={
          <Button color="inherit" size="small" onClick={() => poolQuery.refetch()}>
            {t('eventPage.speakerComms.retry', 'Retry')}
          </Button>
        }
      >
        {t('eventPage.speakerComms.loadError', 'Could not load the speaker pool.')}
      </Alert>
    );
  }

  const noRecipients = recipients.length === 0;

  return (
    <Stack spacing={3} data-testid="speaker-comms">
      <Box>
        <Typography variant="h6" gutterBottom>
          {t('eventPage.speakerComms.title', 'Speaker reminders')}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t(
            'eventPage.speakerComms.description',
            'Send a deadline reminder to the speakers who still owe a response or their content. Each speaker is emailed individually — invitations and one-off nudges stay on the speaker card.'
          )}
        </Typography>
      </Box>

      <Stack spacing={2} maxWidth={420}>
        <FormControl size="small">
          <InputLabel>{t('eventPage.speakerComms.reminderType', 'Reminder type')}</InputLabel>
          <Select
            value={reminderType}
            label={t('eventPage.speakerComms.reminderType', 'Reminder type')}
            disabled={sending}
            onChange={(e) => {
              setReminderType(e.target.value as ReminderType);
              setResult(null);
            }}
            SelectDisplayProps={
              { 'data-testid': 'speaker-comms-type-select' } as React.HTMLAttributes<HTMLDivElement>
            }
          >
            <MenuItem value="RESPONSE">
              {t('eventPage.speakerComms.typeResponse', 'Response deadline (invited speakers)')}
            </MenuItem>
            <MenuItem value="CONTENT">
              {t('eventPage.speakerComms.typeContent', 'Content deadline (accepted speakers)')}
            </MenuItem>
          </Select>
        </FormControl>

        <Typography
          variant="caption"
          color="text.secondary"
          data-testid="speaker-comms-recipient-count"
        >
          {t('eventPage.speakerComms.recipientCount', {
            count: recipients.length,
            defaultValue: `${recipients.length} speaker(s) will be reminded`,
          })}
        </Typography>

        {noRecipients && (
          <Alert severity="info" data-testid="speaker-comms-empty">
            {t('eventPage.speakerComms.empty', 'No speakers are currently awaiting this reminder.')}
          </Alert>
        )}

        <Box>
          <Button
            variant="contained"
            startIcon={sending ? <CircularProgress size={16} /> : <ReminderIcon />}
            onClick={() => setConfirmOpen(true)}
            disabled={noRecipients || sending}
            data-testid="speaker-comms-send-button"
          >
            {t('eventPage.speakerComms.send', 'Send reminders')}
          </Button>
        </Box>

        {result && (
          <Alert
            severity={result.failed.length === 0 ? 'success' : 'warning'}
            onClose={() => setResult(null)}
            data-testid="speaker-comms-result"
            action={
              result.failed.length > 0 ? (
                <Button
                  color="inherit"
                  size="small"
                  disabled={sending}
                  onClick={() => runSend(result.failed)}
                  data-testid="speaker-comms-retry-failed"
                >
                  {t('eventPage.speakerComms.retryFailed', {
                    count: result.failed.length,
                    defaultValue: `Retry the ${result.failed.length} that failed`,
                  })}
                </Button>
              ) : undefined
            }
          >
            {t('eventPage.speakerComms.result', {
              sent: result.sent,
              failed: result.failed.length,
              defaultValue: `Sent ${result.sent}, failed ${result.failed.length}.`,
            })}
          </Alert>
        )}
      </Stack>

      {/* Confirmation dialog (NFR1 — opens before any send call). */}
      <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)} fullScreen={isMobile}>
        <DialogTitle>
          {t('eventPage.speakerComms.confirmTitle', 'Send speaker reminders?')}
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('eventPage.speakerComms.confirmBody', {
              type: typeLabel,
              count: recipients.length,
              eventTitle,
              defaultValue: `Send a “${typeLabel}” reminder to ${recipients.length} speaker(s) of «${eventTitle}»? Each is emailed individually.`,
            })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOpen(false)} disabled={sending}>
            {t('common:actions.cancel', 'Cancel')}
          </Button>
          <Button
            onClick={() => runSend(recipients)}
            variant="contained"
            disabled={sending}
            autoFocus
            data-testid="speaker-comms-confirm-send"
          >
            {sending ? <CircularProgress size={20} /> : t('common.confirm', 'Confirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
};

export default SpeakerBulkComms;
