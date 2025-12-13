/**
 * SlotTemplatePreview Component (Story 5.1 - Task 3b)
 *
 * Preview card showing event type configuration details
 * Features:
 * - Displays slot count range (min-max)
 * - Shows slot duration
 * - Displays default capacity
 * - Shows typical start-end times
 * - Material-UI Card layout
 * - i18n compliance (all text uses react-i18next)
 * - Generated types from OpenAPI spec (ADR-006)
 */

import React from 'react';
import { Card, CardContent, Typography, Chip, Stack } from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { components } from '@/types/generated/events-api.types';

// Import generated types from OpenAPI spec (ADR-006 compliance)
type EventType = components['schemas']['EventType'];
type EventSlotConfigurationResponse = components['schemas']['EventSlotConfigurationResponse'];

interface SlotTemplatePreviewProps {
  eventType: EventType;
  slotConfiguration: EventSlotConfigurationResponse;
}

/**
 * Helper function to format event type names for display
 */
function formatEventTypeName(type: EventType, t: (key: string) => string): string {
  const typeMap: Record<EventType, string> = {
    FULL_DAY: t('form.eventTypes.fullDay'),
    AFTERNOON: t('form.eventTypes.afternoon'),
    EVENING: t('form.eventTypes.evening'),
  };
  return typeMap[type] || type;
}

export const SlotTemplatePreview: React.FC<SlotTemplatePreviewProps> = ({
  eventType,
  slotConfiguration,
}) => {
  const { t } = useTranslation('events');

  const { minSlots, maxSlots, slotDuration, typicalStartTime, typicalEndTime, defaultCapacity } =
    slotConfiguration;

  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="h6" gutterBottom>
          {formatEventTypeName(eventType, t)} {t('slotPreview.title', { type: '' }).trim()}
        </Typography>
        <Stack direction="row" spacing={1} sx={{ mb: 2 }} flexWrap="wrap">
          <Chip
            label={`${minSlots}-${maxSlots} ${t('slotPreview.slots')}`}
            size="small"
            color="primary"
          />
          <Chip label={`${slotDuration} min each`} size="small" />
          <Chip label={`${t('slotPreview.capacity')}: ${defaultCapacity}`} size="small" />
        </Stack>
        {typicalStartTime && typicalEndTime && (
          <Typography variant="body2" color="text.secondary">
            {t('slotPreview.timing')}: {typicalStartTime} - {typicalEndTime}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
};
