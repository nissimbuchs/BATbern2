/**
 * CompanyForm Component
 *
 * Modal form for creating and editing companies
 * - Create and edit modes with pre-filled data
 * - Form validation (Swiss UID, required fields, URL, character limits)
 * - Duplicate name detection from backend
 * - Unsaved changes warning
 * - Draft save support for create mode
 * - Role-based access control
 *
 * Story: 2.5.1 - Company Management Frontend
 * Acceptance Criteria: AC3, AC4
 */

import React, { useEffect, useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  FormHelperText,
  Alert,
  Box,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { Company, CreateCompanyRequest, UpdateCompanyRequest } from '@/types/company.types';

// Validation schema with Zod (matches backend CreateCompanyRequest)
const companySchema = z.object({
  name: z
    .string()
    .min(1, 'Company name is required')
    .min(2, 'Name must be at least 2 characters')
    .max(255, 'Name must be at most 255 characters'),
  displayName: z.string().max(255, 'Display name must be at most 255 characters').optional().or(z.literal('')),
  swissUID: z
    .string()
    .optional()
    .or(z.literal(''))
    .refine((val) => !val || /^CHE-\d{3}\.\d{3}\.\d{3}$/.test(val), {
      message: 'Invalid Swiss UID format (CHE-XXX.XXX.XXX)',
    }),
  website: z
    .string()
    .optional()
    .or(z.literal(''))
    .refine((val) => !val || z.string().url().safeParse(val).success, {
      message: 'Invalid website URL',
    }),
  industry: z.string().max(100, 'Industry must be at most 100 characters').optional().or(z.literal('')),
  description: z.string().max(5000, 'Description must be at most 5000 characters').optional().or(z.literal('')),
});

// Draft schema (relaxed validation)

type CompanyFormData = z.infer<typeof companySchema>;

interface CompanyFormProps {
  open: boolean;
  mode: 'create' | 'edit';
  initialData?: Company;
  onClose: () => void;
  onSubmit: (
    data: CreateCompanyRequest | UpdateCompanyRequest,
    options?: { isDraft?: boolean; isPartialUpdate?: boolean; changedFields?: string[] }
  ) => void | Promise<void>;
  allowDraft?: boolean;
  userRole?: 'organizer' | 'speaker';
  userCompanyId?: string;
}

export const CompanyForm: React.FC<CompanyFormProps> = ({
  open,
  mode,
  initialData,
  onClose,
  onSubmit,
  allowDraft = false,
  userRole = 'organizer',
  userCompanyId,
}) => {
  const { t } = useTranslation('common');
  const [apiError, setApiError] = useState<string | null>(null);

  // Check role-based access control
  const hasEditPermission =
    mode === 'create' ||
    userRole === 'organizer' ||
    (userRole === 'speaker' && initialData?.id === userCompanyId);

  const {
    control,
    handleSubmit,
    formState: { errors, isDirty },
    watch,
    reset,
  } = useForm<CompanyFormData>({
    resolver: zodResolver(companySchema) as any,
    mode: 'onBlur', // Validate on blur for immediate feedback
    defaultValues: initialData
      ? {
          name: initialData.name,
          displayName: initialData.displayName || '',
          swissUID: initialData.swissUID || '',
          website: initialData.website || '',
          industry: initialData.industry || '',
          description: initialData.description || '',
        }
      : {
          name: '',
          displayName: '',
          swissUID: '',
          website: '',
          industry: '',
          description: '',
        },
  });

  // Reset form when initialData changes
  useEffect(() => {
    if (initialData) {
      reset({
        name: initialData.name,
        displayName: initialData.displayName || '',
        swissUID: initialData.swissUID || '',
        website: initialData.website || '',
        industry: initialData.industry || '',
        description: initialData.description || '',
      });
    }
  }, [initialData, reset]);

  const handleClose = () => {
    if (isDirty) {
      const confirmed = window.confirm(
        t('company.form.unsavedChanges')
      );
      if (!confirmed) return;
    }
    setApiError(null);
    onClose();
  };

  const handleFormSubmit = async (data: CompanyFormData) => {
    setApiError(null);

    try {
      // Clean up empty strings for optional fields
      const cleanedData = {
        ...data,
        displayName: data.displayName || undefined,
        swissUID: data.swissUID || undefined,
        website: data.website || undefined,
        industry: data.industry || undefined,
        description: data.description || undefined,
      };

      if (mode === 'edit' && initialData) {
        // Calculate changed fields for partial update
        const changedFields: string[] = [];
        Object.keys(cleanedData).forEach((key) => {
          const typedKey = key as keyof typeof cleanedData;
          if (cleanedData[typedKey] !== initialData[typedKey as keyof Company]) {
            changedFields.push(key);
          }
        });

        // Only send changed fields
        const partialUpdate: Partial<CreateCompanyRequest> = {};
        changedFields.forEach((field) => {
          partialUpdate[field as keyof CreateCompanyRequest] = cleanedData[field as keyof typeof cleanedData] as any;
        });

        await onSubmit(partialUpdate as UpdateCompanyRequest, {
          isPartialUpdate: true,
          changedFields,
        });
      } else {
        // Create mode
        await onSubmit(cleanedData as CreateCompanyRequest, {
          isDraft: false,
        });
      }

      onClose();
    } catch (error: any) {
      // Handle API errors (e.g., duplicate company name)
      if (error?.response?.data?.message) {
        setApiError(error.response.data.message);
      } else {
        setApiError(t('company.errors.saveFailed'));
      }
    }
  };

  const handleSaveDraft = () => {
    // Directly call form submission with draft flag
    const formData = watch(); // Get current form values

    setApiError(null);

    try {
      const cleanedData = {
        ...formData,
        displayName: formData.displayName || undefined,
        swissUID: formData.swissUID || undefined,
        website: formData.website || undefined,
        industry: formData.industry || undefined,
        description: formData.description || undefined,
      };

      onSubmit(cleanedData as CreateCompanyRequest, {
        isDraft: true,
      });

      onClose();
    } catch (error: any) {
      if (error?.response?.data?.message) {
        setApiError(error.response.data.message);
      } else {
        setApiError(t('company.errors.saveFailed'));
      }
    }
  };

  const handleSaveCreate = async () => {
    await handleSubmit(async (data) => {
      await handleFormSubmit(data);
    })();
  };

  // Watch description for character count
  const description = watch('description') || '';

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="md"
      fullWidth
      aria-labelledby="company-form-title"
      aria-describedby="company-form-description"
    >
      <DialogTitle id="company-form-title">
        {mode === 'create' ? t('company.form.createTitle') : t('company.form.editTitle')}
      </DialogTitle>

      <DialogContent id="company-form-description">
        {!hasEditPermission && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {t('company.form.errors.noPermission')}
          </Alert>
        )}

        {apiError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {apiError}
          </Alert>
        )}

        <Box component="form" sx={{ mt: 2 }}>
          {/* Company Name - Required */}
          <Controller
            name="name"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label={`${t('company.fields.name')} *`}
                fullWidth
                margin="normal"
                error={!!errors.name}
                helperText={errors.name?.message}
                disabled={!hasEditPermission}
                inputProps={{ 'aria-label': t('company.fields.name') }}
              />
            )}
          />

          {/* Display Name - Optional */}
          <Controller
            name="displayName"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label={t('company.fields.displayName')}
                fullWidth
                margin="normal"
                error={!!errors.displayName}
                helperText={errors.displayName?.message}
                disabled={!hasEditPermission}
                inputProps={{ 'aria-label': t('company.fields.displayName') }}
              />
            )}
          />

          {/* Swiss UID - Optional */}
          <Controller
            name="swissUID"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label={t('company.fields.swissUID')}
                fullWidth
                margin="normal"
                error={!!errors.swissUID}
                helperText={errors.swissUID?.message || t('company.form.helpText.swissUID')}
                disabled={!hasEditPermission}
                placeholder={t('company.placeholders.swissUID')}
                inputProps={{ 'aria-label': t('company.fields.swissUID') }}
              />
            )}
          />

          {/* Website - Optional */}
          <Controller
            name="website"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label={t('company.fields.website')}
                fullWidth
                margin="normal"
                error={!!errors.website}
                helperText={errors.website?.message}
                disabled={!hasEditPermission}
                placeholder={t('company.placeholders.website')}
                inputProps={{ 'aria-label': t('company.fields.website') }}
              />
            )}
          />

          {/* Industry - Optional */}
          <Controller
            name="industry"
            control={control}
            render={({ field }) => (
              <FormControl fullWidth margin="normal" error={!!errors.industry} disabled={!hasEditPermission}>
                <InputLabel id="industry-label">{t('company.fields.industry')}</InputLabel>
                <Select
                  {...field}
                  labelId="industry-label"
                  label={t('company.fields.industry')}
                  aria-label={t('company.fields.industry')}
                >
                  <MenuItem value="">
                    <em>{t('company.industries.selectIndustry')}</em>
                  </MenuItem>
                  <MenuItem value="Technology">{t('company.industries.technology')}</MenuItem>
                  <MenuItem value="Cloud Computing">{t('company.industries.cloudComputing')}</MenuItem>
                  <MenuItem value="DevOps">{t('company.industries.devOps')}</MenuItem>
                  <MenuItem value="Financial Services">{t('company.industries.financialServices')}</MenuItem>
                  <MenuItem value="Healthcare">{t('company.industries.healthcare')}</MenuItem>
                  <MenuItem value="Manufacturing">{t('company.industries.manufacturing')}</MenuItem>
                  <MenuItem value="Consulting">{t('company.industries.consulting')}</MenuItem>
                  <MenuItem value="Education">{t('company.industries.education')}</MenuItem>
                  <MenuItem value="Other">{t('company.industries.other')}</MenuItem>
                </Select>
                {errors.industry && <FormHelperText>{errors.industry.message}</FormHelperText>}
              </FormControl>
            )}
          />

          {/* Description - Optional */}
          <Controller
            name="description"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label={t('company.fields.description')}
                fullWidth
                margin="normal"
                multiline
                rows={4}
                error={!!errors.description}
                helperText={
                  errors.description?.message || t('company.form.characterCount', { count: description.length, max: 5000 })
                }
                disabled={!hasEditPermission}
                inputProps={{ 'aria-label': t('company.fields.description') }}
              />
            )}
          />
        </Box>
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose} aria-label={t('actions.cancel')}>
          {t('actions.cancel')}
        </Button>

        {mode === 'create' && allowDraft && hasEditPermission && (
          <Button onClick={handleSaveDraft} color="secondary" aria-label={t('company.form.saveDraft')}>
            {t('company.form.saveDraft')}
          </Button>
        )}

        <Button
          onClick={handleSaveCreate}
          variant="contained"
          color="primary"
          disabled={!hasEditPermission}
          aria-label={mode === 'create' ? t('company.form.saveCreate') : t('company.form.saveChanges')}
        >
          {mode === 'create' ? t('company.form.saveCreate') : t('company.form.saveChanges')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
