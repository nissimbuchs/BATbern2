/**
 * User Management API Service Tests (RED Phase)
 *
 * TDD: Writing tests FIRST before implementation
 * Story 2.5.2: User Management Frontend - Task 4a (RED Phase)
 *
 * Test Coverage:
 * - AC1: List users with filters, pagination, includes
 * - AC3: Update user roles
 * - AC4: Create user
 * - AC5: Delete user (GDPR)
 * - AC6: Search users with autocomplete
 */

import { describe, it, expect, beforeEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import apiClient from './apiClient';
import {
  listUsers,
  searchUsers,
  getUserById,
  createUser,
  updateUserRoles,
  deleteUser,
} from './userManagementApi';
import type { UserFilters, UserPagination, Role } from '@/types/user.types';

describe('UserManagementApi', () => {
  let mock: MockAdapter;

  beforeEach(() => {
    mock = new MockAdapter(apiClient);
  });

  afterEach(() => {
    mock.restore();
  });

  describe('listUsers', () => {
    it('should_fetchUserList_when_noFiltersProvided', async () => {
      const mockResponse = {
        data: [
          {
            id: 'john.doe',
            email: 'john.doe@example.com',
            firstName: 'John',
            lastName: 'Doe',
            roles: ['ATTENDEE'],
            isActive: true,
            createdAt: '2025-01-15T10:00:00Z',
            updatedAt: '2025-01-15T10:00:00Z',
          },
        ],
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      mock.onGet('/api/v1/users').reply(200, mockResponse);

      const filters: UserFilters = {};
      const pagination: UserPagination = { page: 1, limit: 20 };

      const result = await listUsers(filters, pagination);

      expect(result).toEqual(mockResponse);
      expect(result.data).toHaveLength(1);
      expect(result.data[0].id).toBe('john.doe');
    });

    it('should_fetchUsersWithIncludes_when_includeParameterProvided', async () => {
      const mockResponse = {
        data: [
          {
            id: 'jane.smith',
            email: 'jane.smith@example.com',
            firstName: 'Jane',
            lastName: 'Smith',
            roles: ['ORGANIZER', 'SPEAKER'],
            companyId: 'Swiss IT Solutions AG',
            isActive: true,
            createdAt: '2025-01-15T10:00:00Z',
            updatedAt: '2025-01-15T10:00:00Z',
            company: {
              id: 'Swiss IT Solutions AG',
              name: 'Swiss IT Solutions AG',
              industry: 'Technology',
            },
          },
        ],
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      mock
        .onGet('/api/v1/users', { params: { page: 1, limit: 20, include: 'company,roles' } })
        .reply(200, mockResponse);

      const filters: UserFilters = {};
      const pagination: UserPagination = { page: 1, limit: 20 };

      const result = await listUsers(filters, pagination, ['company', 'roles']);

      expect(result.data[0].company).toBeDefined();
      expect(result.data[0].company?.name).toBe('Swiss IT Solutions AG');
    });

    it('should_filterByRole_when_roleFilterProvided', async () => {
      const mockResponse = {
        data: [
          {
            id: 'speaker.one',
            email: 'speaker@example.com',
            firstName: 'Speaker',
            lastName: 'One',
            roles: ['SPEAKER'],
            isActive: true,
            createdAt: '2025-01-15T10:00:00Z',
            updatedAt: '2025-01-15T10:00:00Z',
          },
        ],
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      mock.onGet('/api/v1/users').reply(200, mockResponse);

      const filters: UserFilters = { role: ['SPEAKER'] };
      const pagination: UserPagination = { page: 1, limit: 20 };

      const result = await listUsers(filters, pagination);

      expect(result.data).toHaveLength(1);
      expect(result.data[0].roles).toContain('SPEAKER');
    });

    it('should_filterByCompany_when_companyFilterProvided', async () => {
      const mockResponse = {
        data: [
          {
            id: 'employee.one',
            email: 'employee@techcorp.ch',
            firstName: 'Employee',
            lastName: 'One',
            roles: ['ATTENDEE'],
            companyId: 'TechCorp AG',
            isActive: true,
            createdAt: '2025-01-15T10:00:00Z',
            updatedAt: '2025-01-15T10:00:00Z',
          },
        ],
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      mock.onGet('/api/v1/users').reply(200, mockResponse);

      const filters: UserFilters = { company: 'TechCorp AG' };
      const pagination: UserPagination = { page: 1, limit: 20 };

      const result = await listUsers(filters, pagination);

      expect(result.data[0].companyId).toBe('TechCorp AG');
    });

    it('should_filterByStatus_when_statusFilterProvided', async () => {
      const mockResponse = {
        data: [
          {
            id: 'active.user',
            email: 'active@example.com',
            firstName: 'Active',
            lastName: 'User',
            roles: ['ATTENDEE'],
            isActive: true,
            createdAt: '2025-01-15T10:00:00Z',
            updatedAt: '2025-01-15T10:00:00Z',
          },
        ],
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      mock.onGet('/api/v1/users').reply(200, mockResponse);

      const filters: UserFilters = { status: 'active' };
      const pagination: UserPagination = { page: 1, limit: 20 };

      const result = await listUsers(filters, pagination);

      expect(result.data[0].isActive).toBe(true);
    });

    it('should_paginate_when_paginationParametersProvided', async () => {
      const mockResponse = {
        data: [],
        pagination: {
          page: 2,
          limit: 20,
          totalItems: 47,
          totalPages: 3,
          hasNext: true,
          hasPrev: true,
        },
      };

      mock.onGet('/api/v1/users', { params: { page: 2, limit: 20 } }).reply(200, mockResponse);

      const filters: UserFilters = {};
      const pagination: UserPagination = { page: 2, limit: 20 };

      const result = await listUsers(filters, pagination);

      expect(result.pagination.page).toBe(2);
      expect(result.pagination.hasNext).toBe(true);
      expect(result.pagination.hasPrev).toBe(true);
    });
  });

  describe('searchUsers', () => {
    it('should_searchUsers_when_queryProvided', async () => {
      const mockResponse = [
        {
          id: 'anna.mueller',
          email: 'anna.m@techcorp.ch',
          firstName: 'Anna',
          lastName: 'Müller',
          roles: ['ORGANIZER', 'SPEAKER'],
          profilePictureUrl: 'https://cdn.batbern.ch/profile-pictures/anna.jpg',
        },
      ];

      mock
        .onGet('/api/v1/users/search', { params: { query: 'Anna', limit: 10 } })
        .reply(200, mockResponse);

      const result = await searchUsers('Anna');

      expect(result).toHaveLength(1);
      expect(result[0].firstName).toBe('Anna');
      expect(result[0].lastName).toBe('Müller');
    });

    it('should_returnEmptyArray_when_noResultsFound', async () => {
      mock
        .onGet('/api/v1/users/search', { params: { query: 'NonExistent', limit: 10 } })
        .reply(200, []);

      const result = await searchUsers('NonExistent');

      expect(result).toHaveLength(0);
    });
  });

  describe('getUserById', () => {
    it('should_fetchUserById_when_validIdProvided', async () => {
      const mockResponse = {
        id: 'john.doe',
        email: 'john.doe@example.com',
        firstName: 'John',
        lastName: 'Doe',
        roles: ['ATTENDEE'],
        isActive: true,
        createdAt: '2025-01-15T10:00:00Z',
        updatedAt: '2025-01-15T10:00:00Z',
      };

      mock.onGet('/api/v1/users/john.doe').reply(200, mockResponse);

      const result = await getUserById('john.doe');

      expect(result.id).toBe('john.doe');
      expect(result.firstName).toBe('John');
    });

    it('should_fetchUserWithIncludes_when_includesProvided', async () => {
      const mockResponse = {
        id: 'john.doe',
        email: 'john.doe@example.com',
        firstName: 'John',
        lastName: 'Doe',
        roles: ['ATTENDEE'],
        companyId: 'TechCorp AG',
        isActive: true,
        createdAt: '2025-01-15T10:00:00Z',
        updatedAt: '2025-01-15T10:00:00Z',
        company: {
          id: 'TechCorp AG',
          name: 'TechCorp AG',
          industry: 'Technology',
        },
        preferences: {
          theme: 'dark',
          language: 'de',
        },
      };

      mock
        .onGet('/api/v1/users/john.doe', {
          params: { include: 'company,preferences,activity' },
        })
        .reply(200, mockResponse);

      const result = await getUserById('john.doe', ['company', 'preferences', 'activity']);

      expect(result.company).toBeDefined();
      expect(result.preferences).toBeDefined();
    });
  });

  describe('createUser', () => {
    it('should_createUser_when_validDataProvided', async () => {
      const createData = {
        firstName: 'New',
        lastName: 'User',
        email: 'new.user@example.com',
        companyId: 'TechCorp AG',
        roles: ['ATTENDEE'] as Role[],
      };

      const mockResponse = {
        id: 'new.user',
        ...createData,
        isActive: true,
        createdAt: '2025-01-20T10:00:00Z',
        updatedAt: '2025-01-20T10:00:00Z',
      };

      mock.onPost('/api/v1/users', createData).reply(201, mockResponse);

      const result = await createUser(createData);

      expect(result.id).toBe('new.user');
      expect(result.email).toBe('new.user@example.com');
      expect(result.roles).toContain('ATTENDEE');
    });

    it('should_throwError_when_emailAlreadyExists', async () => {
      const createData = {
        firstName: 'Duplicate',
        lastName: 'User',
        email: 'existing@example.com',
        roles: ['ATTENDEE'] as Role[],
      };

      mock.onPost('/api/v1/users', createData).reply(409, {
        code: 'EMAIL_ALREADY_EXISTS',
        message: 'Email address already exists',
      });

      await expect(createUser(createData)).rejects.toThrow();
    });
  });

  describe('updateUserRoles', () => {
    it('should_updateUserRoles_when_validRolesProvided', async () => {
      const userId = 'john.doe';
      const newRoles: Role[] = ['ORGANIZER', 'SPEAKER'];

      const mockResponse = {
        id: userId,
        email: 'john.doe@example.com',
        firstName: 'John',
        lastName: 'Doe',
        roles: newRoles,
        isActive: true,
        createdAt: '2025-01-15T10:00:00Z',
        updatedAt: '2025-01-20T10:00:00Z',
      };

      mock.onPut(`/api/v1/users/${userId}/roles`, { roles: newRoles }).reply(200, mockResponse);

      const result = await updateUserRoles(userId, newRoles);

      expect(result.roles).toEqual(newRoles);
    });

    it('should_throwError_when_minimumOrganizersViolated', async () => {
      const userId = 'last.organizer';
      const newRoles: Role[] = ['SPEAKER']; // Removing ORGANIZER role

      mock.onPut(`/api/v1/users/${userId}/roles`).reply(400, {
        code: 'MINIMUM_ORGANIZERS_REQUIRED',
        message: 'There must be at least 2 organizers in the system',
      });

      await expect(updateUserRoles(userId, newRoles)).rejects.toThrow();
    });
  });

  describe('deleteUser', () => {
    it('should_deleteUser_when_validIdProvided', async () => {
      const userId = 'user.to.delete';

      mock.onDelete(`/api/v1/users/${userId}`).reply(204);

      await expect(deleteUser(userId)).resolves.not.toThrow();
    });

    it('should_throwError_when_deletingLastOrganizer', async () => {
      const userId = 'last.organizer';

      mock.onDelete(`/api/v1/users/${userId}`).reply(400, {
        code: 'CANNOT_DELETE_LAST_ORGANIZER',
        message: 'Cannot delete the last organizer',
      });

      await expect(deleteUser(userId)).rejects.toThrow();
    });

    it('should_throwError_when_userNotFound', async () => {
      const userId = 'nonexistent.user';

      mock.onDelete(`/api/v1/users/${userId}`).reply(404, {
        code: 'USER_NOT_FOUND',
        message: 'User not found',
      });

      await expect(deleteUser(userId)).rejects.toThrow();
    });
  });
});
