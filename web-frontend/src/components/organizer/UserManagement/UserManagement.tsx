/**
 * UserManagement Component with Routing
 *
 * Main container component for User Management screen with routing.
 * Handles navigation between user list and user detail views.
 *
 * Features:
 * - User list table with sorting
 * - Search and filters
 * - Pagination
 * - User detail page with event participation
 * - Role management
 * - User creation and editing
 * - Batch import for speakers and participants
 */

import React, { useState } from 'react';
import { Routes, Route, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import UserList from './UserList';
import { UserDetailView } from './UserDetailView';
import UserCreateEditModal from './UserCreateEditModal';
import { useUserById } from '@/hooks/useUserManagement';
import type { User } from '@/types/user.types';

const UserDetailPage: React.FC = () => {
  const { userId } = useParams<{ userId: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation('userManagement');
  const [editUser, setEditUser] = useState<User | null>(null);

  // Fetch user by ID with company details
  const {
    data: user,
    isLoading,
    isError,
  } = useUserById({
    id: userId || null,
    includes: ['company'],
  });

  const handleBack = () => {
    navigate('/organizer/users');
  };

  const handleEdit = (user: User) => {
    setEditUser(user);
  };

  const handleCloseEditModal = () => {
    setEditUser(null);
  };

  if (isLoading) {
    return <div>Loading...</div>;
  }

  if (isError || !user) {
    return <div>{t('error.notFound')}</div>;
  }

  return (
    <>
      <UserDetailView
        user={user}
        onBack={handleBack}
        onEdit={handleEdit}
        canEdit={true}
        canDelete={false}
      />

      {/* Edit User Modal */}
      <UserCreateEditModal open={!!editUser} user={editUser} onClose={handleCloseEditModal} />
    </>
  );
};

const UserManagement: React.FC = () => {
  return (
    <Routes>
      <Route index element={<UserList />} />
      <Route path=":userId" element={<UserDetailPage />} />
    </Routes>
  );
};

export default UserManagement;
