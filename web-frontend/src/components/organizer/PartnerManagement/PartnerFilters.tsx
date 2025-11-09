/**
 * PartnerFilters Component
 *
 * Story 2.8.1 - Task 5b (GREEN Phase)
 * AC: 1 (Partner Directory Screen)
 *
 * Partner filtering controls:
 * - Tier dropdown filter (All, Strategic, Platinum, Gold, Silver, Bronze)
 * - Status toggle filter (All, Active, Inactive)
 * - Quick filter chips (tier-based)
 * - Reset filters button
 * - Integration with Zustand store
 */

import React from 'react';
import {
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Button,
  Stack,
  Chip,
  Box,
  SelectChangeEvent,
} from '@mui/material';
import { FilterList as FilterListIcon, Clear as ClearIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { usePartnerStore } from '@/stores/partnerStore';
import type { PartnershipLevel } from '@/services/api/partnerApi';

const TIER_OPTIONS: Array<PartnershipLevel | 'all'> = [
  'all',
  'strategic',
  'platinum',
  'gold',
  'silver',
  'bronze',
];

const STATUS_OPTIONS = ['all', 'active', 'inactive'] as const;

// Tier emojis for quick filter chips
const TIER_EMOJIS: Record<string, string> = {
  strategic: '🏆',
  platinum: '💎',
  gold: '🥇',
  silver: '🥈',
  bronze: '🥉',
};

const capitalize = (str: string) => str.charAt(0).toUpperCase() + str.slice(1);

export const PartnerFilters: React.FC = () => {
  const { t } = useTranslation('partners');
  const { filters, setFilters, resetFilters } = usePartnerStore();

  const handleTierChange = (event: SelectChangeEvent<string>) => {
    setFilters({ tier: event.target.value as PartnershipLevel | 'all' });
  };

  const handleStatusChange = (event: SelectChangeEvent<string>) => {
    setFilters({ status: event.target.value as 'all' | 'active' | 'inactive' });
  };

  const handleQuickFilterClick = (tier: PartnershipLevel) => {
    setFilters({ tier });
  };

  return (
    <Box>
      <Stack direction="row" spacing={2} alignItems="center" mb={2}>
        {/* Tier Filter Dropdown */}
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel id="tier-filter-label">Partnership Tier</InputLabel>
          <Select
            labelId="tier-filter-label"
            id="tier-filter"
            value={filters.tier}
            aria-label="Partnership Tier"
            label="Tier"
            onChange={handleTierChange}
          >
            <MenuItem value="all">All Tiers</MenuItem>
            <MenuItem value="strategic">Strategic</MenuItem>
            <MenuItem value="platinum">Platinum</MenuItem>
            <MenuItem value="gold">Gold</MenuItem>
            <MenuItem value="silver">Silver</MenuItem>
            <MenuItem value="bronze">Bronze</MenuItem>
          </Select>
        </FormControl>

        {/* Status Filter Dropdown */}
        <FormControl size="small" sx={{ minWidth: 150 }}>
          <InputLabel id="status-filter-label">Status</InputLabel>
          <Select
            labelId="status-filter-label"
            id="status-filter"
            value={filters.status}
            label="Status"
            aria-label="Status"
            onChange={handleStatusChange}
          >
            <MenuItem value="all">All</MenuItem>
            <MenuItem value="active">Active</MenuItem>
            <MenuItem value="inactive">Inactive</MenuItem>
          </Select>
        </FormControl>

        {/* Reset Filters Button */}
        <Button
          variant="outlined"
          startIcon={<ClearIcon />}
          onClick={resetFilters}
          size="small"
        >
          Reset Filters
        </Button>
      </Stack>

      {/* Quick Filter Chips */}
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        {(['strategic', 'platinum', 'gold', 'silver', 'bronze'] as PartnershipLevel[]).map(
          (tier) => (
            <Chip
              key={tier}
              label={TIER_EMOJIS[tier] + ' ' + capitalize(tier)}
              onClick={() => handleQuickFilterClick(tier)}
              color={filters.tier === tier ? 'primary' : 'default'}
              variant={filters.tier === tier ? 'filled' : 'outlined'}
              size="small"
              icon={<FilterListIcon />}
            />
          )
        )}
      </Stack>
    </Box>
  );
};
