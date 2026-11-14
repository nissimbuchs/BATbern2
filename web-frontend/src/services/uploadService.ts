/**
 * The one upload contract (ADR-002 decision 2026-11-07, Story 17.11): `uploads-api`.
 *
 *   POST /uploads/{purpose}                      reserve, presigned PUT URL
 *   POST /uploads/{purpose}/{uploadId}/confirm   the file is in S3; with a target it is attached
 *   POST /uploads/{purpose}/import               server-side import from an HTTPS URL
 *
 * `uploadTarget(purpose, target?)` is what `useFileUpload` takes. Without a target the upload is
 * CONFIRMED and the entity create or edit takes its `uploadId` (`logoUploadId`,
 * `themeImageUploadId`); with one it is attached on confirm. `profile-picture` always needs its
 * target `username` (Story 17.11a). Errors are passed through for the hook
 * to report per phase.
 */
import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/uploads-api.types';
import type { FileUploadTarget } from '@/types/fileUpload.types';

export type UploadPurpose = components['schemas']['UploadPurpose'];
export type UploadTargetRef = components['schemas']['UploadTarget'];
export type Upload = components['schemas']['Upload'];
type UploadSlot = components['schemas']['UploadSlot'];
type CreateUploadRequest = components['schemas']['CreateUploadRequest'];
type ConfirmUploadRequest = components['schemas']['ConfirmUploadRequest'];
type ImportUploadRequest = components['schemas']['ImportUploadRequest'];

/** The upload target of a purpose for `useFileUpload`, optionally attached to `target` on confirm. */
export const uploadTarget = (
  purpose: UploadPurpose,
  target?: UploadTargetRef
): FileUploadTarget => ({
  purpose,
  presign: async ({ fileName, fileSize, mimeType }) => {
    const body: CreateUploadRequest = { fileName, contentType: mimeType, size: fileSize, target };
    const response = await apiClient.post<UploadSlot>(`/uploads/${purpose}`, body);
    return {
      uploadUrl: response.data.uploadUrl,
      fileId: response.data.uploadId,
      requiredHeaders: response.data.requiredHeaders,
    };
  },
  confirm: async (uploadId) => ({
    fileUrl: (await confirmUpload(purpose, uploadId, target)).fileUrl,
  }),
});

/**
 * Confirm an upload (the file is in S3); with a target it is attached and the result carries its
 * `fileUrl`. A purpose whose target is required (`profile-picture`) answers 400 without one.
 */
export const confirmUpload = async (
  purpose: UploadPurpose,
  uploadId: string,
  target?: UploadTargetRef
): Promise<Upload> => {
  const body: ConfirmUploadRequest = target ? { target } : {};
  const response = await apiClient.post<Upload>(`/uploads/${purpose}/${uploadId}/confirm`, body);
  return response.data;
};

/**
 * Server-side import of an image from an HTTPS URL (batch import): the backend fetches and stores
 * it, no bytes pass through the browser. Returns the CONFIRMED upload's id for the entity create.
 */
export const importUpload = async (
  purpose: UploadPurpose,
  url: string,
  fileName?: string
): Promise<string> => {
  const body: ImportUploadRequest = { url, fileName };
  const response = await apiClient.post<Upload>(`/uploads/${purpose}/import`, body);
  return response.data.uploadId;
};

/**
 * The file name sent on create. The upload contract takes the extension from the name and refuses
 * a name without an accepted one (the former photo and teaser endpoints fell back to the content
 * type), so a name without one gets the extension of its type, e.g. `IMG_0042` → `IMG_0042.jpg`;
 * an empty name becomes `{fallbackBase}.{ext}`. A type outside `extensionByType` leaves the name as
 * it is, and the server answers 400.
 */
export const fileNameWithExtension = (
  file: File,
  acceptedExtensions: readonly string[],
  extensionByType: Readonly<Record<string, string>>,
  fallbackBase: string
): string => {
  const dot = file.name.lastIndexOf('.');
  const extension = dot < 0 ? '' : file.name.slice(dot + 1).toLowerCase();
  const fromType = extensionByType[file.type];
  if (acceptedExtensions.includes(extension) || !fromType) {
    return file.name;
  }
  return `${file.name || fallbackBase}.${fromType}`;
};
