/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { UserSearchResponse } from '../models/UserSearchResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class UserSearchService {
  /**
   * Search users with autocomplete
   * Search users by name or email with autocomplete functionality.
   *
   * **Acceptance Criteria**: AC4, AC11
   *
   * **Caching**:
   * - Caffeine in-memory cache with 10-minute TTL
   * - Cache key includes query, role filter, and limit
   * - Automatic cache invalidation on user updates
   *
   * **Performance**:
   * - Cached response: <50ms (P95)
   * - Cache miss: <100ms (P95)
   *
   * **Default Results**: 20 users (configurable via limit parameter)
   *
   * @returns UserSearchResponse Search results returned successfully
   * @throws ApiError
   */
  public static searchUsers({
    query,
    role,
    limit = 20,
  }: {
    /**
     * Search query (minimum 1 character)
     */
    query: string;
    /**
     * Filter by specific role
     */
    role?: 'ORGANIZER' | 'SPEAKER' | 'PARTNER' | 'ATTENDEE';
    /**
     * Maximum number of results (default 20)
     */
    limit?: number;
  }): CancelablePromise<Array<UserSearchResponse>> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/users/search',
      query: {
        query: query,
        role: role,
        limit: limit,
      },
      errors: {
        400: `Bad request - validation error`,
        401: `Unauthorized - missing or invalid JWT token`,
        500: `Internal server error`,
      },
    });
  }
}
