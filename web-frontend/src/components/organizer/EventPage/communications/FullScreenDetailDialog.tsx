/**
 * FullScreenDetailDialog (Stories 16.3, 16.2a): mobile shows a mail or a conversation as a
 * full-screen dialog with its title in the top bar (`docs/ux/mail-center-mobile.html`).
 */

import React from 'react';
import { AppBar, Box, Dialog, IconButton, Toolbar, Typography } from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';

interface FullScreenDetailDialogProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}

export const FullScreenDetailDialog: React.FC<FullScreenDetailDialogProps> = ({
  open,
  title,
  onClose,
  children,
}) => {
  const { t } = useTranslation('events');
  return (
    <Dialog fullScreen open={open} onClose={onClose} aria-labelledby="mail-detail-title">
      <AppBar
        position="sticky"
        color="default"
        elevation={0}
        sx={{ borderBottom: 1, borderColor: 'divider' }}
      >
        <Toolbar sx={{ gap: 1 }}>
          <IconButton
            edge="start"
            onClick={onClose}
            aria-label={t('common:actions.close', 'Close')}
          >
            <CloseIcon />
          </IconButton>
          <Typography id="mail-detail-title" variant="subtitle1" noWrap sx={{ flex: 1 }}>
            {title}
          </Typography>
        </Toolbar>
      </AppBar>
      <Box sx={{ p: 2 }}>{children}</Box>
    </Dialog>
  );
};

export default FullScreenDetailDialog;
