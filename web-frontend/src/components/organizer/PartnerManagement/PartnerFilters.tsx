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
  'STRATEGIC',
  'PLATINUM',
  'GOLD',
  'SILVER',
  'BRONZE',
];

const STATUS_OPTIONS = ['all', 'active', 'inactive'] as const;

// Tier emojis for quick filter chips
const TIER_EMOJIS: Record<string, string> = {
  STRATEGIC: '🏆',
  PLATINUM: '💎',
  GOLD: '🥇',
  SILVER: '🥈',
  BRONZE: '🥉',
};

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
          <InputLabel id="tier-filter-label">{t('filters.tier')}</InputLabel>
          <Select
            labelId="tier-filter-label"
            id="tier-filter"
            value={filters.tier}
            aria-label={t('filters.tier')}
            label={t('filters.tier')}
            onChange={handleTierChange}
          >
            {TIER_OPTIONS.map((tier) => (
              <MenuItem key={tier} value={tier}>
                {tier === 'all'
                  ? t('filters.tierAll')
                  : `${TIER_EMOJIS[tier]} ${t(`tiers.${tier.toLowerCase()}`)}`}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        {/* Status Filter Dropdown */}
        <FormControl size="small" sx={{ minWidth: 150 }}>
          <InputLabel id="status-filter-label">{t('filters.status')}</InputLabel>
          <Select
            labelId="status-filter-label"
            id="status-filter"
            value={filters.status}
            label={t('filters.status')}
            aria-label={t('filters.status')}
            onChange={handleStatusChange}
          >
            {STATUS_OPTIONS.map((status) => (
              <MenuItem key={status} value={status}>
                {status === 'all'
                  ? t('filters.statusAll')
                  : status === 'active'
                    ? t('filters.statusActive')
                    : t('filters.statusInactive')}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        {/* Reset Filters Button */}
        <Button variant="outlined" startIcon={<ClearIcon />} onClick={resetFilters} size="small">
          {t('filters.reset')}
        </Button>
      </Stack>

      {/* Quick Filter Chips */}
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        {(['STRATEGIC', 'PLATINUM', 'GOLD', 'SILVER', 'BRONZE'] as PartnershipLevel[]).map(
          (tier) => (
            <Chip
              key={tier}
              label={`${TIER_EMOJIS[tier]} ${t(`tiers.${tier.toLowerCase()}`)}`}
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
