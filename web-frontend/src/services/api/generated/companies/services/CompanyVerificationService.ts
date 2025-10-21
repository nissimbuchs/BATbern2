/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CompanyResponse } from '../models/CompanyResponse';
import type { UIDValidationResponse } from '../models/UIDValidationResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class CompanyVerificationService {
  /**
   * Validate Swiss UID format
   * Validates Swiss company UID (Unternehmens-Identifikationsnummer) format.
   *
   * **Acceptance Criteria**: AC12
   *
   * **Expected Format**: CHE-XXX.XXX.XXX
   *
   * **Validation Rules**:
   * - Must start with "CHE-"
   * - Followed by 9 digits in XXX.XXX.XXX format
   * - Total length: 15 characters
   *
   * **Future Enhancement**: Integration with Swiss Business Registry for real-time validation
   *
   * @returns UIDValidationResponse Validation result returned
   * @throws ApiError
   */
  public static validateUid({
    uid,
  }: {
    /**
     * Swiss UID to validate
     */
    uid: string;
  }): CancelablePromise<UIDValidationResponse> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/companies/validate-uid',
      query: {
        uid: uid,
      },
      errors: {
        400: `Bad request - validation error`,
        401: `Unauthorized - missing or invalid JWT token`,
        500: `Internal server error`,
      },
    });
  }
  /**
   * Verify company
   * Marks a company as verified by an ORGANIZER.
   *
   * **Acceptance Criteria**: AC13
   *
   * **Story 1.16.2**: Uses company name as identifier instead of UUID
   *
   * **Authorization**: ORGANIZER role required
   *
   * **Events Published**: CompanyVerifiedEvent to EventBridge
   *
   * **Idempotency**: Safe to call multiple times (no error if already verified)
   *
   * @returns CompanyResponse Company verified successfully
   * @throws ApiError
   */
  public static verifyCompany({
    name,
  }: {
    /**
     * Company name (unique identifier)
     */
    name: string;
  }): CancelablePromise<CompanyResponse> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/companies/{name}/verify',
      path: {
        name: name,
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
