/**
 * Hooks for event teaser image management.
 * Story 10.22: Event Teaser Images for Moderator Presentation Page
 *
 * 3-phase presigned PUT upload pattern (mirrors useEventPhotos):
 * 1. POST /teaser-images/upload-url → { uploadUrl, s3Key }
 * 2. Client PUT to S3 directly (no backend involvement)
 * 3. POST /teaser-images/confirm → TeaserImageItem
 */
import { useMutation, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { eventApiClient } from '@/services/eventApiClient';
import type { components } from '@/types/generated/events-api.types';

type TeaserImageUploadUrlRequest = components['schemas']['TeaserImageUploadUrlRequest'];

/**
 * 3-phase upload mutation.
 * On success: invalidates ['event', eventCode] so EventResponse.teaserImages refreshes.
 */
export const useUploadTeaserImage = (eventCode: string) => {
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
      queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
    },
  });
};

export const useDeleteTeaserImage = (eventCode: string) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (imageId: string) => eventApiClient.deleteTeaserImage(eventCode, imageId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
    },
  });
};
