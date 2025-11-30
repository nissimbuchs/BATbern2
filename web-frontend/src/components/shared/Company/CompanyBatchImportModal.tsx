/**
 * CompanyBatchImportModal Component
 *
 * Modal for batch importing companies from a JSON file.
 * Features:
 * - File upload via drag-and-drop
 * - Preview of companies to import with logos
 * - Sequential import with status tracking
 * - Progress indicator
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
  Avatar,
  Link,
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
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useCompanyBatchImport } from '@/hooks/useCompanyBatchImport';
import { useCompanies } from '@/hooks/useCompanies/useCompanies';
import { parseCompanyJson, createImportCandidates } from '@/utils/companyImport';
import type {
  ImportCandidate,
  CompanyBatchImportModalProps,
  BatchImportResult,
  ImportStatus,
} from '@/types/companyImport.types';

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

export const CompanyBatchImportModal: React.FC<CompanyBatchImportModalProps> = ({
  open,
  onClose,
  onImportComplete,
}) => {
  const { t } = useTranslation('common');
  const [parseError, setParseError] = useState<string | null>(null);
  const [importCandidates, setImportCandidates] = useState<ImportCandidate[]>([]);
  const [importResult, setImportResult] = useState<BatchImportResult | null>(null);
  const [isCheckingDuplicates, setIsCheckingDuplicates] = useState(false);

  // Fetch all existing companies to check for duplicates
  // Using a high limit to get all companies for duplicate checking
  const { data: existingCompaniesData, isLoading: isLoadingCompanies } = useCompanies(
    { page: 1, limit: 1000 },
    {},
    { expand: [] }
  );

  const {
    importCompanies,
    isImporting,
    currentIndex,
    totalCount,
    candidates: updatedCandidates,
    reset: resetImport,
  } = useCompanyBatchImport({
    onComplete: (result) => {
      setImportResult(result);
      onImportComplete?.(result);
    },
  });

  // Get set of existing company names for fast lookup
  const existingCompanyNames = useMemo(
    () => new Set((existingCompaniesData?.data || []).map((c) => c.name.toLowerCase())),
    [existingCompaniesData]
  );

  // Mark duplicates when candidates change or existing companies load
  useEffect(() => {
    if (importCandidates.length > 0 && !isLoadingCompanies && existingCompaniesData) {
      setIsCheckingDuplicates(true);
      const updatedCandidates = importCandidates.map((candidate) => {
        const nameExists = existingCompanyNames.has(candidate.apiPayload.name.toLowerCase());
        if (nameExists && candidate.importStatus === 'pending') {
          return {
            ...candidate,
            importStatus: 'skipped' as const,
            errorMessage: t('company.batchImport.status.skipped'),
          };
        }
        return candidate;
      });

      // Only update if there are actual changes
      const hasChanges = updatedCandidates.some(
        (updated, index) => updated.importStatus !== importCandidates[index].importStatus
      );

      if (hasChanges) {
        setImportCandidates(updatedCandidates);
      }
      setIsCheckingDuplicates(false);
    }
  }, [existingCompaniesData, isLoadingCompanies, t, existingCompanyNames, importCandidates]);

  // Use updated candidates from hook during import, otherwise use local state
  const displayCandidates = isImporting || importResult ? updatedCandidates : importCandidates;

  // Count how many are pending (not pre-skipped)
  const pendingCount = displayCandidates.filter((c) => c.importStatus === 'pending').length;
  const preSkippedCount = displayCandidates.filter((c) => c.importStatus === 'skipped').length;

  const handleFileDrop = useCallback(
    (acceptedFiles: File[]) => {
      setParseError(null);
      setImportResult(null);

      if (acceptedFiles.length === 0) {
        return;
      }

      const file = acceptedFiles[0];
      if (!file.name.endsWith('.json')) {
        setParseError(t('company.batchImport.errors.invalidFile'));
        return;
      }

      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const content = e.target?.result as string;
          const companies = parseCompanyJson(content);

          if (companies.length === 0) {
            setParseError(t('company.batchImport.noCompanies'));
            return;
          }

          const candidates = createImportCandidates(companies);
          setImportCandidates(candidates);
        } catch (error) {
          setParseError(
            t('company.batchImport.errors.parseError', {
              error: error instanceof Error ? error.message : 'Unknown error',
            })
          );
        }
      };

      reader.onerror = () => {
        setParseError(t('company.batchImport.errors.invalidFile'));
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
    await importCompanies(candidatesToImport);
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
          {t('company.batchImport.title')}
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
                ? t('company.batchImport.dropzoneActive')
                : t('company.batchImport.dropzone')}
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
              {t('company.batchImport.progress', {
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
            {t('company.batchImport.complete', {
              success: importResult.success,
              failed: importResult.failed,
              skipped: importResult.skipped,
            })}
          </Alert>
        )}

        {/* Loading indicator while checking duplicates */}
        {(isLoadingCompanies || isCheckingDuplicates) && importCandidates.length > 0 && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
            <CircularProgress size={20} />
            <Typography variant="body2" color="textSecondary">
              {t('company.batchImport.checkingDuplicates')}
            </Typography>
          </Box>
        )}

        {/* Pre-skip info alert */}
        {!isLoadingCompanies && !isCheckingDuplicates && preSkippedCount > 0 && !importResult && (
          <Alert severity="info" sx={{ mb: 2 }}>
            {t('company.batchImport.duplicatesFound', {
              count: preSkippedCount,
              total: displayCandidates.length,
            })}
          </Alert>
        )}

        {/* Preview Table */}
        {displayCandidates.length > 0 && (
          <>
            <Typography variant="h6" sx={{ mb: 2 }}>
              {t('company.batchImport.preview', { count: displayCandidates.length })}
            </Typography>

            <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 400 }}>
              <Table stickyHeader size="small">
                <TableHead>
                  <TableRow>
                    <TableCell width={60}>{t('company.batchImport.columns.logo')}</TableCell>
                    <TableCell>{t('company.batchImport.columns.displayName')}</TableCell>
                    <TableCell>{t('company.batchImport.columns.name')}</TableCell>
                    <TableCell>{t('company.batchImport.columns.industry')}</TableCell>
                    <TableCell>{t('company.batchImport.columns.website')}</TableCell>
                    <TableCell width={150}>{t('company.batchImport.columns.status')}</TableCell>
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
                        <Avatar
                          src={candidate.logoUrl || undefined}
                          alt={candidate.source.displayName}
                          variant="rounded"
                          sx={{ width: 40, height: 40 }}
                        >
                          {candidate.source.displayName.charAt(0).toUpperCase()}
                        </Avatar>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{candidate.source.displayName}</Typography>
                        {candidate.source.note && (
                          <Typography variant="caption" color="textSecondary">
                            {candidate.source.note}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" fontFamily="monospace">
                          {candidate.apiPayload.name}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        {candidate.apiPayload.industry ? (
                          <Chip
                            label={candidate.apiPayload.industry}
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
                        {candidate.apiPayload.website ? (
                          <Link
                            href={candidate.apiPayload.website}
                            target="_blank"
                            rel="noopener noreferrer"
                            variant="body2"
                            sx={{
                              maxWidth: 200,
                              display: 'block',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              whiteSpace: 'nowrap',
                            }}
                          >
                            {candidate.apiPayload.website}
                          </Link>
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
                            t(`company.batchImport.status.${candidate.importStatus}`)
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
          {importResult ? t('actions.close') : t('company.batchImport.cancelButton')}
        </Button>
        {!importResult && displayCandidates.length > 0 && (
          <Button
            variant="contained"
            onClick={handleImport}
            disabled={
              isImporting || isLoadingCompanies || isCheckingDuplicates || pendingCount === 0
            }
          >
            {isImporting
              ? t('company.batchImport.status.importing')
              : pendingCount === 0
                ? t('company.batchImport.allSkipped')
                : t('company.batchImport.importButtonWithCount', { count: pendingCount })}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default CompanyBatchImportModal;
