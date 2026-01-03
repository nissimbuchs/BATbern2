/**
 * Topic Backlog Manager Component (Story 5.2 - AC1-8)
 *
 * Main interface for topic selection with:
 * - Searchable topic list with filtering
 * - Heat map visualization
 * - Staleness scoring and color coding
 * - Duplicate detection and warnings
 * - Topic selection for events
 */

import React, { useState, useMemo, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Container,
  Grid,
  Paper,
  Typography,
  Box,
  CircularProgress,
  Alert,
  Button,
  ToggleButtonGroup,
  ToggleButton,
} from '@mui/material';
import {
  Add as AddIcon,
  ViewModule as HeatMapIcon,
  ViewList as ListIcon,
  ViewKanban as BoardIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useTopics, useTopic } from '@/hooks/useTopics';
import { useEvent, useEvents } from '@/hooks/useEvents';
import { useUserList } from '@/hooks/useUserManagement/useUserList';
import type { Event } from '@/types/event.types';
import { TopicFilterPanel } from './TopicFilterPanel';
import { TopicList } from './TopicList';
import { TopicDetailsPanel } from './TopicDetailsPanel';
import { CreateTopicModal } from './CreateTopicModal';
import { SpeakerBrainstormingPanel } from '@/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel';
import { MultiTopicHeatMap } from '@/components/TopicHeatMap';
import type { Topic, TopicFilters } from '@/types/topic.types';

type ViewMode = 'heatMap' | 'list' | 'board';

export interface TopicBacklogManagerProps {
  eventCode?: string; // Optional: if provided, enables topic selection for event
  onTopicSelected?: (topicId: string) => void;
}

export const TopicBacklogManager: React.FC<TopicBacklogManagerProps> = ({
  eventCode,
  onTopicSelected,
}) => {
  const { t } = useTranslation('organizer');
  const navigate = useNavigate();
  const [filters, setFilters] = useState<TopicFilters>({
    page: 1,
    limit: 20,
    sort: '-stalenessScore', // Default: sort by staleness descending (safest first)
    include: 'history', // Include usage history for heat map visualization
  });
  const [selectedTopic, setSelectedTopic] = useState<Topic | null>(null);
  const [topicConfirmed, setTopicConfirmed] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editingTopic, setEditingTopic] = useState<Topic | null>(null);
  const [viewMode, setViewMode] = useState<ViewMode>('list');

  // Fetch topics with current filters
  const { data, isLoading, isError, error } = useTopics(filters);

  // Fetch event details if eventCode is provided
  const { data: eventData } = useEvent(eventCode);

  // Fetch pre-assigned topic if event has topicCode (Story 5.2 - Topic preselection)
  // ADR-003: Event now includes topicCode (meaningful identifier) for frontend use
  const { data: preassignedTopic } = useTopic(eventData?.topicCode || '', 'history');

  // Fetch organizer users for speaker assignment (Story 5.2)
  const { data: organizersData } = useUserList({
    filters: { role: ['ORGANIZER'] },
    pagination: { page: 1, limit: 100 },
  });

  // Preselect topic when event loads with topicId
  useEffect(() => {
    if (preassignedTopic && !selectedTopic) {
      setSelectedTopic(preassignedTopic);
      setTopicConfirmed(true); // Also confirm topic to show speaker panel
    }
  }, [preassignedTopic, selectedTopic]);

  // Fetch all events for heat map (to show event numbers and titles)
  const { data: allEventsData } = useEvents({ page: 1, limit: 1000 }); // Fetch many events for lookup

  // Create event lookup map (eventNumber -> Event info)
  const eventLookup = useMemo(() => {
    const map = new Map<number, { title: string }>();
    if (allEventsData?.data) {
      allEventsData.data.forEach((event: Event) => {
        map.set(event.eventNumber, {
          title: event.title,
        });
      });
      const sampleKeys = Array.from(map.keys()).slice(0, 5);
      console.debug(`✅ Event lookup map created with ${map.size} events`);
      console.debug(`Sample eventCodes:`, sampleKeys);
    } else {
      console.debug('❌ No event data available for lookup map');
    }
    return map;
  }, [allEventsData]);

  // Transform organizers from API to format needed by SpeakerBrainstormingPanel
  const organizers = useMemo(() => {
    if (!organizersData?.data) return [];
    return organizersData.data.map((user) => ({
      id: user.id,
      name: `${user.firstName} ${user.lastName}`.trim() || user.id,
    }));
  }, [organizersData]);

  const handleFilterChange = (newFilters: TopicFilters) => {
    setFilters((prev) => ({ ...prev, ...newFilters, page: 1 })); // Reset to page 1 on filter change
  };

  const handleTopicSelect = (topic: Topic) => {
    setSelectedTopic(topic);
  };

  const handleTopicConfirm = (topicId: string) => {
    if (onTopicSelected) {
      onTopicSelected(topicId);
    }
    setTopicConfirmed(true); // Show speaker brainstorming panel
  };

  const handleSpeakerBrainstormComplete = () => {
    // Navigate to event page with speakers tab if eventCode exists, otherwise to events dashboard
    if (eventCode) {
      navigate(`/organizer/events/${eventCode}?tab=speakers`);
    } else {
      navigate('/organizer/events');
    }
  };

  const handleEditTopic = (topic: Topic) => {
    setEditingTopic(topic);
    setCreateModalOpen(true);
  };

  const handleModalClose = () => {
    setCreateModalOpen(false);
    setEditingTopic(null);
  };

  return (
    <Container maxWidth="xl">
      {/* Title section - always visible, shows event context if available */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          {eventCode && eventData
            ? t('topicBacklog.breadcrumbs.topicSelection', 'Topic Selection')
            : t('topicBacklog.title', 'Topic Backlog Manager')}
        </Typography>
        {eventCode && eventData && (
          <Typography variant="body1" color="text.secondary">
            {t(
              'topicBacklog.subtitle',
              'Select topics from the backlog with intelligent suggestions and staleness detection'
            )}
          </Typography>
        )}
      </Box>

      {/* View Mode Toggle and Create Topic button */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <ToggleButtonGroup
          value={viewMode}
          exclusive
          onChange={(_event, newMode) => {
            if (newMode !== null) {
              setViewMode(newMode);
            }
          }}
          aria-label={t('topicBacklog.viewMode.label', 'View mode')}
          size="small"
        >
          <ToggleButton value="heatMap" aria-label={t('topicBacklog.viewMode.heatMap', 'Heat Map')}>
            <HeatMapIcon sx={{ mr: 0.5 }} />
            {t('topicBacklog.viewMode.heatMap', 'Heat Map')}
          </ToggleButton>
          <ToggleButton value="list" aria-label={t('topicBacklog.viewMode.list', 'List')}>
            <ListIcon sx={{ mr: 0.5 }} />
            {t('topicBacklog.viewMode.list', 'List')}
          </ToggleButton>
          <ToggleButton value="board" aria-label={t('topicBacklog.viewMode.board', 'Board')}>
            <BoardIcon sx={{ mr: 0.5 }} />
            {t('topicBacklog.viewMode.board', 'Board')}
          </ToggleButton>
        </ToggleButtonGroup>

        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setCreateModalOpen(true)}
        >
          {t('topicBacklog.createNew', 'Create New Topic')}
        </Button>
      </Box>

      <Grid container spacing={3}>
        {/* Left Panel: Filters (3 cols → 2 cols when speaker panel visible) */}
        <Grid size={{ xs: 12, md: eventCode && topicConfirmed ? 2 : 3 }}>
          <TopicFilterPanel filters={filters} onFilterChange={handleFilterChange} />
        </Grid>

        {/* Center Panel: Topic List/Heat Map/Board (5 cols → 4 cols when speaker panel visible) */}
        <Grid size={{ xs: 12, md: eventCode && topicConfirmed ? 4 : 5 }}>
          {isLoading && (
            <Paper
              sx={{
                p: 2,
                height: '70vh',
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
              }}
            >
              <CircularProgress />
            </Paper>
          )}

          {isError && (
            <Paper sx={{ p: 2, height: '70vh' }}>
              <Alert severity="error">
                {t('topicBacklog.error.loadFailed', 'Failed to load topics')}: {error?.message}
              </Alert>
            </Paper>
          )}

          {data && (
            <>
              {/* Heat Map View */}
              {viewMode === 'heatMap' && (
                <MultiTopicHeatMap
                  topics={data.data}
                  selectedTopicId={selectedTopic?.topicCode}
                  onTopicSelect={handleTopicSelect}
                  eventLookup={eventLookup}
                />
              )}

              {/* List View */}
              {viewMode === 'list' && (
                <Paper sx={{ p: 2, height: '70vh', overflow: 'auto' }}>
                  <TopicList
                    topics={data.data}
                    selectedTopicId={selectedTopic?.topicCode}
                    onTopicSelect={handleTopicSelect}
                    pagination={data.pagination}
                    onPageChange={(page) => setFilters((prev) => ({ ...prev, page }))}
                  />
                </Paper>
              )}

              {/* Board View (placeholder) */}
              {viewMode === 'board' && (
                <Paper
                  sx={{
                    p: 3,
                    height: '70vh',
                    display: 'flex',
                    justifyContent: 'center',
                    alignItems: 'center',
                  }}
                >
                  <Typography variant="body1" color="text.secondary">
                    {t('topicBacklog.viewMode.boardComingSoon', 'Board view coming soon...')}
                  </Typography>
                </Paper>
              )}
            </>
          )}
        </Grid>

        {/* Right Panel: Topic Details (4 cols → 3 cols when speaker panel visible) */}
        <Grid size={{ xs: 12, md: eventCode && topicConfirmed ? 3 : 4 }}>
          {selectedTopic ? (
            <TopicDetailsPanel
              topic={selectedTopic}
              eventCode={eventCode}
              onTopicConfirm={handleTopicConfirm}
              onEditTopic={handleEditTopic}
            />
          ) : (
            <Paper sx={{ p: 3, textAlign: 'center' }}>
              <Typography variant="body1" color="text.secondary">
                {t('topicBacklog.selectPrompt', 'Select a topic to view details')}
              </Typography>
            </Paper>
          )}
        </Grid>

        {/* NEW: Speaker Brainstorming Panel (3 cols, only shown when topic confirmed) */}
        {eventCode && topicConfirmed && selectedTopic && (
          <Grid size={{ xs: 12, md: 3 }}>
            <SpeakerBrainstormingPanel
              eventCode={eventCode}
              organizers={organizers}
              onContinue={handleSpeakerBrainstormComplete}
            />
          </Grid>
        )}
      </Grid>

      {/* Create/Edit Topic Modal */}
      <CreateTopicModal
        open={createModalOpen}
        topic={editingTopic}
        onClose={handleModalClose}
        onSuccess={() => {
          // Topics list will auto-refresh via query invalidation
        }}
      />
    </Container>
  );
};

export default TopicBacklogManager;
