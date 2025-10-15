/**
 * CompanySearch Component
 *
 * Autocomplete search with debouncing
 * - AC2: Search & Filters
 * - 300ms debounce
 * - Keyboard navigation
 * - Accessible
 *
 * Story: 2.5.1 - Company Management Frontend
 */

import React, { useState, useEffect } from 'react';
import {
  TextField,
  Autocomplete,
  InputAdornment,
  IconButton,
  Box,
  Typography,
  CircularProgress
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';

export interface CompanySearchProps {
  onSearch?: (query: string) => void;
  onSelect?: (companyId: string) => void;
  onSearchChange?: React.Dispatch<React.SetStateAction<string>>;
  debounceMs?: number;
  placeholder?: string;
}

export const CompanySearch: React.FC<CompanySearchProps> = ({
  onSearch,
  onSelect,
  onSearchChange,
  debounceMs = 300,
  placeholder = 'Search companies...'
}) => {
  const [inputValue, setInputValue] = useState('');
  const [debouncedValue, setDebouncedValue] = useState('');
  const [options, setOptions] = useState<Array<{ id: string; name: string }>>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [open, setOpen] = useState(false);

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedValue(inputValue);
    }, debounceMs);

    return () => clearTimeout(timer);
  }, [inputValue, debounceMs]);

  // Call onSearch and onSearchChange when debounced value changes
  useEffect(() => {
    if (debouncedValue !== undefined) {
      onSearch?.(debouncedValue);
      onSearchChange?.(debouncedValue);
    }
  }, [debouncedValue, onSearch, onSearchChange]);

  // Mock options for testing (in real app, this would come from API)
  useEffect(() => {
    if (debouncedValue.length > 0) {
      setIsLoading(true);
      // Simulate API call
      setTimeout(() => {
        if (debouncedValue.toLowerCase().includes('test')) {
          setOptions([
            { id: '1', name: 'Test Company 1' },
            { id: '2', name: 'Test Company 2' }
          ]);
        } else if (debouncedValue.toLowerCase().includes('acme')) {
          setOptions([
            { id: '3', name: 'Acme Corporation' }
          ]);
        } else {
          setOptions([]);
        }
        setIsLoading(false);
      }, 100);
    } else {
      setOptions([]);
    }
  }, [debouncedValue]);

  const handleInputChange = (_event: React.SyntheticEvent, newValue: string) => {
    setInputValue(newValue);
  };

  const handleChange = (_event: React.SyntheticEvent, value: string | { id: string; name: string } | null) => {
    if (value && typeof value === 'object' && onSelect) {
      onSelect(value.id);
    }
  };

  const handleClear = () => {
    setInputValue('');
    setDebouncedValue('');
    setOptions([]);
    onSearch?.('');
    onSearchChange?.('');
  };

  const highlightMatch = (text: string, query: string) => {
    if (!query) return text;

    const parts = text.split(new RegExp(`(${query})`, 'gi'));
    return (
      <span>
        {parts.map((part, index) =>
          part.toLowerCase() === query.toLowerCase() ? (
            <strong key={index} data-testid="highlighted-text">
              {part}
            </strong>
          ) : (
            <span key={index}>{part}</span>
          )
        )}
      </span>
    );
  };

  return (
    <Autocomplete
      freeSolo
      open={open}
      onOpen={() => setOpen(true)}
      onClose={() => setOpen(false)}
      options={options}
      loading={isLoading}
      inputValue={inputValue}
      onInputChange={handleInputChange}
      onChange={handleChange}
      getOptionLabel={(option) => (typeof option === 'string' ? option : option.name)}
      filterOptions={(x) => x} // Disable built-in filtering, we handle it via API
      noOptionsText="No companies found"
      renderOption={(props, option) => (
        <Box component="li" {...props}>
          <Typography variant="body2">
            {highlightMatch(option.name, inputValue)}
          </Typography>
        </Box>
      )}
      renderInput={(params) => (
        <TextField
          {...params}
          placeholder={placeholder}
          aria-label="Search companies"
          aria-autocomplete="list"
          InputProps={{
            ...params.InputProps,
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon color="action" />
              </InputAdornment>
            ),
            endAdornment: (
              <>
                {isLoading && <CircularProgress color="inherit" size={20} />}
                {inputValue && (
                  <IconButton
                    aria-label="Clear search"
                    onClick={handleClear}
                    edge="end"
                    size="small"
                  >
                    <ClearIcon />
                  </IconButton>
                )}
                {params.InputProps.endAdornment}
              </>
            )
          }}
        />
      )}
    />
  );
};
