/**
 * EventTypeConfigurationForm Component (Story 5.1 - Task 3b)
 *
 * Form for editing event type configurations (ORGANIZER only)
 * Features:
 * - All configuration fields (minSlots, maxSlots, slotDuration, etc.)
 * - Validation (minSlots <= maxSlots, slotDuration >= 15)
 * - Save and Cancel actions
 * - i18n compliance (all text uses react-i18next)
 * - Generated types from OpenAPI spec (ADR-006)
 */

import React, { useState, useEffect } from 'react';
import {
  TextField,
  Button,
  Stack,
  FormControlLabel,
  Switch,
  Alert,
  Divider,
  Typography,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useEventType } from '@/hooks/useEventTypes';
import { SchedulePreview } from './SchedulePreview';
import type { components } from '@/types/generated/events-api.types';

// Import generated types from OpenAPI spec (ADR-006 compliance)
type EventType = components['schemas']['EventType'];
type UpdateEventSlotConfigurationRequest =
  components['schemas']['UpdateEventSlotConfigurationRequest'];

interface EventTypeConfigurationFormProps {
  eventType?: EventType;
  onSave: (config: UpdateEventSlotConfigurationRequest) => Promise<void>;
  onCancel: () => void;
}

type FormData = UpdateEventSlotConfigurationRequest;

interface ValidationErrors {
  minSlots?: string;
  maxSlots?: string;
  slotDuration?: string;
  defaultCapacity?: string;
  general?: string;
}

export const EventTypeConfigurationForm: React.FC<EventTypeConfigurationFormProps> = ({
  eventType,
  onSave,
  onCancel,
}) => {
  const { t } = useTranslation('events');

  // Fetch current configuration for the event type
  const { data: currentConfig, isLoading } = useEventType(eventType!);

  const [formData, setFormData] = useState<FormData>({
    minSlots: 6,
    maxSlots: 8,
    slotDuration: 45,
    theoreticalSlotsAM: true,
    breakSlots: 1,
    lunchSlots: 0,
    defaultCapacity: 200,
    moderationStartDuration: 5,
    moderationEndDuration: 5,
    breakDuration: 20,
    lunchDuration: 60,
  });

  const [errors, setErrors] = useState<ValidationErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Populate form with current configuration when loaded
  useEffect(() => {
    if (currentConfig) {
      setFormData({
        minSlots: currentConfig.minSlots,
        maxSlots: currentConfig.maxSlots,
        slotDuration: currentConfig.slotDuration,
        theoreticalSlotsAM: currentConfig.theoreticalSlotsAM,
        breakSlots: currentConfig.breakSlots,
        lunchSlots: currentConfig.lunchSlots,
        defaultCapacity: currentConfig.defaultCapacity,
        typicalStartTime: currentConfig.typicalStartTime ?? undefined,
        typicalEndTime: currentConfig.typicalEndTime ?? undefined,
        moderationStartDuration: currentConfig.moderationStartDuration ?? 5,
        moderationEndDuration: currentConfig.moderationEndDuration ?? 5,
        breakDuration: currentConfig.breakDuration ?? 20,
        lunchDuration: currentConfig.lunchDuration ?? 60,
      });
    }
  }, [currentConfig]);

  const validateForm = (): boolean => {
    const newErrors: ValidationErrors = {};

    // Validate minSlots
    if (!formData.minSlots || formData.minSlots < 1) {
      newErrors.minSlots = t('validation.minSlotsRequired');
    }

    // Validate maxSlots
    if (!formData.maxSlots || formData.maxSlots < 1) {
      newErrors.maxSlots = t('validation.maxSlotsRequired');
    }

    // Validate minSlots <= maxSlots
    if (formData.minSlots && formData.maxSlots && formData.minSlots > formData.maxSlots) {
      newErrors.general = t('validation.minSlotsLessThanMax');
    }

    // Validate slotDuration >= 15
    if (!formData.slotDuration || formData.slotDuration < 15) {
      newErrors.slotDuration = t('validation.slotDurationMinimum');
    }

    // Validate defaultCapacity
    if (!formData.defaultCapacity || formData.defaultCapacity < 1) {
      newErrors.defaultCapacity = t('validation.defaultCapacityPositive');
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    setIsSubmitting(true);
    try {
      await onSave(formData);
    } catch {
      setErrors({ general: t('form.eventTypeConfig.saveFailed') });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleChange = (field: keyof FormData, value: number | boolean) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    // Clear field-specific errors on change
    if (errors[field as keyof ValidationErrors]) {
      setErrors((prev) => {
        const newErrors = { ...prev };
        delete newErrors[field as keyof ValidationErrors];
        return newErrors;
      });
    }
  };

  // Show loading state while fetching current configuration
  if (isLoading) {
    return (
      <Stack spacing={3}>
        <Alert severity="info">{t('common:loading')}</Alert>
      </Stack>
    );
  }

  return (
    <form onSubmit={handleSubmit}>
      <Stack spacing={3}>
        {errors.general && (
          <Alert severity="error" data-testid="form-error">
            {errors.general}
          </Alert>
        )}

        {/* Row 1: Min Slots | Max Slots | Slot Duration */}
        <Stack direction="row" spacing={2}>
          <TextField
            label={t('form.eventTypeConfig.minSlots')}
            type="number"
            value={formData.minSlots}
            onChange={(e) => handleChange('minSlots', parseInt(e.target.value, 10))}
            error={!!errors.minSlots}
            helperText={errors.minSlots}
            fullWidth
            required
            inputProps={{ min: 1 }}
          />
          <TextField
            label={t('form.eventTypeConfig.maxSlots')}
            type="number"
            value={formData.maxSlots}
            onChange={(e) => handleChange('maxSlots', parseInt(e.target.value, 10))}
            error={!!errors.maxSlots}
            helperText={errors.maxSlots}
            fullWidth
            required
            inputProps={{ min: 1 }}
          />
          <TextField
            label={t('form.eventTypeConfig.slotDuration')}
            type="number"
            value={formData.slotDuration}
            onChange={(e) => handleChange('slotDuration', parseInt(e.target.value, 10))}
            error={!!errors.slotDuration}
            helperText={errors.slotDuration}
            fullWidth
            required
            inputProps={{ min: 15 }}
          />
        </Stack>

        <FormControlLabel
          control={
            <Switch
              checked={formData.theoreticalSlotsAM}
              onChange={(e) => handleChange('theoreticalSlotsAM', e.target.checked)}
            />
          }
          label={t('form.eventTypeConfig.theoreticalSlotsAM')}
        />

        {/* Row 2: Break Slots | Lunch Slots | Capacity */}
        <Stack direction="row" spacing={2}>
          <TextField
            label={t('form.eventTypeConfig.breakSlots')}
            type="number"
            value={formData.breakSlots}
            onChange={(e) => handleChange('breakSlots', parseInt(e.target.value, 10))}
            fullWidth
            inputProps={{ min: 0 }}
          />
          <TextField
            label={t('form.eventTypeConfig.lunchSlots')}
            type="number"
            value={formData.lunchSlots}
            onChange={(e) => handleChange('lunchSlots', parseInt(e.target.value, 10))}
            fullWidth
            inputProps={{ min: 0 }}
          />
          <TextField
            label={t('form.eventTypeConfig.defaultCapacity')}
            type="number"
            value={formData.defaultCapacity}
            onChange={(e) => handleChange('defaultCapacity', parseInt(e.target.value, 10))}
            error={!!errors.defaultCapacity}
            helperText={errors.defaultCapacity}
            fullWidth
            required
            inputProps={{ min: 1 }}
          />
        </Stack>

        <Divider />

        <Typography variant="subtitle2" color="text.secondary">
          {t('form.eventTypeConfig.structuralDurations')}
        </Typography>

        {/* Row 3: Moderation Start | Moderation End */}
        <Stack direction="row" spacing={2}>
          <TextField
            label={t('form.eventTypeConfig.moderationStartDuration')}
            type="number"
            value={formData.moderationStartDuration ?? 5}
            onChange={(e) => handleChange('moderationStartDuration', parseInt(e.target.value, 10))}
            fullWidth
            inputProps={{ min: 1 }}
          />
          <TextField
            label={t('form.eventTypeConfig.moderationEndDuration')}
            type="number"
            value={formData.moderationEndDuration ?? 5}
            onChange={(e) => handleChange('moderationEndDuration', parseInt(e.target.value, 10))}
            fullWidth
            inputProps={{ min: 1 }}
          />
        </Stack>

        {/* Row 4: Break Duration | Lunch Duration */}
        <Stack direction="row" spacing={2}>
          <TextField
            label={t('form.eventTypeConfig.breakDuration')}
            type="number"
            value={formData.breakDuration ?? 20}
            onChange={(e) => handleChange('breakDuration', parseInt(e.target.value, 10))}
            fullWidth
            inputProps={{ min: 1 }}
          />
          <TextField
            label={t('form.eventTypeConfig.lunchDuration')}
            type="number"
            value={formData.lunchDuration ?? 60}
            onChange={(e) => handleChange('lunchDuration', parseInt(e.target.value, 10))}
            fullWidth
            inputProps={{ min: 1 }}
          />
        </Stack>

        <Divider />

        <SchedulePreview config={formData} />

        <Stack direction="row" spacing={2} justifyContent="flex-end">
          <Button onClick={onCancel} disabled={isSubmitting}>
            {t('common:actions.cancel')}
          </Button>
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            {t('common:actions.save')}
          </Button>
        </Stack>
      </Stack>
    </form>
  );
};
