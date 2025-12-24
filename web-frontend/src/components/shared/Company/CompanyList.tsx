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
import { Box, Typography, Skeleton } from '@mui/material';
import Grid from '@mui/material/Grid';
import { useTranslation } from 'react-i18next';
import { CompanyCard } from '@/components/shared/Company/CompanyCard';
import type { CompanyListItem } from '@/types/company.types';

// Using MUI v7 Grid (Grid2 API) - cleaner syntax with size prop instead of item/xs/sm/md

export interface CompanyListProps {
  companies: CompanyListItem[];
  isLoading: boolean;
  viewMode: 'grid' | 'list';
  onCompanyClick?: (companyId: string) => void;
}

export const CompanyList: React.FC<CompanyListProps> = ({
  companies,
  isLoading,
  viewMode,
  onCompanyClick,
}) => {
  const { t } = useTranslation('common');

  // Render loading skeletons
  if (isLoading) {
    return (
      <Box data-testid="company-list-container" data-view-mode={viewMode}>
        <Grid container spacing={2}>
          {[...Array(6)].map((_, index) => (
            <Grid
              size={{ xs: 12, sm: viewMode === 'grid' ? 6 : 12, md: viewMode === 'grid' ? 4 : 12 }}
              key={index}
            >
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
          py: 8,
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
      {/* Company grid/list */}
      <Grid container spacing={2}>
        {companies.map((company) => (
          <Grid
            size={{ xs: 12, sm: viewMode === 'grid' ? 6 : 12, md: viewMode === 'grid' ? 4 : 12 }}
            key={company.name}
          >
            <CompanyCard
              company={company}
              onClick={(name) => onCompanyClick?.(name)}
              viewMode={viewMode}
            />
          </Grid>
        ))}
      </Grid>
    </Box>
  );
};
