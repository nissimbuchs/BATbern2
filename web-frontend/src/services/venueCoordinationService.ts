import apiClient from './api/apiClient';
import type { components } from '@/types/generated/events-core-api.types';

export type VenueRecipientRole = components['schemas']['VenueRole'];

export type VenueCoordinationPreviewRequest =
  components['schemas']['VenueCoordinationPreviewRequest'];
export type VenueCoordinationPreviewResponse =
  components['schemas']['VenueCoordinationPreviewResponse'];
export type VenueCoordinationSendRequest = components['schemas']['VenueCoordinationSendRequest'];
export type VenueCoordinationSendResponse = components['schemas']['VenueCoordinationSendResponse'];

export const previewVenueCoordinationEmail = async (
  eventCode: string,
  request: VenueCoordinationPreviewRequest
): Promise<VenueCoordinationPreviewResponse> => {
  const response = await apiClient.post<VenueCoordinationPreviewResponse>(
    `/events/${encodeURIComponent(eventCode)}/venue-coordination/preview`,
    request
  );
  return response.data;
};

export const sendVenueCoordinationEmail = async (
  eventCode: string,
  request: VenueCoordinationSendRequest
): Promise<VenueCoordinationSendResponse> => {
  const response = await apiClient.post<VenueCoordinationSendResponse>(
    `/events/${encodeURIComponent(eventCode)}/venue-coordination/send`,
    request
  );
  return response.data;
};

export const venueCoordinationService = {
  preview: previewVenueCoordinationEmail,
  send: sendVenueCoordinationEmail,
};
