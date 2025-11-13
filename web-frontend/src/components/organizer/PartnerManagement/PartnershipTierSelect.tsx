import React from 'react';
import {
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  FormHelperText,
  SelectChangeEvent,
  Box,
  Typography,
} from '@mui/material';

/**
 * Partnership tier levels with their display properties
 */
export const PARTNERSHIP_TIERS = {
  STRATEGIC: {
    emoji: '🏆',
    label: 'Strategic',
    description: 'Highest tier partnership with full benefits',
  },
  PLATINUM: {
    emoji: '💎',
    label: 'Platinum',
    description: 'Premium partnership with advanced benefits',
  },
  GOLD: {
    emoji: '🥇',
    label: 'Gold',
    description: 'High-value partnership with extensive benefits',
  },
  SILVER: { emoji: '🥈', label: 'Silver', description: 'Standard partnership with core benefits' },
  BRONZE: {
    emoji: '🥉',
    label: 'Bronze',
    description: 'Entry-level partnership with basic benefits',
  },
} as const;

export type PartnershipLevel = keyof typeof PARTNERSHIP_TIERS;

interface PartnershipTierSelectProps {
  value: string;
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
  required?: boolean;
}

/**
 * Partnership Tier Select Component
 *
 * Displays a dropdown with all partnership tiers, including emoji icons and labels.
 * Supports validation, error display, and accessibility features.
 *
 * @component
 * @example
 * ```tsx
 * <PartnershipTierSelect
 *   value={tier}
 *   onChange={setTier}
 *   error={errors.tier}
 * />
 * ```
 */
export const PartnershipTierSelect: React.FC<PartnershipTierSelectProps> = ({
  value,
  onChange,
  error,
  disabled = false,
  required = true,
}) => {
  const handleChange = (event: SelectChangeEvent) => {
    onChange(event.target.value);
  };

  return (
    <FormControl fullWidth error={!!error} disabled={disabled} required={required}>
      <InputLabel id="partnership-tier-label">Partnership Tier</InputLabel>
      <Select
        labelId="partnership-tier-label"
        id="partnership-tier-select"
        value={value}
        label="Partnership Tier"
        onChange={handleChange}
        renderValue={(selected) => {
          if (!selected) return '';
          const tier = PARTNERSHIP_TIERS[selected as PartnershipLevel];
          return (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <span>{tier.emoji}</span>
              <span>{tier.label}</span>
            </Box>
          );
        }}
      >
        {(Object.keys(PARTNERSHIP_TIERS) as PartnershipLevel[]).map((tierKey) => {
          const tier = PARTNERSHIP_TIERS[tierKey];
          return (
            <MenuItem key={tierKey} value={tierKey}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, width: '100%' }}>
                <Typography component="span" sx={{ fontSize: '1.25rem' }}>
                  {tier.emoji}
                </Typography>
                <Box sx={{ display: 'flex', flexDirection: 'column' }}>
                  <Typography component="span" variant="body1">
                    {tier.label}
                  </Typography>
                  <Typography component="span" variant="caption" color="text.secondary">
                    {tier.description}
                  </Typography>
                </Box>
              </Box>
            </MenuItem>
          );
        })}
      </Select>
      {error && <FormHelperText>{error}</FormHelperText>}
    </FormControl>
  );
};
