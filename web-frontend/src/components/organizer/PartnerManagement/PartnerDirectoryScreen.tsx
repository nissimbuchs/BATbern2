import React from 'react';
import {
  Box,
  Container,
  Typography,
  Button,
  Stack,
  Paper,
  ToggleButton,
  ToggleButtonGroup,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from '@mui/material';
import {
  Add as AddIcon,
  ViewModule as GridViewIcon,
  ViewList as ListViewIcon,
} from '@mui/icons-material';
import { usePartners, usePartnerStatistics } from '@/hooks/usePartners';
import { usePartnerStore } from '@/stores/partnerStore';
import { PartnerOverviewStats } from './PartnerOverviewStats';
import { PartnerSearch } from './PartnerSearch';
import { PartnerFilters } from './PartnerFilters';
import { PartnerList } from './PartnerList';

/**
 * Partner Directory Screen - Main partner management interface for organizers
 *
 * Features:
 * - Partner overview statistics
 * - Search and filtering
 * - Grid/List view toggle
 * - Sort options
 * - Paginated partner list
 *
 * Related: Story 2.8.1, AC1, AC5
 */
export const PartnerDirectoryScreen: React.FC = () => {
  const {
    filters,
    viewMode,
    searchQuery,
    sortBy,
    sortOrder,
    page,
    setViewMode,
    setSortBy,
    setSortOrder,
  } = usePartnerStore();

  // Fetch partners with current filters, sort, and pagination
  const {
    data: partnersData,
    isLoading: isLoadingPartners,
    isError: isPartnersError,
    error: partnersError,
  } = usePartners(
    filters,
    { sortBy, sortOrder },
    { page, size: 20 }
  );

  // Fetch partner statistics
  const {
    data: statisticsData,
    isLoading: isLoadingStatistics,
  } = usePartnerStatistics();

  const handleViewModeChange = (
    _event: React.MouseEvent<HTMLElement>,
    newViewMode: 'grid' | 'list' | null
  ) => {
    if (newViewMode !== null) {
      setViewMode(newViewMode);
    }
  };

  const handleSortChange = (event: any) => {
    const value = event.target.value as string;
    setSortBy(value as any);
  };

  const handleAddPartner = () => {
    // Story 2.8.3 - Create Partner Modal
    console.log('Add Partner - Coming Soon in Story 2.8.3');
  };

  return (
    <Container maxWidth="xl" data-testid="partner-directory-screen">
      {/* Header */}
      <Box sx={{ mb: 3, mt: 3 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant="h4" component="h1">
            Partner Directory
          </Typography>
          <Button
            variant="contained"
            color="primary"
            startIcon={<AddIcon />}
            onClick={handleAddPartner}
            disabled
            title="Coming Soon - Story 2.8.3"
            data-testid="add-partner-button"
          >
            Add Partner
          </Button>
        </Stack>
      </Box>

      {/* Overview Statistics */}
      <Box sx={{ mb: 3 }}>
        <PartnerOverviewStats
          statistics={statisticsData}
          isLoading={isLoadingStatistics}
        />
      </Box>

      {/* Search and Filter Controls */}
      <Paper sx={{ p: 2, mb: 3 }} data-testid="filter-panel" aria-expanded="true">
        <Stack spacing={2}>
          {/* Search Bar */}
          <Box>
            <PartnerSearch />
          </Box>

          {/* Filters and View Controls */}
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            spacing={2}
            alignItems={{ xs: 'stretch', md: 'center' }}
            justifyContent="space-between"
            data-testid="filter-container"
            sx={{ flexDirection: { xs: 'column', md: 'row' } }}
          >
            {/* Left: Filters */}
            <Box sx={{ flex: 1 }}>
              <PartnerFilters />
            </Box>

            {/* Right: View Mode Toggle and Sort */}
            <Stack direction="row" spacing={2} alignItems="center">
              {/* Sort Select */}
              <FormControl size="small" sx={{ minWidth: 200 }}>
                <InputLabel id="sort-select-label">Sort By</InputLabel>
                <Select
                  labelId="sort-select-label"
                  id="sort-select"
                  value={sortBy}
                  label="Sort By"
                  onChange={handleSortChange}
                  data-testid="partner-sort-select"
                >
                  <MenuItem value="engagement">Engagement Score</MenuItem>
                  <MenuItem value="name">Company Name</MenuItem>
                  <MenuItem value="tier">Partnership Tier</MenuItem>
                  <MenuItem value="lastEvent">Last Event</MenuItem>
                </Select>
              </FormControl>

              {/* View Mode Toggle */}
              <ToggleButtonGroup
                value={viewMode}
                exclusive
                onChange={handleViewModeChange}
                aria-label="view mode"
                size="small"
                data-testid="view-mode-toggle"
              >
                <ToggleButton value="grid" aria-label="grid view">
                  <GridViewIcon />
                </ToggleButton>
                <ToggleButton value="list" aria-label="list view">
                  <ListViewIcon />
                </ToggleButton>
              </ToggleButtonGroup>
            </Stack>
          </Stack>
        </Stack>
      </Paper>

      {/* Partner List */}
      <Box>
        <PartnerList />
      </Box>

      {/* Screen Reader Announcements (aria-live regions) */}
      <Box
        role="status"
        aria-label="search results"
        aria-live="polite"
        aria-atomic="true"
        sx={{ position: 'absolute', left: '-10000px', width: '1px', height: '1px', overflow: 'hidden' }}
      >
        {partnersData?.partners && `${partnersData.pagination?.totalElements || 0} ${partnersData.pagination?.totalElements === 1 ? 'partner' : 'partners'} found`}
      </Box>

      <Box
        role="status"
        aria-label="filter update"
        aria-live="polite"
        sx={{ position: 'absolute', left: '-10000px', width: '1px', height: '1px', overflow: 'hidden' }}
      >
        {filters.tier !== 'all' && `Filtered by tier: ${filters.tier}`}
        {filters.status !== 'all' && ` Status: ${filters.status}`}
      </Box>

      <Box
        role="status"
        aria-label="loading"
        aria-live="polite"
        sx={{ position: 'absolute', left: '-10000px', width: '1px', height: '1px', overflow: 'hidden' }}
      >
        {isLoadingPartners && 'Loading partners'}
      </Box>
    </Container>
  );
};
