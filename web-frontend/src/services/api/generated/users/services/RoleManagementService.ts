/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class RoleManagementService {
  /**
   * Get user roles
   * Retrieve roles for a specific user.
   *
   * **Acceptance Criteria**: AC8
   *
   * **Story 1.16.2**: Uses username as identifier instead of UUID
   *
   * **Authorization**: ADMIN or ORGANIZER role required
   *
   * @returns any Roles retrieved successfully
   * @throws ApiError
   */
  public static getUserRoles({
    username,
  }: {
    /**
     * Username (unique identifier)
     */
    username: string;
  }): CancelablePromise<{
    /**
     * Username (unique identifier) - Story 1.16.2
     */
    username?: string;
    roles?: Array<'ORGANIZER' | 'SPEAKER' | 'PARTNER' | 'ATTENDEE'>;
  }> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/users/{username}/roles',
      path: {
        username: username,
      },
      errors: {
        401: `Unauthorized - missing or invalid JWT token`,
        403: `Forbidden - insufficient permissions`,
        404: `Resource not found`,
        500: `Internal server error`,
      },
    });
  }
  /**
   * Update user roles
   * Update roles for a specific user.
   *
   * **Acceptance Criteria**: AC8
   *
   * **Story 1.16.2**: Uses username as identifier instead of UUID
   *
   * **Authorization**: ADMIN or ORGANIZER role required
   *
   * **Business Rules**:
   * - System must maintain at least 2 active ORGANIZER role users
   * - Removing ORGANIZER role requires approval if only 2 remain
   *
   * **Events Published**: UserRoleChangedEvent to EventBridge
   *
   * **Cache Invalidation**: All user caches cleared on role update
   *
   * @returns any Roles updated successfully
   * @throws ApiError
   */
  public static updateUserRoles({
    username,
    requestBody,
  }: {
    /**
     * Username (unique identifier)
     */
    username: string;
    requestBody: {
      roles: Array<'ORGANIZER' | 'SPEAKER' | 'PARTNER' | 'ATTENDEE'>;
    };
  }): CancelablePromise<{
    /**
     * Username (unique identifier) - Story 1.16.2
     */
    username?: string;
    roles?: Array<string>;
  }> {
    return __request(OpenAPI, {
      method: 'PUT',
      url: '/users/{username}/roles',
      path: {
        username: username,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        400: `Bad request - validation error`,
        401: `Unauthorized - missing or invalid JWT token`,
        403: `Forbidden - insufficient permissions`,
        404: `Resource not found`,
        409: `Conflict - resource already exists or business rule violation`,
        500: `Internal server error`,
      },
    });
  }
}
