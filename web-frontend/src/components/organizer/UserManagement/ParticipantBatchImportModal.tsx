/**
 * Modal component for batch importing participants from CSV
 *
 * Features:
 * - CSV drag-and-drop upload
 * - Preview table with participant details
 * - Progress tracking during import
 * - Result summary with success/error details
 */

import { useState, useCallback, useMemo, useEffect } from 'react';
import { useDropzone } from 'react-dropzone';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  LinearProgress,
  Alert,
  Chip,
  Box,
  Typography,
} from '@mui/material';
import {
  CloudUpload as UploadIcon,
  CheckCircle as SuccessIcon,
  Error as ErrorIcon,
  HourglassEmpty as PendingIcon,
  Email as EmailIcon,
  HourglassEmpty,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useParticipantBatchImport } from '../../../hooks/useParticipantBatchImport';
import { useUserList } from '../../../hooks/useUserManagement/useUserList';
import {
  parseParticipantCsv,
  convertParticipantToRegistrationRequest,
  constructUsername,
} from '../../../utils/participantImportUtils';
import type {
  ParticipantBatchImportModalProps,
  ImportStatus,
  ParticipantImportCandidate,
} from '../../../types/participantImport.types';

export function ParticipantBatchImportModal({
  open,
  onClose,
  onImportComplete,
}: ParticipantBatchImportModalProps) {
  const { t } = useTranslation('userManagement');
  const [csvFile, setCsvFile] = useState<File | null>(null);
  const [parseError, setParseError] = useState<string | null>(null);
  const [progress, setProgress] = useState(0);
  const [previewCandidates, setPreviewCandidates] = useState<ParticipantImportCandidate[]>([]);
  const [shouldCheckUsers, setShouldCheckUsers] = useState(false);

  const { importCandidates, isImporting, candidates, reset } = useParticipantBatchImport();

  // Fetch all users to check for existing participants
  // Only fetch when we have candidates to check
  const { data: existingUsersData } = useUserList({
    filters: {},
    pagination: {
      page: 1,
      limit: 1000, // Large limit to get all users
    },
    enabled: shouldCheckUsers,
  });

  // Parse CSV when file is uploaded
  const handleFileDrop = useCallback((acceptedFiles: File[]) => {
    if (acceptedFiles.length === 0) {
      return;
    }

    const file = acceptedFiles[0];
    setCsvFile(file);
    setParseError(null);
    setPreviewCandidates([]);

    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const content = e.target?.result as string;
        const participants = parseParticipantCsv(content);

        // Convert to preview candidates with constructed usernames
        const previewCands: ParticipantImportCandidate[] = participants.map((p) => {
          const requests = convertParticipantToRegistrationRequest(p);
          const eventCodes = requests.registrations.map((r) => r.eventCode);
          const username = constructUsername(requests.firstName, requests.lastName);

          return {
            firstName: requests.firstName,
            lastName: requests.lastName,
            email: requests.participantEmail,
            username,
            eventCount: eventCodes.length,
            eventCodes, // Add event codes array for display
            isSyntheticEmail: requests.participantEmail.endsWith('@batbern.ch'),
            importStatus: 'pending',
            isExisting: undefined, // Will be set after checking existing users
          };
        });

        setPreviewCandidates(previewCands);

        // Trigger user list fetch to check for existing users
        setShouldCheckUsers(true);
      } catch (error) {
        setParseError(error instanceof Error ? error.message : t('participantImport.parseError'));
      }
    };
    reader.readAsText(file);
  }, []);

  // Update candidates with existing user information
  useEffect(() => {
    if (!existingUsersData?.data || previewCandidates.length === 0) {
      return;
    }

    // Create a Set of existing usernames for O(1) lookup
    // user.id is the username (e.g., "zacharias.kull")
    const existingUsernames = new Set(existingUsersData.data.map((user) => user.id));

    // Update candidates to mark which ones already exist
    setPreviewCandidates((prev) =>
      prev.map((candidate) => ({
        ...candidate,
        isExisting: existingUsernames.has(candidate.username),
      }))
    );
  }, [existingUsersData]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop: handleFileDrop,
    accept: {
      'text/csv': ['.csv'],
      'application/vnd.ms-excel': ['.csv'],
    },
    multiple: false,
    disabled: isImporting,
  });

  // Start import process
  const handleImport = useCallback(async () => {
    if (!csvFile) {
      return;
    }

    const reader = new FileReader();
    reader.onload = async (e) => {
      try {
        const content = e.target?.result as string;
        const participants = parseParticipantCsv(content);
        const requests = participants.map(convertParticipantToRegistrationRequest);

        const result = await importCandidates(requests, (current, total) => {
          setProgress((current / total) * 100);
        });

        onImportComplete?.(result);
      } catch (error) {
        setParseError(error instanceof Error ? error.message : t('participantImport.importFailed'));
      }
    };
    reader.readAsText(csvFile);
  }, [csvFile, importCandidates, onImportComplete]);

  // Reset modal state
  const handleClose = useCallback(() => {
    if (!isImporting) {
      setCsvFile(null);
      setParseError(null);
      setProgress(0);
      setPreviewCandidates([]);
      reset(); // Reset hook state (candidates)
      onClose();
    }
  }, [isImporting, onClose, reset]);

  // Summary statistics
  const summary = useMemo(() => {
    const total = candidates.length;
    const success = candidates.filter((c) => c.importStatus === 'success').length;
    const failed = candidates.filter((c) => c.importStatus === 'error').length;
    const pending = candidates.filter((c) => c.importStatus === 'pending').length;
    const importing = candidates.filter((c) => c.importStatus === 'importing').length;

    return { total, success, failed, pending, importing };
  }, [candidates]);

  // Status chip component
  const StatusChip = ({ status }: { status: ImportStatus }) => {
    const config = {
      pending: {
        icon: <PendingIcon />,
        label: t('participantImport.status.pending'),
        color: 'default' as const,
      },
      importing: {
        icon: <HourglassEmpty />,
        label: t('participantImport.status.importing'),
        color: 'info' as const,
      },
      success: {
        icon: <SuccessIcon />,
        label: t('participantImport.status.success'),
        color: 'success' as const,
      },
      error: {
        icon: <ErrorIcon />,
        label: t('participantImport.status.error'),
        color: 'error' as const,
      },
      skipped: {
        icon: <ErrorIcon />,
        label: t('participantImport.status.skipped'),
        color: 'warning' as const,
      },
    };

    const { icon, label, color } = config[status];

    return (
      <Chip
        icon={icon}
        label={label}
        color={color}
        size="small"
        aria-label={t('participantImport.aria.statusLabel', { label })}
      />
    );
  };

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="md"
      fullWidth
      data-testid="participant-import-modal"
    >
      <DialogTitle>{t('participantImport.title')}</DialogTitle>
      <DialogContent dividers>
        {/* File Upload Area */}
        {!csvFile && !candidates.length && (
          <Box
            {...getRootProps()}
            role="button"
            aria-label={t('participantImport.aria.dropzone')}
            tabIndex={0}
            sx={{
              border: '2px dashed',
              borderColor: isDragActive ? 'primary.main' : 'grey.300',
              borderRadius: 2,
              p: 4,
              textAlign: 'center',
              cursor: 'pointer',
              bgcolor: isDragActive ? 'action.hover' : 'background.paper',
              transition: 'all 0.2s',
              '&:hover': {
                borderColor: 'primary.main',
                bgcolor: 'action.hover',
              },
            }}
          >
            <input
              {...getInputProps()}
              aria-label={t('participantImport.aria.fileInput')}
              data-testid="csv-file-input"
            />
            <UploadIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              {isDragActive
                ? t('participantImport.dropzoneActive')
                : t('participantImport.dropzone')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {t('participantImport.dropzoneHint')}
            </Typography>
          </Box>
        )}

        {/* Parse Error */}
        {parseError && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {parseError}
          </Alert>
        )}

        {/* CSV File Info */}
        {csvFile && !candidates.length && previewCandidates.length === 0 && !parseError && (
          <Alert severity="info" sx={{ mt: 2 }}>
            {t('participantImport.fileLoaded', {
              name: csvFile.name,
              size: (csvFile.size / 1024).toFixed(2),
            })}
          </Alert>
        )}

        {/* Progress Bar */}
        {isImporting && (
          <Box
            sx={{ mt: 2 }}
            role="status"
            aria-live="polite"
            aria-label={t('participantImport.aria.importProgress')}
          >
            <LinearProgress
              variant="determinate"
              value={progress}
              aria-label={t('participantImport.aria.progressBar', {
                percent: Math.round(progress),
              })}
            />
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1, textAlign: 'center' }}>
              {t('participantImport.progress', {
                current: summary.success + summary.failed,
                total: summary.total,
              })}
            </Typography>
          </Box>
        )}

        {/* Preview/Status Table - Show for both preview and import */}
        {(previewCandidates.length > 0 || candidates.length > 0) && (
          <>
            {/* Summary - only show if importing or completed */}
            {candidates.length > 0 && (
              <Box sx={{ mb: 2, mt: 2 }}>
                <Typography variant="subtitle2" gutterBottom>
                  {t('participantImport.summary')}
                </Typography>
                <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                  <Chip label={t('participantImport.stats.total', { count: summary.total })} />
                  <Chip
                    label={t('participantImport.stats.success', { count: summary.success })}
                    color="success"
                  />
                  <Chip
                    label={t('participantImport.stats.failed', { count: summary.failed })}
                    color="error"
                  />
                  {summary.pending > 0 && (
                    <Chip
                      label={t('participantImport.stats.pending', { count: summary.pending })}
                    />
                  )}
                </Box>
              </Box>
            )}

            {/* Preview info when showing preview */}
            {previewCandidates.length > 0 && candidates.length === 0 && (
              <>
                <Alert severity="info" sx={{ mb: 2, mt: 2 }}>
                  {t('participantImport.preview', { count: previewCandidates.length })}
                </Alert>
                <Alert severity="warning" sx={{ mb: 2 }}>
                  {t('participantImport.previewLimitation')}
                </Alert>
              </>
            )}

            <TableContainer
              component={Paper}
              sx={{ maxHeight: 400 }}
              role="region"
              aria-label={t('participantImport.aria.previewTable')}
            >
              <Table
                stickyHeader
                size="small"
                aria-label={t('participantImport.aria.participantTable')}
              >
                <TableHead>
                  <TableRow>
                    <TableCell>{t('common:labels.name')}</TableCell>
                    <TableCell>{t('common:labels.email')}</TableCell>
                    <TableCell align="center">{t('common:navigation.events')}</TableCell>
                    <TableCell align="center">{t('common:labels.status')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {/* Use candidates during/after import, otherwise use preview candidates */}
                  {(candidates.length > 0 ? candidates : previewCandidates).map(
                    (candidate, index) => (
                      <TableRow key={index}>
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Typography variant="body2">
                              {candidate.firstName} {candidate.lastName}
                            </Typography>
                            {candidate.isExisting && (
                              <Chip
                                label={t('participantImport.badges.existing')}
                                size="small"
                                color="info"
                                variant="outlined"
                              />
                            )}
                            {candidate.isExisting === false && (
                              <Chip
                                label={t('participantImport.badges.new')}
                                size="small"
                                color="success"
                                variant="outlined"
                              />
                            )}
                          </Box>
                          <Typography variant="caption" color="text.secondary">
                            @{candidate.username}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            {candidate.email}
                            {candidate.isSyntheticEmail && (
                              <Chip
                                icon={<EmailIcon />}
                                label={t('participantImport.badges.synthetic')}
                                size="small"
                                variant="outlined"
                                color="warning"
                              />
                            )}
                          </Box>
                        </TableCell>
                        <TableCell align="center">
                          <Box
                            sx={{
                              display: 'flex',
                              flexDirection: 'column',
                              alignItems: 'center',
                              gap: 0.5,
                            }}
                          >
                            <Typography variant="body2">
                              {t('participantImport.badges.eventsCount', {
                                count: candidate.eventCount,
                              })}
                            </Typography>
                            {candidate.eventCodes && candidate.eventCodes.length > 0 && (
                              <Typography
                                variant="caption"
                                color="text.secondary"
                                sx={{ maxWidth: 200, textAlign: 'center' }}
                              >
                                {candidate.eventCodes.join(', ')}
                              </Typography>
                            )}
                          </Box>
                        </TableCell>
                        <TableCell align="center">
                          <StatusChip status={candidate.importStatus} />
                          {candidate.errorMessage && (
                            <Typography variant="caption" color="error" display="block">
                              {candidate.errorMessage}
                            </Typography>
                          )}
                        </TableCell>
                      </TableRow>
                    )
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </>
        )}
      </DialogContent>

      <DialogActions>
        <Button
          onClick={handleClose}
          disabled={isImporting}
          aria-label={t('participantImport.aria.closeModal')}
          data-testid="participant-import-cancel-button"
        >
          {candidates.length > 0 && !isImporting
            ? t('common:actions.close')
            : t('common:actions.cancel')}
        </Button>
        {previewCandidates.length > 0 && !candidates.length && !parseError && (
          <Button
            variant="contained"
            onClick={handleImport}
            disabled={isImporting}
            aria-label={t('participantImport.aria.startImport')}
            data-testid="participant-import-start-button"
          >
            {t('participantImport.importButton', { count: previewCandidates.length })}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
