/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { GetOrCreateUserRequest } from '../models/GetOrCreateUserRequest';
import type { GetOrCreateUserResponse } from '../models/GetOrCreateUserResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class DomainIntegrationService {
  /**
   * Get or create user (for domain services)
   * Idempotent endpoint for domain services to get existing user or create new user.
   *
   * **Acceptance Criteria**: AC12
   *
   * **Use Cases**:
   * - Speaker Service: Create user account when speaker registers
   * - Partner Service: Create user account for partner contact
   * - Attendee Service: Create user account during event registration
   *
   * **Idempotency**: Safe to call multiple times with same email
   *
   * **Cognito Sync**: Optionally creates Cognito user if createIfMissing=true
   *
   * **Performance**: <200ms (P95)
   *
   * @returns GetOrCreateUserResponse User retrieved or created
   * @throws ApiError
   */
  public static getOrCreateUser({
    requestBody,
  }: {
    requestBody: GetOrCreateUserRequest;
  }): CancelablePromise<GetOrCreateUserResponse> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/users/get-or-create',
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        400: `Bad request - validation error`,
        401: `Unauthorized - missing or invalid JWT token`,
        500: `Internal server error`,
      },
    });
  }
}
