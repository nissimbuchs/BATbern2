/**
 * CompanyAutocomplete Component (MUI) — selection-locked combobox with logos + create
 *
 * Mirrors the public (tailwind) RegistrationWizard combobox approach (PR #774) for the
 * organizer/admin MUI surfaces. Shared by three consumers: UserProfileTab ("My Profile"),
 * UserCreateEditModal (admin "Edit User"), and PartnerCreateEditModal.
 *
 * Behaviour:
 *  - The input shows the human DISPLAY NAME, never the ADR-003 slug — so a selected
 *    company never echoes "swissitsolutionsag" back at the user.
 *  - Search results render the company logo + display name + industry (?include=logo).
 *  - The selected value shows its logo as a start-adornment avatar (resolved via
 *    GET /companies/{slug}), falling back to the display-name initial.
 *  - When no existing company matches the query, an explicit "Create new company" row
 *    lets the user deliberately create one. Picking it MATERIALISES the company via
 *    POST /companies:get-or-create and reports the resulting canonical-slug Company,
 *    so every parent's existing `company.name` save logic keeps working unchanged.
 *
 * Contract unchanged: fully controlled, `value: Company | null`, `onChange(Company | null)`.
 */

import React, { useState, useCallback } from 'react';
import { Autocomplete, TextField, CircularProgress, Box, Avatar, Typography } from '@mui/material';
import { Add as AddIcon } from '@mui/icons-material';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { searchCompanies, getOrCreateCompany } from '@/services/api/companyApi';
import { useCompany } from '@/hooks/useCompany/useCompany';
import type { components } from '@/types/generated/company-api.types';

type Company = components['schemas']['CompanyResponse'];

interface CompanyAutocompleteProps {
  value: Company | null;
  onChange: (company: Company | null) => void;
  error?: string;
  disabled?: boolean;
  label?: string;
  inputRef?: React.Ref<HTMLInputElement>;
  /** Show the explicit "Create new company" row when no existing company matches (default true). */
  allowCreate?: boolean;
}

// A brand-new, not-yet-materialised company is represented by an empty slug (`name === ''`)
// + the typed display name. Selecting it triggers get-or-create to resolve the real slug.
const isCreateOption = (c: Company | null): boolean => !!c && c.name === '' && !!c.displayName;

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
  allowCreate = true,
}) => {
  const { t } = useTranslation('common');
  const [inputValue, setInputValue] = useState('');
  const [creating, setCreating] = useState(false);
  const debouncedInputValue = useDebounce(inputValue, 300);

  // The label shown for a company is its display name; the slug is never surfaced.
  const labelOf = useCallback((c: Company) => c.displayName || c.name, []);

  // Sync inputValue when value prop changes (e.g., when editing existing user).
  React.useEffect(() => {
    setInputValue(value ? labelOf(value) : '');
  }, [value, labelOf]);

  // Resolve the selected company's logo for the start-adornment avatar. Only fires for a
  // real slug (existing company) — a brand-new (name === '') value has no logo to fetch.
  const selectedSlug = value && !isCreateOption(value) ? value.name : '';
  const { data: selectedCompany } = useCompany(selectedSlug, { expand: ['logo'] });
  const selectedLogoUrl = selectedCompany?.logo?.url;

  // Only search if input ≥2 chars and doesn't already match the selected value's label
  // (avoid a redundant search right after selection).
  const isValueSelected = !!value && debouncedInputValue === labelOf(value);
  const shouldSearch = debouncedInputValue.length >= 2 && !isValueSelected;

  const {
    data: companies = [],
    isLoading,
    isError: isSearchError,
  } = useQuery({
    queryKey: ['companies', 'search', debouncedInputValue],
    queryFn: () => searchCompanies(debouncedInputValue, 20, { expand: ['logo'] }),
    enabled: shouldSearch,
  });

  // An exact (case-insensitive) display-name/slug match means the company already exists —
  // suppress the "Create" row so the user picks it instead of producing a duplicate.
  const trimmedInput = inputValue.trim();
  const hasExactMatch = companies.some(
    (c) =>
      (c.displayName || c.name).toLowerCase() === trimmedInput.toLowerCase() ||
      c.name.toLowerCase() === trimmedInput.toLowerCase()
  );
  // Don't offer "create" while loading or when the search itself errored — surface the
  // loading/error state instead of letting the create row mask it.
  const showCreate =
    allowCreate &&
    trimmedInput.length >= 2 &&
    !isLoading &&
    !isSearchError &&
    !hasExactMatch &&
    !isValueSelected;

  const createOption: Company = {
    name: '',
    displayName: trimmedInput,
    isVerified: false,
    createdAt: '',
    updatedAt: '',
  };
  const options = showCreate ? [...companies, createOption] : companies;

  const handleInputChange = useCallback((_event: React.SyntheticEvent, newInputValue: string) => {
    setInputValue(newInputValue);
  }, []);

  const handleChange = useCallback(
    async (newValue: Company | null) => {
      if (isCreateOption(newValue)) {
        // Materialise the brand-new company to its canonical slug, then report the real
        // Company so the parent stores a proper reference (ADR-003), not a free-typed string.
        setCreating(true);
        try {
          const created = await getOrCreateCompany(newValue!.displayName!);
          onChange(created);
        } catch {
          onChange(null);
        } finally {
          setCreating(false);
        }
        return;
      }
      onChange(newValue);
    },
    [onChange]
  );

  const busy = isLoading || creating;

  return (
    <Autocomplete
      value={value}
      onChange={(_event, newValue) => {
        void handleChange(newValue);
      }}
      inputValue={inputValue}
      onInputChange={handleInputChange}
      options={options}
      getOptionLabel={(option) => labelOf(option)} // Show display name in the input field
      isOptionEqualToValue={(option, val) => option.name === val.name}
      disabled={disabled}
      loading={busy}
      filterOptions={(x) => x} // Disable client-side filtering (we do server-side filtering)
      noOptionsText={
        isSearchError
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
          helperText={error || undefined}
          inputRef={inputRef}
          data-testid="company-autocomplete"
          InputProps={{
            ...params.InputProps,
            startAdornment: value ? (
              <Avatar
                src={selectedLogoUrl}
                alt={labelOf(value)}
                sx={{ width: 24, height: 24, ml: 0.5, mr: 0.5, fontSize: '0.75rem' }}
              >
                {labelOf(value).charAt(0).toUpperCase()}
              </Avatar>
            ) : (
              params.InputProps.startAdornment
            ),
            endAdornment: (
              <React.Fragment>
                {busy ? <CircularProgress color="inherit" size={20} /> : null}
                {params.InputProps.endAdornment}
              </React.Fragment>
            ),
          }}
        />
      )}
      renderOption={(props, option) => {
        const { key, ...otherProps } = props;

        // Explicit "Create new company" affordance.
        if (isCreateOption(option)) {
          return (
            <Box
              component="li"
              key="__create__"
              {...otherProps}
              data-testid="company-create-option"
              sx={{ display: 'flex', alignItems: 'center', gap: 1 }}
            >
              <Avatar sx={{ width: 32, height: 32, bgcolor: 'grey.200' }}>
                <AddIcon fontSize="small" sx={{ color: 'grey.700' }} />
              </Avatar>
              <Typography variant="body1">
                {t('companySearch.createOption', { value: option.displayName })}
              </Typography>
            </Box>
          );
        }

        return (
          <Box
            component="li"
            key={key}
            {...otherProps}
            data-testid={`company-option-${option.name}`}
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
