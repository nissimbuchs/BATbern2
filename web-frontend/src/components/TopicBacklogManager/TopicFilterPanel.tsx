/**
 * Topic Filter Panel Component (Story 5.2 - AC1)
 *
 * Filter controls for topic list:
 * - Category filter
 * - Status filter (available, caution, unavailable)
 * - Sort options
 */

import React from 'react';
import {
  Paper,
  Typography,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Box,
  SelectChangeEvent,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { TopicFilters } from '@/types/topic.types';

export interface TopicFilterPanelProps {
  filters: TopicFilters;
  onFilterChange: (filters: Partial<TopicFilters>) => void;
}

export const TopicFilterPanel: React.FC<TopicFilterPanelProps> = ({ filters, onFilterChange }) => {
  const { t } = useTranslation('organizer');

  const handleCategoryChange = (event: SelectChangeEvent<string>) => {
    onFilterChange({ category: event.target.value || undefined });
  };

  const handleStatusChange = (event: SelectChangeEvent<string>) => {
    onFilterChange({
      status: (event.target.value as 'available' | 'caution' | 'unavailable') || undefined,
    });
  };

  const handleSortChange = (event: SelectChangeEvent<string>) => {
    onFilterChange({ sort: event.target.value });
  };

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h6" gutterBottom>
        {t('topicBacklog.filters.title', 'Filters')}
      </Typography>

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}>
        {/* Category Filter */}
        <FormControl fullWidth size="small">
          <InputLabel>{t('topicBacklog.filters.category', 'Category')}</InputLabel>
          <Select
            value={filters.category || ''}
            onChange={handleCategoryChange}
            label={t('topicBacklog.filters.category', 'Category')}
          >
            <MenuItem value="">
              <em>{t('topicBacklog.filters.all', 'All Categories')}</em>
            </MenuItem>
            <MenuItem value="technical">
              {t('topicBacklog.filters.categories.technical', 'Technical')}
            </MenuItem>
            <MenuItem value="business">
              {t('topicBacklog.filters.categories.business', 'Business')}
            </MenuItem>
            <MenuItem value="architecture">
              {t('topicBacklog.filters.categories.architecture', 'Architecture')}
            </MenuItem>
            <MenuItem value="cloud-native">
              {t('topicBacklog.filters.categories.cloudNative', 'Cloud Native')}
            </MenuItem>
            <MenuItem value="security">
              {t('topicBacklog.filters.categories.security', 'Security')}
            </MenuItem>
          </Select>
        </FormControl>

        {/* Status Filter (AC3 - Color-coded freshness) */}
        <FormControl fullWidth size="small">
          <InputLabel>{t('topicBacklog.filters.status', 'Status')}</InputLabel>
          <Select
            value={filters.status || ''}
            onChange={handleStatusChange}
            label={t('topicBacklog.filters.status', 'Status')}
          >
            <MenuItem value="">
              <em>{t('topicBacklog.filters.allStatuses', 'All Statuses')}</em>
            </MenuItem>
            <MenuItem value="available">
              {t('topicBacklog.filters.statuses.available', 'Available (Green)')}
            </MenuItem>
            <MenuItem value="caution">
              {t('topicBacklog.filters.statuses.caution', 'Use with Caution (Yellow)')}
            </MenuItem>
            <MenuItem value="unavailable">
              {t('topicBacklog.filters.statuses.unavailable', 'Too Recent (Red)')}
            </MenuItem>
          </Select>
        </FormControl>

        {/* Sort Options */}
        <FormControl fullWidth size="small">
          <InputLabel>{t('topicBacklog.filters.sort', 'Sort By')}</InputLabel>
          <Select
            value={filters.sort || '-stalenessScore'}
            onChange={handleSortChange}
            label={t('topicBacklog.filters.sort', 'Sort By')}
          >
            <MenuItem value="-stalenessScore">
              {t('topicBacklog.filters.sortOptions.safestFirst', 'Safest First (Staleness ↓)')}
            </MenuItem>
            <MenuItem value="stalenessScore">
              {t('topicBacklog.filters.sortOptions.recentFirst', 'Recent First (Staleness ↑)')}
            </MenuItem>
            <MenuItem value="title">
              {t('topicBacklog.filters.sortOptions.titleAZ', 'Title (A-Z)')}
            </MenuItem>
            <MenuItem value="-usageCount">
              {t('topicBacklog.filters.sortOptions.mostUsed', 'Most Used')}
            </MenuItem>
          </Select>
        </FormControl>
      </Box>
    </Paper>
  );
};

export default TopicFilterPanel;
