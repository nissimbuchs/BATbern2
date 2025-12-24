/**
 * UserAutocomplete Component (Story 5.5)
 *
 * Autocomplete component for searching and selecting users
 * Features:
 * - Debounced search (300ms)
 * - Min 2 characters to trigger search
 * - Filter by role
 * - Display user name, email, and company
 * - Loading states
 * - Error handling
 */

import React, { useState, useCallback } from 'react';
import { Autocomplete, TextField, CircularProgress, Box, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { searchUsers } from '@/services/api/userManagementApi';
import { UserAvatar } from '@/components/shared/UserAvatar';
import type { UserSearchResponse } from '@/types/user.types';
import { useDebounce } from '@/hooks/useDebounce';

interface UserAutocompleteProps {
  value: UserSearchResponse | null;
  onChange: (user: UserSearchResponse | null) => void;
  error?: string;
  disabled?: boolean;
  label?: string;
  role?: string; // Filter by role (e.g., 'SPEAKER')
  inputRef?: React.Ref<HTMLInputElement>;
}

export const UserAutocomplete: React.FC<UserAutocompleteProps> = ({
  value,
  onChange,
  error,
  disabled = false,
  label,
  role,
  inputRef,
}) => {
  const { t } = useTranslation('common');
  const [inputValue, setInputValue] = useState('');
  const debouncedInputValue = useDebounce(inputValue, 300);

  // Sync inputValue when value prop changes
  React.useEffect(() => {
    if (value) {
      setInputValue(value.id); // id is the username
    } else {
      setInputValue('');
    }
  }, [value]);

  // Only search if input is at least 2 characters
  const isValueSelected = value && debouncedInputValue === value.id;
  const shouldSearch = debouncedInputValue.length >= 2 && !isValueSelected;

  // Query for user search
  const {
    data: users = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['users', 'search', debouncedInputValue, role],
    queryFn: async () => {
      const allUsers = await searchUsers(debouncedInputValue, 20);
      // Filter by role if specified
      return role ? allUsers.filter((u) => u.roles?.includes(role)) : allUsers;
    },
    enabled: shouldSearch,
  });

  // Ensure selected value is always in options for MUI Autocomplete
  const optionsWithValue = React.useMemo(() => {
    if (!value) return users;
    // Check if value is already in users array
    const valueExists = users.some((u) => u.id === value.id);
    return valueExists ? users : [value, ...users];
  }, [users, value]);

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
      options={optionsWithValue}
      getOptionLabel={(option) => option.id} // id is the username
      isOptionEqualToValue={(option, value) => option.id === value.id}
      disabled={disabled}
      loading={isLoading}
      filterOptions={(x) => x} // Server-side filtering
      noOptionsText={
        isError
          ? t('Error loading users')
          : inputValue.length < 2
            ? t('Type at least 2 characters')
            : role
              ? t(`No users found with role ${role}`)
              : t('No users found')
      }
      renderInput={(params) => (
        <TextField
          {...params}
          label={label || t('User')}
          error={!!error}
          helperText={error}
          inputRef={inputRef}
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
          <Box component="li" key={key} {...otherProps}>
            <UserAvatar
              firstName={option.firstName}
              lastName={option.lastName}
              company={option.companyId}
              profilePictureUrl={option.profilePictureUrl}
              size={32}
              showCompany={false}
            />
            <Box sx={{ ml: 1, flex: 1 }}>
              {option.email && (
                <Typography variant="body2" color="text.secondary">
                  {option.email}
                </Typography>
              )}
              {option.roles && option.roles.length > 0 && (
                <Typography variant="caption" color="text.secondary">
                  Role: {option.roles.join(', ')}
                </Typography>
              )}
            </Box>
          </Box>
        );
      }}
    />
  );
};

export default UserAutocomplete;
