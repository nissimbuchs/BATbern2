/**
 * Hooks for event photo management.
 * Story 10.21: Event Photos Gallery
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { eventApiClient } from '@/services/eventApiClient';
import type { components } from '@/types/generated/events-api.types';

type EventPhotoUploadRequest = components['schemas']['EventPhotoUploadRequest'];

export const useEventPhotos = (eventCode: string) => {
  return useQuery({
    queryKey: ['event-photos', eventCode],
    queryFn: () => eventApiClient.listEventPhotos(eventCode),
  });
};

/**
 * 3-phase upload mutation:
 * 1. POST /photos/upload-url → { photoId, uploadUrl, s3Key }
 * 2. PUT to S3 directly with the file
 * 3. POST /photos/confirm → EventPhotoResponse
 * On success: invalidate ['event-photos', eventCode]
 */
export const useUploadEventPhoto = (eventCode: string) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ file, request }: { file: File; request: EventPhotoUploadRequest }) => {
      const uploadResponse = await eventApiClient.requestEventPhotoUploadUrl(eventCode, request);
      await eventApiClient.uploadPhotoToS3(uploadResponse.uploadUrl, file);
      return eventApiClient.confirmEventPhotoUpload(eventCode, {
        photoId: uploadResponse.photoId,
        s3Key: uploadResponse.s3Key,
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
