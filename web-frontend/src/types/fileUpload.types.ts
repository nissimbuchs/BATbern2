/**
 * File upload target.
 *
 * `useFileUpload` runs the three-phase upload (ADR-002): presign at the backend, PUT straight to
 * S3, confirm at the backend. The two backend calls differ by purpose, so the hook takes a target
 * instead of an endpoint string: `uploadService.uploadTarget(purpose, target?)`, the purposes of the
 * upload contract (`uploads-api`, Story 17.11; session materials since Story 17.11d). Errors are
 * passed through unchanged; the hook reports them per phase.
 */

/** Phase 1 request: what the client knows about the file. */
export interface FileUploadPresignRequest {
  fileName: string;
  fileSize: number;
  mimeType: string;
}

/** Phase 1 result: where to PUT the file and with which headers. */
export interface FileUploadPresignedUrl {
  uploadUrl: string;
  fileId: string;
  requiredHeaders: Record<string, string>;
}

/** Phase 3 result: the CDN URL when the confirm attached the file to its target. */
export interface FileUploadConfirmResult {
  fileUrl?: string | null;
}

export interface FileUploadTarget {
  /** The purpose, for logs, e.g. 'company-logo' or 'session-material'. */
  purpose: string;
  presign: (request: FileUploadPresignRequest) => Promise<FileUploadPresignedUrl>;
  /** Phase 3: the upload contract needs only the upload id (and the target the target carries). */
  confirm: (fileId: string) => Promise<FileUploadConfirmResult | void>;
}
