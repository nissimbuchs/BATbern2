/**
 * UserSyncPanel Component
 *
 * Admin panel for managing Cognito-Database user synchronization.
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * Features:
 * - Real-time sync status display
 * - Manual sync trigger button
 * - Sync report modal with statistics
 * - Visual sync status indicator
 */

import React, { useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  Button,
  CircularProgress,
  Alert,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  List,
  ListItem,
  ListItemText,
  Divider,
  Stack,
} from '@mui/material';
import {
  Sync as SyncIcon,
  CheckCircle as CheckCircleIcon,
  Warning as WarningIcon,
  Error as ErrorIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useUserSync } from '@/hooks/useUserSync';
import { useNotifications } from '@/hooks/useNotifications';

const UserSyncPanel: React.FC = () => {
  const { t } = useTranslation(['admin', 'common']);
  const { showSuccess, showError } = useNotifications();
  const [reportModalOpen, setReportModalOpen] = useState(false);

  const {
    syncStatus,
    isSyncStatusLoading,
    isSyncStatusError,
    reconcile,
    reconciliationResult,
    isReconciling,
    resetReconciliation,
    isInSync,
    needsSync,
    missingCount,
    orphanedCount,
  } = useUserSync();

  const handleSyncClick = () => {
    reconcile(undefined, {
      onSuccess: (data) => {
        showSuccess(
          t('admin:userSync.reconciliation.success', {
            created: data.missingUsersCreated,
            deactivated: data.orphanedUsersDeactivated,
          })
        );
        setReportModalOpen(true);
      },
      onError: (error: Error) => {
        showError(
          t('admin:userSync.reconciliation.error', {
            message: error.message,
          })
        );
      },
    });
  };

  const handleCloseReportModal = () => {
    setReportModalOpen(false);
    resetReconciliation();
  };

  // Determine status color and icon
  const getSyncStatusDisplay = () => {
    if (isSyncStatusLoading) {
      return {
        color: 'default' as const,
        icon: <CircularProgress size={16} />,
        label: t('admin:userSync.status.checking'),
      };
    }

    if (isSyncStatusError) {
      return {
        color: 'error' as const,
        icon: <ErrorIcon fontSize="small" />,
        label: t('admin:userSync.status.error'),
      };
    }

    if (isInSync) {
      return {
        color: 'success' as const,
        icon: <CheckCircleIcon fontSize="small" />,
        label: t('admin:userSync.status.inSync'),
      };
    }

    return {
      color: 'warning' as const,
      icon: <WarningIcon fontSize="small" />,
      label: t('admin:userSync.status.outOfSync'),
    };
  };

  const statusDisplay = getSyncStatusDisplay();

  return (
    <>
      <Paper elevation={1} sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={2} alignItems="center" justifyContent="space-between">
          <Box display="flex" alignItems="center" gap={2}>
            <Typography variant="h6" component="h2">
              {t('admin:userSync.title')}
            </Typography>
            <Chip
              icon={statusDisplay.icon}
              label={statusDisplay.label}
              color={statusDisplay.color}
              size="small"
            />
          </Box>

          <Box display="flex" alignItems="center" gap={2}>
            {syncStatus && (
              <Typography variant="body2" color="text.secondary">
                {t('admin:userSync.counts', {
                  cognito: syncStatus.cognitoUserCount,
                  database: syncStatus.databaseUserCount,
                })}
              </Typography>
            )}

            <Button
              variant="contained"
              color="primary"
              startIcon={isReconciling ? <CircularProgress size={16} /> : <SyncIcon />}
              onClick={handleSyncClick}
              disabled={isReconciling || isSyncStatusLoading || isInSync}
            >
              {isReconciling ? t('admin:userSync.button.syncing') : t('admin:userSync.button.sync')}
            </Button>
          </Box>
        </Stack>

        {needsSync && syncStatus && (
          <Alert severity="warning" sx={{ mt: 2 }}>
            <Typography variant="body2">
              {t('admin:userSync.warning', {
                missing: missingCount,
                orphaned: orphanedCount,
              })}
            </Typography>
          </Alert>
        )}

        {syncStatus && !needsSync && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {syncStatus.message}
          </Typography>
        )}
      </Paper>

      {/* Reconciliation Report Modal */}
      <Dialog open={reportModalOpen} onClose={handleCloseReportModal} maxWidth="sm" fullWidth>
        <DialogTitle>{t('admin:userSync.report.title')}</DialogTitle>
        <DialogContent>
          {reconciliationResult && (
            <Box>
              <Alert severity={reconciliationResult.success ? 'success' : 'warning'} sx={{ mb: 2 }}>
                <Typography variant="body2">{reconciliationResult.message}</Typography>
              </Alert>

              <List>
                <ListItem>
                  <ListItemText
                    primary={t('admin:userSync.report.created')}
                    secondary={reconciliationResult.missingUsersCreated}
                  />
                </ListItem>
                <Divider />
                <ListItem>
                  <ListItemText
                    primary={t('admin:userSync.report.deactivated')}
                    secondary={reconciliationResult.orphanedUsersDeactivated}
                  />
                </ListItem>
                <Divider />
                <ListItem>
                  <ListItemText
                    primary={t('admin:userSync.report.duration')}
                    secondary={`${reconciliationResult.durationMs}ms`}
                  />
                </ListItem>
              </List>

              {reconciliationResult.errors && reconciliationResult.errors.length > 0 && (
                <Box mt={2}>
                  <Typography variant="subtitle2" color="error" gutterBottom>
                    {t('admin:userSync.report.errors')}
                  </Typography>
                  <List dense>
                    {reconciliationResult.errors.map((error, index) => (
                      <ListItem key={index}>
                        <ListItemText primary={error} />
                      </ListItem>
                    ))}
                  </List>
                </Box>
              )}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseReportModal}>{t('common:close')}</Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default UserSyncPanel;
