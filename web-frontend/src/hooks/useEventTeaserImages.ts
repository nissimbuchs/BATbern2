/**
 * Hooks for teaser image management (event-specific or global).
 *
 * Pass eventCode for event-specific images, or null/undefined for global images
 * (shown on all event presentations).
 *
 * Upload on the upload contract, purpose `teaser-image` (Story 17.11c):
 * 1. POST /uploads/teaser-image with the target `eventCode` (`_global` for the global set)
 * 2. Client PUT to S3 directly (no backend involvement)
 * 3. POST /uploads/teaser-image/{uploadId}/confirm with the same target → the teaser image exists
 * A full set (10 images) is refused with 409 on step 1 or 3.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { eventApiClient, GLOBAL_TEASER_CODE } from '@/services/eventApiClient';
import { fileNameWithExtension, uploadTarget } from '@/services/uploadService';
import type { components } from '@/types/generated/events-core-api.types';

type TeaserImageUpdateRequest = components['schemas']['TeaserImageUpdateRequest'];

const GLOBAL_KEY = ['global-teaser-images'] as const;

/** The extensions `teaser-image` accepts, and the one each allowed type implies. */
const TEASER_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp', 'svg'];
const EXTENSION_BY_TYPE: Record<string, string> = {
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
  'image/svg+xml': 'svg',
};

/**
 * The server's message for a refused step (e.g. the limit: "Maximum of 10 teaser images reached
 * for event: …"), which the tabs show; any other error as it is.
 */
function withServerMessage(error: unknown): Error {
  if (isAxiosError(error)) {
    const message = (error.response?.data as { message?: string } | undefined)?.message;
    if (message) {
      return new Error(message);
    }
  }
  return error instanceof Error ? error : new Error(String(error));
}

function cacheKey(eventCode?: string | null) {
  return eventCode ? ['event', eventCode] : [...GLOBAL_KEY];
}

/**
 * Fetch teaser images. For event-specific pass eventCode; for global pass null.
 */
export const useTeaserImages = (eventCode?: string | null) => {
  return useQuery({
    queryKey: cacheKey(eventCode),
    queryFn: () => eventApiClient.listTeaserImages(eventCode),
    enabled: eventCode === null || eventCode === undefined || eventCode.length > 0,
  });
};

/**
 * Uploads one teaser image to the event, or to the global set for null/undefined.
 * On success: invalidates the event (its `teaserImages`) or the global list.
 */
export const useUploadTeaserImage = (eventCode?: string | null) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ file }: { file: File }) => {
      const target = uploadTarget('teaser-image', {
        eventCode: eventCode || GLOBAL_TEASER_CODE,
      });
      const fileName = fileNameWithExtension(file, TEASER_EXTENSIONS, EXTENSION_BY_TYPE, 'teaser');
      try {
        const slot = await target.presign({
          fileName,
          fileSize: file.size,
          mimeType: file.type,
        });
        await eventApiClient.uploadPhotoToS3(slot.uploadUrl, file);
        return await target.confirm(slot.fileId, {
          fileId: slot.fileId,
          fileExtension: fileName.split('.').pop() ?? '',
          checksum: '',
        });
      } catch (error) {
        throw withServerMessage(error);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cacheKey(eventCode) });
    },
  });
};

export const useDeleteTeaserImage = (eventCode?: string | null) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (imageId: string) => eventApiClient.deleteTeaserImage(eventCode, imageId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cacheKey(eventCode) });
    },
  });
};

export const useUpdateTeaserImagePosition = (eventCode?: string | null) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ imageId, request }: { imageId: string; request: TeaserImageUpdateRequest }) =>
      eventApiClient.updateTeaserImage(eventCode, imageId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cacheKey(eventCode) });
    },
  });
};
