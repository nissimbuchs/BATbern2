/**
 * Topic List Component (Story 5.2 - AC1, AC3)
 *
 * Displays list of topics with:
 * - Color-coded staleness indicators
 * - Usage count and last used date
 * - Selection highlighting
 * - Pagination
 */

import React from 'react';
import {
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  Chip,
  Box,
  Typography,
  Pagination,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { Topic, PaginationMetadata } from '@/types/topic.types';

export interface TopicListProps {
  topics: Topic[];
  selectedTopicId?: string;
  onTopicSelect: (topic: Topic) => void;
  pagination: PaginationMetadata;
  onPageChange: (page: number) => void;
}

/**
 * Get color for staleness score (AC3)
 * - Red (<50): Too recent, high risk
 * - Yellow (50-83): Use with caution
 * - Green (>83): Safe to reuse
 */
const getColorForStaleness = (score: number): 'error' | 'warning' | 'success' => {
  if (score < 50) return 'error';
  if (score <= 83) return 'warning';
  return 'success';
};

/**
 * Map database category names (snake_case) to translation keys (camelCase)
 */
const getCategoryTranslationKey = (category: string): string => {
  const categoryMap: Record<string, string> = {
    technical: 'technical',
    management: 'management',
    soft_skills: 'softSkills',
    industry_trends: 'industryTrends',
    tools_platforms: 'toolsPlatforms',
  };
  return categoryMap[category] || category;
};

export const TopicList: React.FC<TopicListProps> = ({
  topics,
  selectedTopicId,
  onTopicSelect,
  pagination,
  onPageChange,
}) => {
  const { t } = useTranslation('organizer');

  if (topics.length === 0) {
    return (
      <Box sx={{ textAlign: 'center', py: 4 }}>
        <Typography variant="body1" color="text.secondary">
          {t('topicBacklog.noTopics', 'No topics found matching your filters')}
        </Typography>
      </Box>
    );
  }

  return (
    <>
      <List>
        {topics.map((topic) => (
          <ListItem key={topic.topicCode} disablePadding>
            <ListItemButton
              selected={topic.topicCode === selectedTopicId}
              onClick={() => onTopicSelect(topic)}
              sx={{
                borderLeft: 4,
                borderColor:
                  topic.colorZone === 'red'
                    ? 'error.main'
                    : topic.colorZone === 'yellow'
                      ? 'warning.main'
                      : topic.colorZone === 'green'
                        ? 'success.main'
                        : 'grey.300',
                mb: 1,
              }}
            >
              <ListItemText
                primary={
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant="subtitle1">{topic.title}</Typography>
                    <Chip
                      label={`${topic.stalenessScore}%`}
                      size="small"
                      color={getColorForStaleness(topic.stalenessScore)}
                    />
                  </Box>
                }
                secondary={
                  <>
                    <Typography component="span" variant="body2" color="text.secondary">
                      {t(
                        `topicBacklog.filters.categories.${getCategoryTranslationKey(topic.category)}`,
                        topic.category
                      )}
                      {topic.lastUsedDate && (
                        <>
                          {' • '}
                          {t('topicBacklog.lastUsed', 'Last used')}:{' '}
                          {new Date(topic.lastUsedDate).toLocaleDateString()}
                        </>
                      )}
                      {topic.usageCount && topic.usageCount > 0 && (
                        <>
                          {' • '}
                          {t('topicBacklog.usageCount', 'Used {{count}} times', {
                            count: topic.usageCount,
                          })}
                        </>
                      )}
                    </Typography>
                  </>
                }
              />
            </ListItemButton>
          </ListItem>
        ))}
      </List>

      {/* Pagination */}
      {pagination.total > pagination.limit && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
          <Pagination
            count={Math.ceil(pagination.total / pagination.limit)}
            page={pagination.page}
            onChange={(_, page) => onPageChange(page)}
            color="primary"
          />
        </Box>
      )}
    </>
  );
};

export default TopicList;
