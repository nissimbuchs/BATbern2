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
  FormControlLabel,
  Checkbox,
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

const WORKFLOW_STATE_OPTIONS = [
  'CREATED',
  'TOPIC_SELECTION',
  'SPEAKER_IDENTIFICATION',
  'SLOT_ASSIGNMENT',
  'AGENDA_PUBLISHED',
  'AGENDA_FINALIZED',
  'EVENT_LIVE',
  'EVENT_COMPLETED',
  'ARCHIVED',
];

export const EventSearch: React.FC<EventSearchProps> = ({ onFiltersChange, filters }) => {
  const { t } = useTranslation('events');
  const [searchParams, setSearchParams] = useSearchParams();

  const [searchQuery, setSearchQuery] = useState(filters.search || '');
  const [showArchived, setShowArchived] = useState(filters.includeArchived ?? false);

  // Parse URL params on mount
  useEffect(() => {
    const workflowStateParam = searchParams.get('workflowState');
    const yearParam = searchParams.get('year');
    const searchParam = searchParams.get('search');
    const includeArchivedParam = searchParams.get('includeArchived');

    const urlFilters: EventFilters = {};
    if (workflowStateParam) urlFilters.workflowState = workflowStateParam.split(',');
    if (yearParam) urlFilters.year = parseInt(yearParam, 10);
    if (searchParam) urlFilters.search = searchParam;
    if (includeArchivedParam !== null) {
      urlFilters.includeArchived = includeArchivedParam === 'true';
    }

    // Only apply URL filters if they exist
    if (Object.keys(urlFilters).length > 0) {
      onFiltersChange(urlFilters);
      if (searchParam) setSearchQuery(searchParam);
      if (includeArchivedParam !== null) {
        setShowArchived(includeArchivedParam === 'true');
      }
    }
  }, []); // Only on mount

  // Update URL when filters change
  useEffect(() => {
    const params = new URLSearchParams();
    if (filters.workflowState && filters.workflowState.length > 0) {
      params.set('workflowState', filters.workflowState.join(','));
    }
    if (filters.year) {
      params.set('year', filters.year.toString());
    }
    if (filters.search) {
      params.set('search', filters.search);
    }
    if (filters.includeArchived !== undefined) {
      params.set('includeArchived', filters.includeArchived.toString());
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

  const handleWorkflowStateChange = (event: SelectChangeEvent<string[]>) => {
    const value = event.target.value as string[];
    onFiltersChange({ ...filters, workflowState: value.length > 0 ? value : undefined });
  };

  const handleShowArchivedChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const checked = event.target.checked;
    setShowArchived(checked);

    // Update the includeArchived flag
    onFiltersChange({
      ...filters,
      includeArchived: checked,
    });
  };

  const handleRemoveWorkflowState = (workflowStateToRemove: string) => {
    const newWorkflowStates = (filters.workflowState || []).filter(
      (s) => s !== workflowStateToRemove
    );
    onFiltersChange({
      ...filters,
      workflowState: newWorkflowStates.length > 0 ? newWorkflowStates : undefined,
    });
  };

  const handleClearAll = () => {
    setSearchQuery('');
    setShowArchived(false);
    onFiltersChange({ includeArchived: false });
  };

  // Count active filters
  const activeFilterCount =
    (filters.workflowState?.length || 0) + (filters.year ? 1 : 0) + (filters.search ? 1 : 0);

  return (
    <Box data-testid="filter-container">
      <Stack spacing={2} data-testid="event-filters">
        {/* Row 1: Search and Filters */}
        <Stack spacing={2} direction={{ xs: 'column', sm: 'row' }} alignItems="stretch">
          {/* Search Input */}
          <FormControl fullWidth>
            <TextField
              data-testid="event-search-input"
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

          {/* Workflow State Filter */}
          <FormControl sx={{ minWidth: 200 }}>
            <InputLabel id="workflow-state-filter-label">
              {t('dashboard.filter.workflowState')}
            </InputLabel>
            <Select
              data-testid="filter-status"
              labelId="workflow-state-filter-label"
              multiple
              value={filters.workflowState || []}
              onChange={handleWorkflowStateChange}
              input={<OutlinedInput label={t('dashboard.filter.workflowState')} />}
              renderValue={(selected) => (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                  {selected.map((value) => (
                    <Chip
                      key={value}
                      label={t(`workflow.states.${value.toLowerCase()}`)}
                      size="small"
                      onDelete={() => handleRemoveWorkflowState(value)}
                      onMouseDown={(e) => e.stopPropagation()}
                      aria-label={`Remove ${t(`workflow.states.${value.toLowerCase()}`).toLowerCase()} filter`}
                    />
                  ))}
                </Box>
              )}
              aria-label="Filter by workflow state"
            >
              {WORKFLOW_STATE_OPTIONS.map((workflowState) => (
                <MenuItem key={workflowState} value={workflowState}>
                  {t(`workflow.states.${workflowState.toLowerCase()}`)}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          {/* Year Filter */}
          <FormControl sx={{ minWidth: 150 }}>
            <TextField
              data-testid="filter-year"
              type="number"
              label={t('dashboard.filter.year')}
              placeholder={t('dashboard.filter.allYears')}
              value={filters.year || ''}
              onChange={(e) => {
                const value = e.target.value ? parseInt(e.target.value, 10) : undefined;
                onFiltersChange({ ...filters, year: value });
              }}
              inputProps={{
                min: 2000,
                max: 2100,
                step: 1,
                'aria-label': 'Filter by year',
              }}
            />
          </FormControl>
        </Stack>

        {/* Row 2: Show Archived Toggle */}
        <Box>
          <FormControlLabel
            control={
              <Checkbox
                checked={showArchived}
                onChange={handleShowArchivedChange}
                data-testid="show-archived-checkbox"
              />
            }
            label={t('dashboard.filter.showArchived')}
          />
        </Box>
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
