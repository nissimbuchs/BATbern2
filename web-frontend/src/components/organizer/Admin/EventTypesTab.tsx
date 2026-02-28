/**
 * EventTypesTab (Story 10.1 - Task 2)
 *
 * Tab 0 of the Admin page.
 * Renders the Event Type configuration UI (extracted from EventTypeConfigurationAdmin).
 */

import React, { useState, useMemo } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Dialog,
  DialogContent,
  DialogTitle,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import { Edit as EditIcon } from '@mui/icons-material';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { useTranslation } from 'react-i18next';
import { useEventTypes, useUpdateEventType } from '@/hooks/useEventTypes';
import { EventTypeConfigurationForm } from '@/components/organizer/EventTypeConfigurationForm/EventTypeConfigurationForm';
import { SlotTemplatePreview } from '@/components/organizer/SlotTemplatePreview/SlotTemplatePreview';
import type { components } from '@/types/generated/events-api.types';

type EventType = components['schemas']['EventType'];
type UpdateEventSlotConfigurationRequest =
  components['schemas']['UpdateEventSlotConfigurationRequest'];

export const EventTypesTab: React.FC = () => {
  const { t } = useTranslation('events');
  const { data: eventTypes, isLoading, error } = useEventTypes();
  const updateMutation = useUpdateEventType();

  const [editingType, setEditingType] = useState<EventType | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const getEventTypeName = useMemo(
    () =>
      (type: EventType): string => {
        const typeMap: Record<EventType, string> = {
          FULL_DAY: t('form.eventTypes.fullDay'),
          AFTERNOON: t('form.eventTypes.afternoon'),
          EVENING: t('form.eventTypes.evening'),
        };
        return typeMap[type] || type;
      },
    [t]
  );

  const handleEditClick = (type: EventType) => {
    setEditingType(type);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setEditingType(null);
  };

  const handleSave = async (config: UpdateEventSlotConfigurationRequest) => {
    if (!editingType) return;
    await updateMutation.mutateAsync({ type: editingType, config });
    handleCloseModal();
  };

  if (isLoading) {
    return (
      <Box sx={{ textAlign: 'center', mt: 4 }}>
        <BATbernLoader size={96} />
      </Box>
    );
  }

  if (error) {
    return <Alert severity="error">{t('errors.loadFailed')}</Alert>;
  }

  return (
    <>
      <Grid container spacing={3}>
        {eventTypes?.map((config) => (
          <Grid size={{ xs: 12, md: 4 }} key={config.type}>
            <Card>
              <CardContent>
                <SlotTemplatePreview eventType={config.type} slotConfiguration={config} />
              </CardContent>
              <CardActions>
                <Button
                  size="small"
                  startIcon={<EditIcon />}
                  onClick={() => handleEditClick(config.type)}
                  aria-label={`Edit ${config.type}`}
                >
                  {t('common:actions.edit')}
                </Button>
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Dialog open={isModalOpen} onClose={handleCloseModal} maxWidth="md" fullWidth>
        <DialogTitle>
          {t('common:actions.edit')} {editingType && getEventTypeName(editingType)}
        </DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            <EventTypeConfigurationForm
              eventType={editingType || undefined}
              onSave={handleSave}
              onCancel={handleCloseModal}
            />
          </Box>
        </DialogContent>
      </Dialog>
    </>
  );
};
