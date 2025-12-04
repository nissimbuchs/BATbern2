import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  IconButton,
  Alert,
} from '@mui/material';
import { Close as CloseIcon, Warning as WarningIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useDeleteCompany } from '@/hooks/useCompanyMutations/useCompanyMutations';
import type { components } from '@/types/generated/company-api.types';

type CompanyDetail = components['schemas']['CompanyResponse'];

interface DeleteCompanyDialogProps {
  company: CompanyDetail | null;
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
}

const DeleteCompanyDialog: React.FC<DeleteCompanyDialogProps> = ({
  company,
  open,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslation('common');
  const deleteCompanyMutation = useDeleteCompany();

  const handleDelete = async () => {
    if (!company) return;

    try {
      await deleteCompanyMutation.mutateAsync(company.name);
      onSuccess?.();
      onClose();
    } catch (error) {
      console.error('Failed to delete company:', error);
      // Error is handled by React Query mutation error state
    }
  };

  if (!company) return null;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      aria-labelledby="delete-company-dialog-title"
    >
      <DialogTitle id="delete-company-dialog-title">
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Box display="flex" alignItems="center" gap={1}>
            <WarningIcon color="error" />
            <Typography variant="h6">{t('company.delete.title')}</Typography>
          </Box>
          <IconButton edge="end" color="inherit" onClick={onClose} aria-label="close">
            <CloseIcon />
          </IconButton>
        </Box>
      </DialogTitle>

      <DialogContent dividers>
        {/* Company Information */}
        <Box sx={{ mb: 3 }}>
          <Typography variant="body1" gutterBottom>
            {t('company.delete.message')}
          </Typography>
          <Typography variant="body1" sx={{ mt: 2 }}>
            <strong>{company.displayName || company.name}</strong>
          </Typography>
          {company.industry && (
            <Typography variant="body2" color="text.secondary">
              {company.industry}
            </Typography>
          )}
          {company.website && (
            <Typography variant="body2" color="text.secondary">
              {company.website}
            </Typography>
          )}
        </Box>

        {/* Data Warning */}
        <Alert severity="error" sx={{ mb: 2 }}>
          <Typography variant="body2" fontWeight="bold">
            {t('company.delete.dataWarning')}
          </Typography>
        </Alert>

        {/* Cascade Warning */}
        <Alert severity="warning">
          <Typography variant="body2">{t('company.delete.cascadeWarning')}</Typography>
        </Alert>

        {/* Mutation Error */}
        {deleteCompanyMutation.isError && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {t('company.errors.deleteFailed')}
          </Alert>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} variant="outlined">
          {t('actions.cancel')}
        </Button>
        <Button
          onClick={handleDelete}
          variant="contained"
          color="error"
          disabled={deleteCompanyMutation.isPending}
        >
          {deleteCompanyMutation.isPending ? t('company.delete.deleting') : t('actions.delete')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default DeleteCompanyDialog;
