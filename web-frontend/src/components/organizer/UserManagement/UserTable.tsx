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
} from '@mui/material';
import { MoreVert as MoreVertIcon, Person as PersonIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { User, Role } from '@/types/user.types';
import { ROLE_ICONS } from '@/types/user.types';

interface UserTableProps {
  users: User[];
  onRowClick: (user: User) => void;
  onAction: (action: string, user: User) => void;
}

type SortField = 'name' | 'email' | 'company';
type SortDirection = 'asc' | 'desc';

const UserTable: React.FC<UserTableProps> = ({ users, onRowClick, onAction }) => {
  const { t } = useTranslation('userManagement');
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [sortField, setSortField] = useState<SortField>('name');
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc');

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
    if (sortField === field) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortDirection('asc');
    }
  };

  const sortedUsers = [...users].sort((a, b) => {
    let aValue = '';
    let bValue = '';

    switch (sortField) {
      case 'name':
        aValue = `${a.firstName} ${a.lastName}`;
        bValue = `${b.firstName} ${b.lastName}`;
        break;
      case 'email':
        aValue = a.email;
        bValue = b.email;
        break;
      case 'company':
        aValue = a.companyId || '';
        bValue = b.companyId || '';
        break;
    }

    const comparison = aValue.localeCompare(bValue);
    return sortDirection === 'asc' ? comparison : -comparison;
  });

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
    <TableContainer component={Paper}>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>
              <TableSortLabel
                active={sortField === 'name'}
                direction={sortField === 'name' ? sortDirection : 'asc'}
                onClick={() => handleSort('name')}
              >
                {t('table.headers.name')}
              </TableSortLabel>
            </TableCell>
            <TableCell>
              <TableSortLabel
                active={sortField === 'email'}
                direction={sortField === 'email' ? sortDirection : 'asc'}
                onClick={() => handleSort('email')}
              >
                {t('table.headers.email')}
              </TableSortLabel>
            </TableCell>
            <TableCell>
              <TableSortLabel
                active={sortField === 'company'}
                direction={sortField === 'company' ? sortDirection : 'asc'}
                onClick={() => handleSort('company')}
              >
                {t('table.headers.company')}
              </TableSortLabel>
            </TableCell>
            <TableCell>{t('table.headers.roles')}</TableCell>
            <TableCell>{t('table.headers.status')}</TableCell>
            <TableCell align="right">{t('table.headers.actions')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {sortedUsers.map((user) => (
            <TableRow
              key={user.id}
              hover
              onClick={() => onRowClick(user)}
              sx={{ cursor: 'pointer' }}
            >
              <TableCell>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Avatar sx={{ width: 32, height: 32 }}>
                    {user.firstName[0]}
                    {user.lastName[0]}
                  </Avatar>
                  <Typography variant="body2">
                    {user.firstName} {user.lastName}
                  </Typography>
                </Box>
              </TableCell>
              <TableCell>
                <Typography variant="body2" color="text.secondary">
                  {user.email}
                </Typography>
              </TableCell>
              <TableCell>
                <Typography variant="body2" color="text.secondary">
                  {user.companyId || '-'}
                </Typography>
              </TableCell>
              <TableCell>
                <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                  {user.roles.map((role) => (
                    <Chip
                      key={role}
                      label={role}
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
              <TableCell align="right">
                <IconButton
                  size="small"
                  onClick={(e) => handleMenuOpen(e, user)}
                  aria-label={t('actions.openMenu')}
                >
                  <MoreVertIcon />
                </IconButton>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={handleMenuClose}>
        <MenuItem onClick={() => handleAction('view')}>{t('actions.view')}</MenuItem>
        <MenuItem onClick={() => handleAction('editRoles')}>{t('actions.editRoles')}</MenuItem>
        <MenuItem onClick={() => handleAction('delete')}>{t('actions.delete')}</MenuItem>
      </Menu>
    </TableContainer>
  );
};

export default UserTable;
