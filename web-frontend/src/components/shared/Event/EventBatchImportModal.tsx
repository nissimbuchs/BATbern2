/**
 * EventBatchImportModal Component
 *
 * Modal for batch importing historical events from topics.json.
 * Features:
 * - File upload via drag-and-drop (topics.json only)
 * - Preview of events to import with event details
 * - Sequential import with status tracking
 * - Progress indicator
 * - Duplicate detection by event number
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
  RadioGroup,
  FormControlLabel,
  Radio,
  FormControl,
  FormLabel,
  Divider,
} from '@mui/material';
import {
  CloudUpload as CloudUploadIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  HourglassEmpty as PendingIcon,
  SkipNext as SkipIcon,
  Close as CloseIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useEventBatchImport } from '@/hooks/useEventBatchImport';
import { parseEventsJson, createImportCandidates } from '@/utils/eventImport';
import type {
  EventImportCandidate,
  EventBatchImportModalProps,
  EventBatchImportResult,
  ImportStatus,
  UpdateFieldSelection,
} from '@/types/eventImport.types';

/**
 * Get the appropriate icon for an import status
 */
function getStatusIcon(status: ImportStatus) {
  switch (status) {
    case 'success':
      return <CheckCircleIcon color="success" fontSize="small" />;
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
): 'default' | 'primary' | 'success' | 'error' | 'warning' {
  switch (status) {
    case 'success':
      return 'success';
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

export const EventBatchImportModal: React.FC<EventBatchImportModalProps> = ({
  open,
  onClose,
  onImportComplete,
}) => {
  const { t } = useTranslation('common');
  const importLabel = t('event.batchImport.fieldSelection.import');
  const ignoreLabel = t('event.batchImport.fieldSelection.ignore');
  const [parseError, setParseError] = useState<string | null>(null);
  const [importCandidates, setImportCandidates] = useState<EventImportCandidate[]>([]);
  const [importResult, setImportResult] = useState<EventBatchImportResult | null>(null);

  // Story 5.2a: Field selection (import = upsert, ignore = skip)
  const [fieldSelection, setFieldSelection] = useState<UpdateFieldSelection>({
    title: false, // ignore by default
    description: false, // ignore by default
    topic: true, // import by default (main purpose of this feature)
    date: false, // ignore by default
    venue: false, // ignore by default
    organizer: false, // ignore by default
  });

  const {
    importEvents,
    isImporting,
    currentIndex,
    totalCount,
    candidates: updatedCandidates,
    reset: resetImport,
  } = useEventBatchImport({
    fieldSelection, // Import = upsert (update if exists, create if not), Ignore = skip
    onComplete: (result) => {
      setImportResult(result);
      onImportComplete?.(result);
    },
  });

  // Use updated candidates from hook during import, otherwise use local state
  const displayCandidates = isImporting || importResult ? updatedCandidates : importCandidates;

  // Count how many are pending (not pre-skipped)
  const pendingCount = displayCandidates.filter((c) => c.importStatus === 'pending').length;

  const handleFileDrop = useCallback(
    (acceptedFiles: File[]) => {
      setParseError(null);
      setImportResult(null);

      if (acceptedFiles.length === 0) {
        return;
      }

      const file = acceptedFiles[0];
      if (!file.name.endsWith('.json')) {
        setParseError(t('event.batchImport.errors.invalidFile'));
        return;
      }

      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const content = e.target?.result as string;
          const events = parseEventsJson(content);

          if (events.length === 0) {
            setParseError(t('event.batchImport.noEvents'));
            return;
          }

          const candidates = createImportCandidates(events);
          setImportCandidates(candidates);
        } catch (error) {
          setParseError(
            t('event.batchImport.errors.parseError', {
              error: error instanceof Error ? error.message : 'Unknown error',
            })
          );
        }
      };

      reader.onerror = () => {
        setParseError(t('event.batchImport.errors.invalidFile'));
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
    // Only import candidates that are pending (not pre-skipped)
    const candidatesToImport = importCandidates.filter((c) => c.importStatus === 'pending');
    if (candidatesToImport.length === 0) return;
    await importEvents(candidatesToImport);
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
      aria-labelledby="batch-import-dialog-title"
    >
      <DialogTitle id="batch-import-dialog-title">
        <Box display="flex" justifyContent="space-between" alignItems="center">
          {t('event.batchImport.title')}
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
              {isDragActive
                ? t('event.batchImport.dropzoneActive')
                : t('event.batchImport.dropzone')}
            </Typography>
            <Typography variant="caption" color="textSecondary" sx={{ mt: 1, display: 'block' }}>
              {t('event.batchImport.dropzoneHint')}
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
              {t('event.batchImport.progress', {
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
            {t('event.batchImport.complete', {
              success: importResult.success,
              failed: importResult.failed,
              skipped: importResult.skipped,
            })}
          </Alert>
        )}

        {/* Story 5.2a: Field Selection (Import vs Ignore) */}
        {displayCandidates.length > 0 && !isImporting && !importResult && (
          <Box sx={{ mb: 3 }}>
            <FormControl component="fieldset">
              <FormLabel component="legend" sx={{ mb: 2 }}>
                {t('event.batchImport.fieldSelection.legend')}
              </FormLabel>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                {/* Title Field */}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Typography sx={{ width: 120, fontWeight: 500 }}>
                    {t('event.batchImport.fieldSelection.fieldTitle')}
                  </Typography>
                  <RadioGroup
                    row
                    value={fieldSelection.title ? 'import' : 'ignore'}
                    onChange={(e) =>
                      setFieldSelection((prev) => ({
                        ...prev,
                        title: e.target.value === 'import',
                      }))
                    }
                  >
                    <FormControlLabel
                      value="import"
                      control={<Radio size="small" />}
                      label={importLabel}
                    />
                    <FormControlLabel
                      value="ignore"
                      control={<Radio size="small" />}
                      label={ignoreLabel}
                    />
                  </RadioGroup>
                </Box>

                {/* Description Field */}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Typography sx={{ width: 120, fontWeight: 500 }}>
                    {t('event.batchImport.fieldSelection.fieldDescription')}
                  </Typography>
                  <RadioGroup
                    row
                    value={fieldSelection.description ? 'import' : 'ignore'}
                    onChange={(e) =>
                      setFieldSelection((prev) => ({
                        ...prev,
                        description: e.target.value === 'import',
                      }))
                    }
                  >
                    <FormControlLabel
                      value="import"
                      control={<Radio size="small" />}
                      label={importLabel}
                    />
                    <FormControlLabel
                      value="ignore"
                      control={<Radio size="small" />}
                      label={ignoreLabel}
                    />
                  </RadioGroup>
                </Box>

                {/* Topic Field */}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Typography sx={{ width: 120, fontWeight: 500 }}>
                    {t('event.batchImport.fieldSelection.fieldTopic')}
                  </Typography>
                  <RadioGroup
                    row
                    value={fieldSelection.topic ? 'import' : 'ignore'}
                    onChange={(e) =>
                      setFieldSelection((prev) => ({
                        ...prev,
                        topic: e.target.value === 'import',
                      }))
                    }
                  >
                    <FormControlLabel
                      value="import"
                      control={<Radio size="small" />}
                      label={importLabel}
                    />
                    <FormControlLabel
                      value="ignore"
                      control={<Radio size="small" />}
                      label={ignoreLabel}
                    />
                  </RadioGroup>
                </Box>

                {/* Date Field */}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Typography sx={{ width: 120, fontWeight: 500 }}>
                    {t('event.batchImport.fieldSelection.fieldDate')}
                  </Typography>
                  <RadioGroup
                    row
                    value={fieldSelection.date ? 'import' : 'ignore'}
                    onChange={(e) =>
                      setFieldSelection((prev) => ({
                        ...prev,
                        date: e.target.value === 'import',
                      }))
                    }
                  >
                    <FormControlLabel
                      value="import"
                      control={<Radio size="small" />}
                      label={importLabel}
                    />
                    <FormControlLabel
                      value="ignore"
                      control={<Radio size="small" />}
                      label={ignoreLabel}
                    />
                  </RadioGroup>
                </Box>

                {/* Venue Field */}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Typography sx={{ width: 120, fontWeight: 500 }}>
                    {t('event.batchImport.fieldSelection.fieldVenue')}
                  </Typography>
                  <RadioGroup
                    row
                    value={fieldSelection.venue ? 'import' : 'ignore'}
                    onChange={(e) =>
                      setFieldSelection((prev) => ({
                        ...prev,
                        venue: e.target.value === 'import',
                      }))
                    }
                  >
                    <FormControlLabel
                      value="import"
                      control={<Radio size="small" />}
                      label={importLabel}
                    />
                    <FormControlLabel
                      value="ignore"
                      control={<Radio size="small" />}
                      label={ignoreLabel}
                    />
                  </RadioGroup>
                </Box>

                {/* Organizer Field */}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Typography sx={{ width: 120, fontWeight: 500 }}>
                    {t('event.batchImport.fieldSelection.fieldOrganizer')}
                  </Typography>
                  <RadioGroup
                    row
                    value={fieldSelection.organizer ? 'import' : 'ignore'}
                    onChange={(e) =>
                      setFieldSelection((prev) => ({
                        ...prev,
                        organizer: e.target.value === 'import',
                      }))
                    }
                  >
                    <FormControlLabel
                      value="import"
                      control={<Radio size="small" />}
                      label={importLabel}
                    />
                    <FormControlLabel
                      value="ignore"
                      control={<Radio size="small" />}
                      label={ignoreLabel}
                    />
                  </RadioGroup>
                </Box>
              </Box>
            </FormControl>

            <Divider sx={{ mt: 2 }} />
          </Box>
        )}

        {/* Preview Table */}
        {displayCandidates.length > 0 && (
          <>
            <Typography variant="h6" sx={{ mb: 2 }}>
              {t('event.batchImport.preview', { count: displayCandidates.length })}
            </Typography>

            <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 400 }}>
              <Table stickyHeader size="small">
                <TableHead>
                  <TableRow>
                    <TableCell width={80}>{t('event.batchImport.columns.eventNumber')}</TableCell>
                    <TableCell>{t('labels.title')}</TableCell>
                    <TableCell width={180}>{t('event.batchImport.columns.category')}</TableCell>
                    <TableCell>{t('labels.date')}</TableCell>
                    <TableCell>{t('event.batchImport.columns.organizer')}</TableCell>
                    <TableCell width={150}>{t('labels.status')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {displayCandidates.map((candidate) => (
                    <TableRow
                      key={candidate.source.bat}
                      sx={{
                        backgroundColor:
                          candidate.importStatus === 'importing' ? 'action.hover' : undefined,
                      }}
                    >
                      <TableCell>
                        <Typography variant="body2" fontFamily="monospace">
                          BATbern{candidate.apiPayload.eventNumber}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{candidate.apiPayload.title}</Typography>
                        <Typography variant="caption" color="textSecondary" noWrap>
                          {candidate.source.description.substring(0, 80)}...
                        </Typography>
                      </TableCell>
                      <TableCell>
                        {candidate.topicCategory ? (
                          <Chip
                            label={candidate.topicCategory}
                            size="small"
                            variant="outlined"
                            color="primary"
                          />
                        ) : (
                          <Typography variant="caption" color="textSecondary">
                            No category
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">
                          {new Date(candidate.apiPayload.date).toLocaleDateString('de-CH', {
                            year: 'numeric',
                            month: 'short',
                            day: 'numeric',
                          })}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" fontFamily="monospace">
                          {candidate.apiPayload.organizerUsername}
                        </Typography>
                        <Typography variant="caption" color="textSecondary">
                          ({candidate.source.moderator})
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          icon={getStatusIcon(candidate.importStatus)}
                          label={
                            candidate.errorMessage ||
                            t(`event.batchImport.status.${candidate.importStatus}`)
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
          {importResult ? t('actions.close') : t('event.batchImport.cancelButton')}
        </Button>
        {!importResult && displayCandidates.length > 0 && (
          <Button
            variant="contained"
            onClick={handleImport}
            disabled={isImporting || pendingCount === 0}
          >
            {isImporting
              ? t('event.batchImport.status.importing')
              : pendingCount === 0
                ? t('event.batchImport.allSkipped')
                : t('event.batchImport.importButtonWithCount', { count: pendingCount })}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default EventBatchImportModal;
