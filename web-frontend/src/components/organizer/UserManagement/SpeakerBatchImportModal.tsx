/**
 * SpeakerBatchImportModal Component
 *
 * Modal for batch importing speakers from legacy speakers.json file as SPEAKER role users.
 * Features:
 * - File upload via drag-and-drop
 * - Preview of speakers to import
 * - Sequential import with status tracking
 * - Progress indicator
 * - Duplicate detection by email
 */

import React, { useState, useCallback, useEffect, useMemo } from 'react';
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
  CircularProgress,
} from '@mui/material';
import {
  CloudUpload as CloudUploadIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  HourglassEmpty as PendingIcon,
  SkipNext as SkipIcon,
  Close as CloseIcon,
  Photo as PhotoIcon,
  PhotoCamera as PhotoCameraIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSpeakerBatchImport } from '@/hooks/useSpeakerBatchImport';
import { useUserList } from '@/hooks/useUserManagement';
import { parseSpeakersJson, createImportCandidates, detectChanges } from '@/utils/speakerImport';
import type {
  SpeakerImportCandidate,
  SpeakerBatchImportModalProps,
  SpeakerBatchImportResult,
  ImportStatus,
} from '@/types/speakerImport.types';

/**
 * Get the appropriate icon for an import status
 */
function getStatusIcon(status: ImportStatus) {
  switch (status) {
    case 'success':
      return <CheckCircleIcon color="success" fontSize="small" />;
    case 'updated':
      return <CheckCircleIcon color="info" fontSize="small" />;
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
  status: ImportStatus
): 'default' | 'primary' | 'success' | 'info' | 'error' | 'warning' {
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

export const SpeakerBatchImportModal: React.FC<SpeakerBatchImportModalProps> = ({
  open,
  onClose,
  onImportComplete,
}) => {
  const { t } = useTranslation('userManagement');
  const [parseError, setParseError] = useState<string | null>(null);
  const [importCandidates, setImportCandidates] = useState<SpeakerImportCandidate[]>([]);
  const [importResult, setImportResult] = useState<SpeakerBatchImportResult | null>(null);
  const [isCheckingDuplicates, setIsCheckingDuplicates] = useState(false);

  // Fetch existing users to check for duplicates
  // Only fetch when modal is open to avoid unnecessary API calls
  const { data: existingUsersData, isLoading: isLoadingUsers } = useUserList({
    filters: { role: ['SPEAKER'] },
    pagination: { page: 1, limit: 1000 },
    enabled: open,
  });

  const {
    importSpeakers,
    isImporting,
    currentIndex,
    totalCount,
    candidates: updatedCandidates,
    reset: resetImport,
  } = useSpeakerBatchImport({
    onComplete: (result) => {
      setImportResult(result);
      onImportComplete?.(result);
    },
  });

  // Get map of existing users by email for fast lookup
  const existingUsersMap = useMemo(
    () =>
      new Map(
        (existingUsersData?.data || []).map((u) => [
          u.email.toLowerCase(),
          {
            username: u.id,
            firstName: u.firstName,
            lastName: u.lastName,
            bio: u.bio,
            companyId: u.companyId,
            profilePictureUrl: u.profilePictureUrl,
          },
        ])
      ),
    [existingUsersData]
  );

  // Mark existing users and detect changes when candidates change or existing users load
  useEffect(() => {
    if (importCandidates.length > 0 && !isLoadingUsers && existingUsersData) {
      setIsCheckingDuplicates(true);
      const updatedCandidates = importCandidates.map((candidate) => {
        const existingUser = existingUsersMap.get(candidate.apiPayload.email.toLowerCase());
        if (existingUser && candidate.importStatus === 'pending') {
          // User exists - detect what has changed
          const changedFields = detectChanges(
            candidate.apiPayload,
            existingUser,
            !!candidate.portraitUrl
          );

          return {
            ...candidate,
            existingUser,
            hasChanges: changedFields.length > 0,
            changedFields,
          };
        }
        return candidate;
      });

      // Only update if there are actual changes to the candidates array
      const hasUpdates = updatedCandidates.some(
        (updated, index) =>
          updated.existingUser?.username !== importCandidates[index].existingUser?.username ||
          updated.hasChanges !== importCandidates[index].hasChanges
      );

      if (hasUpdates) {
        setImportCandidates(updatedCandidates);
      }
      setIsCheckingDuplicates(false);
    }
  }, [existingUsersData, isLoadingUsers, existingUsersMap, importCandidates]);

  // Use updated candidates from hook during import, otherwise use local state
  const displayCandidates = isImporting || importResult ? updatedCandidates : importCandidates;

  // Count candidates by type
  const newCount = displayCandidates.filter(
    (c) => c.importStatus === 'pending' && !c.existingUser
  ).length;
  const updateCount = displayCandidates.filter(
    (c) => c.importStatus === 'pending' && c.existingUser && c.hasChanges
  ).length;
  const skipCount = displayCandidates.filter((c) => c.existingUser && !c.hasChanges).length;
  const actionableCount = newCount + updateCount;

  const handleFileDrop = useCallback(
    (acceptedFiles: File[]) => {
      setParseError(null);
      setImportResult(null);

      if (acceptedFiles.length === 0) {
        return;
      }

      const file = acceptedFiles[0];
      if (!file.name.endsWith('.json')) {
        setParseError(t('batchImport.errors.invalidFile'));
        return;
      }

      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const content = e.target?.result as string;
          const speakers = parseSpeakersJson(content);

          if (speakers.length === 0) {
            setParseError(t('batchImport.noSpeakers'));
            return;
          }

          const candidates = createImportCandidates(speakers);
          setImportCandidates(candidates);
        } catch (error) {
          setParseError(
            t('batchImport.errors.parseError', {
              error: error instanceof Error ? error.message : 'Unknown error',
            })
          );
        }
      };

      reader.onerror = () => {
        setParseError(t('batchImport.errors.invalidFile'));
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
    // Only import candidates that are new OR have changes to update
    const candidatesToImport = importCandidates.filter(
      (c) => c.importStatus === 'pending' && (!c.existingUser || c.hasChanges)
    );
    if (candidatesToImport.length === 0) return;
    await importSpeakers(candidatesToImport);
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
      aria-labelledby="speaker-batch-import-dialog-title"
    >
      <DialogTitle id="speaker-batch-import-dialog-title">
        <Box display="flex" justifyContent="space-between" alignItems="center">
          {t('batchImport.title')}
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
            data-testid="json-dropzone"
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
              {isDragActive ? t('batchImport.dropzoneActive') : t('batchImport.dropzone')}
            </Typography>
            <Typography variant="caption" color="textSecondary" sx={{ mt: 1, display: 'block' }}>
              {t('batchImport.dropzoneHint')}
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
              {t('batchImport.progress', {
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
            {t('batchImport.complete', {
              success: importResult.success,
              updated: importResult.updated,
              failed: importResult.failed,
              skipped: importResult.skipped,
            })}
          </Alert>
        )}

        {/* Loading indicator while checking existing users */}
        {(isLoadingUsers || isCheckingDuplicates) && importCandidates.length > 0 && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
            <CircularProgress size={20} />
            <Typography variant="body2" color="textSecondary">
              {t('batchImport.checkingExisting')}
            </Typography>
          </Box>
        )}

        {/* Import summary alert */}
        {!isLoadingUsers &&
          !isCheckingDuplicates &&
          displayCandidates.length > 0 &&
          !importResult && (
            <Alert severity="info" sx={{ mb: 2 }}>
              {t('batchImport.summary', {
                total: displayCandidates.length,
                new: newCount,
                update: updateCount,
                skip: skipCount,
              })}
            </Alert>
          )}

        {/* Preview Table */}
        {displayCandidates.length > 0 && (
          <>
            <Typography variant="h6" sx={{ mb: 2 }}>
              {t('batchImport.preview', { count: displayCandidates.length })}
            </Typography>

            <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 400 }}>
              <Table stickyHeader size="small">
                <TableHead>
                  <TableRow>
                    <TableCell width={40}></TableCell>
                    <TableCell>{t('batchImport.columns.name')}</TableCell>
                    <TableCell>{t('batchImport.columns.email')}</TableCell>
                    <TableCell>{t('batchImport.columns.company')}</TableCell>
                    <TableCell width={150}>{t('batchImport.columns.status')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {displayCandidates.map((candidate) => (
                    <TableRow
                      key={candidate.source.id}
                      sx={{
                        backgroundColor:
                          candidate.importStatus === 'importing' ? 'action.hover' : undefined,
                      }}
                    >
                      <TableCell>
                        {candidate.portraitUrl ? (
                          <PhotoCameraIcon
                            fontSize="small"
                            color="success"
                            titleAccess={t('batchImport.hasPortrait')}
                          />
                        ) : (
                          <PhotoIcon
                            fontSize="small"
                            color="disabled"
                            titleAccess={t('batchImport.noPortrait')}
                          />
                        )}
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">
                          {candidate.apiPayload.firstName} {candidate.apiPayload.lastName}
                        </Typography>
                        <Typography variant="caption" color="textSecondary">
                          {candidate.source.name}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" fontFamily="monospace">
                          {candidate.apiPayload.email}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        {candidate.apiPayload.companyId ? (
                          <Chip
                            label={candidate.apiPayload.companyId}
                            size="small"
                            variant="outlined"
                          />
                        ) : (
                          <Typography variant="body2" color="textSecondary">
                            -
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        <Chip
                          icon={getStatusIcon(candidate.importStatus)}
                          label={
                            candidate.errorMessage ||
                            (candidate.existingUser
                              ? candidate.hasChanges
                                ? t('batchImport.status.willUpdate', {
                                    fields: candidate.changedFields?.join(', '),
                                  })
                                : t('batchImport.status.noChanges')
                              : t(`batchImport.status.${candidate.importStatus}`))
                          }
                          color={
                            candidate.existingUser
                              ? candidate.hasChanges
                                ? 'info'
                                : 'default'
                              : getStatusColor(candidate.importStatus)
                          }
                          size="small"
                          variant={
                            candidate.importStatus === 'pending' && !candidate.hasChanges
                              ? 'outlined'
                              : 'filled'
                          }
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
          {importResult ? t('actions.close') : t('batchImport.cancelButton')}
        </Button>
        {!importResult && displayCandidates.length > 0 && (
          <Button
            variant="contained"
            onClick={handleImport}
            disabled={
              isImporting || isLoadingUsers || isCheckingDuplicates || actionableCount === 0
            }
          >
            {isImporting
              ? t('batchImport.status.importing')
              : actionableCount === 0
                ? t('batchImport.allSkipped')
                : t('batchImport.importButton', { new: newCount, update: updateCount })}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default SpeakerBatchImportModal;
