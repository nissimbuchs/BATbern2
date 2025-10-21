/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class GdprComplianceService {
  /**
   * Delete user and all associated data
   * Delete a user and cascade deletion across all domain services.
   *
   * **Acceptance Criteria**: AC11
   *
   * **Story 1.16.2**: Uses username as identifier instead of UUID
   *
   * **Authorization**: ADMIN role or self-deletion
   *
   * **Business Rules**:
   * - Cannot delete last ORGANIZER user
   * - Cascade deletion across Event, Speaker, Partner, Attendee services
   *
   * **Events Published**: UserDeletedEvent to EventBridge
   *
   * **Audit Logging**: GDPR compliance logging
   *
   * @returns void
   * @throws ApiError
   */
  public static deleteUser({
    username,
  }: {
    /**
     * Username (unique identifier)
     */
    username: string;
  }): CancelablePromise<void> {
    return __request(OpenAPI, {
      method: 'DELETE',
      url: '/users/{username}',
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
}
