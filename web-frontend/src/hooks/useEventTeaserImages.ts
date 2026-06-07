/**
 * Hooks for teaser image management (event-specific or global).
 *
 * Pass eventCode for event-specific images, or null/undefined for global images
 * (shown on all event presentations).
 *
 * 3-phase presigned PUT upload pattern:
 * 1. POST .../upload-url → { uploadUrl, s3Key }
 * 2. Client PUT to S3 directly (no backend involvement)
 * 3. POST .../confirm → TeaserImageItem
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { eventApiClient } from '@/services/eventApiClient';
import type { components } from '@/types/generated/events-api.types';

type TeaserImageUploadUrlRequest = components['schemas']['TeaserImageUploadUrlRequest'];
type TeaserImageUpdateRequest = components['schemas']['TeaserImageUpdateRequest'];

const GLOBAL_KEY = ['global-teaser-images'] as const;

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
 * 3-phase upload mutation.
 */
export const useUploadTeaserImage = (eventCode?: string | null) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ file, request }: { file: File; request: TeaserImageUploadUrlRequest }) => {
      const urlResponse = await eventApiClient.requestTeaserImageUploadUrl(eventCode, request);
      await axios.put(urlResponse.uploadUrl, file, {
        headers: { 'Content-Type': file.type },
      });
      return eventApiClient.confirmTeaserImageUpload(eventCode, { s3Key: urlResponse.s3Key });
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
