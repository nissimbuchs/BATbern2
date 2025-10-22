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
 */

import React, { useState } from 'react';
import { Box, Typography, Button, CircularProgress, Alert, Container } from '@mui/material';
import { Add as AddIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useUserManagementStore } from '@/stores/userManagementStore';
import { useUserList } from '@/hooks/useUserManagement';
import UserTable from './UserTable';
import UserFilters from './UserFilters';
import UserDetailModal from './UserDetailModal';
import UserCreateModal from './UserCreateModal';
import RoleManagerModal from './RoleManagerModal';
import DeleteUserDialog from './DeleteUserDialog';
import UserPagination from './UserPagination';
// import UserSyncPanel from './UserSyncPanel'; // TODO: Re-enable when AWS credentials configured (Story 1.2.5)
import type { User } from '@/types/user.types';

const UserList: React.FC = () => {
  const { t } = useTranslation('userManagement');
  const { filters, pagination, selectedUser, setSelectedUser, setPage, setLimit } =
    useUserManagementStore();
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [roleManagerUser, setRoleManagerUser] = useState<User | null>(null);
  const [deleteDialogUser, setDeleteDialogUser] = useState<User | null>(null);

  // Fetch user list with React Query
  const { data, isLoading, isError, refetch } = useUserList({
    filters,
    pagination,
  });

  const handleRowClick = (user: User) => {
    setSelectedUser(user);
  };

  const handleCloseDetailModal = () => {
    setSelectedUser(null);
  };

  const handleEditUser = (user: User) => {
    // Close detail modal and open role manager
    setSelectedUser(null);
    setRoleManagerUser(user);
  };

  const handleOpenCreateModal = () => {
    setCreateModalOpen(true);
  };

  const handleCloseCreateModal = () => {
    setCreateModalOpen(false);
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
          <CircularProgress />
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
            {t('actions.retry')}
          </Button>
        </Box>
      </Container>
    );
  }

  const users = data?.data || [];
  const paginationData = data?.pagination;

  return (
    <Container maxWidth="xl">
      <Box sx={{ py: 4 }}>
        {/* Header */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
          <Typography variant="h4" component="h1">
            {t('title')}
          </Typography>
          <Button
            variant="contained"
            color="primary"
            startIcon={<AddIcon />}
            onClick={handleOpenCreateModal}
          >
            {t('addUser')}
          </Button>
        </Box>

        {/* User Sync Panel - Story 1.2.5 */}
        {/* TODO: Re-enable when AWS credentials configured - requires STS SDK for role assumption */}
        {/* <UserSyncPanel /> */}

        {/* Filters Panel */}
        <UserFilters />

        {/* User Table */}
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

        {/* Pagination */}
        {paginationData && (
          <UserPagination
            page={pagination.page}
            totalPages={paginationData.totalPages}
            limit={pagination.limit}
            onPageChange={(newPage) => setPage(newPage)}
            onLimitChange={(newLimit) => setLimit(newLimit)}
          />
        )}

        {/* Modals */}
        <UserDetailModal
          user={selectedUser}
          open={!!selectedUser}
          onClose={handleCloseDetailModal}
          onEdit={handleEditUser}
        />
        <UserCreateModal
          open={createModalOpen}
          onClose={handleCloseCreateModal}
          onSuccess={() => refetch()}
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
      </Box>
    </Container>
  );
};

export default UserList;
