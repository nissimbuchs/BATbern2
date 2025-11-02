/**
 * EventSearch Component
 *
 * Story 2.5.3 - Task 8b (GREEN Phase)
 * AC: 2 (Event List & Filters)
 * Wireframe: docs/wireframes/story-1.16-event-management-dashboard.md v1.0
 *
 * Event search and filters with URL persistence
 */

import React, { useEffect, useState } from 'react';
import {
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Stack,
  Chip,
  Button,
  InputAdornment,
  IconButton,
  Box,
  SelectChangeEvent,
  OutlinedInput,
} from '@mui/material';
import {
  Search as SearchIcon,
  Clear as ClearIcon,
  FilterList as FilterListIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { useDebounce } from '@/hooks/useDebounce';
import type { EventFilters } from '@/types/event.types';

interface EventSearchProps {
  onFiltersChange: (filters: EventFilters) => void;
  filters: EventFilters;
}

const STATUS_OPTIONS = ['active', 'published', 'completed', 'archived'];
const YEAR_OPTIONS = [2025, 2024, 2023, 2022];

export const EventSearch: React.FC<EventSearchProps> = ({ onFiltersChange, filters }) => {
  const { t } = useTranslation('events');
  const [searchParams, setSearchParams] = useSearchParams();

  const [searchQuery, setSearchQuery] = useState(filters.search || '');

  // Parse URL params on mount
  useEffect(() => {
    const statusParam = searchParams.get('status');
    const yearParam = searchParams.get('year');
    const searchParam = searchParams.get('search');

    const urlFilters: EventFilters = {};
    if (statusParam) urlFilters.status = statusParam.split(',');
    if (yearParam) urlFilters.year = parseInt(yearParam, 10);
    if (searchParam) urlFilters.search = searchParam;

    if (Object.keys(urlFilters).length > 0) {
      onFiltersChange(urlFilters);
      if (searchParam) setSearchQuery(searchParam);
    }
  }, []); // Only on mount

  // Update URL when filters change
  useEffect(() => {
    const params = new URLSearchParams();
    if (filters.status && filters.status.length > 0) {
      params.set('status', filters.status.join(','));
    }
    if (filters.year) {
      params.set('year', filters.year.toString());
    }
    if (filters.search) {
      params.set('search', filters.search);
    }
    setSearchParams(params);
  }, [filters, setSearchParams]);

  // Debounced search
  const debouncedSearchQuery = useDebounce(searchQuery, 300);

  useEffect(() => {
    onFiltersChange({ ...filters, search: debouncedSearchQuery });
  }, [debouncedSearchQuery]);

  const handleSearchChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;
    setSearchQuery(value);
  };

  const handleClearSearch = () => {
    setSearchQuery('');
    onFiltersChange({ ...filters, search: '' });
  };

  const handleStatusChange = (event: SelectChangeEvent<string[]>) => {
    const value = event.target.value as string[];
    onFiltersChange({ ...filters, status: value });
  };

  const handleYearChange = (event: SelectChangeEvent<number>) => {
    const value = event.target.value as number;
    onFiltersChange({ ...filters, year: value });
  };

  const handleRemoveStatus = (statusToRemove: string) => {
    const newStatus = (filters.status || []).filter((s) => s !== statusToRemove);
    onFiltersChange({ ...filters, status: newStatus.length > 0 ? newStatus : undefined });
  };

  const handleClearAll = () => {
    setSearchQuery('');
    onFiltersChange({});
  };

  // Count active filters
  const activeFilterCount =
    (filters.status?.length || 0) + (filters.year ? 1 : 0) + (filters.search ? 1 : 0);

  return (
    <Box data-testid="filter-container">
      <Stack spacing={2} direction={{ xs: 'column', sm: 'row' }} alignItems="stretch">
        {/* Search Input */}
        <FormControl fullWidth>
          <TextField
            placeholder={t('dashboard.searchEvents')}
            value={searchQuery}
            onChange={handleSearchChange}
            aria-label="Search events"
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon data-testid="search-icon" />
                </InputAdornment>
              ),
              endAdornment: searchQuery && (
                <InputAdornment position="end">
                  <IconButton onClick={handleClearSearch} size="small" aria-label="Clear search">
                    <ClearIcon />
                  </IconButton>
                </InputAdornment>
              ),
            }}
          />
        </FormControl>

        {/* Status Filter */}
        <FormControl sx={{ minWidth: 200 }}>
          <InputLabel id="status-filter-label">{t('dashboard.filter.status')}</InputLabel>
          <Select
            labelId="status-filter-label"
            multiple
            value={filters.status || []}
            onChange={handleStatusChange}
            input={<OutlinedInput label={t('dashboard.filter.status')} />}
            renderValue={(selected) => (
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                {selected.map((value) => (
                  <Chip
                    key={value}
                    label={t(`dashboard.status.${value}`)}
                    size="small"
                    onDelete={() => handleRemoveStatus(value)}
                    onMouseDown={(e) => e.stopPropagation()}
                    aria-label={`Remove ${value} filter`}
                  />
                ))}
              </Box>
            )}
            aria-label="Filter by status"
          >
            {STATUS_OPTIONS.map((status) => (
              <MenuItem key={status} value={status}>
                {t(`dashboard.status.${status}`)}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        {/* Year Filter */}
        <FormControl sx={{ minWidth: 150 }}>
          <InputLabel id="year-filter-label">{t('dashboard.filter.year')}</InputLabel>
          <Select
            labelId="year-filter-label"
            value={filters.year || ''}
            onChange={handleYearChange}
            label={t('dashboard.filter.year')}
            aria-label="Filter by year"
          >
            <MenuItem value="">
              <em>{t('dashboard.filter.allYears')}</em>
            </MenuItem>
            {YEAR_OPTIONS.map((year) => (
              <MenuItem key={year} value={year}>
                {year}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </Stack>

      {/* Active Filters & Clear All */}
      {activeFilterCount > 0 && (
        <Stack direction="row" spacing={2} alignItems="center" mt={2}>
          <Chip
            icon={<FilterListIcon />}
            label={t('dashboard.filter.activeFilters', { count: activeFilterCount })}
            size="small"
            variant="outlined"
            aria-label={`${activeFilterCount} filters active`}
          />
          <Button
            size="small"
            onClick={handleClearAll}
            startIcon={<ClearIcon />}
            aria-label="Clear all filters"
          >
            {t('dashboard.filter.clearAll')}
          </Button>
        </Stack>
      )}
    </Box>
  );
};
