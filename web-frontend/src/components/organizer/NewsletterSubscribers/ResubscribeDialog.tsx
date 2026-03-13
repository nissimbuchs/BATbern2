/**
 * ResubscribeDialog — Confirmation dialog for re-subscribing a subscriber
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
import { useResubscribeSubscriber } from '@/hooks/useNewsletterSubscribers';

type SubscriberResponse = components['schemas']['SubscriberResponse'];

interface ResubscribeDialogProps {
  open: boolean;
  subscriber: SubscriberResponse | null;
  onClose: () => void;
  onSuccess: () => void;
}

const ResubscribeDialog: React.FC<ResubscribeDialogProps> = ({
  open,
  subscriber,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslation('newsletterSubscribers');
  const { t: tCommon } = useTranslation('common');
  const mutation = useResubscribeSubscriber();

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
            <Typography variant="h6">{t('dialogs.resubscribe.title')}</Typography>
          </Box>
          <IconButton onClick={onClose} aria-label={tCommon('actions.close')}>
            <Close />
          </IconButton>
        </Box>
      </DialogTitle>
      <DialogContent dividers>
        <Typography>{t('dialogs.resubscribe.message', { email: subscriber?.email })}</Typography>
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
          color="primary"
          disabled={mutation.isPending}
        >
          {mutation.isPending ? <CircularProgress size={20} /> : t('actions.resubscribe')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ResubscribeDialog;
