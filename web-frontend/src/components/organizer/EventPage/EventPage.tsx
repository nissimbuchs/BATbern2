/**
 * EventPage Component (Story 5.6)
 *
 * Unified event page with tab-based navigation consolidating:
 * - EventDetail (overview + speaker status)
 * - EventDetailEdit (edit + sessions)
 * - SpeakerOutreachPage (outreach tracking)
 *
 * Route: /organizer/events/:eventCode
 * URL params: ?tab=overview|speakers|venue|team|publishing|settings
 */

import React, { useMemo } from 'react';
import { useParams, useSearchParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Container,
  Tabs,
  Tab,
  Typography,
  CircularProgress,
  Alert,
  Button,
  Stack,
  Chip,
  useTheme,
  useMediaQuery,
  BottomNavigation,
  BottomNavigationAction,
  Paper,
} from '@mui/material';
import {
  ArrowBack as ArrowBackIcon,
  Dashboard as OverviewIcon,
  People as SpeakersIcon,
  LocationOn as VenueIcon,
  Groups as TeamIcon,
  PersonAdd as ParticipantsIcon,
  Publish as PublishIcon,
  Settings as SettingsIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useEvent } from '@/hooks/useEvents';
import { useEventStore } from '@/stores/eventStore';
import { Breadcrumbs } from '@/components/shared/Breadcrumbs';
import type { BreadcrumbItem } from '@/components/shared/Breadcrumbs';
import { getWorkflowStateLabel } from '@/utils/workflow/workflowState';

import { EventOverviewTab } from './EventOverviewTab';
import { EventSpeakersTab } from './EventSpeakersTab';
import { EventVenueTab } from './EventVenueTab';
import { EventTeamTab } from './EventTeamTab';
import EventParticipantsTab from './EventParticipantsTab';
import { EventPublishingTab } from './EventPublishingTab';
import { EventSettingsTab } from './EventSettingsTab';
import { EventForm } from '@/components/organizer/EventManagement';

// Tab configuration
const TABS = [
  { id: 'overview', labelKey: 'eventPage.tabs.overview', icon: <OverviewIcon /> },
  { id: 'speakers', labelKey: 'eventPage.tabs.speakers', icon: <SpeakersIcon /> },
  { id: 'venue', labelKey: 'eventPage.tabs.venue', icon: <VenueIcon /> },
  { id: 'team', labelKey: 'eventPage.tabs.team', icon: <TeamIcon /> },
  { id: 'participants', labelKey: 'eventPage.tabs.participants', icon: <ParticipantsIcon /> },
  { id: 'publishing', labelKey: 'eventPage.tabs.publishing', icon: <PublishIcon /> },
  { id: 'settings', labelKey: 'eventPage.tabs.settings', icon: <SettingsIcon /> },
] as const;

type TabId = (typeof TABS)[number]['id'];

const isValidTab = (tab: string | null): tab is TabId => {
  return tab !== null && TABS.some((t) => t.id === tab);
};

export const EventPage: React.FC = () => {
  const { eventCode } = useParams<{ eventCode: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { t } = useTranslation('events');
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const { openEditModal, isEditModalOpen, selectedEventCode, closeEditModal } = useEventStore();

  // Get current tab from URL, default to 'overview'
  const currentTab = isValidTab(searchParams.get('tab')) ? searchParams.get('tab')! : 'overview';

  // Fetch event data with resource expansion including registrations for accurate counts
  const {
    data: event,
    isLoading,
    error,
  } = useEvent(eventCode, [
    'venue',
    'topics',
    'sessions',
    'team',
    'workflow',
    'metrics',
    'registrations',
  ]);

  // Build breadcrumb items
  const breadcrumbItems: BreadcrumbItem[] = useMemo(
    () => [
      { label: t('navigation.events', 'Events'), path: '/organizer/events' },
      { label: event?.title || t('common.loading', 'Loading...') },
    ],
    [event?.title, t]
  );

  // Handle tab change
  const handleTabChange = (_event: React.SyntheticEvent, newValue: TabId) => {
    const newParams = new URLSearchParams(searchParams);
    if (newValue === 'overview') {
      newParams.delete('tab');
    } else {
      newParams.set('tab', newValue);
    }
    // Clear view param when switching away from speakers tab
    if (newValue !== 'speakers') {
      newParams.delete('view');
    }
    setSearchParams(newParams, { replace: true });
  };

  // Handle mobile bottom nav change
  const handleMobileNavChange = (_event: React.SyntheticEvent, newValue: TabId) => {
    handleTabChange(_event, newValue);
  };

  const handleBack = () => {
    navigate('/organizer/events');
  };

  // Loading state
  if (isLoading) {
    return (
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '50vh',
        }}
      >
        <CircularProgress />
      </Box>
    );
  }

  // Error state
  if (error) {
    return (
      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Alert severity="error" sx={{ mb: 2 }}>
          {error.message || t('errors.loadFailed', 'Failed to load event')}
        </Alert>
        <Button startIcon={<ArrowBackIcon />} onClick={handleBack}>
          {t('common.back', 'Back to Events')}
        </Button>
      </Container>
    );
  }

  // Not found state
  if (!event) {
    return (
      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Alert severity="warning">{t('errors.notFound', 'Event not found')}</Alert>
        <Button startIcon={<ArrowBackIcon />} onClick={handleBack} sx={{ mt: 2 }}>
          {t('common.back', 'Back to Events')}
        </Button>
      </Container>
    );
  }

  // Handle edit button click
  const handleEdit = () => {
    if (eventCode) {
      openEditModal(eventCode);
    }
  };

  // Render current tab content
  const renderTabContent = () => {
    switch (currentTab) {
      case 'overview':
        return <EventOverviewTab event={event} eventCode={eventCode!} onEdit={handleEdit} />;
      case 'speakers':
        return <EventSpeakersTab eventCode={eventCode!} />;
      case 'venue':
        return <EventVenueTab event={event} />;
      case 'team':
        return <EventTeamTab event={event} eventCode={eventCode!} />;
      case 'participants':
        return <EventParticipantsTab event={event} />;
      case 'publishing':
        return <EventPublishingTab event={event} eventCode={eventCode!} />;
      case 'settings':
        return <EventSettingsTab event={event} eventCode={eventCode!} />;
      default:
        return <EventOverviewTab event={event} eventCode={eventCode!} />;
    }
  };

  return (
    <Box sx={{ pb: isMobile ? 8 : 0 }}>
      <Container maxWidth="xl" sx={{ py: 3 }}>
        {/* Breadcrumbs */}
        <Breadcrumbs items={breadcrumbItems} marginBottom={2} />

        {/* Header */}
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          justifyContent="space-between"
          alignItems={{ xs: 'flex-start', sm: 'center' }}
          spacing={2}
          mb={3}
        >
          <Box>
            <Stack direction="row" spacing={1} alignItems="center" mb={0.5}>
              <Chip
                label={getWorkflowStateLabel(event.workflowState || 'CREATED', t)}
                color="primary"
                size="small"
              />
              <Typography variant="caption" color="text.secondary">
                {eventCode}
              </Typography>
            </Stack>
            <Typography variant="h4" component="h1">
              {event.title}
            </Typography>
          </Box>
        </Stack>

        {/* Desktop Tabs */}
        {!isMobile && (
          <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
            <Tabs
              value={currentTab}
              onChange={handleTabChange}
              aria-label={t('eventPage.tabsAriaLabel', 'Event page navigation')}
              variant="scrollable"
              scrollButtons="auto"
            >
              {TABS.map((tab) => (
                <Tab
                  key={tab.id}
                  value={tab.id}
                  label={t(tab.labelKey, tab.id)}
                  icon={tab.icon}
                  iconPosition="start"
                  sx={{ minHeight: 48 }}
                />
              ))}
            </Tabs>
          </Box>
        )}

        {/* Tab Content */}
        <Box>{renderTabContent()}</Box>
      </Container>

      {/* Mobile Bottom Navigation */}
      {isMobile && (
        <Paper sx={{ position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 1100 }} elevation={3}>
          <BottomNavigation value={currentTab} onChange={handleMobileNavChange} showLabels>
            {TABS.map((tab) => (
              <BottomNavigationAction
                key={tab.id}
                value={tab.id}
                label={t(tab.labelKey, tab.id)}
                icon={tab.icon}
              />
            ))}
          </BottomNavigation>
        </Paper>
      )}

      {/* Edit Event Modal */}
      {isEditModalOpen && selectedEventCode && (
        <EventForm
          open={isEditModalOpen}
          mode="edit"
          event={event}
          onClose={closeEditModal}
          onSuccess={(updatedEvent) => {
            closeEditModal();
            // Redirect if eventCode changed (e.g., eventNumber 58 -> 998 regenerates BATbern58 -> BATbern998)
            if (updatedEvent && updatedEvent.eventCode !== eventCode) {
              navigate(
                `/organizer/events/${updatedEvent.eventCode}${searchParams.size > 0 ? `?${searchParams.toString()}` : ''}`
              );
            }
          }}
        />
      )}
    </Box>
  );
};

export default EventPage;
