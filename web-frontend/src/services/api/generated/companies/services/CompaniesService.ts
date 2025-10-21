/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CompanyResponse } from '../models/CompanyResponse';
import type { CreateCompanyRequest } from '../models/CreateCompanyRequest';
import type { LogoUploadConfirmRequest } from '../models/LogoUploadConfirmRequest';
import type { LogoUploadConfirmResponse } from '../models/LogoUploadConfirmResponse';
import type { LogoUploadRequest } from '../models/LogoUploadRequest';
import type { PaginatedCompanyResponse } from '../models/PaginatedCompanyResponse';
import type { PresignedUploadUrl } from '../models/PresignedUploadUrl';
import type { UpdateCompanyRequest } from '../models/UpdateCompanyRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class CompaniesService {
  /**
   * List and search companies with advanced query support
   * Retrieve a paginated list of companies with optional filtering, sorting, field selection, and resource expansion.
   *
   * **Acceptance Criteria**: AC1, AC14, AC15
   *
   * **Filter Syntax Examples**:
   * - Single filter: `{"industry":"Technology"}`
   * - Multiple fields: `{"industry":"Technology","isVerified":true}`
   * - Logical operators: `{"$or":[{"industry":"Technology"},{"industry":"Finance"}]}`
   * - Comparison: `{"createdAt":{"$gte":"2025-01-01T00:00:00Z"}}`
   * - Contains: `{"name":{"$contains":"Bern"}}`
   *
   * **Sort Syntax**:
   * - Ascending: `name` or `+name`
   * - Descending: `-name`
   * - Multiple fields: `industry,-createdAt`
   *
   * **Field Selection**:
   * - Specific fields: `?fields=id,name,industry`
   * - All fields: omit `fields` parameter
   *
   * **Resource Expansion**:
   * - Statistics: `?include=statistics` (totalEvents, totalSpeakers, totalPartners)
   * - Logo: `?include=logo` (url, s3Key, fileId)
   * - Multiple: `?include=statistics,logo`
   *
   * **Performance**:
   * - Basic query: <100ms (P95)
   * - With all includes: <200ms (P95)
   *
   * @returns PaginatedCompanyResponse Successful response with paginated companies
   * @throws ApiError
   */
  public static listCompanies({
    filter,
    sort,
    page = 1,
    limit = 20,
    fields,
    include,
  }: {
    /**
     * MongoDB-style JSON filter criteria
     */
    filter?: string;
    /**
     * Sort fields (comma-separated, prefix with - for descending)
     */
    sort?: string;
    /**
     * Page number (1-indexed)
     */
    page?: number;
    /**
     * Items per page (max 100)
     */
    limit?: number;
    /**
     * Comma-separated field names for sparse fieldsets
     */
    fields?: string;
    /**
     * Comma-separated list of resources to include
     */
    include?: string;
  }): CancelablePromise<PaginatedCompanyResponse> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/companies',
      query: {
        filter: filter,
        sort: sort,
        page: page,
        limit: limit,
        fields: fields,
        include: include,
      },
      errors: {
        400: `Bad request - validation error`,
        401: `Unauthorized - missing or invalid JWT token`,
        500: `Internal server error`,
      },
    });
  }
  /**
   * Create a new company
   * Create a new company with complete company data.
   *
   * **Acceptance Criteria**: AC1, AC3
   *
   * **Validation Rules**:
   * - Company name must be unique
   * - Swiss UID format (if provided): CHE-XXX.XXX.XXX
   * - Website must be valid URL (if provided)
   *
   * **Events Published**: CompanyCreatedEvent to EventBridge
   *
   * **Performance**: <200ms (P95)
   *
   * @returns CompanyResponse Company created successfully
   * @throws ApiError
   */
  public static createCompany({
    requestBody,
  }: {
    requestBody: CreateCompanyRequest;
  }): CancelablePromise<CompanyResponse> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/companies',
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        400: `Bad request - validation error`,
        401: `Unauthorized - missing or invalid JWT token`,
        403: `Forbidden - insufficient permissions`,
        409: `Conflict - resource already exists`,
        500: `Internal server error`,
      },
    });
  }
  /**
   * Get company details by name
   * Retrieve detailed information about a specific company.
   *
   * **Acceptance Criteria**: AC2
   *
   * **Story 1.16.2**: Uses company name as identifier instead of UUID
   *
   * **Performance**: <150ms (P95)
   *
   * @returns CompanyResponse Company details retrieved successfully
   * @throws ApiError
   */
  public static getCompany({
    name,
  }: {
    /**
     * Company name (unique identifier)
     */
    name: string;
  }): CancelablePromise<CompanyResponse> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/companies/{name}',
      path: {
        name: name,
      },
      errors: {
        401: `Unauthorized - missing or invalid JWT token`,
        404: `Resource not found`,
        500: `Internal server error`,
      },
    });
  }
  /**
   * Update company (full replacement)
   * Replace all company data with new data.
   *
   * **Acceptance Criteria**: AC4
   *
   * **Story 1.16.2**: Uses company name as identifier instead of UUID
   *
   * **Validation Rules**:
   * - Company name must be unique
   * - Swiss UID format (if provided): CHE-XXX.XXX.XXX
   *
   * **Events Published**: CompanyUpdatedEvent to EventBridge
   *
   * **Cache Invalidation**: All company caches cleared on update
   *
   * @returns CompanyResponse Company updated successfully
   * @throws ApiError
   */
  public static updateCompany({
    name,
    requestBody,
  }: {
    /**
     * Company name (unique identifier)
     */
    name: string;
    requestBody: UpdateCompanyRequest;
  }): CancelablePromise<CompanyResponse> {
    return __request(OpenAPI, {
      method: 'PUT',
      url: '/companies/{name}',
      path: {
        name: name,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        400: `Bad request - validation error`,
        401: `Unauthorized - missing or invalid JWT token`,
        403: `Forbidden - insufficient permissions`,
        404: `Resource not found`,
        409: `Conflict - resource already exists`,
        500: `Internal server error`,
      },
    });
  }
  /**
   * Partially update company
   * Update specific fields of an existing company.
   *
   * **Acceptance Criteria**: AC4
   *
   * **Story 1.16.2**: Uses company name as identifier instead of UUID
   *
   * **Validation Rules**:
   * - Company name must be unique
   * - Swiss UID format (if provided): CHE-XXX.XXX.XXX
   *
   * **Events Published**: CompanyUpdatedEvent to EventBridge
   *
   * **Cache Invalidation**: All company caches cleared on update
   *
   * @returns CompanyResponse Company updated successfully
   * @throws ApiError
   */
  public static patchCompany({
    name,
    requestBody,
  }: {
    /**
     * Company name (unique identifier)
     */
    name: string;
    requestBody: UpdateCompanyRequest;
  }): CancelablePromise<CompanyResponse> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/companies/{name}',
      path: {
        name: name,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        400: `Bad request - validation error`,
        401: `Unauthorized - missing or invalid JWT token`,
        403: `Forbidden - insufficient permissions`,
        404: `Resource not found`,
        409: `Conflict - resource already exists`,
        500: `Internal server error`,
      },
    });
  }
  /**
   * Delete company
   * Delete a company and all associated data.
   *
   * **Acceptance Criteria**: AC4
   *
   * **Story 1.16.2**: Uses company name as identifier instead of UUID
   *
   * **Events Published**: CompanyDeletedEvent to EventBridge
   *
   * **Cache Invalidation**: All company caches cleared on deletion
   *
   * @returns void
   * @throws ApiError
   */
  public static deleteCompany({
    name,
  }: {
    /**
     * Company name (unique identifier)
     */
    name: string;
  }): CancelablePromise<void> {
    return __request(OpenAPI, {
      method: 'DELETE',
      url: '/companies/{name}',
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
  /**
   * Request presigned URL for company logo upload
   * Generates a presigned S3 URL for uploading a company logo directly to S3.
   *
   * **Acceptance Criteria**: AC5
   *
   * **Upload Flow**:
   * 1. Client requests presigned URL with file metadata
   * 2. Backend generates S3 presigned URL (15-minute expiration)
   * 3. Client uploads file directly to S3 using presigned URL
   * 4. Client calls confirm endpoint to save logo reference in company
   *
   * **File Validation**:
   * - Supported formats: PNG, JPEG, SVG
   * - Maximum size: 5 MB (5,242,880 bytes)
   * - MIME types: image/png, image/jpeg, image/svg+xml
   *
   * **Performance**: <100ms (P95)
   *
   * @returns PresignedUploadUrl Presigned URL generated successfully
   * @throws ApiError
   */
  public static requestLogoUploadUrl({
    id,
    requestBody,
  }: {
    /**
     * Company UUID
     */
    id: string;
    requestBody: LogoUploadRequest;
  }): CancelablePromise<PresignedUploadUrl> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/companies/{id}/logo/presigned-url',
      path: {
        id: id,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        400: `Bad request - validation error`,
        401: `Unauthorized - missing or invalid JWT token`,
        404: `Resource not found`,
        500: `Internal server error`,
      },
    });
  }
  /**
   * Confirm logo upload completion
   * Confirms that the logo has been successfully uploaded to S3 and stores the CloudFront CDN URL in the company record.
   *
   * **Acceptance Criteria**: AC5
   *
   * **Workflow**:
   * 1. Client uploads file to S3 using presigned URL
   * 2. Client calls this endpoint with fileId and fileExtension
   * 3. Backend verifies S3 object exists (optional checksum validation)
   * 4. Backend stores CloudFront CDN URL in company.logo_url
   * 5. Returns full company response with logo URL
   *
   * **Cache Invalidation**: Company caches cleared on confirmation
   *
   * **Events Published**: CompanyLogoUpdatedEvent to EventBridge
   *
   * **Performance**: <150ms (P95)
   *
   * @returns LogoUploadConfirmResponse Logo upload confirmed and company updated
   * @throws ApiError
   */
  public static confirmLogoUpload({
    id,
    requestBody,
  }: {
    /**
     * Company UUID
     */
    id: string;
    requestBody: LogoUploadConfirmRequest;
  }): CancelablePromise<LogoUploadConfirmResponse> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/companies/{id}/logo/confirm',
      path: {
        id: id,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        400: `Bad request - validation error`,
        401: `Unauthorized - missing or invalid JWT token`,
        403: `Forbidden - insufficient permissions`,
        404: `Resource not found`,
        500: `Internal server error`,
      },
    });
  }
}
