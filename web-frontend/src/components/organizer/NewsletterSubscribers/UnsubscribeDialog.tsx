/**
 * UnsubscribeDialog — Confirmation dialog for unsubscribing a subscriber
 *
 * Story 10.28: Newsletter Subscriber Management Page
 */

import React from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Typography,
} from '@mui/material';
import { Close, WarningAmber } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { components } from '@/types/generated/events-api.types';
import { useUnsubscribeSubscriber } from '@/hooks/useNewsletterSubscribers';

type SubscriberResponse = components['schemas']['SubscriberResponse'];

interface UnsubscribeDialogProps {
  open: boolean;
  subscriber: SubscriberResponse | null;
  onClose: () => void;
  onSuccess: () => void;
}

const UnsubscribeDialog: React.FC<UnsubscribeDialogProps> = ({
  open,
  subscriber,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslation('newsletterSubscribers');
  const { t: tCommon } = useTranslation('common');
  const mutation = useUnsubscribeSubscriber();

  const handleConfirm = () => {
    if (!subscriber?.id) return;
    mutation.mutate(subscriber.id, {
      onSuccess: () => {
        onSuccess();
        onClose();
      },
    });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Box display="flex" alignItems="center" gap={1}>
            <WarningAmber color="warning" />
            <Typography variant="h6">{t('dialogs.unsubscribe.title')}</Typography>
          </Box>
          <IconButton onClick={onClose} aria-label={tCommon('actions.close')}>
            <Close />
          </IconButton>
        </Box>
      </DialogTitle>
      <DialogContent dividers>
        <Typography>{t('dialogs.unsubscribe.message', { email: subscriber?.email })}</Typography>
        {mutation.isError && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {tCommon('errors.unexpected')}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} variant="outlined">
          {tCommon('actions.cancel')}
        </Button>
        <Button
          onClick={handleConfirm}
          variant="contained"
          color="warning"
          disabled={mutation.isPending}
        >
          {mutation.isPending ? <CircularProgress size={20} /> : t('actions.unsubscribe')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default UnsubscribeDialog;
