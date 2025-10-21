/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ActivityHistory } from '../models/ActivityHistory';
import type { PaginationMetadata } from '../models/PaginationMetadata';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ActivityHistoryService {
  /**
   * Get user activity history
   * Retrieve paginated activity history for a specific user.
   *
   * **Acceptance Criteria**: AC9
   *
   * **Story 1.16.2**: Uses username as identifier instead of UUID
   *
   * **Activity Types**: event_registered, session_attended, topic_voted, content_viewed
   *
   * **Timeframe Filtering**: Support for date range queries
   *
   * @returns any Activity history retrieved successfully
   * @throws ApiError
   */
  public static getUserActivity({
    username,
    timeframe = '30d',
    activityType,
    page = 1,
    limit = 20,
  }: {
    /**
     * Username (unique identifier)
     */
    username: string;
    /**
     * Filter by timeframe
     */
    timeframe?: '24h' | '7d' | '30d' | '90d' | 'all';
    /**
     * Filter by activity type
     */
    activityType?: string;
    page?: number;
    limit?: number;
  }): CancelablePromise<{
    data?: Array<ActivityHistory>;
    pagination?: PaginationMetadata;
  }> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/users/{username}/activity',
      path: {
        username: username,
      },
      query: {
        timeframe: timeframe,
        activityType: activityType,
        page: page,
        limit: limit,
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
