/**
 * CompanyList Component
 *
 * Displays paginated list of companies with grid/list view toggle
 * - AC1: Company List Display
 * - Supports grid and list view modes
 * - Shows loading skeletons and empty states
 *
 * Story: 2.5.1 - Company Management Frontend
 */

import React from 'react';
import {
  Box,
  IconButton,
  Typography,
  Skeleton,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import ViewModuleIcon from '@mui/icons-material/ViewModule';
import ViewListIcon from '@mui/icons-material/ViewList';
import { useTranslation } from 'react-i18next';
import { CompanyCard } from '@/components/shared/Company/CompanyCard';
import type { CompanyListItem } from '@/types/company.types';

// Using MUI v7 Grid (Grid2 API) - cleaner syntax with size prop instead of item/xs/sm/md

export interface CompanyListProps {
  companies: CompanyListItem[];
  isLoading: boolean;
  viewMode: 'grid' | 'list';
  onViewModeToggle: () => void;
  onCompanyClick?: (companyId: string) => void;
}

export const CompanyList: React.FC<CompanyListProps> = ({
  companies,
  isLoading,
  viewMode,
  onViewModeToggle,
  onCompanyClick
}) => {
  const { t } = useTranslation('common');

  // Render loading skeletons
  if (isLoading) {
    return (
      <Box data-testid="company-list-container" data-view-mode={viewMode}>
        <Grid container spacing={2}>
          {[...Array(6)].map((_, index) => (
            <Grid size={{ xs: 12, sm: viewMode === 'grid' ? 6 : 12, md: viewMode === 'grid' ? 4 : 12 }} key={index}>
              <Skeleton
                variant="rectangular"
                height={viewMode === 'grid' ? 200 : 120}
                data-testid={`skeleton-${index}`}
              />
            </Grid>
          ))}
        </Grid>
      </Box>
    );
  }

  // Render empty state
  if (companies.length === 0) {
    return (
      <Box
        data-testid="company-list-container"
        data-view-mode={viewMode}
        sx={{
          textAlign: 'center',
          py: 8
        }}
      >
        <Typography variant="h6" color="text.secondary">
          {t('company.noCompaniesFound')}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          {t('company.adjustFilters')}
        </Typography>
      </Box>
    );
  }

  // Render company list
  return (
    <Box data-testid="company-list-container" data-view-mode={viewMode}>
      {/* View mode toggle */}
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <IconButton
          onClick={onViewModeToggle}
          aria-label={t('company.viewMode.toggleCurrent', { mode: viewMode })}
          size="small"
        >
          {viewMode === 'grid' ? <ViewListIcon /> : <ViewModuleIcon />}
        </IconButton>
      </Box>

      {/* Company grid/list */}
      <Grid container spacing={2}>
        {companies.map((company) => (
          <Grid
            size={{ xs: 12, sm: viewMode === 'grid' ? 6 : 12, md: viewMode === 'grid' ? 4 : 12 }}
            key={company.id}
          >
            <CompanyCard
              company={company}
              onClick={(id) => onCompanyClick?.(id)}
              viewMode={viewMode}
            />
          </Grid>
        ))}
      </Grid>
    </Box>
  );
};
