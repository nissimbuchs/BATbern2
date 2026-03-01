import React, { useEffect, useState } from 'react';
import {
  Box,
  TextField,
  Autocomplete,
  Chip,
  FormControl,
  FormLabel,
  RadioGroup,
  FormControlLabel,
  Radio,
  Button,
  Paper,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useUserManagementStore } from '../../../stores/userManagementStore';
import type { Role } from '../../../types/user.types';

const UserFilters: React.FC = () => {
  const { t } = useTranslation('userManagement');
  const { filters, searchQuery, setFilters, setSearchQuery, resetFilters } =
    useUserManagementStore();

  // Local state for debounced search
  const [searchValue, setSearchValue] = useState(searchQuery);

  // Debounced search effect
  useEffect(() => {
    const timer = setTimeout(() => {
      // Only update if the search value actually changed
      if (searchValue !== searchQuery) {
        setSearchQuery(searchValue);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [searchValue, searchQuery, setSearchQuery]);

  // Role options
  const roleOptions: { value: Role; label: string }[] = [
    { value: 'ORGANIZER', label: t('common:role.organizer') },
    { value: 'SPEAKER', label: t('common:role.speaker') },
    { value: 'PARTNER', label: t('common:role.partner') },
    { value: 'ATTENDEE', label: t('common:role.attendee') },
  ];

  // Status options
  const statusOptions = [
    { value: 'all', label: t('common:filters.status.all') },
    { value: 'active', label: t('common:filters.status.active') },
    { value: 'inactive', label: t('common:filters.status.inactive') },
  ];

  const handleRoleChange = (
    _event: React.SyntheticEvent,
    value: { value: Role; label: string }[]
  ) => {
    setFilters({
      ...filters,
      role: value.map((v) => v.value),
    });
  };

  const handleStatusChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const status = event.target.value as 'active' | 'inactive' | 'all';
    setFilters({
      ...filters,
      status,
    });
  };

  const handleClearFilters = () => {
    setSearchValue('');
    resetFilters();
  };

  const selectedRoles = roleOptions.filter((option) => filters.role?.includes(option.value));

  return (
    <Paper sx={{ p: 2, mb: 3 }}>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {/* Search Input */}
        <TextField
          fullWidth
          label={t('search.placeholder')}
          variant="outlined"
          value={searchValue}
          onChange={(e) => setSearchValue(e.target.value)}
          placeholder={t('search.placeholder')}
        />

        {/* Role Filter */}
        <Autocomplete
          multiple
          id="role-filter"
          options={roleOptions}
          value={selectedRoles}
          onChange={handleRoleChange}
          getOptionLabel={(option) => option.label}
          isOptionEqualToValue={(option, value) => option.value === value.value}
          renderInput={(params) => <TextField {...params} label={t('filters.role.label')} />}
          renderTags={(value, getTagProps) =>
            value.map((option, index) => (
              <Chip {...getTagProps({ index })} key={option.value} label={option.label} />
            ))
          }
        />

        {/* Status Filter */}
        <FormControl component="fieldset">
          <FormLabel component="legend">{t('common:labels.status')}</FormLabel>
          <RadioGroup row value={filters.status || 'all'} onChange={handleStatusChange}>
            {statusOptions.map((option) => (
              <FormControlLabel
                key={option.value}
                value={option.value}
                control={<Radio />}
                label={option.label}
              />
            ))}
          </RadioGroup>
        </FormControl>

        {/* Clear Filters Button */}
        <Button variant="outlined" onClick={handleClearFilters} sx={{ alignSelf: 'flex-start' }}>
          {t('filters.clearAll')}
        </Button>
      </Box>
    </Paper>
  );
};

export default UserFilters;
