/**
 * UserList Component (GREEN Phase)
 *
 * Main container component for User Management screen.
 * Story 2.5.2: User Management Frontend - Task 5b (GREEN Phase)
 *
 * Features:
 * - User list table with sorting
 * - Search and filters
 * - Pagination
 * - User creation modal
 * - User detail modal
 * - Role management
 * - User deletion (GDPR compliant)
 * - Batch import speakers from legacy JSON
 */

import React, { useState } from 'react';
import {
  Box,
  Typography,
  Button,
  Alert,
  Container,
  ToggleButtonGroup,
  ToggleButton,
  Stack,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import Grid from '@mui/material/Grid';
import { Add as AddIcon, ViewModule as GridIcon, ViewList as ListIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useUserManagementStore } from '@/stores/userManagementStore';
import { useUserList } from '@/hooks/useUserManagement';
import UserTable from './UserTable';
import UserCard from './UserCard';
import UserFilters from './UserFilters';
import UserCreateEditModal from './UserCreateEditModal';
import RoleManagerModal from './RoleManagerModal';
import DeleteUserDialog from './DeleteUserDialog';
import UserPagination from './UserPagination';
import type { User } from '@/types/user.types';

const UserList: React.FC = () => {
  const { t } = useTranslation('userManagement');
  const navigate = useNavigate();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const { filters, pagination, setPage, setLimit } = useUserManagementStore();
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('list');
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editUser, setEditUser] = useState<User | null>(null);
  const [roleManagerUser, setRoleManagerUser] = useState<User | null>(null);
  const [deleteDialogUser, setDeleteDialogUser] = useState<User | null>(null);
  // Fetch user list with React Query
  const { data, isLoading, isError, refetch } = useUserList({
    filters,
    pagination,
  });

  const handleRowClick = (user: User) => {
    navigate(`/organizer/users/${user.id}`);
  };

  const handleOpenCreateModal = () => {
    setCreateModalOpen(true);
  };

  const handleViewModeChange = (
    _: React.MouseEvent<HTMLElement>,
    newMode: 'grid' | 'list' | null
  ) => {
    if (newMode !== null) {
      setViewMode(newMode);
    }
  };

  // Loading state
  if (isLoading) {
    return (
      <Container>
        <Box
          display="flex"
          flexDirection="column"
          alignItems="center"
          justifyContent="center"
          minHeight="400px"
        >
          <BATbernLoader size={96} />
          <Typography variant="body1" sx={{ mt: 2 }}>
            {t('loading.users')}
          </Typography>
        </Box>
      </Container>
    );
  }

  // Error state
  if (isError) {
    return (
      <Container>
        <Box sx={{ mt: 4 }}>
          <Alert severity="error" sx={{ mb: 2 }}>
            {t('error.loadFailed')}
          </Alert>
          <Button variant="contained" onClick={() => refetch()}>
            {t('common:actions.retry')}
          </Button>
        </Box>
      </Container>
    );
  }

  const users = data?.data || [];
  const paginationData = data?.pagination;

  return (
    <Box sx={{ flexGrow: 1, p: 3 }}>
      <Container maxWidth="xl">
        {/* Header */}
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          justifyContent="space-between"
          alignItems={{ xs: 'flex-start', md: 'center' }}
          spacing={2}
          mb={3}
        >
          <Typography variant="h4" component="h1">
            {t('title')}
          </Typography>

          <Stack direction="row" spacing={1}>
            {/* View Toggle — hidden on mobile (cards forced) */}
            {!isMobile && (
              <ToggleButtonGroup
                value={viewMode}
                exclusive
                onChange={handleViewModeChange}
                aria-label={t('common:labels.viewMode')}
                size="small"
              >
                <ToggleButton value="grid" aria-label={t('common:labels.gridView')}>
                  <GridIcon />
                </ToggleButton>
                <ToggleButton value="list" aria-label={t('common:labels.listView')}>
                  <ListIcon />
                </ToggleButton>
              </ToggleButtonGroup>
            )}

            <Button
              variant="contained"
              color="primary"
              startIcon={<AddIcon />}
              onClick={handleOpenCreateModal}
            >
              <Box component="span" sx={{ display: { xs: 'none', sm: 'inline' } }}>
                {t('addUser')}
              </Box>
            </Button>
          </Stack>
        </Stack>

        {/* Filters Panel */}
        <UserFilters />

        {/* User List - Cards on mobile, Grid or Table on desktop */}
        {isMobile ? (
          <Stack spacing={2}>
            {users.map((user) => (
              <UserCard key={user.id} user={user} onClick={handleRowClick} />
            ))}
          </Stack>
        ) : viewMode === 'list' ? (
          <UserTable
            users={users}
            onRowClick={handleRowClick}
            onAction={(action, user) => {
              if (action === 'view') {
                handleRowClick(user);
              } else if (action === 'editRoles') {
                setRoleManagerUser(user);
              } else if (action === 'delete') {
                setDeleteDialogUser(user);
              }
            }}
          />
        ) : (
          <Grid container spacing={2}>
            {users.map((user) => (
              <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={user.id}>
                <UserCard user={user} onClick={handleRowClick} />
              </Grid>
            ))}
          </Grid>
        )}

        {/* Pagination */}
        {paginationData && (
          <UserPagination
            page={paginationData.page}
            totalPages={paginationData.totalPages}
            limit={paginationData.limit}
            onPageChange={(newPage) => setPage(newPage)}
            onLimitChange={(newLimit) => setLimit(newLimit)}
          />
        )}

        {/* Modals */}
        {/* Create/Edit User Modal */}
        <UserCreateEditModal
          open={createModalOpen || !!editUser}
          user={editUser}
          onClose={() => {
            setCreateModalOpen(false);
            setEditUser(null);
          }}
          onSuccess={() => {
            refetch();
            setCreateModalOpen(false);
            setEditUser(null);
          }}
        />
        <RoleManagerModal
          user={roleManagerUser}
          open={!!roleManagerUser}
          onClose={() => setRoleManagerUser(null)}
          onSuccess={() => refetch()}
        />
        <DeleteUserDialog
          user={deleteDialogUser}
          open={!!deleteDialogUser}
          onClose={() => setDeleteDialogUser(null)}
          onSuccess={() => refetch()}
        />
      </Container>
    </Box>
  );
};

export default UserList;
