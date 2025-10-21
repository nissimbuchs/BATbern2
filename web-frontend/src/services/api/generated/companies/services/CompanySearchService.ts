/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CompanySearchResponse } from '../models/CompanySearchResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class CompanySearchService {
  /**
   * Search companies with autocomplete
   * Search companies by name with autocomplete functionality.
   *
   * **Acceptance Criteria**: AC5, AC11
   *
   * **Caching**:
   * - Caffeine in-memory cache with 15-minute TTL
   * - Cache key includes both query and limit
   * - Automatic cache invalidation on company updates
   *
   * **Performance**:
   * - Cached response: <50ms (P95)
   * - Cache miss: <100ms (P95)
   *
   * **Default Results**: 20 companies (configurable via limit parameter)
   *
   * @returns CompanySearchResponse Search results returned successfully
   * @throws ApiError
   */
  public static searchCompanies({
    query,
    limit = 20,
  }: {
    /**
     * Search query (minimum 1 character)
     */
    query: string;
    /**
     * Maximum number of results (default 20)
     */
    limit?: number;
  }): CancelablePromise<Array<CompanySearchResponse>> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/companies/search',
      query: {
        query: query,
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
