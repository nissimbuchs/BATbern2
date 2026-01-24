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
import type { SelectChangeEvent } from '@mui/material';
import {
  Add as AddIcon,
  ViewModule as GridViewIcon,
  ViewList as ListViewIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { usePartners } from '@/hooks/usePartners';
import { usePartnerStore } from '@/stores/partnerStore';
import { usePartnerModalStore } from '@/stores/partnerModalStore';
import { PartnerOverviewStats } from './PartnerOverviewStats';
import { PartnerSearch } from './PartnerSearch';
import { PartnerFilters } from './PartnerFilters';
import { PartnerList } from './PartnerList';
import { PartnerCreateEditModal } from './PartnerCreateEditModal';

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
  const { t } = useTranslation('partners');
  const { filters, viewMode, sortBy, sortOrder, page, setViewMode, setSortBy } = usePartnerStore();
  const { openCreateModal } = usePartnerModalStore();

  // Fetch partners with current filters, sort, and pagination
  const { data: partnersData, isLoading: isLoadingPartners } = usePartners(
    filters,
    { sortBy, sortOrder },
    { page, size: 20 }
  );

  const handleViewModeChange = (
    _event: React.MouseEvent<HTMLElement>,
    newViewMode: 'grid' | 'list' | null
  ) => {
    if (newViewMode !== null) {
      setViewMode(newViewMode);
    }
  };

  const handleSortChange = (event: SelectChangeEvent<string>) => {
    const value = event.target.value as 'engagement' | 'name' | 'tier' | 'lastEvent';
    setSortBy(value);
  };

  const handleAddPartner = () => {
    openCreateModal();
  };

  return (
    <Box sx={{ flexGrow: 1, p: 3 }}>
      <Container maxWidth="xl" data-testid="partner-directory-screen">
        {/* Header */}
        <Box sx={{ mb: 3 }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Typography variant="h4" component="h1">
              {t('title')}
            </Typography>
            <Button
              variant="contained"
              color="primary"
              startIcon={<AddIcon />}
              onClick={handleAddPartner}
              data-testid="add-partner-button"
            >
              {t('addPartner')}
            </Button>
          </Stack>
        </Box>

        {/* Overview Statistics */}
        <Box sx={{ mb: 3 }}>
          <PartnerOverviewStats />
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
                  <InputLabel id="sort-select-label">{t('sort.label')}</InputLabel>
                  <Select
                    labelId="sort-select-label"
                    id="sort-select"
                    value={sortBy}
                    label={t('sort.label')}
                    onChange={handleSortChange}
                    data-testid="partner-sort-select"
                  >
                    <MenuItem value="engagement" data-testid="sort-option-engagement">
                      {t('sort.engagement')}
                    </MenuItem>
                    <MenuItem value="name" data-testid="sort-option-name">
                      {t('sort.name')}
                    </MenuItem>
                    <MenuItem value="tier" data-testid="sort-option-tier">
                      {t('sort.tier')}
                    </MenuItem>
                    <MenuItem value="lastEvent" data-testid="sort-option-lastEvent">
                      {t('sort.lastEvent')}
                    </MenuItem>
                  </Select>
                </FormControl>

                {/* View Mode Toggle */}
                <ToggleButtonGroup
                  value={viewMode}
                  exclusive
                  onChange={handleViewModeChange}
                  aria-label={t('viewMode.label')}
                  size="small"
                  data-testid="view-mode-toggle"
                >
                  <ToggleButton value="grid" aria-label={t('viewMode.grid')}>
                    <GridViewIcon />
                  </ToggleButton>
                  <ToggleButton value="list" aria-label={t('viewMode.list')}>
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
          aria-label={t('searchAriaLabel')}
          aria-live="polite"
          aria-atomic="true"
          sx={{
            position: 'absolute',
            left: '-10000px',
            width: '1px',
            height: '1px',
            overflow: 'hidden',
          }}
        >
          {partnersData?.data &&
            t('screenReader.searchResults', { count: partnersData.data.length || 0 })}
        </Box>

        <Box
          role="status"
          aria-label={t('screenReader.filterUpdate')}
          aria-live="polite"
          sx={{
            position: 'absolute',
            left: '-10000px',
            width: '1px',
            height: '1px',
            overflow: 'hidden',
          }}
        >
          {filters.tier !== 'all' && `Filtered by tier: ${filters.tier}`}
          {filters.status !== 'all' && ` Status: ${filters.status}`}
        </Box>

        <Box
          role="status"
          aria-label={t('screenReader.loading')}
          aria-live="polite"
          sx={{
            position: 'absolute',
            left: '-10000px',
            width: '1px',
            height: '1px',
            overflow: 'hidden',
          }}
        >
          {isLoadingPartners && t('loading')}
        </Box>

        {/* Create/Edit Partner Modal */}
        <PartnerCreateEditModal />
      </Container>
    </Box>
  );
};
