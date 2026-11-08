/**
 * File upload target.
 *
 * `useFileUpload` runs the three-phase upload (ADR-002): presign at the backend, PUT straight to
 * S3, confirm at the backend. The two backend calls differ by purpose (company logo, session
 * material), so the hook takes a target whose functions live in the service that owns the
 * resource (`companyApi.logoUploadTarget`, `sessionApiClient.materialUploadTarget`) instead of
 * an endpoint string. Errors are passed through unchanged; the hook reports them per phase.
 */

/** Phase 1 request body. */
export interface FileUploadPresignRequest {
  fileName: string;
  fileSize: number;
  mimeType: string;
}

/** Phase 1 response: where to PUT the file and how to confirm it. */
export interface FileUploadPresignedUrl {
  uploadUrl: string;
  fileId: string;
  s3Key: string; // S3 key for constructing the CloudFront URL
  fileExtension: string;
  expiresInMinutes: number;
  requiredHeaders: Record<string, string>;
}

/** Phase 3 request body. */
export interface FileUploadConfirmRequest {
  fileId: string;
  fileExtension: string;
  checksum: string;
}

export interface FileUploadTarget {
  /** Short name for logs, e.g. 'logo' or 'material'. */
  purpose: string;
  presign: (request: FileUploadPresignRequest) => Promise<FileUploadPresignedUrl>;
  confirm: (fileId: string, request: FileUploadConfirmRequest) => Promise<void>;
}
