/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { UserPreferences } from '../models/UserPreferences';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class UserPreferencesService {
  /**
   * Get current user preferences
   * Retrieve preferences for the currently authenticated user.
   *
   * **Acceptance Criteria**: AC6
   *
   * Includes: theme, language, notification settings, quiet hours
   *
   * @returns UserPreferences Preferences retrieved successfully
   * @throws ApiError
   */
  public static getUserPreferences(): CancelablePromise<UserPreferences> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/users/me/preferences',
      errors: {
        401: `Unauthorized - missing or invalid JWT token`,
        500: `Internal server error`,
      },
    });
  }
  /**
   * Update current user preferences
   * Update preferences for the currently authenticated user.
   *
   * **Acceptance Criteria**: AC6
   *
   * **Validation**: All preference values validated against allowed enums/formats
   *
   * @returns UserPreferences Preferences updated successfully
   * @throws ApiError
   */
  public static updateUserPreferences({
    requestBody,
  }: {
    requestBody: UserPreferences;
  }): CancelablePromise<UserPreferences> {
    return __request(OpenAPI, {
      method: 'PUT',
      url: '/users/me/preferences',
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
