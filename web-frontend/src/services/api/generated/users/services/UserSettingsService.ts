/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { UserSettings } from '../models/UserSettings';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class UserSettingsService {
  /**
   * Get current user settings
   * Retrieve account settings for the currently authenticated user.
   *
   * **Acceptance Criteria**: AC7
   *
   * Includes: privacy controls, account preferences, security settings
   *
   * @returns UserSettings Settings retrieved successfully
   * @throws ApiError
   */
  public static getUserSettings(): CancelablePromise<UserSettings> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/users/me/settings',
      errors: {
        401: `Unauthorized - missing or invalid JWT token`,
        500: `Internal server error`,
      },
    });
  }
  /**
   * Update current user settings
   * Update account settings for the currently authenticated user.
   *
   * **Acceptance Criteria**: AC7
   *
   * **Privacy Controls**: Profile visibility, contact information display
   * **Security Settings**: Two-factor authentication enablement
   *
   * @returns UserSettings Settings updated successfully
   * @throws ApiError
   */
  public static updateUserSettings({
    requestBody,
  }: {
    requestBody: UserSettings;
  }): CancelablePromise<UserSettings> {
    return __request(OpenAPI, {
      method: 'PUT',
      url: '/users/me/settings',
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
