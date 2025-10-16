/**
 * CompanyFilters - Filter panel for company list
 *
 * Features:
 * - Verification status filter (checkbox)
 * - Industry filter (select)
 * - Clear all filters button
 * - URL persistence
 * - Responsive collapsible design
 * - Active filter count badge
 */

import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Box,
  FormControl,
  FormControlLabel,
  Checkbox,
  Select,
  MenuItem,
  InputLabel,
  Button,
  Stack,
  Chip,
  useTheme,
  useMediaQuery,
  Collapse,
  IconButton,
  Typography
} from '@mui/material';
import { FilterList as FilterIcon, ExpandMore as ExpandMoreIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { CompanyFilters as CompanyFiltersType } from '@/types/company.types';

interface CompanyFiltersProps {
  onFilterChange: (filters: CompanyFiltersType) => void;
  initialFilters?: CompanyFiltersType;
}

const INDUSTRIES = [
  'Cloud Computing',
  'DevOps',
  'Financial Services',
  'Healthcare',
  'Manufacturing',
  'Retail',
  'Education',
  'Government',
  'Technology',
  'Consulting'
];

const CompanyFilters: React.FC<CompanyFiltersProps> = ({ onFilterChange, initialFilters = {} }) => {
  const { t } = useTranslation('common');
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [searchParams, setSearchParams] = useSearchParams();

  const [isVerified, setIsVerified] = useState(initialFilters?.isVerified || false);
  const [industry, setIndustry] = useState(initialFilters?.industry || '');
  const [isExpanded, setIsExpanded] = useState(!isMobile);

  // Load filters from URL on mount
  useEffect(() => {
    const verifiedParam = searchParams.get('isVerified');
    const industryParam = searchParams.get('industry');

    if (verifiedParam) setIsVerified(verifiedParam === 'true');
    if (industryParam) setIndustry(industryParam);
  }, [searchParams]);

  // Update URL and notify parent when filters change
  useEffect(() => {
    const filters: CompanyFiltersType = {};

    if (isVerified) filters.isVerified = true;
    if (industry) filters.industry = industry;

    // Update URL
    const newParams = new URLSearchParams();
    if (isVerified) newParams.set('isVerified', 'true');
    if (industry) newParams.set('industry', industry);
    setSearchParams(newParams);

    // Notify parent
    onFilterChange(filters);
  }, [isVerified, industry, onFilterChange, setSearchParams]);

  const handleClearFilters = () => {
    setIsVerified(false);
    setIndustry('');
    setSearchParams(new URLSearchParams());
    onFilterChange({});
  };

  const activeFilterCount = [isVerified, industry].filter(Boolean).length;
  const hasActiveFilters = activeFilterCount > 0;

  const filterContent = (
    <Stack direction="row" spacing={2} flexWrap="wrap" alignItems="center">
      {/* Verification Filter */}
      <FormControlLabel
        control={
          <Checkbox
            checked={isVerified}
            onChange={(e) => setIsVerified(e.target.checked)}
            aria-label={t('company.filters.verifiedOnly')}
          />
        }
        label={t('company.filters.verifiedOnly')}
      />

      {/* Industry Filter */}
      <FormControl sx={{ minWidth: 200 }} size="small">
        <InputLabel id="industry-filter-label">{t('company.filters.industry')}</InputLabel>
        <Select
          labelId="industry-filter-label"
          id="industry-filter"
          value={industry}
          label={t('company.filters.industry')}
          onChange={(e) => setIndustry(e.target.value)}
          aria-label={t('company.filters.industry')}
        >
          <MenuItem value="">
            <em>{t('company.filters.allIndustries')}</em>
          </MenuItem>
          {INDUSTRIES.map((ind) => (
            <MenuItem key={ind} value={ind}>
              {ind}
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      {/* Clear Filters Button */}
      <Button
        variant="outlined"
        size="small"
        onClick={handleClearFilters}
        disabled={!hasActiveFilters}
        aria-label={t('company.filters.clearAll')}
      >
        {t('company.filters.clearAll')}
      </Button>

      {/* Active Filter Count Badge */}
      {hasActiveFilters && (
        <Chip
          label={activeFilterCount}
          color="primary"
          size="small"
          sx={{ fontWeight: 'bold' }}
        />
      )}
    </Stack>
  );

  if (isMobile) {
    return (
      <Box data-testid="company-filters">
        {/* Mobile: Collapsible Filters */}
        <Stack direction="row" alignItems="center" spacing={1} mb={1}>
          <IconButton
            onClick={() => setIsExpanded(!isExpanded)}
            aria-label={t('company.filters.filters')}
            size="small"
          >
            <FilterIcon />
          </IconButton>
          <Typography variant="body2">{t('company.filters.filters')}</Typography>
          {hasActiveFilters && (
            <Chip
              label={activeFilterCount}
              color="primary"
              size="small"
            />
          )}
          <IconButton
            onClick={() => setIsExpanded(!isExpanded)}
            size="small"
            sx={{
              transform: isExpanded ? 'rotate(180deg)' : 'rotate(0deg)',
              transition: theme.transitions.create('transform', {
                duration: theme.transitions.duration.shortest,
              }),
            }}
          >
            <ExpandMoreIcon />
          </IconButton>
        </Stack>

        <Collapse in={isExpanded}>
          <Box p={2} border={1} borderColor="divider" borderRadius={1}>
            <Stack spacing={2}>
              {filterContent}
            </Stack>
          </Box>
        </Collapse>
      </Box>
    );
  }

  // Desktop: Always visible filters
  return (
    <Box
      data-testid="company-filters"
      p={2}
      border={1}
      borderColor="divider"
      borderRadius={1}
      bgcolor="background.paper"
    >
      {filterContent}
    </Box>
  );
};

export default CompanyFilters;
