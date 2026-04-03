/**
 * UnsuppressDialog — Confirmation dialog for unsuppressing a bounced subscriber
 *
 * Story 10.29 AC8: Admin visibility — bounce status in subscriber list
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
import { useUnsuppressSubscriber } from '@/hooks/useNewsletterSubscribers';

type SubscriberResponse = components['schemas']['SubscriberResponse'];

interface UnsuppressDialogProps {
  open: boolean;
  subscriber: SubscriberResponse | null;
  onClose: () => void;
  onSuccess: () => void;
}

const UnsuppressDialog: React.FC<UnsuppressDialogProps> = ({
  open,
  subscriber,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslation('newsletterSubscribers');
  const { t: tCommon } = useTranslation('common');
  const mutation = useUnsuppressSubscriber();

  React.useEffect(() => {
    if (open) mutation.reset();
  }, [open]);

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
            <Typography variant="h6">{t('dialogs.unsuppress.title')}</Typography>
          </Box>
          <IconButton onClick={onClose} aria-label={tCommon('actions.close')}>
            <Close />
          </IconButton>
        </Box>
      </DialogTitle>
      <DialogContent dividers>
        <Typography>{t('dialogs.unsuppress.message', { email: subscriber?.email })}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          {t('dialogs.unsuppress.detail', {
            bounceType: subscriber?.bounceType ?? '—',
            bounceCount: subscriber?.bounceCount ?? 0,
          })}
        </Typography>
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
          data-testid="confirm-unsuppress"
        >
          {mutation.isPending ? <CircularProgress size={20} /> : t('actions.unsuppress')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default UnsuppressDialog;
