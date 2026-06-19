/**
 * EventPage Component (Story 5.6; Epic 14 Phase A — lifecycle-aware 8-tab shell)
 *
 * Unified organizer event-detail page. As of Epic 14 Phase A the page presents
 * a lifecycle-aware 8-tab IA in two clusters — a "work" cluster
 * (Cockpit · Speakers & Agenda · Registrations · Communications · Publishing ·
 * Wrap-up) and a "config" cluster (Details · Settings) — driven by the
 * declarative workflowState → relevance map (tabs dim / lock per state) and
 * count-driven attention badges. Existing tab components are recomposed into
 * the new slots with unchanged content; Phases B–G replace each tab's internals.
 *
 * Route: /organizer/events/:eventCode
 * URL params: ?tab=cockpit|speakers|registrations|communications|publishing|wrapup|details|settings
 */

import React, { useEffect, useMemo, useState } from 'react';
import { useParams, useSearchParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Container,
  Tabs,
  Tab,
  Typography,
  Alert,
  Button,
  Stack,
  Badge,
  useTheme,
  useMediaQuery,
  BottomNavigation,
  BottomNavigationAction,
  Paper,
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import {
  ArrowBack as ArrowBackIcon,
  Dashboard as CockpitIcon,
  People as SpeakersIcon,
  PersonAdd as RegistrationsIcon,
  EmailOutlined as CommunicationsIcon,
  Publish as PublishIcon,
  CardGiftcard as WrapupIcon,
  Description as DetailsIcon,
  Lock as LockIcon,
  MoreHoriz as MoreIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useEvent } from '@/hooks/useEvents';
import { useEventStore } from '@/stores/eventStore';
import { getTabRelevance, type EventTabId } from '@/utils/workflow/workflowState';
import { Breadcrumbs } from '@/components/shared/Breadcrumbs';
import type { BreadcrumbItem } from '@/components/shared/Breadcrumbs';
import { BATbernLoader } from '@components/shared/BATbernLoader';

import { CockpitTab } from './cockpit/CockpitTab';
import type { CardTarget } from './cockpit/cockpitCards';
import { EventSpeakersTab } from './EventSpeakersTab';
import EventParticipantsTab from './EventParticipantsTab';
import { EventPublishingTab } from './EventPublishingTab';
import { EventDetailsContainer } from './EventDetailsContainer';
import { EventCommunicationsContainer } from './EventCommunicationsContainer';
import { EventWrapupContainer } from './EventWrapupContainer';
import { useTabBadges } from './useTabBadges';
import { EventForm } from '@/components/organizer/EventManagement';

// 8-tab lifecycle IA: "work" cluster (cockpit…wrapup) then "config" cluster.
// `id` values are the stable ?tab= keys + EventTabId values consumed by the
// relevance map.
const TABS = [
  { id: 'cockpit', labelKey: 'eventPage.tabs.cockpit', icon: <CockpitIcon />, cluster: 'work' },
  {
    id: 'speakers',
    labelKey: 'eventPage.tabs.speakersAgenda',
    icon: <SpeakersIcon />,
    cluster: 'work',
  },
  {
    id: 'registrations',
    labelKey: 'eventPage.tabs.registrations',
    icon: <RegistrationsIcon />,
    cluster: 'work',
  },
  {
    id: 'communications',
    labelKey: 'eventPage.tabs.communications',
    icon: <CommunicationsIcon />,
    cluster: 'work',
  },
  {
    id: 'publishing',
    labelKey: 'eventPage.tabs.publishing',
    icon: <PublishIcon />,
    cluster: 'work',
  },
  { id: 'wrapup', labelKey: 'eventPage.tabs.wrapup', icon: <WrapupIcon />, cluster: 'work' },
  // Config cluster: a single "Details" tab with Info / Tasks / Settings sub-tabs
  // (Story 14.F.2 merge — the former standalone Settings tab folds in here).
  { id: 'details', labelKey: 'eventPage.tabs.details', icon: <DetailsIcon />, cluster: 'config' },
] as const;

type TabId = (typeof TABS)[number]['id'];

const DEFAULT_TAB: TabId = 'cockpit';

// Mobile (Phase G — 14.G.1): the four day-to-day destinations live in the bottom
// bar; the occasional ones live behind a ⋯ More bottom sheet.
const PRIMARY_TAB_IDS: TabId[] = ['cockpit', 'speakers', 'registrations', 'communications'];
const MORE_TAB_IDS: TabId[] = ['publishing', 'wrapup', 'details'];

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
  const [moreOpen, setMoreOpen] = useState(false);
  const { isEditModalOpen, selectedEventCode, closeEditModal } = useEventStore();

  // Get current tab from URL, default to the Cockpit.
  const currentTab: TabId = isValidTab(searchParams.get('tab'))
    ? (searchParams.get('tab') as TabId)
    : DEFAULT_TAB;

  // Fetch event data with resource expansion including registrations for accurate counts
  const {
    data: event,
    isLoading,
    error,
  } = useEvent(eventCode, ['venue', 'topics', 'sessions', 'workflow', 'metrics', 'registrations']);

  const workflowState = (event as { workflowState?: string } | undefined)?.workflowState ?? '';
  const badges = useTabBadges(event, eventCode);

  // If the URL points at a tab that is locked for this state (e.g. a stale
  // ?tab=wrapup on an early-stage event), fall back to the Cockpit so we never
  // render a locked tab's content.
  const effectiveTab: TabId =
    getTabRelevance(workflowState, currentTab as EventTabId) === 'locked'
      ? DEFAULT_TAB
      : currentTab;

  // If the URL pointed at a locked tab we render the Cockpit instead; normalize
  // the URL so the address bar (and any stale ?view=) matches the rendered tab.
  useEffect(() => {
    if (event && effectiveTab !== currentTab) {
      const params = new URLSearchParams(searchParams);
      params.delete('tab'); // effectiveTab is the default Cockpit
      params.delete('view');
      setSearchParams(params, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [event, effectiveTab, currentTab]);

  // Build breadcrumb items
  const breadcrumbItems: BreadcrumbItem[] = useMemo(
    () => [
      { label: t('common:navigation.events'), path: '/organizer/events' },
      { label: event?.title || t('common.loading', 'Loading...') },
    ],
    [event?.title, t]
  );

  // Handle tab change (ignores locked tabs defensively — disabled tabs don't fire onChange).
  const handleTabChange = (_event: React.SyntheticEvent, newValue: TabId) => {
    if (getTabRelevance(workflowState, newValue as EventTabId) === 'locked') return;
    const newParams = new URLSearchParams(searchParams);
    if (newValue === DEFAULT_TAB) {
      newParams.delete('tab');
    } else {
      newParams.set('tab', newValue);
    }
    // Clear view param when switching away from the speakers tab
    if (newValue !== 'speakers') {
      newParams.delete('view');
    }
    setSearchParams(newParams, { replace: true });
  };

  // Deep-link from the Cockpit: switch tab (and Speakers sub-view) via the same
  // ?tab=/?view= URL mechanism as handleTabChange — no route change (FR9/FR13).
  const navigateToTab = (tab: EventTabId, view?: string) => {
    if (getTabRelevance(workflowState, tab) === 'locked') return;
    const params = new URLSearchParams(searchParams);
    if (tab === DEFAULT_TAB) {
      params.delete('tab');
    } else {
      params.set('tab', tab);
    }
    if (tab === 'speakers' && view) {
      params.set('view', view);
    } else {
      params.delete('view');
    }
    setSearchParams(params, { replace: true });
  };

  // Cockpit card navigation: tab switch (in-page) or route navigation (event-day cards).
  const handleCardNavigate = (target: CardTarget) => {
    if (target.kind === 'tab') {
      navigateToTab(target.tab, target.view);
    } else if (target.newTab) {
      window.open(target.path, '_blank', 'noopener,noreferrer');
    } else {
      navigate(target.path);
    }
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
        <BATbernLoader size={96} />
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
          {t('common:actions.back')}
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
          {t('common:actions.back')}
        </Button>
      </Container>
    );
  }

  // Per-tab attention badge content (FR6). Returns null when nothing is waiting.
  // The 'muted' variant (FR24) is a subtle inline count, NOT a heavy attention badge.
  const tabBadge = (
    id: TabId
  ): { content: React.ReactNode; variant: 'standard' | 'dot' | 'muted' } | null => {
    if (id === 'speakers' && badges.speakers > 0) {
      return { content: badges.speakers, variant: 'standard' };
    }
    if (id === 'publishing' && badges.publishingReady) {
      return { content: '', variant: 'dot' };
    }
    if (id === 'communications' && badges.commsOverdue) {
      return { content: '', variant: 'dot' };
    }
    if (id === 'registrations' && badges.registrations > 0) {
      return { content: badges.registrations, variant: 'muted' };
    }
    return null;
  };

  // Render a tab label, wrapping it in an attention badge when relevant.
  const renderTabLabel = (id: TabId, labelKey: string, locked: boolean) => {
    const label = t(labelKey, id);
    const badge = locked ? null : tabBadge(id);
    if (!badge) return label;
    // Subtle muted count (Registrations, FR24) — appended inline, low emphasis.
    if (badge.variant === 'muted') {
      return (
        <span>
          {label}{' '}
          <Box
            component="span"
            sx={{ color: 'text.secondary', fontWeight: 400, ml: 0.25 }}
            data-testid={`event-tab-badge-${id}`}
          >
            {badge.content}
          </Box>
        </span>
      );
    }
    return (
      <Badge
        color={badge.variant === 'dot' ? 'error' : 'primary'}
        variant={badge.variant}
        badgeContent={badge.variant === 'standard' ? badge.content : undefined}
        sx={{ '& .MuiBadge-badge': { right: -10, top: 2 } }}
        data-testid={`event-tab-badge-${id}`}
      >
        <span>{label}</span>
      </Badge>
    );
  };

  // Bottom-nav / More-sheet icon, wrapping the tab icon in its attention badge
  // (locked tabs show a lock; the muted Registrations count is desktop-only).
  const navIcon = (tab: (typeof TABS)[number], locked: boolean): React.ReactNode => {
    if (locked) return <LockIcon fontSize="small" />;
    const badge = tabBadge(tab.id);
    if (!badge || badge.variant === 'muted') return tab.icon;
    return (
      <Badge
        color={badge.variant === 'dot' ? 'error' : 'primary'}
        variant={badge.variant}
        badgeContent={badge.variant === 'standard' ? badge.content : undefined}
        data-testid={`event-mobile-badge-${tab.id}`}
      >
        {tab.icon}
      </Badge>
    );
  };

  const primaryTabs = TABS.filter((tab) => PRIMARY_TAB_IDS.includes(tab.id));
  const moreTabs = TABS.filter((tab) => MORE_TAB_IDS.includes(tab.id));
  // The bottom bar highlights "More" whenever the active tab lives in the sheet.
  const bottomNavValue: TabId | 'more' = PRIMARY_TAB_IDS.includes(effectiveTab)
    ? effectiveTab
    : 'more';

  // Render current tab content
  const renderTabContent = () => {
    switch (effectiveTab) {
      case 'cockpit':
        // Phase B — task-driven Cockpit (lifecycle spine · attention cards · metric tiles).
        return <CockpitTab event={event} eventCode={eventCode!} onNavigate={handleCardNavigate} />;
      case 'speakers':
        return <EventSpeakersTab eventCode={eventCode!} />;
      case 'registrations':
        return <EventParticipantsTab event={event} />;
      case 'communications':
        return <EventCommunicationsContainer event={event} eventCode={eventCode!} />;
      case 'publishing':
        return <EventPublishingTab event={event} eventCode={eventCode!} />;
      case 'wrapup':
        return <EventWrapupContainer eventCode={eventCode!} />;
      case 'details':
        return <EventDetailsContainer event={event} eventCode={eventCode!} />;
      default:
        // `effectiveTab` is already clamped to a valid tab, so this is purely
        // defensive — the Cockpit is the canonical landing surface (14.F.5).
        return <CockpitTab event={event} eventCode={eventCode!} onNavigate={handleCardNavigate} />;
    }
  };

  // Index of the first config-cluster tab — used to draw a divider between the
  // two clusters.
  const firstConfigIndex = TABS.findIndex((t) => t.cluster === 'config');

  return (
    <Box sx={{ pb: isMobile ? 8 : 0 }}>
      <Container maxWidth="xl" sx={{ py: 3 }}>
        {/* Breadcrumbs */}
        <Breadcrumbs items={breadcrumbItems} marginBottom={2} />

        {/* Header — event title persists on every tab (FR2). The event-day live
            controls (Start Presentation / Live Control) are NOT here (14.B.4): they
            surface as urgent Cockpit attention cards from AGENDA_PUBLISHED onward. */}
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          justifyContent="space-between"
          alignItems={{ xs: 'flex-start', sm: 'center' }}
          spacing={2}
          mb={3}
        >
          <Box>
            <Typography variant="h4" component="h1">
              {event.title}
            </Typography>
          </Box>
        </Stack>

        {/* Desktop Tabs */}
        {!isMobile && (
          <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
            <Tabs
              value={effectiveTab}
              onChange={handleTabChange}
              aria-label={t('eventPage.tabsAriaLabel', 'Event page navigation')}
              variant="scrollable"
              scrollButtons="auto"
            >
              {TABS.map((tab, index) => {
                const relevance = getTabRelevance(workflowState, tab.id as EventTabId);
                const locked = relevance === 'locked';
                const dimmed = relevance === 'dimmed';
                const isClusterStart = index === firstConfigIndex;
                return (
                  <Tab
                    key={tab.id}
                    value={tab.id}
                    label={renderTabLabel(tab.id, tab.labelKey, locked)}
                    icon={locked ? <LockIcon fontSize="small" /> : tab.icon}
                    iconPosition="start"
                    disabled={locked}
                    aria-label={
                      locked
                        ? t('eventPage.lockedTabSuffix', '{{tab}} (locked)', {
                            tab: t(tab.labelKey, tab.id),
                          })
                        : undefined
                    }
                    data-testid={`event-tab-${tab.id}`}
                    sx={{
                      minHeight: 48,
                      opacity: dimmed ? 0.55 : undefined,
                      ...(isClusterStart
                        ? { borderLeft: 2, borderColor: 'divider', ml: 1, pl: 2 }
                        : {}),
                    }}
                  />
                );
              })}
            </Tabs>
          </Box>
        )}

        {/* Tab Content */}
        <Box>{renderTabContent()}</Box>
      </Container>

      {/* Mobile Bottom Navigation (Phase G — 14.G.1): four day-to-day destinations
          + a ⋯ More entry opening a bottom sheet with the occasional tabs. Badges/dots
          ride on the icons; locked tabs (e.g. Wrap-up before EVENT_LIVE) live in the
          sheet with a 🔒 and are non-interactive. */}
      {isMobile && (
        <Paper sx={{ position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 1100 }} elevation={3}>
          <BottomNavigation
            value={bottomNavValue}
            onChange={(_e, value: TabId | 'more') => {
              if (value === 'more') {
                setMoreOpen(true);
              } else {
                handleTabChange(_e, value);
              }
            }}
            showLabels={false}
          >
            {primaryTabs.map((tab) => (
              <BottomNavigationAction
                key={tab.id}
                value={tab.id}
                icon={navIcon(tab, false)}
                aria-label={t(tab.labelKey, tab.id)}
                data-testid={`event-tab-${tab.id}`}
                sx={{ minWidth: 0, flex: 1, px: 0 }}
              />
            ))}
            <BottomNavigationAction
              value="more"
              icon={<MoreIcon />}
              aria-label={t('eventPage.mobile.more', 'More')}
              data-testid="event-more-button"
              sx={{ minWidth: 0, flex: 1, px: 0 }}
            />
          </BottomNavigation>
        </Paper>
      )}

      {/* ⋯ More sheet — occasional tabs (Publishing · Wrap-up · Details). */}
      <Drawer anchor="bottom" open={moreOpen} onClose={() => setMoreOpen(false)}>
        <Box sx={{ pb: 2 }} role="presentation" data-testid="event-more-sheet">
          <Typography
            variant="overline"
            sx={{ display: 'block', px: 2, pt: 2, color: 'text.secondary' }}
          >
            {t('eventPage.mobile.moreSheetTitle', 'More')}
          </Typography>
          <List>
            {moreTabs.map((tab) => {
              const locked = getTabRelevance(workflowState, tab.id as EventTabId) === 'locked';
              return (
                <ListItemButton
                  key={tab.id}
                  selected={effectiveTab === tab.id}
                  disabled={locked}
                  onClick={() => {
                    navigateToTab(tab.id as EventTabId);
                    setMoreOpen(false);
                  }}
                  data-testid={`event-more-tab-${tab.id}`}
                  aria-label={
                    locked
                      ? t('eventPage.lockedTabSuffix', '{{tab}} (locked)', {
                          tab: t(tab.labelKey, tab.id),
                        })
                      : t(tab.labelKey, tab.id)
                  }
                >
                  <ListItemIcon>{locked ? <LockIcon fontSize="small" /> : tab.icon}</ListItemIcon>
                  <ListItemText primary={t(tab.labelKey, tab.id)} />
                  {locked && (
                    <LockIcon
                      fontSize="small"
                      color="disabled"
                      data-testid={`event-more-lock-${tab.id}`}
                    />
                  )}
                </ListItemButton>
              );
            })}
          </List>
        </Box>
      </Drawer>

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
