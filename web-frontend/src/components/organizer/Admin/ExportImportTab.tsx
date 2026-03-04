/**
 * ExportImportTab (Story 10.20)
 *
 * Tab for the Admin page providing legacy BAT format export and import.
 * AC5: Export button downloads JSON. AC6: Import uploads JSON + confirmation dialog.
 * Also supports asset manifest export and asset ZIP import.
 */

import React, { useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { Download as DownloadIcon, Upload as UploadIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/events-api.types';

type LegacyImportResult = components['schemas']['LegacyImportResult'];
type AssetManifestResponse = components['schemas']['AssetManifestResponse'];
type AssetImportResult = components['schemas']['AssetImportResult'];
type BundleImportResult = components['schemas']['BundleImportResult'];

export const ExportImportTab: React.FC = () => {
  const { t } = useTranslation('common');

  // ── JSON export/import state ────────────────────────────────────────────
  const [exportLoading, setExportLoading] = useState(false);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importLoading, setImportLoading] = useState(false);
  const [importDialogOpen, setImportDialogOpen] = useState(false);
  const [importResult, setImportResult] = useState<LegacyImportResult | null>(null);
  const [importError, setImportError] = useState<string | null>(null);
  const jsonFileRef = useRef<HTMLInputElement>(null);

  // ── Asset export/import state ───────────────────────────────────────────
  const [assetManifest, setAssetManifest] = useState<AssetManifestResponse | null>(null);
  const [assetManifestLoading, setAssetManifestLoading] = useState(false);
  const [assetImportFile, setAssetImportFile] = useState<File | null>(null);
  const [assetImportLoading, setAssetImportLoading] = useState(false);
  const [assetImportDialogOpen, setAssetImportDialogOpen] = useState(false);
  const [assetImportResult, setAssetImportResult] = useState<AssetImportResult | null>(null);
  const [assetImportError, setAssetImportError] = useState<string | null>(null);
  const zipFileRef = useRef<HTMLInputElement>(null);

  // ── Bundle export/import state ──────────────────────────────────────────
  const [bundleExportLoading, setBundleExportLoading] = useState(false);
  const [bundleImportFile, setBundleImportFile] = useState<File | null>(null);
  const [bundleImportLoading, setBundleImportLoading] = useState(false);
  const [bundleImportDialogOpen, setBundleImportDialogOpen] = useState(false);
  const [bundleImportResult, setBundleImportResult] = useState<BundleImportResult | null>(null);
  const [bundleImportError, setBundleImportError] = useState<string | null>(null);
  const bundleFileRef = useRef<HTMLInputElement>(null);

  // ── JSON export ─────────────────────────────────────────────────────────
  const handleExportJson = async () => {
    setExportLoading(true);
    try {
      const response = await apiClient.get('/admin/export/legacy', {
        responseType: 'blob',
      });
      const url = URL.createObjectURL(new Blob([response.data], { type: 'application/json' }));
      const a = document.createElement('a');
      a.href = url;
      a.download = `batbern-export-${new Date().toISOString().slice(0, 10)}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } finally {
      setExportLoading(false);
    }
  };

  // ── JSON import ─────────────────────────────────────────────────────────
  const handleImportFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setImportFile(e.target.files?.[0] ?? null);
    setImportResult(null);
    setImportError(null);
  };

  const handleImportConfirm = async () => {
    if (!importFile) return;
    setImportDialogOpen(false);
    setImportLoading(true);
    setImportResult(null);
    setImportError(null);
    try {
      const formData = new FormData();
      formData.append('file', importFile);
      const response = await apiClient.post<LegacyImportResult>('/admin/import/legacy', formData);
      setImportResult(response.data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setImportError(msg);
    } finally {
      setImportLoading(false);
    }
  };

  // ── Asset manifest export ───────────────────────────────────────────────
  const handleGetAssetManifest = async () => {
    setAssetManifestLoading(true);
    try {
      const response = await apiClient.get<AssetManifestResponse>('/admin/export/assets');
      setAssetManifest(response.data);
    } finally {
      setAssetManifestLoading(false);
    }
  };

  // ── Bundle export ───────────────────────────────────────────────────────
  const handleExportBundle = async () => {
    setBundleExportLoading(true);
    try {
      const response = await apiClient.get('/admin/export/bundle', { responseType: 'blob' });
      const url = URL.createObjectURL(new Blob([response.data], { type: 'application/zip' }));
      const a = document.createElement('a');
      a.href = url;
      a.download = `batbern-bundle-${new Date().toISOString().slice(0, 10)}.zip`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } finally {
      setBundleExportLoading(false);
    }
  };

  // ── Bundle import ───────────────────────────────────────────────────────
  const handleBundleImportFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setBundleImportFile(e.target.files?.[0] ?? null);
    setBundleImportResult(null);
    setBundleImportError(null);
  };

  const handleBundleImportConfirm = async () => {
    if (!bundleImportFile) return;
    setBundleImportDialogOpen(false);
    setBundleImportLoading(true);
    setBundleImportResult(null);
    setBundleImportError(null);
    try {
      const formData = new FormData();
      formData.append('file', bundleImportFile);
      const response = await apiClient.post<BundleImportResult>('/admin/import/bundle', formData);
      setBundleImportResult(response.data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setBundleImportError(msg);
    } finally {
      setBundleImportLoading(false);
    }
  };

  // ── Asset ZIP import ────────────────────────────────────────────────────
  const handleAssetImportFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setAssetImportFile(e.target.files?.[0] ?? null);
    setAssetImportResult(null);
    setAssetImportError(null);
  };

  const handleAssetImportConfirm = async () => {
    if (!assetImportFile) return;
    setAssetImportDialogOpen(false);
    setAssetImportLoading(true);
    setAssetImportResult(null);
    setAssetImportError(null);
    try {
      const formData = new FormData();
      formData.append('file', assetImportFile);
      const response = await apiClient.post<AssetImportResult>('/admin/import/assets', formData);
      setAssetImportResult(response.data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setAssetImportError(msg);
    } finally {
      setAssetImportLoading(false);
    }
  };

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        {t('admin.exportImport.title', 'Export / Import')}
      </Typography>

      {/* ── Data Export ─────────────────────────────────────────────────── */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            {t('admin.exportImport.exportSection', 'Export')}
          </Typography>
          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
            <Button
              variant="contained"
              startIcon={<DownloadIcon />}
              onClick={handleExportJson}
              disabled={exportLoading}
            >
              {t('admin.exportImport.exportJsonButton', 'Export All Data (JSON)')}
            </Button>
            <Button
              variant="contained"
              color="secondary"
              startIcon={<DownloadIcon />}
              onClick={handleExportBundle}
              disabled={bundleExportLoading}
            >
              {t('admin.exportImport.exportBundleButton', 'Export Bundle (JSON + Assets)')}
            </Button>
            <Button
              variant="outlined"
              startIcon={<DownloadIcon />}
              onClick={handleGetAssetManifest}
              disabled={assetManifestLoading}
            >
              {t('admin.exportImport.exportAssetsButton', 'View Asset Manifest')}
            </Button>
          </Box>

          {/* Asset manifest table */}
          {assetManifest && (
            <Box sx={{ mt: 2 }}>
              <Typography variant="subtitle2">{assetManifest.assetCount} assets</Typography>
              {assetManifest.assets.length > 0 && (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Type</TableCell>
                      <TableCell>Filename</TableCell>
                      <TableCell>URL</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {assetManifest.assets.map((asset) => (
                      <TableRow key={asset.entityId}>
                        <TableCell>{asset.type}</TableCell>
                        <TableCell>{asset.filename}</TableCell>
                        <TableCell>
                          <a href={asset.presignedUrl} target="_blank" rel="noreferrer">
                            {asset.filename}
                          </a>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </Box>
          )}
        </CardContent>
      </Card>

      {/* ── Data Import ─────────────────────────────────────────────────── */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            {t('admin.exportImport.importSection', 'Import')}
          </Typography>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
            <input
              ref={jsonFileRef}
              type="file"
              accept=".json"
              style={{ display: 'none' }}
              onChange={handleImportFileChange}
            />
            <Button variant="outlined" onClick={() => jsonFileRef.current?.click()}>
              {t('admin.exportImport.importJsonLabel', 'Choose JSON File')}
            </Button>
            {importFile && (
              <Typography variant="body2" color="text.secondary">
                {importFile.name}
              </Typography>
            )}
            <Button
              variant="contained"
              color="warning"
              startIcon={<UploadIcon />}
              disabled={!importFile || importLoading}
              onClick={() => setImportDialogOpen(true)}
            >
              {t('admin.exportImport.importButton', 'Import')}
            </Button>
          </Box>

          {importResult && (
            <Box sx={{ mt: 2 }}>
              <Alert severity="success" sx={{ mb: 1 }}>
                {t('admin.exportImport.successAlert', 'Import completed successfully.')}
              </Alert>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>{t('admin.exportImport.resultEvents', 'Events')}</TableCell>
                    <TableCell>{t('admin.exportImport.resultSessions', 'Sessions')}</TableCell>
                    <TableCell>{t('admin.exportImport.resultSpeakers', 'Speakers')}</TableCell>
                    <TableCell>{t('admin.exportImport.resultCompanies', 'Companies')}</TableCell>
                    <TableCell>{t('admin.exportImport.resultAttendees', 'Attendees')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  <TableRow>
                    <TableCell>{importResult.imported.events ?? 0}</TableCell>
                    <TableCell>{importResult.imported.sessions ?? 0}</TableCell>
                    <TableCell>{importResult.imported.speakers ?? 0}</TableCell>
                    <TableCell>{importResult.imported.companies ?? 0}</TableCell>
                    <TableCell>{importResult.imported.attendees ?? 0}</TableCell>
                  </TableRow>
                </TableBody>
              </Table>
              {importResult.errors && importResult.errors.length > 0 && (
                <Box sx={{ mt: 1 }}>
                  <Typography variant="subtitle2" color="error">
                    {t('admin.exportImport.resultErrors', 'Errors')}:
                  </Typography>
                  {importResult.errors.map((err, i) => (
                    <Typography key={i} variant="body2" color="error">
                      {err}
                    </Typography>
                  ))}
                </Box>
              )}
            </Box>
          )}

          {importError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {t('admin.exportImport.errorAlert', 'Import failed. See errors below.')}
              {': '}
              {importError}
            </Alert>
          )}
        </CardContent>
      </Card>

      {/* ── Bundle Import ───────────────────────────────────────────────── */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            {t('admin.exportImport.importBundleLabel', 'Import Bundle (JSON + Assets)')}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t(
              'admin.exportImport.importBundleDescription',
              'Restore data and binary assets from a bundle ZIP. Session materials (PDFs) are not embedded — download links are provided in material-links.json inside the ZIP.'
            )}
          </Typography>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
            <input
              ref={bundleFileRef}
              type="file"
              accept=".zip"
              style={{ display: 'none' }}
              onChange={handleBundleImportFileChange}
            />
            <Button variant="outlined" onClick={() => bundleFileRef.current?.click()}>
              {t('admin.exportImport.importBundleChooseLabel', 'Choose Bundle ZIP')}
            </Button>
            {bundleImportFile && (
              <Typography variant="body2" color="text.secondary">
                {bundleImportFile.name}
              </Typography>
            )}
            <Button
              variant="contained"
              color="warning"
              startIcon={<UploadIcon />}
              disabled={!bundleImportFile || bundleImportLoading}
              onClick={() => setBundleImportDialogOpen(true)}
            >
              {t('admin.exportImport.importBundleButton', 'Import Bundle')}
            </Button>
          </Box>

          {bundleImportResult && (
            <Box sx={{ mt: 2 }}>
              <Alert severity="success" sx={{ mb: 1 }}>
                {t(
                  'admin.exportImport.bundleSuccessAlert',
                  'Bundle import completed successfully.'
                )}
              </Alert>
              <Typography variant="body2">
                {t('admin.exportImport.bundleAssetsImported', 'Assets imported')}:{' '}
                {bundleImportResult.assetsImported}
              </Typography>
              <Table size="small" sx={{ mt: 1 }}>
                <TableHead>
                  <TableRow>
                    <TableCell>{t('admin.exportImport.resultEvents', 'Events')}</TableCell>
                    <TableCell>{t('admin.exportImport.resultSessions', 'Sessions')}</TableCell>
                    <TableCell>{t('admin.exportImport.resultSpeakers', 'Speakers')}</TableCell>
                    <TableCell>{t('admin.exportImport.resultAttendees', 'Attendees')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  <TableRow>
                    <TableCell>{bundleImportResult.dataResult?.imported?.events ?? 0}</TableCell>
                    <TableCell>{bundleImportResult.dataResult?.imported?.sessions ?? 0}</TableCell>
                    <TableCell>{bundleImportResult.dataResult?.imported?.speakers ?? 0}</TableCell>
                    <TableCell>{bundleImportResult.dataResult?.imported?.attendees ?? 0}</TableCell>
                  </TableRow>
                </TableBody>
              </Table>
              {bundleImportResult.assetErrors && bundleImportResult.assetErrors.length > 0 && (
                <Box sx={{ mt: 1 }}>
                  <Typography variant="subtitle2" color="error">
                    {t('admin.exportImport.resultErrors', 'Errors')}:
                  </Typography>
                  {bundleImportResult.assetErrors.map((err, i) => (
                    <Typography key={i} variant="body2" color="error">
                      {err}
                    </Typography>
                  ))}
                </Box>
              )}
            </Box>
          )}

          {bundleImportError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {t('admin.exportImport.errorAlert', 'Import failed. See errors below.')}
              {': '}
              {bundleImportError}
            </Alert>
          )}
        </CardContent>
      </Card>

      {/* ── Asset Import ────────────────────────────────────────────────── */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            {t('admin.exportImport.importAssetsLabel', 'Import Assets (ZIP)')}
          </Typography>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
            <input
              ref={zipFileRef}
              type="file"
              accept=".zip"
              style={{ display: 'none' }}
              onChange={handleAssetImportFileChange}
            />
            <Button variant="outlined" onClick={() => zipFileRef.current?.click()}>
              {t('admin.exportImport.importAssetsChooseLabel', 'Choose ZIP File')}
            </Button>
            {assetImportFile && (
              <Typography variant="body2" color="text.secondary">
                {assetImportFile.name}
              </Typography>
            )}
            <Button
              variant="contained"
              color="warning"
              startIcon={<UploadIcon />}
              disabled={!assetImportFile || assetImportLoading}
              onClick={() => setAssetImportDialogOpen(true)}
            >
              {t('admin.exportImport.importAssetsButton', 'Import Assets')}
            </Button>
          </Box>

          {assetImportResult && (
            <Alert severity="success" sx={{ mt: 2 }}>
              {assetImportResult.importedCount} assets imported to {assetImportResult.s3Prefix}
            </Alert>
          )}
          {assetImportError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {assetImportError}
            </Alert>
          )}
        </CardContent>
      </Card>

      {/* ── JSON Import Confirmation Dialog ──────────────────────────────── */}
      <Dialog open={importDialogOpen} onClose={() => setImportDialogOpen(false)}>
        <DialogTitle>{t('admin.exportImport.confirmTitle', 'Confirm Import')}</DialogTitle>
        <DialogContent>
          <Typography>
            {t(
              'admin.exportImport.confirmMessage',
              'This will upsert data. Existing records will be updated. This cannot be undone automatically.'
            )}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setImportDialogOpen(false)}>{t('common.cancel', 'Cancel')}</Button>
          <Button onClick={handleImportConfirm} color="warning" variant="contained">
            {t('admin.exportImport.confirmButton', 'Proceed')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Bundle Import Confirmation Dialog ───────────────────────────── */}
      <Dialog open={bundleImportDialogOpen} onClose={() => setBundleImportDialogOpen(false)}>
        <DialogTitle>
          {t('admin.exportImport.confirmBundleTitle', 'Confirm Bundle Import')}
        </DialogTitle>
        <DialogContent>
          <Typography>
            {t(
              'admin.exportImport.confirmBundleMessage',
              'This will restore all data and binary assets from the bundle. Existing records will be updated. This cannot be undone automatically.'
            )}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBundleImportDialogOpen(false)}>
            {t('common.cancel', 'Cancel')}
          </Button>
          <Button onClick={handleBundleImportConfirm} color="warning" variant="contained">
            {t('admin.exportImport.confirmButton', 'Proceed')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Asset Import Confirmation Dialog (AC6) ───────────────────────── */}
      <Dialog open={assetImportDialogOpen} onClose={() => setAssetImportDialogOpen(false)}>
        <DialogTitle>
          {t('admin.exportImport.confirmAssetsTitle', 'Confirm Asset Import')}
        </DialogTitle>
        <DialogContent>
          <Typography>
            {t(
              'admin.exportImport.confirmAssetsMessage',
              'This will upload all assets from the ZIP file to S3. This cannot be undone automatically.'
            )}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAssetImportDialogOpen(false)}>
            {t('common.cancel', 'Cancel')}
          </Button>
          <Button onClick={handleAssetImportConfirm} color="warning" variant="contained">
            {t('admin.exportImport.confirmButton', 'Proceed')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
