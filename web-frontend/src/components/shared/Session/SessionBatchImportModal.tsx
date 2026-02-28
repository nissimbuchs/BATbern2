/**
 * SessionBatchImportModal Component
 *
 * Modal for batch importing historical sessions from sessions.json.
 * Features:
 * - File upload via drag-and-drop (sessions.json only)
 * - Preview of sessions to import with event and speaker details
 * - Batch import per event with progress tracking
 * - Duplicate detection by (event + title)
 */

import React, { useState, useCallback } from 'react';
import { useDropzone } from 'react-dropzone';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  LinearProgress,
  Alert,
  IconButton,
} from '@mui/material';
import {
  CloudUpload as CloudUploadIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  HourglassEmpty as PendingIcon,
  SkipNext as SkipIcon,
  Close as CloseIcon,
  Update as UpdateIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSessionBatchImport } from '@/hooks/useSessionBatchImport';
import { parseSessionsJson, createSessionImportCandidates } from '@/utils/sessionImport';
import type {
  SessionImportCandidate,
  SessionBatchImportModalProps,
  SessionBatchImportResult,
  SessionImportStatus,
} from '@/types/sessionImport.types';

/**
 * Get the appropriate icon for an import status
 */
function getStatusIcon(status: SessionImportStatus) {
  switch (status) {
    case 'success':
      return <CheckCircleIcon color="success" fontSize="small" />;
    case 'updated':
      return <UpdateIcon color="info" fontSize="small" />;
    case 'error':
      return <ErrorIcon color="error" fontSize="small" />;
    case 'skipped':
      return <SkipIcon color="warning" fontSize="small" />;
    case 'importing':
      return <PendingIcon color="primary" fontSize="small" />;
    default:
      return <PendingIcon color="disabled" fontSize="small" />;
  }
}

/**
 * Get the chip color for an import status
 */
function getStatusColor(
  status: SessionImportStatus
): 'default' | 'primary' | 'success' | 'error' | 'warning' | 'info' {
  switch (status) {
    case 'success':
      return 'success';
    case 'updated':
      return 'info';
    case 'error':
      return 'error';
    case 'skipped':
      return 'warning';
    case 'importing':
      return 'primary';
    default:
      return 'default';
  }
}

export const SessionBatchImportModal: React.FC<SessionBatchImportModalProps> = ({
  open,
  onClose,
  onImportComplete,
}) => {
  const { t } = useTranslation('common');
  const [parseError, setParseError] = useState<string | null>(null);
  const [importCandidates, setImportCandidates] = useState<SessionImportCandidate[]>([]);
  const [importResult, setImportResult] = useState<SessionBatchImportResult | null>(null);

  const {
    importSessions,
    isImporting,
    currentIndex,
    totalCount,
    candidates: updatedCandidates,
    reset: resetImport,
  } = useSessionBatchImport({
    onComplete: (result) => {
      setImportResult(result);
      onImportComplete?.(result);
    },
  });

  // Use updated candidates from hook during import, otherwise use local state
  const displayCandidates = isImporting || importResult ? updatedCandidates : importCandidates;

  const handleFileDrop = useCallback(
    (acceptedFiles: File[]) => {
      setParseError(null);
      setImportResult(null);

      if (acceptedFiles.length === 0) {
        return;
      }

      const file = acceptedFiles[0];
      if (!file.name.endsWith('.json')) {
        setParseError(t('session.batchImport.errors.invalidFile'));
        return;
      }

      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const content = e.target?.result as string;
          const sessions = parseSessionsJson(content);

          if (sessions.length === 0) {
            setParseError(t('session.batchImport.noSessions'));
            return;
          }

          const candidates = createSessionImportCandidates(sessions);
          setImportCandidates(candidates);
        } catch (error) {
          setParseError(
            t('session.batchImport.errors.parseError', {
              error: error instanceof Error ? error.message : 'Unknown error',
            })
          );
        }
      };

      reader.onerror = () => {
        setParseError(t('session.batchImport.errors.invalidFile'));
      };

      reader.readAsText(file);
    },
    [t]
  );

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop: handleFileDrop,
    accept: {
      'application/json': ['.json'],
    },
    multiple: false,
    disabled: isImporting,
  });

  const handleImport = async () => {
    if (importCandidates.length === 0) return;
    await importSessions(importCandidates);
  };

  const handleClose = () => {
    if (isImporting) return; // Don't allow closing during import
    setParseError(null);
    setImportCandidates([]);
    setImportResult(null);
    resetImport();
    onClose();
  };

  const progress = totalCount > 0 ? ((currentIndex + 1) / totalCount) * 100 : 0;

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="lg"
      fullWidth
      aria-labelledby="session-batch-import-dialog-title"
    >
      <DialogTitle id="session-batch-import-dialog-title">
        <Box display="flex" justifyContent="space-between" alignItems="center">
          {t('session.batchImport.title')}
          <IconButton
            onClick={handleClose}
            disabled={isImporting}
            aria-label={t('actions.close')}
            size="small"
          >
            <CloseIcon />
          </IconButton>
        </Box>
      </DialogTitle>

      <DialogContent dividers>
        {/* File Upload Zone - show only if no candidates loaded */}
        {displayCandidates.length === 0 && (
          <Box
            {...getRootProps()}
            data-testid="session-json-dropzone"
            sx={{
              border: '2px dashed',
              borderColor: isDragActive ? 'primary.main' : 'grey.400',
              borderRadius: 2,
              padding: 4,
              textAlign: 'center',
              cursor: isImporting ? 'not-allowed' : 'pointer',
              backgroundColor: isDragActive ? 'action.hover' : 'background.paper',
              transition: 'all 0.2s ease-in-out',
              '&:hover': {
                borderColor: 'primary.main',
                backgroundColor: 'action.hover',
              },
            }}
          >
            <input {...getInputProps()} />
            <CloudUploadIcon sx={{ fontSize: 48, color: 'grey.500', mb: 2 }} />
            <Typography variant="body1" color="textSecondary">
              {isDragActive
                ? t('session.batchImport.dropzoneActive')
                : t('session.batchImport.dropzone')}
            </Typography>
            <Typography variant="caption" color="textSecondary" sx={{ mt: 1, display: 'block' }}>
              {t('session.batchImport.dropzoneHint')}
            </Typography>
          </Box>
        )}

        {/* Parse Error */}
        {parseError && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {parseError}
          </Alert>
        )}

        {/* Import Progress */}
        {isImporting && (
          <Box sx={{ mb: 2 }}>
            <Typography variant="body2" color="textSecondary" sx={{ mb: 1 }}>
              {t('session.batchImport.progress', {
                current: currentIndex + 1,
                total: totalCount,
              })}
            </Typography>
            <LinearProgress variant="determinate" value={progress} />
          </Box>
        )}

        {/* Import Result */}
        {importResult && (
          <Alert severity={importResult.failed > 0 ? 'warning' : 'success'} sx={{ mb: 2 }}>
            {t('session.batchImport.complete', {
              success: importResult.successfullyCreated,
              updated: importResult.updated || 0,
              skipped: importResult.skipped,
              failed: importResult.failed,
            })}
          </Alert>
        )}

        {/* Preview Table */}
        {displayCandidates.length > 0 && (
          <>
            <Typography variant="h6" sx={{ mb: 2 }}>
              {t('session.batchImport.preview', { count: displayCandidates.length })}
            </Typography>

            <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 400 }}>
              <Table stickyHeader size="small">
                <TableHead>
                  <TableRow>
                    <TableCell width={120}>{t('session.batchImport.columns.event')}</TableCell>
                    <TableCell>{t('session.batchImport.columns.title')}</TableCell>
                    <TableCell width={100}>{t('session.batchImport.columns.speakers')}</TableCell>
                    <TableCell width={150}>{t('session.batchImport.columns.status')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {displayCandidates.map((candidate, index) => (
                    <TableRow
                      key={`${candidate.eventCode}-${index}`}
                      sx={{
                        backgroundColor:
                          candidate.importStatus === 'importing' ? 'action.hover' : undefined,
                      }}
                    >
                      <TableCell>
                        <Typography variant="body2" fontFamily="monospace">
                          {candidate.eventCode}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{candidate.source.title}</Typography>
                        {candidate.source.abstract && (
                          <Typography variant="caption" color="textSecondary" noWrap>
                            {candidate.source.abstract.substring(0, 80)}...
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={t(
                            candidate.speakersCount === 1
                              ? 'session.speakerCount'
                              : 'session.speakersCount',
                            { count: candidate.speakersCount }
                          )}
                          size="small"
                          variant="outlined"
                          color={candidate.speakersCount > 0 ? 'primary' : 'default'}
                        />
                      </TableCell>
                      <TableCell>
                        <Chip
                          icon={getStatusIcon(candidate.importStatus)}
                          label={
                            candidate.errorMessage ||
                            t(`session.batchImport.status.${candidate.importStatus}`)
                          }
                          color={getStatusColor(candidate.importStatus)}
                          size="small"
                          variant={candidate.importStatus === 'pending' ? 'outlined' : 'filled'}
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose} disabled={isImporting}>
          {importResult ? t('actions.close') : t('session.batchImport.cancelButton')}
        </Button>
        {!importResult && displayCandidates.length > 0 && (
          <Button variant="contained" onClick={handleImport} disabled={isImporting}>
            {isImporting
              ? t('session.batchImport.status.importing')
              : t('session.batchImport.importButtonWithCount', {
                  count: displayCandidates.length,
                })}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default SessionBatchImportModal;
