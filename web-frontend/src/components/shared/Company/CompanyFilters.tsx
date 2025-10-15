/**
 * CompanyFilters - Filter panel for company list
 *
 * Features:
 * - Partner status filter (checkbox)
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
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [searchParams, setSearchParams] = useSearchParams();

  const [isPartner, setIsPartner] = useState(initialFilters?.isPartner || false);
  const [isVerified, setIsVerified] = useState(initialFilters?.isVerified || false);
  const [industry, setIndustry] = useState(initialFilters?.industry || '');
  const [isExpanded, setIsExpanded] = useState(!isMobile);

  // Load filters from URL on mount
  useEffect(() => {
    const partnerParam = searchParams.get('isPartner');
    const verifiedParam = searchParams.get('isVerified');
    const industryParam = searchParams.get('industry');

    if (partnerParam) setIsPartner(partnerParam === 'true');
    if (verifiedParam) setIsVerified(verifiedParam === 'true');
    if (industryParam) setIndustry(industryParam);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Update URL and notify parent when filters change
  useEffect(() => {
    const filters: CompanyFiltersType = {};

    if (isPartner) filters.isPartner = true;
    if (isVerified) filters.isVerified = true;
    if (industry) filters.industry = industry;

    // Update URL
    const newParams = new URLSearchParams();
    if (isPartner) newParams.set('isPartner', 'true');
    if (isVerified) newParams.set('isVerified', 'true');
    if (industry) newParams.set('industry', industry);
    setSearchParams(newParams);

    // Notify parent
    onFilterChange(filters);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isPartner, isVerified, industry]);

  const handleClearFilters = () => {
    setIsPartner(false);
    setIsVerified(false);
    setIndustry('');
    setSearchParams(new URLSearchParams());
    onFilterChange({});
  };

  const activeFilterCount = [isPartner, isVerified, industry].filter(Boolean).length;
  const hasActiveFilters = activeFilterCount > 0;

  const filterContent = (
    <Stack direction="row" spacing={2} flexWrap="wrap" alignItems="center">
      {/* Partner Filter */}
      <FormControlLabel
        control={
          <Checkbox
            checked={isPartner}
            onChange={(e) => setIsPartner(e.target.checked)}
            aria-label="partner companies only"
          />
        }
        label="Partner Companies Only"
      />

      {/* Verification Filter */}
      <FormControlLabel
        control={
          <Checkbox
            checked={isVerified}
            onChange={(e) => setIsVerified(e.target.checked)}
            aria-label="verified companies only"
          />
        }
        label="Verified Companies Only"
      />

      {/* Industry Filter */}
      <FormControl sx={{ minWidth: 200 }} size="small">
        <InputLabel id="industry-filter-label">Industry</InputLabel>
        <Select
          labelId="industry-filter-label"
          id="industry-filter"
          value={industry}
          label="Industry"
          onChange={(e) => setIndustry(e.target.value)}
          aria-label="industry"
        >
          <MenuItem value="">
            <em>All Industries</em>
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
        aria-label="clear all filters"
      >
        Clear All Filters
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
            aria-label="filters"
            size="small"
          >
            <FilterIcon />
          </IconButton>
          <Typography variant="body2">Filters</Typography>
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
