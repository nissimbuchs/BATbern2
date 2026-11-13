/**
 * Hooks for event photo management.
 * Story 10.21: Event Photos Gallery
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { eventApiClient } from '@/services/eventApiClient';
import { fileNameWithExtension, uploadTarget } from '@/services/uploadService';

/** The extensions `event-photo` accepts, and the one each allowed type implies. */
const PHOTO_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp'];
const EXTENSION_BY_TYPE: Record<string, string> = {
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
};

/**
 * The file name sent on create (Story 17.11b): a name without an accepted extension gets the one
 * of its type, e.g. `IMG_0042` → `IMG_0042.jpg`. See {@link fileNameWithExtension}.
 */
export const photoFileName = (file: File): string =>
  fileNameWithExtension(file, PHOTO_EXTENSIONS, EXTENSION_BY_TYPE, 'photo');

export const useEventPhotos = (eventCode: string, enabled = true) => {
  return useQuery({
    queryKey: ['event-photos', eventCode],
    queryFn: () => eventApiClient.listEventPhotos(eventCode),
    enabled: enabled && !!eventCode,
  });
};

/**
 * Uploads one photo on the upload contract, purpose `event-photo` with the target `eventCode`
 * (Story 17.11b):
 * 1. POST /uploads/event-photo → presigned PUT URL (the upload is bound to the event)
 * 2. PUT to S3 directly with the file
 * 3. POST /uploads/event-photo/{uploadId}/confirm with the same target → the photo is in the gallery
 * On success: invalidate ['event-photos', eventCode]
 */
export const useUploadEventPhoto = (eventCode: string) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ file }: { file: File }) => {
      const target = uploadTarget('event-photo', { eventCode });
      const fileName = photoFileName(file);
      const slot = await target.presign({
        fileName,
        fileSize: file.size,
        mimeType: file.type,
      });
      await eventApiClient.uploadPhotoToS3(slot.uploadUrl, file);
      return target.confirm(slot.fileId, {
        fileId: slot.fileId,
        fileExtension: fileName.split('.').pop() ?? '',
        checksum: '',
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['event-photos', eventCode] });
    },
  });
};

export const useDeleteEventPhoto = (eventCode: string) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (photoId: string) => eventApiClient.deleteEventPhoto(eventCode, photoId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['event-photos', eventCode] });
    },
  });
};
