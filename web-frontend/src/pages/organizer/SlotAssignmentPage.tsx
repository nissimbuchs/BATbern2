/**
 * Slot Assignment Page - Story 5.7 (BAT-11)
 *
 * Dedicated page for assigning speakers to time slots with drag-and-drop interface.
 *
 * Architecture Decision: This is a separate dedicated page at
 * `/organizer/events/:eventCode/slot-assignment`, NOT integrated into Story 5.8 tabs.
 *
 * Entry Points:
 * - Speakers tab: "Assign Slots" button (visible when speaker status is CONFIRMED)
 * - Overview tab: "Slot Assignment" CTA banner (when workflow_state = REVIEWING_SPEAKERS)
 * - Publishing tab: "Assign Slots" link (if slots not assigned)
 *
 * @see docs/wireframes/story-3.1-speaker-matching-interface.md
 */

import React, { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Box, Container, Typography, Button, Breadcrumbs, Link, Alert } from '@mui/material';
import { ArrowBack as ArrowBackIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { DragDropSlotAssignment } from '@components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment';
import { speakerPoolKeys } from '@/hooks/useSpeakerPool';

/**
 * Slot Assignment Page Component
 *
 * Features:
 * - Drag-and-drop slot assignment interface
 * - Visual timeline showing all slots and assignments
 * - Speaker time preferences display
 * - Conflict detection warnings
 * - Unassigned speakers list
 * - Breadcrumb navigation
 * - Return to event page
 */
const SlotAssignmentPage: React.FC = () => {
  const { eventCode } = useParams<{ eventCode: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation('events');
  const queryClient = useQueryClient();

  // Invalidate speaker pool cache on unmount so kanban reflects slot changes
  useEffect(() => {
    return () => {
      if (eventCode) {
        queryClient.invalidateQueries({ queryKey: speakerPoolKeys.list(eventCode) });
        queryClient.invalidateQueries({ queryKey: ['speakerStatusSummary', eventCode] });
      }
    };
  }, [eventCode, queryClient]);

  const handleBackToEvent = () => {
    navigate(`/organizer/events/${eventCode}`);
  };

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      {/* Breadcrumb Navigation */}
      <Breadcrumbs sx={{ mb: 2 }}>
        <Link
          color="inherit"
          href="/organizer/events"
          onClick={(e) => {
            e.preventDefault();
            navigate('/organizer/events');
          }}
          sx={{ cursor: 'pointer' }}
        >
          {t('navigation.events')}
        </Link>
        <Link
          color="inherit"
          href={`/organizer/events/${eventCode}`}
          onClick={(e) => {
            e.preventDefault();
            handleBackToEvent();
          }}
          sx={{ cursor: 'pointer' }}
        >
          {eventCode}
        </Link>
        <Typography color="text.primary">{t('slotAssignment.title')}</Typography>
      </Breadcrumbs>

      {/* Page Header */}
      <Box sx={{ mb: 3, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Box>
          <Typography variant="h4" component="h1" gutterBottom>
            {t('slotAssignment.pageTitle')}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {t('slotAssignment.pageDescription')}
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={<ArrowBackIcon />}
          onClick={handleBackToEvent}
          data-testid="back-to-event-button"
        >
          {t('slotAssignment.backToEvent')}
        </Button>
      </Box>

      {/* Help Banner */}
      <Alert severity="info" sx={{ mb: 3 }}>
        <strong>{t('slotAssignment.helpBanner.title')}</strong>{' '}
        {t('slotAssignment.helpBanner.description')}
      </Alert>

      {/* Drag-and-Drop Slot Assignment Component */}
      {eventCode && <DragDropSlotAssignment eventCode={eventCode} />}

      {/* Return Navigation */}
      <Box sx={{ mt: 4, display: 'flex', justifyContent: 'center' }}>
        <Button
          variant="text"
          startIcon={<ArrowBackIcon />}
          onClick={handleBackToEvent}
          data-testid="return-to-event-button"
        >
          {t('slotAssignment.returnToEventOverview')}
        </Button>
      </Box>
    </Container>
  );
};

export default SlotAssignmentPage;
