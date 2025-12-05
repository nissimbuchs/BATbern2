/**
 * EventTypeConfigurationAdmin Page Component (Story 5.1 - Task 3b)
 *
 * Admin page for managing event type configurations (ORGANIZER only)
 * Features:
 * - List of all event type configurations (3 cards)
 * - Edit button for each type (opens modal)
 * - Role-based access control (ORGANIZER only)
 * - i18n compliance (all text uses react-i18next)
 * - Generated types from OpenAPI spec (ADR-006)
 */

import React, { useState } from 'react';
import {
  Container,
  Typography,
  Card,
  CardContent,
  CardActions,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  Alert,
  CircularProgress,
  Box,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import { Edit as EditIcon, ArrowBack as ArrowBackIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { useEventTypes, useUpdateEventType } from '@/hooks/useEventTypes';
import { EventTypeConfigurationForm } from '@/components/organizer/EventTypeConfigurationForm/EventTypeConfigurationForm';
import { SlotTemplatePreview } from '@/components/organizer/SlotTemplatePreview/SlotTemplatePreview';
import type { components } from '@/types/generated/events-api.types';

// Import generated types from OpenAPI spec (ADR-006 compliance)
type EventType = components['schemas']['EventType'];
type UpdateEventSlotConfigurationRequest =
  components['schemas']['UpdateEventSlotConfigurationRequest'];

export const EventTypeConfigurationAdmin: React.FC = () => {
  const { t } = useTranslation('events');
  const navigate = useNavigate();
  const { user } = useAuth();
  const { data: eventTypes, isLoading, error } = useEventTypes();
  const updateMutation = useUpdateEventType();

  const [editingType, setEditingType] = useState<EventType | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const handleBack = () => {
    navigate('/organizer/events');
  };

  // Role-based access control - ORGANIZER only
  if (user?.role !== 'organizer') {
    return (
      <Container maxWidth="md" sx={{ mt: 4 }}>
        <Alert severity="error">
          <Typography variant="h6">{t('form.eventTypeConfig.accessDenied')}</Typography>
          <Typography>{t('form.eventTypeConfig.organizerOnly')}</Typography>
        </Alert>
      </Container>
    );
  }

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
      <Container maxWidth="lg" sx={{ mt: 4, textAlign: 'center' }}>
        <CircularProgress />
      </Container>
    );
  }

  if (error) {
    return (
      <Container maxWidth="lg" sx={{ mt: 4 }}>
        <Alert severity="error">{t('errors.loadFailed')}</Alert>
      </Container>
    );
  }

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
      {/* Back button header */}
      <Box sx={{ mb: 3 }}>
        <Button startIcon={<ArrowBackIcon />} onClick={handleBack}>
          {t('actions.back')}
        </Button>
      </Box>

      <Typography variant="h4" component="h1" gutterBottom>
        {t('form.eventTypeConfig.title')}
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
        {t('form.eventTypeConfig.description')}
      </Typography>

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
                  {t('dashboard.actions.edit')}
                </Button>
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* Edit Modal */}
      <Dialog open={isModalOpen} onClose={handleCloseModal} maxWidth="sm" fullWidth>
        <DialogTitle>
          {t('dashboard.actions.edit')} {editingType}
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
    </Container>
  );
};

export default EventTypeConfigurationAdmin;
