/**
 * File upload target.
 *
 * `useFileUpload` runs the three-phase upload (ADR-002): presign at the backend, PUT straight to
 * S3, confirm at the backend. The two backend calls differ by purpose, so the hook takes a target
 * instead of an endpoint string: `uploadService.uploadTarget(purpose, target?)` for the purposes of
 * the upload contract (`uploads-api`, Story 17.11), `sessionApiClient.materialUploadTarget` for the
 * session materials until they move (Story 17.11d). Errors are passed through unchanged; the hook
 * reports them per phase.
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
  /** Only the materials endpoint returns it; the hook falls back to the file name. */
  fileExtension?: string;
}

/** Phase 3 request (the materials endpoint reads it; the upload contract does not). */
export interface FileUploadConfirmRequest {
  fileId: string;
  fileExtension: string;
  checksum: string;
}

/** Phase 3 result: the CDN URL when the confirm attached the file to its target. */
export interface FileUploadConfirmResult {
  fileUrl?: string | null;
}

export interface FileUploadTarget {
  /** The purpose, for logs, e.g. 'company-logo' or 'material'. */
  purpose: string;
  presign: (request: FileUploadPresignRequest) => Promise<FileUploadPresignedUrl>;
  confirm: (
    fileId: string,
    request: FileUploadConfirmRequest
  ) => Promise<FileUploadConfirmResult | void>;
}
