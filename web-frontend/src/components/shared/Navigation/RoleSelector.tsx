/**
 * RoleSelector Component
 * Shown only for multi-role users. Renders a compact toggle chip row that switches
 * the active navigation context (e.g. Organizer | Partner | Speaker).
 */

import React from 'react';
import { ToggleButtonGroup, ToggleButton } from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { UserRole } from '@/types/auth';

interface RoleSelectorProps {
  roles: UserRole[];
  activeRole: UserRole;
  onChange: (role: UserRole) => void;
}

const ROLE_LABEL_KEYS: Record<UserRole, string> = {
  organizer: 'role.organizer',
  partner: 'role.partner',
  speaker: 'role.speaker',
  attendee: 'role.attendee',
};

export const RoleSelector: React.FC<RoleSelectorProps> = ({ roles, activeRole, onChange }) => {
  const { t } = useTranslation();

  if (roles.length <= 1) return null;

  const handleChange = (_: React.MouseEvent<HTMLElement>, value: UserRole | null) => {
    // MUI passes null when the active button is re-clicked — ignore to keep one always selected
    if (value !== null) onChange(value);
  };

  return (
    <ToggleButtonGroup
      exclusive
      size="small"
      value={activeRole}
      onChange={handleChange}
      aria-label="active role"
      sx={{ mr: 2 }}
    >
      {roles.map((role) => (
        <ToggleButton
          key={role}
          value={role}
          aria-label={t(ROLE_LABEL_KEYS[role], role)}
          sx={{
            px: 1.5,
            py: 0.5,
            fontSize: '0.75rem',
            fontWeight: 500,
            textTransform: 'capitalize',
            borderRadius: '16px !important',
            '&.Mui-selected': {
              backgroundColor: 'primary.main',
              color: 'primary.contrastText',
              '&:hover': { backgroundColor: 'primary.dark' },
            },
          }}
        >
          {t(ROLE_LABEL_KEYS[role], role)}
        </ToggleButton>
      ))}
    </ToggleButtonGroup>
  );
};
