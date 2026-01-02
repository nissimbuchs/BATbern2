import React, { useState } from 'react';
import {
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Button,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Typography,
  CircularProgress,
  Tooltip,
  TextField,
} from '@mui/material';
import { Restore, CheckCircle, HourglassEmpty, Error } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { usePublishing } from '@/hooks/usePublishing/usePublishing';
import type { PublishingVersion } from '@/types/event.types';

export interface VersionControlProps {
  eventCode: string;
}

const MIN_REASON_LENGTH = 10;
const MAX_REASON_LENGTH = 500;

export const VersionControl: React.FC<VersionControlProps> = ({ eventCode }) => {
  const { t } = useTranslation('events');
  const { versionHistory, rollbackVersion, isLoadingVersions, isRollingBack } =
    usePublishing(eventCode);
  const [rollbackDialogOpen, setRollbackDialogOpen] = useState(false);
  const [selectedVersion, setSelectedVersion] = useState<PublishingVersion | null>(null);
  const [rollbackReason, setRollbackReason] = useState('');
  const [reasonError, setReasonError] = useState('');

  const handleRollbackClick = (version: PublishingVersion) => {
    setSelectedVersion(version);
    setRollbackReason('');
    setReasonError('');
    setRollbackDialogOpen(true);
  };

  const validateReason = (reason: string): boolean => {
    if (reason.length < MIN_REASON_LENGTH) {
      setReasonError(
        t('publishing.versionControl.reasonTooShort', { min: MIN_REASON_LENGTH })
      );
      return false;
    }
    if (reason.length > MAX_REASON_LENGTH) {
      setReasonError(
        t('publishing.versionControl.reasonTooLong', { max: MAX_REASON_LENGTH })
      );
      return false;
    }
    setReasonError('');
    return true;
  };

  const handleReasonChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newReason = e.target.value;
    setRollbackReason(newReason);
    // Clear error when user starts typing
    if (reasonError && newReason.length >= MIN_REASON_LENGTH) {
      setReasonError('');
    }
  };

  const handleConfirmRollback = async () => {
    if (!validateReason(rollbackReason)) {
      return;
    }

    if (selectedVersion) {
      await rollbackVersion?.(selectedVersion.versionNumber, {
        reason: rollbackReason,
      });
      setRollbackDialogOpen(false);
      setSelectedVersion(null);
      setRollbackReason('');
    }
  };

  const handleCancelRollback = () => {
    setRollbackDialogOpen(false);
    setSelectedVersion(null);
    setRollbackReason('');
    setReasonError('');
  };

  const formatDate = (dateString: string): string => {
    const date = new Date(dateString);
    return (
      date.toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      }) +
      ', ' +
      date.toLocaleTimeString('en-US', {
        hour: 'numeric',
        minute: '2-digit',
        hour12: true,
      })
    );
  };

  const getCDNStatusIcon = (status?: string) => {
    switch (status) {
      case 'COMPLETED':
        return <CheckCircle color="success" fontSize="small" data-testid="cdn-status-completed" />;
      case 'PENDING':
        return <HourglassEmpty color="warning" fontSize="small" data-testid="cdn-status-pending" />;
      case 'FAILED':
        return <Error color="error" fontSize="small" data-testid="cdn-status-failed" />;
      default:
        return null;
    }
  };

  const getCDNStatusText = (status?: string): string => {
    switch (status) {
      case 'COMPLETED':
        return t('publishing.versionControl.cdnCleared');
      case 'PENDING':
        return t('publishing.versionControl.cdnPending');
      case 'FAILED':
        return t('publishing.versionControl.cdnFailed');
      default:
        return t('publishing.versionControl.cdnUnknown');
    }
  };

  // Sort versions by version number descending (newest first)
  const sortedVersions = Array.isArray(versionHistory)
    ? [...versionHistory].sort((a, b) => b.versionNumber - a.versionNumber)
    : [];

  const isReasonValid = rollbackReason.length >= MIN_REASON_LENGTH && rollbackReason.length <= MAX_REASON_LENGTH;

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h6" gutterBottom>
        {t('publishing.versionControl.title')}
      </Typography>

      {isLoadingVersions ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress data-testid="version-history-loading" />
        </Box>
      ) : (
        <TableContainer>
          <Table aria-label={t('publishing.versionControl.tableAriaLabel')}>
            <TableHead>
              <TableRow>
                <TableCell>{t('publishing.versionControl.version')}</TableCell>
                <TableCell>{t('publishing.versionControl.published')}</TableCell>
                <TableCell>{t('publishing.versionControl.phase')}</TableCell>
                <TableCell>{t('publishing.versionControl.publisher')}</TableCell>
                <TableCell>{t('publishing.versionControl.status')}</TableCell>
                <TableCell>{t('publishing.versionControl.actions')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sortedVersions.map((version) => (
                <TableRow key={version.id} data-testid={`version-row-${version.versionNumber}`}>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      {t('publishing.versionControl.versionNumber', { number: version.versionNumber })}
                      {version.isCurrent && (
                        <Chip
                          label={t('publishing.versionControl.current')}
                          size="small"
                          color="primary"
                          data-testid={`current-badge-${version.versionNumber}`}
                        />
                      )}
                    </Box>
                  </TableCell>
                  <TableCell data-testid={`publish-date-${version.versionNumber}`}>
                    {formatDate(version.publishedAt)}
                  </TableCell>
                  <TableCell>
                    <Chip label={version.publishedPhase} size="small" />
                  </TableCell>
                  <TableCell>{version.publishedBy}</TableCell>
                  <TableCell>
                    <Tooltip title={`CDN Invalidation ID: ${version.cdnInvalidationId || 'N/A'}`}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        {getCDNStatusIcon(version.cdnInvalidationStatus)}
                        <Typography variant="body2">
                          {getCDNStatusText(version.cdnInvalidationStatus)}
                        </Typography>
                      </Box>
                    </Tooltip>
                  </TableCell>
                  <TableCell>
                    {!version.isCurrent && (
                      <Button
                        size="small"
                        startIcon={<Restore />}
                        onClick={() => handleRollbackClick(version)}
                        disabled={isRollingBack}
                        data-testid={`rollback-button-${version.versionNumber}`}
                        aria-label={t('publishing.versionControl.rollbackToVersion', { version: version.versionNumber })}
                      >
                        {t('publishing.versionControl.rollback')}
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {sortedVersions.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} align="center">
                    <Typography
                      variant="body2"
                      color="text.secondary"
                      data-testid="no-versions-message"
                    >
                      {t('publishing.versionControl.noVersions')}
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Rollback Confirmation Dialog */}
      <Dialog
        open={rollbackDialogOpen}
        onClose={handleCancelRollback}
        data-testid="rollback-confirmation-modal"
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>{t('publishing.versionControl.confirmRollback')}</DialogTitle>
        <DialogContent>
          {selectedVersion && (
            <Box>
              <Typography variant="body1" gutterBottom>
                {t('publishing.versionControl.rollbackQuestion')}
              </Typography>
              <Box
                sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}
                data-testid="rollback-version-details"
              >
                <Typography variant="body2">
                  <strong>{t('publishing.versionControl.version')}:</strong> {selectedVersion.versionNumber}
                </Typography>
                <Typography variant="body2">
                  <strong>{t('publishing.versionControl.phase')}:</strong> {selectedVersion.publishedPhase}
                </Typography>
                <Typography variant="body2">
                  <strong>{t('publishing.versionControl.published')}:</strong> {formatDate(selectedVersion.publishedAt)}
                </Typography>
                <Typography variant="body2">
                  <strong>{t('publishing.versionControl.publisher')}:</strong> {selectedVersion.publishedBy}
                </Typography>
              </Box>

              {/* Rollback Reason Input */}
              <TextField
                fullWidth
                multiline
                rows={3}
                label={t('publishing.versionControl.reasonLabel')}
                placeholder={t('publishing.versionControl.reasonPlaceholder')}
                value={rollbackReason}
                onChange={handleReasonChange}
                error={!!reasonError}
                helperText={
                  reasonError ||
                  t('publishing.versionControl.reasonHelperText', {
                    current: rollbackReason.length,
                    min: MIN_REASON_LENGTH,
                    max: MAX_REASON_LENGTH
                  })
                }
                sx={{ mt: 2 }}
                inputProps={{
                  'data-testid': 'rollback-reason-input',
                  maxLength: MAX_REASON_LENGTH + 10, // Allow slightly over to show error
                }}
                required
              />

              <Typography variant="body2" color="warning.main" sx={{ mt: 2 }}>
                {t('publishing.versionControl.cdnWarning')}
              </Typography>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCancelRollback} data-testid="cancel-rollback-button">
            {t('publishing.versionControl.cancel')}
          </Button>
          <Button
            onClick={handleConfirmRollback}
            variant="contained"
            color="warning"
            disabled={isRollingBack || !isReasonValid}
            data-testid="confirm-rollback-button"
            startIcon={isRollingBack ? <CircularProgress size={16} /> : <Restore />}
          >
            {isRollingBack
              ? t('publishing.versionControl.rollingBack')
              : t('publishing.versionControl.confirmRollbackButton')}
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  );
};
