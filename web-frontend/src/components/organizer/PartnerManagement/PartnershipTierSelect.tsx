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
import { PARTNERSHIP_TIERS, PartnershipLevel } from './partnershipTiers.constants';

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
        data-testid="partnership-tier-select"
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
