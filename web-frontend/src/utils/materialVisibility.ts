/**
 * Whether a new session material is published after the event when nobody says otherwise: a PDF
 * is, any other file is not. The same rule as the backend (`SessionMaterial.isPdf`): extension
 * `pdf` or type `application/pdf`, case-insensitive.
 */
export const isPublicByDefault = (fileName: string, mimeType?: string | null): boolean =>
  fileName.toLowerCase().endsWith('.pdf') || mimeType?.toLowerCase() === 'application/pdf';
