/* eslint-disable react-refresh/only-export-components */
/**
 * OrganizerSelect Component
 *
 * Shared dropdown component for selecting organizers
 * Fetches users with ORGANIZER role from the API
 *
 * Used in:
 * - EventTasksTab (Story 5.5) - Assign tasks to organizers
 * - SpeakerOutreachDashboard (Story 5.3) - Filter speakers by assigned organizer
 */

import React from 'react';
import {
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  CircularProgress,
  FormControlProps,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useUserList } from '@/hooks/useUserManagement';
import type { User } from '@/types/user.types';

export interface Organizer {
  id: string;
  name: string;
}

interface OrganizerSelectProps extends Omit<FormControlProps, 'onChange'> {
  value: string;
  onChange: (organizerId: string) => void;
  organizers?: Organizer[]; // Optional - if not provided, will fetch from API
  includeUnassigned?: boolean;
  includeAllOption?: boolean;
  label?: string;
  disabled?: boolean;
  size?: 'small' | 'medium';
}

export const OrganizerSelect: React.FC<OrganizerSelectProps> = ({
  value,
  onChange,
  organizers: organizersProp,
  includeUnassigned = true,
  includeAllOption = false,
  label,
  disabled = false,
  size = 'small',
  ...formControlProps
}) => {
  const { t } = useTranslation('common');

  // Fetch users with ORGANIZER role (only if not provided via props)
  const { data: organizersData, isLoading } = useUserList({
    filters: { role: ['ORGANIZER'] },
    pagination: { page: 1, limit: 100 },
    enabled: !organizersProp, // Only fetch if organizers not provided
  });

  // Use prop organizers if provided, otherwise transform from API
  const organizers: Organizer[] = React.useMemo(() => {
    if (organizersProp) return organizersProp;
    if (!organizersData?.data) return [];
    return organizersData.data.map((user: User) => ({
      id: user.id, // id field IS the username (e.g., "john.doe")
      name: `${user.firstName} ${user.lastName}`.trim() || user.id,
    }));
  }, [organizersProp, organizersData]);

  const labelText = label || t('organizer.select', 'Select Organizer');

  // Build menu items as an array (MUI Select requires array, not Fragment)
  const menuItems = React.useMemo(() => {
    if (isLoading) {
      return [
        <MenuItem key="loading" disabled>
          <CircularProgress size={20} sx={{ mr: 1 }} />
          {t('loading', 'Loading...')}
        </MenuItem>,
      ];
    }

    const items = [];

    if (includeAllOption) {
      items.push(
        <MenuItem key="all" value="all">
          {t('organizer.all', 'All Organizers')}
        </MenuItem>
      );
    }

    if (includeUnassigned) {
      items.push(
        <MenuItem key="unassigned" value="">
          <em>{t('organizer.unassigned', 'Unassigned')}</em>
        </MenuItem>
      );
    }

    organizers.forEach((org) => {
      items.push(
        <MenuItem key={org.id} value={org.id}>
          {org.name}
        </MenuItem>
      );
    });

    return items;
  }, [isLoading, includeAllOption, includeUnassigned, organizers, t]);

  return (
    <FormControl size={size} disabled={disabled || isLoading} {...formControlProps}>
      <InputLabel>{labelText}</InputLabel>
      <Select value={value || ''} onChange={(e) => onChange(e.target.value)} label={labelText}>
        {menuItems}
      </Select>
    </FormControl>
  );
};

/**
 * Hook to get organizers list (for non-dropdown usage)
 */
export const useOrganizers = () => {
  const { data: organizersData, isLoading } = useUserList({
    filters: { role: ['ORGANIZER'] },
    pagination: { page: 1, limit: 100 },
  });

  const organizers: Organizer[] = React.useMemo(() => {
    if (!organizersData?.data) return [];
    return organizersData.data.map((user: User) => ({
      id: user.id, // id field IS the username (e.g., "john.doe")
      name: `${user.firstName} ${user.lastName}`.trim() || user.id,
    }));
  }, [organizersData]);

  return { organizers, isLoading };
};
