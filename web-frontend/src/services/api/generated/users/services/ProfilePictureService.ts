/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ProfilePictureService {
  /**
   * Upload profile picture
   * Generate presigned S3 upload URL for profile picture.
   *
   * **Acceptance Criteria**: AC10
   *
   * **File Constraints**:
   * - Max size: 5 MB
   * - Allowed formats: PNG, JPG, JPEG
   * - Recommended dimensions: 400x400 to 1000x1000 pixels
   *
   * **Upload Process**:
   * 1. Client calls this endpoint to get presigned URL
   * 2. Client uploads directly to S3 using presigned URL
   * 3. Client calls confirm endpoint with file ID
   *
   * @returns any Presigned upload URL generated successfully
   * @throws ApiError
   */
  public static uploadProfilePicture({
    requestBody,
  }: {
    requestBody: {
      filename: string;
      fileSizeBytes: number;
      mimeType: 'image/png' | 'image/jpeg' | 'image/jpg';
    };
  }): CancelablePromise<{
    /**
     * Presigned S3 upload URL (valid for 15 minutes)
     */
    uploadUrl?: string;
    /**
     * File identifier for confirmation
     */
    fileId?: string;
    /**
     * URL expiration time in seconds
     */
    expiresIn?: number;
  }> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/users/me/picture',
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
