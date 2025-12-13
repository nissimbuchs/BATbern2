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
import { Container, Grid, Paper, Typography, Box, CircularProgress, Alert } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useTopics } from '@/hooks/useTopics';
import { TopicFilterPanel } from './TopicFilterPanel';
import { TopicList } from './TopicList';
import { TopicDetailsPanel } from './TopicDetailsPanel';
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
  const [filters, setFilters] = useState<TopicFilters>({
    page: 1,
    limit: 20,
    sort: '-stalenessScore', // Default: sort by staleness descending (safest first)
  });
  const [selectedTopic, setSelectedTopic] = useState<Topic | null>(null);

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
  };

  return (
    <Container maxWidth="xl" sx={{ mt: 4, mb: 4 }}>
      <Typography variant="h4" gutterBottom>
        {t('topicBacklog.title', 'Topic Backlog Manager')}
      </Typography>
      <Typography variant="body1" color="text.secondary" paragraph>
        {t(
          'topicBacklog.subtitle',
          'Select topics from the backlog with intelligent suggestions and staleness detection'
        )}
      </Typography>

      <Grid container spacing={3}>
        {/* Left Panel: Filters */}
        <Grid size={{ xs: 12, md: 3 }}>
          <TopicFilterPanel filters={filters} onFilterChange={handleFilterChange} />
        </Grid>

        {/* Center Panel: Topic List */}
        <Grid size={{ xs: 12, md: 5 }}>
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

        {/* Right Panel: Topic Details */}
        <Grid size={{ xs: 12, md: 4 }}>
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
      </Grid>
    </Container>
  );
};

export default TopicBacklogManager;
