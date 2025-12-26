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
} from '@mui/material';
import { RestoreIcon, CheckCircle, HourglassEmpty, ErrorIcon } from '@mui/icons-material';
import { usePublishing } from '@/hooks/usePublishing/usePublishing';

interface PublishingVersion {
  id: string;
  eventCode: string;
  versionNumber: number;
  publishedPhase: string;
  publishedAt: string;
  publishedBy: string;
  cdnInvalidationId: string;
  cdnInvalidationStatus: 'COMPLETED' | 'PENDING' | 'FAILED';
  isCurrent: boolean;
}

export interface VersionControlProps {
  eventCode: string;
}

export const VersionControl: React.FC<VersionControlProps> = ({ eventCode }) => {
  const { versionHistory, rollbackVersion, isLoadingVersions, isRollingBack } =
    usePublishing(eventCode);
  const [rollbackDialogOpen, setRollbackDialogOpen] = useState(false);
  const [selectedVersion, setSelectedVersion] = useState<PublishingVersion | null>(null);

  const handleRollbackClick = (version: PublishingVersion) => {
    setSelectedVersion(version);
    setRollbackDialogOpen(true);
  };

  const handleConfirmRollback = async () => {
    if (selectedVersion) {
      await rollbackVersion?.(selectedVersion.id);
      setRollbackDialogOpen(false);
      setSelectedVersion(null);
    }
  };

  const handleCancelRollback = () => {
    setRollbackDialogOpen(false);
    setSelectedVersion(null);
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

  const getCDNStatusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return <CheckCircle color="success" fontSize="small" data-testid="cdn-status-completed" />;
      case 'PENDING':
        return <HourglassEmpty color="warning" fontSize="small" data-testid="cdn-status-pending" />;
      case 'FAILED':
        return <ErrorIcon color="error" fontSize="small" data-testid="cdn-status-failed" />;
      default:
        return null;
    }
  };

  const getCDNStatusText = (status: string): string => {
    switch (status) {
      case 'COMPLETED':
        return 'CDN Cleared';
      case 'PENDING':
        return 'CDN Pending';
      case 'FAILED':
        return 'CDN Failed';
      default:
        return 'Unknown';
    }
  };

  // Sort versions by version number descending (newest first)
  const sortedVersions = [...(versionHistory || [])].sort(
    (a, b) => b.versionNumber - a.versionNumber
  );

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h6" gutterBottom>
        Version History
      </Typography>

      {isLoadingVersions ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress data-testid="version-history-loading" />
        </Box>
      ) : (
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Version</TableCell>
                <TableCell>Published</TableCell>
                <TableCell>Phase</TableCell>
                <TableCell>Publisher</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sortedVersions.map((version) => (
                <TableRow key={version.id} data-testid={`version-row-${version.versionNumber}`}>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      Version {version.versionNumber}
                      {version.isCurrent && (
                        <Chip
                          label="Current"
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
                    <Tooltip title={`CDN Invalidation ID: ${version.cdnInvalidationId}`}>
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
                        startIcon={<RestoreIcon />}
                        onClick={() => handleRollbackClick(version)}
                        disabled={isRollingBack}
                        data-testid={`rollback-button-${version.versionNumber}`}
                      >
                        Rollback
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
                      No version history available
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
      >
        <DialogTitle>Confirm Rollback</DialogTitle>
        <DialogContent>
          {selectedVersion && (
            <Box>
              <Typography variant="body1" gutterBottom>
                Are you sure you want to rollback to this version?
              </Typography>
              <Box
                sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}
                data-testid="rollback-version-details"
              >
                <Typography variant="body2">
                  <strong>Version:</strong> {selectedVersion.versionNumber}
                </Typography>
                <Typography variant="body2">
                  <strong>Phase:</strong> {selectedVersion.publishedPhase}
                </Typography>
                <Typography variant="body2">
                  <strong>Published:</strong> {formatDate(selectedVersion.publishedAt)}
                </Typography>
                <Typography variant="body2">
                  <strong>Publisher:</strong> {selectedVersion.publishedBy}
                </Typography>
              </Box>
              <Typography variant="body2" color="warning.main" sx={{ mt: 2 }}>
                This action will invalidate the CloudFront CDN cache and may take a few minutes to
                complete.
              </Typography>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCancelRollback} data-testid="cancel-rollback-button">
            Cancel
          </Button>
          <Button
            onClick={handleConfirmRollback}
            variant="contained"
            color="warning"
            disabled={isRollingBack}
            data-testid="confirm-rollback-button"
            startIcon={isRollingBack ? <CircularProgress size={16} /> : <RestoreIcon />}
          >
            {isRollingBack ? 'Rolling back...' : 'Confirm Rollback'}
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  );
};
