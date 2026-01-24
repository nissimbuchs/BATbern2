/**
 * CompanyAutocomplete Component (Story 2.8.3 - GREEN Phase)
 *
 * Autocomplete component for searching and selecting companies
 * Features:
 * - Debounced search (300ms)
 * - Min 2 characters to trigger search
 * - Display company name, logo, and industry
 * - Loading states
 * - Error handling
 * - Empty states
 */

import React, { useState, useCallback } from 'react';
import { Autocomplete, TextField, CircularProgress, Box, Avatar, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { searchCompanies } from '@/services/api/companyApi';
import type { components } from '@/types/generated/company-api.types';

type Company = components['schemas']['CompanyResponse'];

interface CompanyAutocompleteProps {
  value: Company | null;
  onChange: (company: Company | null) => void;
  error?: string;
  disabled?: boolean;
  label?: string;
  inputRef?: React.Ref<HTMLInputElement>;
}

// Debounce hook
function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  React.useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => {
      clearTimeout(handler);
    };
  }, [value, delay]);

  return debouncedValue;
}

export const CompanyAutocomplete: React.FC<CompanyAutocompleteProps> = ({
  value,
  onChange,
  error,
  disabled = false,
  label,
  inputRef,
}) => {
  const { t } = useTranslation('common');
  const [inputValue, setInputValue] = useState('');
  const debouncedInputValue = useDebounce(inputValue, 300);

  // Sync inputValue when value prop changes (e.g., when editing existing user)
  React.useEffect(() => {
    if (value) {
      setInputValue(value.name); // Show technical identifier in input
    } else {
      setInputValue('');
    }
  }, [value]);

  // Only search if:
  // 1. Input is at least 2 characters
  // 2. Input doesn't match already selected value (avoid unnecessary search after selection)
  const isValueSelected = value && debouncedInputValue === value.name;
  const shouldSearch = debouncedInputValue.length >= 2 && !isValueSelected;

  // Query for company search
  const {
    data: companies = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['companies', 'search', debouncedInputValue],
    queryFn: () => searchCompanies(debouncedInputValue, 20),
    enabled: shouldSearch,
  });

  const handleInputChange = useCallback((_event: React.SyntheticEvent, newInputValue: string) => {
    setInputValue(newInputValue);
  }, []);

  return (
    <Autocomplete
      value={value}
      onChange={(_event, newValue) => {
        onChange(newValue);
      }}
      inputValue={inputValue}
      onInputChange={handleInputChange}
      options={companies}
      getOptionLabel={(option) => option.name} // Show technical identifier in input field
      isOptionEqualToValue={(option, value) => option.name === value.name}
      disabled={disabled}
      loading={isLoading}
      filterOptions={(x) => x} // Disable client-side filtering (we do server-side filtering)
      noOptionsText={
        isError
          ? t('Error loading companies')
          : inputValue.length < 2
            ? t('Type at least 2 characters')
            : t('No companies found')
      }
      renderInput={(params) => (
        <TextField
          {...params}
          label={label || t('Company')}
          error={!!error}
          helperText={
            error ||
            (value?.displayName && value.displayName !== value.name ? value.displayName : undefined)
          }
          inputRef={inputRef}
          data-testid="company-autocomplete"
          InputProps={{
            ...params.InputProps,
            endAdornment: (
              <React.Fragment>
                {isLoading ? <CircularProgress color="inherit" size={20} /> : null}
                {params.InputProps.endAdornment}
              </React.Fragment>
            ),
          }}
        />
      )}
      renderOption={(props, option) => {
        const { key, ...otherProps } = props;
        return (
          <Box
            component="li"
            key={key}
            {...otherProps}
            sx={{ display: 'flex', alignItems: 'center', gap: 1 }}
          >
            <Avatar
              src={option.logo?.url}
              alt={option.displayName || option.name}
              sx={{ width: 32, height: 32 }}
            >
              {(option.displayName || option.name).charAt(0).toUpperCase()}
            </Avatar>
            <Box sx={{ flex: 1 }}>
              <Typography variant="body1">{option.displayName || option.name}</Typography>
              {option.industry && (
                <Typography variant="body2" color="text.secondary">
                  {option.industry}
                </Typography>
              )}
            </Box>
          </Box>
        );
      }}
    />
  );
};
