/**
 * UserTable Component (GREEN Phase)
 *
 * Displays users in a sortable table with role badges and actions.
 * Story 2.5.2: User Management Frontend - Task 6b (GREEN Phase)
 *
 * Features:
 * - Sortable columns
 * - Role badges with icons
 * - Row click to view details
 * - Actions menu per row
 */

import React, { useState } from 'react';
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  IconButton,
  Menu,
  MenuItem,
  Avatar,
  Box,
  Typography,
  TableSortLabel,
  Tooltip,
} from '@mui/material';
import {
  MoreVert as MoreVertIcon,
  Person as PersonIcon,
  Cloud as CloudIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { User, Role } from '@/types/user.types';
import { ROLE_ICONS } from '@/types/user.types';
import CompanyCell from './CompanyCell';

type SortField = 'name' | 'email' | 'company';
type SortDirection = 'asc' | 'desc';

interface UserTableProps {
  users: User[];
  onRowClick: (user: User) => void;
  onAction: (action: string, user: User) => void;
  showAdminActions?: boolean;
  sortBy?: SortField;
  sortDir?: SortDirection;
  onSortChange?: (field: SortField, dir: SortDirection) => void;
}

const UserTable: React.FC<UserTableProps> = ({
  users,
  onRowClick,
  onAction,
  showAdminActions = true,
  sortBy: externalSortBy,
  sortDir: externalSortDir,
  onSortChange,
}) => {
  const { t } = useTranslation('userManagement');
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);

  const sortField = externalSortBy ?? 'name';
  const sortDirection = externalSortDir ?? 'asc';

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>, user: User) => {
    event.stopPropagation();
    setAnchorEl(event.currentTarget);
    setSelectedUser(user);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
    setSelectedUser(null);
  };

  const handleAction = (action: string) => {
    if (selectedUser) {
      onAction(action, selectedUser);
    }
    handleMenuClose();
  };

  const handleSort = (field: SortField) => {
    if (onSortChange) {
      if (field === sortField) {
        onSortChange(field, sortDirection === 'asc' ? 'desc' : 'asc');
      } else {
        onSortChange(field, 'asc');
      }
    }
  };

  const getRoleBadgeColor = (role: Role): 'primary' | 'secondary' | 'success' | 'default' => {
    switch (role) {
      case 'ORGANIZER':
        return 'primary';
      case 'SPEAKER':
        return 'secondary';
      case 'PARTNER':
        return 'success';
      default:
        return 'default';
    }
  };

  if (users.length === 0) {
    return (
      <Paper sx={{ p: 4, textAlign: 'center' }}>
        <PersonIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
        <Typography variant="h6" color="text.secondary">
          {t('table.empty')}
        </Typography>
      </Paper>
    );
  }

  return (
    <TableContainer component={Paper} data-testid="user-table">
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>
              <TableSortLabel
                active={sortField === 'name'}
                direction={sortField === 'name' ? sortDirection : 'asc'}
                onClick={() => handleSort('name')}
              >
                {t('common:labels.name')}
              </TableSortLabel>
            </TableCell>
            <TableCell>
              <TableSortLabel
                active={sortField === 'email'}
                direction={sortField === 'email' ? sortDirection : 'asc'}
                onClick={() => handleSort('email')}
              >
                {t('common:labels.email')}
              </TableSortLabel>
            </TableCell>
            <TableCell>
              <TableSortLabel
                active={sortField === 'company'}
                direction={sortField === 'company' ? sortDirection : 'asc'}
                onClick={() => handleSort('company')}
              >
                {t('common:labels.company')}
              </TableSortLabel>
            </TableCell>
            <TableCell>{t('table.headers.roles')}</TableCell>
            <TableCell>{t('common:labels.status')}</TableCell>
            {showAdminActions && <TableCell align="right">{t('common:labels.actions')}</TableCell>}
          </TableRow>
        </TableHead>
        <TableBody>
          {users.map((user) => (
            <TableRow
              key={user.id}
              hover
              onClick={() => onRowClick(user)}
              sx={{ cursor: 'pointer' }}
              data-testid="user-table-row"
            >
              <TableCell>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Avatar
                    src={user.profilePictureUrl || undefined}
                    sx={{ width: 32, height: 32 }}
                    imgProps={{ loading: 'lazy' }}
                  >
                    {user.firstName[0]}
                    {user.lastName[0]}
                  </Avatar>
                  <Typography variant="body2">
                    {user.firstName} {user.lastName}
                  </Typography>
                  {user.hasCognitoAccount && (
                    <Tooltip title={t('cognito.tooltip')}>
                      <CloudIcon
                        sx={{ fontSize: 16, color: 'info.main' }}
                        aria-label={t('cognito.linked')}
                      />
                    </Tooltip>
                  )}
                </Box>
              </TableCell>
              <TableCell>
                <Typography variant="body2" color="text.secondary">
                  {user.email}
                </Typography>
              </TableCell>
              <TableCell>
                <CompanyCell companyId={user.companyId} />
              </TableCell>
              <TableCell>
                <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                  {user.roles.map((role) => (
                    <Chip
                      key={role}
                      label={t(`common:role.${role.toLowerCase()}`)}
                      size="small"
                      color={getRoleBadgeColor(role as Role)}
                      icon={<span>{ROLE_ICONS[role as Role]}</span>}
                    />
                  ))}
                </Box>
              </TableCell>
              <TableCell>
                <Chip
                  label={user.active ? t('status.active') : t('status.inactive')}
                  size="small"
                  color={user.active ? 'success' : 'default'}
                />
              </TableCell>
              {showAdminActions && (
                <TableCell align="right">
                  <IconButton
                    size="small"
                    onClick={(e) => handleMenuOpen(e, user)}
                    aria-label={t('actions.openMenu')}
                  >
                    <MoreVertIcon />
                  </IconButton>
                </TableCell>
              )}
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={handleMenuClose}>
        <MenuItem onClick={() => handleAction('view')} data-testid="user-action-view">
          {t('actions.view')}
        </MenuItem>
        <MenuItem onClick={() => handleAction('editRoles')} data-testid="user-action-edit-roles">
          {t('actions.editRoles')}
        </MenuItem>
        <MenuItem onClick={() => handleAction('delete')} data-testid="user-action-delete">
          {t('common:actions.delete')}
        </MenuItem>
      </Menu>
    </TableContainer>
  );
};

export default UserTable;
