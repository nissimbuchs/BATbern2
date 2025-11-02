/**
 * VenueLogistics Component (Task 11b - GREEN Phase - SIMPLIFIED)
 *
 * Story 2.5.3 - AC6: Venue & Logistics Management (SIMPLIFIED)
 * Wireframe: docs/wireframes/story-1.16-event-detail-edit.md v1.1
 *
 * SIMPLIFIED SCOPE (matches actual backend Event.java):
 * - Display/edit 3 inline venue fields: venueName, venueAddress, venueCapacity
 * - NO separate Venue entity, NO booking status, NO catering model
 * - Catering moved to quality checkpoints (separate component)
 *
 * Features:
 * - Inline editing with debounced auto-save
 * - Validation (required fields, capacity range)
 * - Responsive design (mobile-first)
 * - Accessibility (WCAG 2.1 AA)
 * - i18n (German/English)
 * - Error handling with user-friendly messages
 */

import React, { useState, useEffect } from 'react';
import { Box, TextField, Typography, Alert, CircularProgress, Paper } from '@mui/material';
import Grid from '@mui/material/Grid';
import { useTranslation } from 'react-i18next';
import { useDebounce } from '@/hooks/useDebounce';
import type { EventDetail } from '@/types/event.types';

interface VenueLogisticsProps {
  event: EventDetail;
  onUpdate: (updates: Partial<EventDetail>) => Promise<void>;
}

interface VenueFormData {
  venueName: string;
  venueAddress: string;
  venueCapacity: number;
}

interface ValidationErrors {
  venueName?: string;
  venueAddress?: string;
  venueCapacity?: string;
}

export const VenueLogistics: React.FC<VenueLogisticsProps> = ({ event, onUpdate }) => {
  const { t } = useTranslation('events');

  // Local state for form values
  const [formData, setFormData] = useState<VenueFormData>({
    venueName: event.venueName || '',
    venueAddress: event.venueAddress || '',
    venueCapacity: event.venueCapacity || 0,
  });

  // Track original values for revert on error
  const [originalValues, setOriginalValues] = useState<VenueFormData>(formData);

  // Validation errors
  const [errors, setErrors] = useState<ValidationErrors>({});

  // Loading and error states
  const [isUpdating, setIsUpdating] = useState(false);
  const [updateError, setUpdateError] = useState<string | null>(null);

  // Debounced form data for auto-save
  const debouncedFormData = useDebounce(formData, 1000);

  // Sync form data when event prop changes
  useEffect(() => {
    const newData = {
      venueName: event.venueName || '',
      venueAddress: event.venueAddress || '',
      venueCapacity: event.venueCapacity || 0,
    };
    setFormData(newData);
    setOriginalValues(newData);
  }, [event]);

  // Auto-save when debounced data changes
  useEffect(() => {
    // Skip auto-save if data hasn't changed from original
    if (
      debouncedFormData.venueName === originalValues.venueName &&
      debouncedFormData.venueAddress === originalValues.venueAddress &&
      debouncedFormData.venueCapacity === originalValues.venueCapacity
    ) {
      return;
    }

    // Validate before saving
    const validationErrors = validateForm(debouncedFormData);
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    // Clear errors and save
    setErrors({});
    handleAutoSave(debouncedFormData);
  }, [debouncedFormData]);

  // Validation logic
  const validateForm = (data: VenueFormData): ValidationErrors => {
    const errors: ValidationErrors = {};

    if (!data.venueName.trim()) {
      errors.venueName = t('events.venue.errors.nameRequired', 'Venue name is required');
    }

    if (!data.venueAddress.trim()) {
      errors.venueAddress = t('events.venue.errors.addressRequired', 'Venue address is required');
    }

    if (data.venueCapacity < 1) {
      errors.venueCapacity = t(
        'events.venue.errors.capacityPositive',
        'Capacity must be a positive number'
      );
    }

    if (data.venueCapacity > 5000) {
      errors.venueCapacity = t('events.venue.errors.capacityMax', 'Capacity cannot exceed 5000');
    }

    return errors;
  };

  // Auto-save handler
  const handleAutoSave = async (data: VenueFormData) => {
    setIsUpdating(true);
    setUpdateError(null);

    try {
      await onUpdate({
        venueName: data.venueName,
        venueAddress: data.venueAddress,
        venueCapacity: data.venueCapacity,
      });

      // Update original values on success
      setOriginalValues(data);
    } catch (error) {
      console.error('Failed to update venue:', error);
      setUpdateError(t('events.venue.errors.updateFailed', 'Failed to update venue'));

      // Revert to original values
      setFormData(originalValues);
    } finally {
      setIsUpdating(false);
    }
  };

  // Field change handlers with immediate validation
  const handleVenueNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    setFormData((prev) => ({ ...prev, venueName: newValue }));

    // Immediate validation feedback
    if (!newValue.trim()) {
      setErrors((prev) => ({
        ...prev,
        venueName: t('events.venue.errors.nameRequired', 'Venue name is required'),
      }));
    } else {
      setErrors((prev) => ({ ...prev, venueName: undefined }));
    }
  };

  const handleVenueAddressChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const newValue = e.target.value;
    setFormData((prev) => ({ ...prev, venueAddress: newValue }));

    // Immediate validation feedback
    if (!newValue.trim()) {
      setErrors((prev) => ({
        ...prev,
        venueAddress: t('events.venue.errors.addressRequired', 'Venue address is required'),
      }));
    } else {
      setErrors((prev) => ({ ...prev, venueAddress: undefined }));
    }
  };

  const handleVenueCapacityChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = parseInt(e.target.value, 10) || 0;
    setFormData((prev) => ({ ...prev, venueCapacity: value }));

    // Immediate validation feedback
    if (value < 1) {
      setErrors((prev) => ({
        ...prev,
        venueCapacity: t(
          'events.venue.errors.capacityPositive',
          'Capacity must be a positive number'
        ),
      }));
    } else if (value > 5000) {
      setErrors((prev) => ({
        ...prev,
        venueCapacity: t('events.venue.errors.capacityMax', 'Capacity cannot exceed 5000'),
      }));
    } else {
      setErrors((prev) => ({ ...prev, venueCapacity: undefined }));
    }
  };

  return (
    <Box
      component="section"
      role="region"
      aria-label={t('events.venue.sectionTitle', 'Venue and Logistics')}
      sx={{
        display: 'flex',
        flexDirection: { xs: 'column', md: 'row' },
        gap: 2,
        width: '100%',
      }}
    >
      <Paper
        elevation={1}
        sx={{
          p: 3,
          flex: 1,
          position: 'relative',
        }}
      >
        {/* Section Title */}
        <Typography variant="h6" gutterBottom>
          {t('events.venue.title', 'Venue & Logistics')}
        </Typography>

        {/* Update Error */}
        {updateError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {updateError}
          </Alert>
        )}

        {/* Loading Spinner */}
        {isUpdating && (
          <CircularProgress
            size={24}
            role="progressbar"
            sx={{
              position: 'absolute',
              top: 16,
              right: 16,
            }}
          />
        )}

        {/* Venue Form Fields */}
        <Grid container spacing={2}>
          {/* Venue Name */}
          <Grid size={{ xs: 12, md: 6 }}>
            <TextField
              fullWidth
              label={t('events.venue.nameLabel', 'Venue Name')}
              value={formData.venueName}
              onChange={handleVenueNameChange}
              disabled={isUpdating}
              error={!!errors.venueName}
              helperText={errors.venueName}
              inputProps={{
                'aria-label': t('events.venue.nameLabel', 'Venue Name'),
              }}
              FormHelperTextProps={{
                role: errors.venueName ? 'alert' : undefined,
              }}
            />
          </Grid>

          {/* Venue Capacity */}
          <Grid size={{ xs: 12, md: 6 }}>
            <TextField
              fullWidth
              type="number"
              label={t('events.venue.capacityLabel', 'Venue Capacity')}
              value={formData.venueCapacity}
              onChange={handleVenueCapacityChange}
              disabled={isUpdating}
              error={!!errors.venueCapacity}
              helperText={errors.venueCapacity}
              inputProps={{
                'aria-label': t('events.venue.capacityLabel', 'Venue Capacity'),
                min: 1,
                max: 5000,
              }}
              FormHelperTextProps={{
                role: errors.venueCapacity ? 'alert' : undefined,
              }}
            />
          </Grid>

          {/* Venue Address */}
          <Grid size={{ xs: 12 }}>
            <TextField
              fullWidth
              multiline
              rows={3}
              label={t('events.venue.addressLabel', 'Venue Address')}
              value={formData.venueAddress}
              onChange={handleVenueAddressChange}
              disabled={isUpdating}
              error={!!errors.venueAddress}
              helperText={errors.venueAddress}
              inputProps={{
                'aria-label': t('events.venue.addressLabel', 'Venue Address'),
              }}
              FormHelperTextProps={{
                role: errors.venueAddress ? 'alert' : undefined,
              }}
            />
          </Grid>
        </Grid>
      </Paper>
    </Box>
  );
};
