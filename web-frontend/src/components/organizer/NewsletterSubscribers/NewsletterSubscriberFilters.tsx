/**
 * NewsletterSubscriberFilters — Filters panel
 *
 * Mirrors UserFilters.tsx
 *
 * Story 10.28: Newsletter Subscriber Management Page
 */

import React, { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Button,
  FormControlLabel,
  Paper,
  Radio,
  RadioGroup,
  TextField,
  Typography,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useNewsletterSubscriberStore } from '@/stores/newsletterSubscriberStore';

const NewsletterSubscriberFilters: React.FC = () => {
  const { t } = useTranslation('newsletterSubscribers');
  const { filters, setSearchQuery, setFilters, resetFilters } = useNewsletterSubscriberStore();
  const [localSearch, setLocalSearch] = useState(filters.searchQuery ?? '');

  // Debounced search
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearchQuery(localSearch);
    }, 300);
    return () => clearTimeout(timer);
  }, [localSearch, setSearchQuery]);

  // Sync local search when filters are reset externally
  useEffect(() => {
    setLocalSearch(filters.searchQuery ?? '');
  }, [filters.searchQuery]);

  const handleStatusChange = useCallback(
    (_: React.ChangeEvent<HTMLInputElement>, value: string) => {
      setFilters({ status: value as 'all' | 'active' | 'unsubscribed' });
    },
    [setFilters]
  );

  return (
    <Paper sx={{ p: 2, mb: 3 }}>
      <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
        <TextField
          size="small"
          placeholder={t('search.placeholder')}
          value={localSearch}
          onChange={(e) => setLocalSearch(e.target.value)}
          data-testid="subscriber-search-input"
          sx={{ minWidth: 250 }}
        />

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {t('filters.status.label')}:
          </Typography>
          <RadioGroup
            row
            value={filters.status ?? 'all'}
            onChange={handleStatusChange}
            data-testid="subscriber-status-filter"
          >
            <FormControlLabel
              value="all"
              control={<Radio size="small" />}
              label={t('filters.status.all')}
            />
            <FormControlLabel
              value="active"
              control={<Radio size="small" />}
              label={t('filters.status.active')}
            />
            <FormControlLabel
              value="unsubscribed"
              control={<Radio size="small" />}
              label={t('filters.status.unsubscribed')}
            />
          </RadioGroup>
        </Box>

        <Button
          variant="text"
          size="small"
          onClick={resetFilters}
          data-testid="subscriber-clear-filters"
        >
          {t('filters.clearAll')}
        </Button>
      </Box>
    </Paper>
  );
};

export default NewsletterSubscriberFilters;
