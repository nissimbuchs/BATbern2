import React, { useEffect, useState } from 'react';
import { TextField, InputAdornment, IconButton } from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import { usePartnerStore } from '@/stores/partnerStore';

/**
 * Search input component with 300ms debouncing for partner name search.
 * Implements AC1 requirement for debounced search functionality.
 */
export const PartnerSearch: React.FC = () => {
  const { searchQuery, setSearchQuery } = usePartnerStore();
  const [localValue, setLocalValue] = useState(searchQuery);

  // Debounce search query updates (300ms)
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearchQuery(localValue);
    }, 300);

    return () => clearTimeout(timer);
  }, [localValue, setSearchQuery]);

  // Sync local value when store value changes externally (e.g., clear filters)
  useEffect(() => {
    setLocalValue(searchQuery);
  }, [searchQuery]);

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setLocalValue(event.target.value);
  };

  const handleClear = () => {
    setLocalValue('');
    setSearchQuery('');
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Escape') {
      handleClear();
    }
  };

  const showClearButton = localValue.length > 0;

  return (
    <TextField
      fullWidth
      value={localValue}
      onChange={handleChange}
      onKeyDown={handleKeyDown}
      placeholder="Search by partner name..."
      inputProps={{
        'aria-label': 'Search partners',
      }}
      InputProps={{
        startAdornment: (
          <InputAdornment position="start">
            <SearchIcon data-testid="search-icon" />
          </InputAdornment>
        ),
        endAdornment: showClearButton ? (
          <InputAdornment position="end">
            <IconButton aria-label="Clear search" onClick={handleClear} edge="end" size="small">
              <ClearIcon />
            </IconButton>
          </InputAdornment>
        ) : null,
      }}
    />
  );
};
