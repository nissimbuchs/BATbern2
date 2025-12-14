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

import React, { useState } from 'react';
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
} from '@mui/material';
import { Add as AddIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useTopics } from '@/hooks/useTopics';
import { TopicFilterPanel } from './TopicFilterPanel';
import { TopicList } from './TopicList';
import { TopicDetailsPanel } from './TopicDetailsPanel';
import { CreateTopicModal } from './CreateTopicModal';
import { SpeakerBrainstormingPanel } from '@/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel';
import type { Topic, TopicFilters } from '@/types/topic.types';

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
  });
  const [selectedTopic, setSelectedTopic] = useState<Topic | null>(null);
  const [topicConfirmed, setTopicConfirmed] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);

  // Fetch topics with current filters
  const { data, isLoading, isError, error } = useTopics(filters);

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
    // Navigate back to event details if eventCode exists, otherwise to events dashboard
    if (eventCode) {
      navigate(`/organizer/events/${eventCode}`);
    } else {
      navigate('/organizer/events');
    }
  };

  return (
    <Container maxWidth="xl" sx={{ mt: 4, mb: 4 }}>
      <Box
        sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}
      >
        <Box>
          <Typography variant="h4" gutterBottom>
            {t('topicBacklog.title', 'Topic Backlog Manager')}
          </Typography>
          <Typography variant="body1" color="text.secondary" paragraph>
            {t(
              'topicBacklog.subtitle',
              'Select topics from the backlog with intelligent suggestions and staleness detection'
            )}
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setCreateModalOpen(true)}
          sx={{ mt: 1 }}
        >
          {t('topicBacklog.createNew', 'Create New Topic')}
        </Button>
      </Box>

      <Grid container spacing={3}>
        {/* Left Panel: Filters (3 cols → 2 cols when speaker panel visible) */}
        <Grid size={{ xs: 12, md: eventCode && topicConfirmed ? 2 : 3 }}>
          <TopicFilterPanel filters={filters} onFilterChange={handleFilterChange} />
        </Grid>

        {/* Center Panel: Topic List (5 cols → 4 cols when speaker panel visible) */}
        <Grid size={{ xs: 12, md: eventCode && topicConfirmed ? 4 : 5 }}>
          <Paper sx={{ p: 2, height: '70vh', overflow: 'auto' }}>
            {isLoading && (
              <Box display="flex" justifyContent="center" alignItems="center" minHeight={200}>
                <CircularProgress />
              </Box>
            )}

            {isError && (
              <Alert severity="error">
                {t('topicBacklog.error.loadFailed', 'Failed to load topics')}: {error?.message}
              </Alert>
            )}

            {data && (
              <TopicList
                topics={data.data}
                selectedTopicId={selectedTopic?.id}
                onTopicSelect={handleTopicSelect}
                pagination={data.pagination}
                onPageChange={(page) => setFilters((prev) => ({ ...prev, page }))}
              />
            )}
          </Paper>
        </Grid>

        {/* Right Panel: Topic Details (4 cols → 3 cols when speaker panel visible) */}
        <Grid size={{ xs: 12, md: eventCode && topicConfirmed ? 3 : 4 }}>
          {selectedTopic ? (
            <TopicDetailsPanel
              topic={selectedTopic}
              eventCode={eventCode}
              onTopicConfirm={handleTopicConfirm}
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
              organizers={[]} // Can fetch from event details if needed
              onContinue={handleSpeakerBrainstormComplete}
            />
          </Grid>
        )}
      </Grid>

      {/* Create Topic Modal */}
      <CreateTopicModal
        open={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        onSuccess={() => {
          // Topics list will auto-refresh via query invalidation
        }}
      />
    </Container>
  );
};

export default TopicBacklogManager;
