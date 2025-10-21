/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CreateUserRequest } from '../models/CreateUserRequest';
import type { PaginatedUserResponse } from '../models/PaginatedUserResponse';
import type { UpdateUserRequest } from '../models/UpdateUserRequest';
import type { UserResponse } from '../models/UserResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class UserManagementService {
  /**
   * Get current authenticated user
   * Retrieve the profile of the currently authenticated user with optional resource expansion.
   *
   * **Acceptance Criteria**: AC1
   *
   * **Resource Expansion**:
   * - company: Expands company details
   * - preferences: Includes user preferences
   * - settings: Includes account settings
   * - roles: Includes full role information
   * - activity: Includes recent activity history
   *
   * **Performance**: <100ms (P95)
   *
   * @returns UserResponse User profile retrieved successfully
   * @throws ApiError
   */
  public static getCurrentUser({
    include,
  }: {
    /**
     * Comma-separated list of resources to include
     */
    include?: string;
  }): CancelablePromise<UserResponse> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/users/me',
      query: {
        include: include,
      },
      errors: {
        401: `Unauthorized - missing or invalid JWT token`,
        500: `Internal server error`,
      },
    });
  }
  /**
   * Update current user profile
   * Update the profile of the currently authenticated user.
   *
   * **Acceptance Criteria**: AC2
   *
   * **Validation Rules**:
   * - Email must be valid and unique
   * - Names: 2-100 characters
   * - Bio: max 2000 characters
   *
   * **Cognito Sync**: Updates synchronize with AWS Cognito
   *
   * **Events Published**: UserUpdatedEvent to EventBridge
   *
   * **Cache Invalidation**: All user caches cleared on update
   *
   * @returns UserResponse User updated successfully
   * @throws ApiError
   */
  public static updateCurrentUser({
    requestBody,
  }: {
    requestBody: UpdateUserRequest;
  }): CancelablePromise<UserResponse> {
    return __request(OpenAPI, {
      method: 'PUT',
      url: '/users/me',
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        400: `Bad request - validation error`,
        401: `Unauthorized - missing or invalid JWT token`,
        409: `Conflict - resource already exists or business rule violation`,
        500: `Internal server error`,
      },
    });
  }
  /**
   * Partially update current user profile
   * Partially update the profile of the currently authenticated user.
   *
   * **Acceptance Criteria**: AC2
   *
   * Similar to PUT but allows partial updates.
   *
   * @returns UserResponse User updated successfully
   * @throws ApiError
   */
  public static patchCurrentUser({
    requestBody,
  }: {
    requestBody: UpdateUserRequest;
  }): CancelablePromise<UserResponse> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/users/me',
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        400: `Bad request - validation error`,
        401: `Unauthorized - missing or invalid JWT token`,
        409: `Conflict - resource already exists or business rule violation`,
        500: `Internal server error`,
      },
    });
  }
  /**
   * List users with advanced query support
   * Retrieve a paginated list of users with optional filtering, sorting,
   * field selection, and resource expansion.
   *
   * **Acceptance Criteria**: AC3, AC13, AC14
   *
   * **Authorization**: ADMIN or ORGANIZER role required
   *
   * **Filter Syntax Examples**:
   * - Single filter: {"role":"SPEAKER"}
   * - Multiple fields: {"role":"SPEAKER","isActive":true}
   * - Company filter: {"companyId":"550e8400-e29b-41d4-a716-446655440000"}
   * - Logical operators: {"$or":[{"role":"SPEAKER"},{"role":"PARTNER"}]}
   *
   * **Sort Syntax**:
   * - Ascending: lastName or +lastName
   * - Descending: -createdAt
   * - Multiple fields: lastName,-createdAt
   *
   * **Field Selection**:
   * - Specific fields: ?fields=id,email,firstName,lastName,roles
   * - All fields: omit fields parameter
   *
   * **Resource Expansion**:
   * - ?include=company: Expands company details
   * - ?include=roles: Includes full role information
   * - ?include=preferences: Includes user preferences
   *
   * **Performance**:
   * - Basic query: <100ms (P95)
   * - With all includes: <150ms (P95)
   *
   * @returns PaginatedUserResponse Successful response with paginated users
   * @throws ApiError
   */
  public static listUsers({
    filter,
    sort,
    page = 1,
    limit = 20,
    fields,
    include,
    role,
    company,
  }: {
    /**
     * MongoDB-style JSON filter criteria
     */
    filter?: string;
    /**
     * Sort fields (comma-separated, prefix with - for descending)
     */
    sort?: string;
    /**
     * Page number (1-indexed)
     */
    page?: number;
    /**
     * Items per page (max 100)
     */
    limit?: number;
    /**
     * Comma-separated field names for sparse fieldsets
     */
    fields?: string;
    /**
     * Comma-separated list of resources to include
     */
    include?: string;
    /**
     * Filter by specific role (deprecated, use filter parameter)
     */
    role?: 'ORGANIZER' | 'SPEAKER' | 'PARTNER' | 'ATTENDEE';
    /**
     * Filter by company name (deprecated, use filter parameter)
     */
    company?: string;
  }): CancelablePromise<PaginatedUserResponse> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/users',
      query: {
        filter: filter,
        sort: sort,
        page: page,
        limit: limit,
        fields: fields,
        include: include,
        role: role,
        company: company,
      },
      errors: {
        400: `Bad request - validation error`,
        401: `Unauthorized - missing or invalid JWT token`,
        403: `Forbidden - insufficient permissions`,
        500: `Internal server error`,
      },
    });
  }
  /**
   * Create new user (Organizer/Admin only)
   * Create a new user with Cognito integration and automatic username generation.
   *
   * **Acceptance Criteria**: AC4 (Story 2.5.2 - User Management Frontend)
   *
   * **Authorization**: ORGANIZER or ADMIN role required
   *
   * **Business Rules**:
   * - Email must be unique
   * - Username auto-generated from first/last name (e.g., john.doe)
   * - User created in Cognito with email_verified=true
   * - Default role: ATTENDEE (unless initialRoles specified)
   * - Password set via Cognito welcome email
   *
   * **Events Published**: UserCreatedEvent to EventBridge
   *
   * **Performance**: <200ms (P95)
   *
   * @returns UserResponse User created successfully
   * @throws ApiError
   */
  public static createUser({
    requestBody,
  }: {
    requestBody: CreateUserRequest;
  }): CancelablePromise<UserResponse> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/users',
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        400: `Validation error`,
        401: `Unauthorized - missing or invalid JWT token`,
        403: `Forbidden - Requires ORGANIZER or ADMIN role`,
        500: `Internal server error`,
      },
    });
  }
  /**
   * Get user details by username
   * Retrieve detailed information about a specific user.
   *
   * **Acceptance Criteria**: AC5
   *
   * **Story 1.16.2**: Uses username as identifier instead of UUID
   *
   * **Resource Expansion**: Supports ?include parameter
   *
   * **Performance**: <150ms (P95)
   *
   * @returns UserResponse User details retrieved successfully
   * @throws ApiError
   */
  public static getUserByUsername({
    username,
    include,
  }: {
    /**
     * Username (unique identifier)
     */
    username: string;
    /**
     * Comma-separated list of resources to include
     */
    include?: string;
  }): CancelablePromise<UserResponse> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/users/{username}',
      path: {
        username: username,
      },
      query: {
        include: include,
      },
      errors: {
        401: `Unauthorized - missing or invalid JWT token`,
        404: `Resource not found`,
        500: `Internal server error`,
      },
    });
  }
}
