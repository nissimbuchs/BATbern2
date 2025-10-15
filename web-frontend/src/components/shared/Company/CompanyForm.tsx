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
import type { Company, CreateCompanyRequest, UpdateCompanyRequest } from '@/types/company.types';

// Validation schema with Zod
const companySchema = z.object({
  name: z
    .string()
    .min(1, 'Company name is required')
    .min(2, 'Name must be at least 2 characters')
    .max(200, 'Name must be at most 200 characters'),
  displayName: z.string().max(200, 'Display name must be at most 200 characters').optional().or(z.literal('')),
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
  industry: z.string().min(1, 'Industry is required'),
  sector: z.enum(['Public', 'Private', 'Non-profit', 'Government', '']).optional(),
  location: z.object({
    city: z.string().min(1, 'City is required'),
    canton: z.string().min(1, 'Canton is required'),
    country: z.string().min(1, 'Country is required'),
  }),
  description: z.string().max(500, 'Description must be at most 500 characters').optional().or(z.literal('')),
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
          industry: initialData.industry,
          sector: initialData.sector || '',
          location: {
            city: initialData.location.city,
            canton: initialData.location.canton,
            country: initialData.location.country,
          },
          description: initialData.description || '',
        }
      : {
          name: '',
          displayName: '',
          swissUID: '',
          website: '',
          industry: '',
          sector: '',
          location: {
            city: '',
            canton: '',
            country: '',
          },
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
        industry: initialData.industry,
        sector: initialData.sector || '',
        location: {
          city: initialData.location.city,
          canton: initialData.location.canton,
          country: initialData.location.country,
        },
        description: initialData.description || '',
      });
    }
  }, [initialData, reset]);

  const handleClose = () => {
    if (isDirty) {
      const confirmed = window.confirm(
        'You have unsaved changes. Are you sure you want to close?'
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
      const validSectors: Array<'Public' | 'Private' | 'Non-profit' | 'Government'> = ['Public', 'Private', 'Non-profit', 'Government'];
      const cleanedData = {
        ...data,
        displayName: data.displayName || undefined,
        swissUID: data.swissUID || undefined,
        website: data.website || undefined,
        sector: (data.sector && validSectors.includes(data.sector as any)) ? data.sector as 'Public' | 'Private' | 'Non-profit' | 'Government' : undefined,
        description: data.description || undefined,
      };

      if (mode === 'edit' && initialData) {
        // Calculate changed fields for partial update
        const changedFields: string[] = [];
        Object.keys(cleanedData).forEach((key) => {
          const typedKey = key as keyof typeof cleanedData;
          if (key === 'location') {
            if (JSON.stringify(cleanedData.location) !== JSON.stringify(initialData.location)) {
              changedFields.push('location');
            }
          } else if (cleanedData[typedKey] !== initialData[typedKey as keyof Company]) {
            changedFields.push(key);
          }
        });

        // Only send changed fields
        const partialUpdate: Partial<CreateCompanyRequest> = {};
        changedFields.forEach((field) => {
          if (field === 'location') {
            partialUpdate.location = cleanedData.location;
          } else {
            partialUpdate[field as keyof CreateCompanyRequest] = cleanedData[field as keyof typeof cleanedData] as any;
          }
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
        setApiError('An error occurred while saving the company');
      }
    }
  };

  const handleSaveDraft = () => {
    // Directly call form submission with draft flag
    const formData = watch(); // Get current form values

    setApiError(null);

    try {
      const validSectors: Array<'Public' | 'Private' | 'Non-profit' | 'Government'> = ['Public', 'Private', 'Non-profit', 'Government'];
      const cleanedData = {
        ...formData,
        displayName: formData.displayName || undefined,
        swissUID: formData.swissUID || undefined,
        website: formData.website || undefined,
        sector: (formData.sector && validSectors.includes(formData.sector as any)) ? formData.sector as 'Public' | 'Private' | 'Non-profit' | 'Government' : undefined,
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
        setApiError('An error occurred while saving the company');
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
        {mode === 'create' ? 'Create New Company' : 'Edit Company'}
      </DialogTitle>

      <DialogContent id="company-form-description">
        {!hasEditPermission && (
          <Alert severity="error" sx={{ mb: 2 }}>
            You don't have permission to edit this company. Speakers can only edit their own company.
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
                label="Company Name *"
                fullWidth
                margin="normal"
                error={!!errors.name}
                helperText={errors.name?.message}
                disabled={!hasEditPermission}
                inputProps={{ 'aria-label': 'Company Name' }}
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
                label="Display Name (if different from legal name)"
                fullWidth
                margin="normal"
                error={!!errors.displayName}
                helperText={errors.displayName?.message}
                disabled={!hasEditPermission}
                inputProps={{ 'aria-label': 'Display Name' }}
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
                label="Swiss UID (CHE-XXX.XXX.XXX)"
                fullWidth
                margin="normal"
                error={!!errors.swissUID}
                helperText={errors.swissUID?.message || 'Optional - for automatic verification'}
                disabled={!hasEditPermission}
                placeholder="CHE-123.456.789"
                inputProps={{ 'aria-label': 'Swiss UID' }}
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
                label="Website"
                fullWidth
                margin="normal"
                error={!!errors.website}
                helperText={errors.website?.message}
                disabled={!hasEditPermission}
                placeholder="https://example.com"
                inputProps={{ 'aria-label': 'Website' }}
              />
            )}
          />

          {/* Industry - Required */}
          <Controller
            name="industry"
            control={control}
            render={({ field }) => (
              <FormControl fullWidth margin="normal" error={!!errors.industry} disabled={!hasEditPermission}>
                <InputLabel id="industry-label">Industry *</InputLabel>
                <Select
                  {...field}
                  labelId="industry-label"
                  label="Industry *"
                  aria-label="Industry"
                >
                  <MenuItem value="">
                    <em>Select Industry</em>
                  </MenuItem>
                  <MenuItem value="Technology">Technology</MenuItem>
                  <MenuItem value="Cloud Computing">Cloud Computing</MenuItem>
                  <MenuItem value="DevOps">DevOps</MenuItem>
                  <MenuItem value="Financial Services">Financial Services</MenuItem>
                  <MenuItem value="Healthcare">Healthcare</MenuItem>
                  <MenuItem value="Manufacturing">Manufacturing</MenuItem>
                  <MenuItem value="Consulting">Consulting</MenuItem>
                  <MenuItem value="Education">Education</MenuItem>
                  <MenuItem value="Other">Other</MenuItem>
                </Select>
                {errors.industry && <FormHelperText>{errors.industry.message}</FormHelperText>}
              </FormControl>
            )}
          />

          {/* Sector - Optional */}
          <Controller
            name="sector"
            control={control}
            render={({ field }) => (
              <FormControl fullWidth margin="normal" disabled={!hasEditPermission}>
                <InputLabel id="sector-label">Sector (Optional)</InputLabel>
                <Select
                  {...field}
                  labelId="sector-label"
                  label="Sector (Optional)"
                  aria-label="Sector"
                >
                  <MenuItem value="">
                    <em>Select Sector</em>
                  </MenuItem>
                  <MenuItem value="Public">Public</MenuItem>
                  <MenuItem value="Private">Private</MenuItem>
                  <MenuItem value="Non-profit">Non-profit</MenuItem>
                  <MenuItem value="Government">Government</MenuItem>
                </Select>
              </FormControl>
            )}
          />

          {/* Location - City (Required) */}
          <Controller
            name="location.city"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label="City *"
                fullWidth
                margin="normal"
                error={!!errors.location?.city}
                helperText={errors.location?.city?.message}
                disabled={!hasEditPermission}
                inputProps={{ 'aria-label': 'City' }}
              />
            )}
          />

          {/* Location - Canton (Required) */}
          <Controller
            name="location.canton"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label="Canton *"
                fullWidth
                margin="normal"
                error={!!errors.location?.canton}
                helperText={errors.location?.canton?.message}
                disabled={!hasEditPermission}
                placeholder="BE, ZH, GE, etc."
                inputProps={{ 'aria-label': 'Canton' }}
              />
            )}
          />

          {/* Location - Country (Required) */}
          <Controller
            name="location.country"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label="Country *"
                fullWidth
                margin="normal"
                error={!!errors.location?.country}
                helperText={errors.location?.country?.message}
                disabled={!hasEditPermission}
                inputProps={{ 'aria-label': 'Country' }}
              />
            )}
          />

          {/* Description - Optional */}
          <Controller
            name="description"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label="Description"
                fullWidth
                margin="normal"
                multiline
                rows={4}
                error={!!errors.description}
                helperText={
                  errors.description?.message || `${description.length}/500 characters`
                }
                disabled={!hasEditPermission}
                inputProps={{ 'aria-label': 'Description' }}
              />
            )}
          />
        </Box>
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose} aria-label="Cancel">
          Cancel
        </Button>

        {mode === 'create' && allowDraft && hasEditPermission && (
          <Button onClick={handleSaveDraft} color="secondary" aria-label="Save Draft">
            Save Draft
          </Button>
        )}

        <Button
          onClick={handleSaveCreate}
          variant="contained"
          color="primary"
          disabled={!hasEditPermission}
          aria-label={mode === 'create' ? 'Save & Create' : 'Save Changes'}
        >
          {mode === 'create' ? 'Save & Create' : 'Save Changes'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
