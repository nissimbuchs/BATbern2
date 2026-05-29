import apiClient from './api/apiClient';

export type VenueRecipientRole = 'VENUE' | 'CATERING';

export interface VenueCoordinationPreviewRequest {
  templateKey: string;
  recipientRole: VenueRecipientRole;
  locale: 'de' | 'en';
  notes?: string;
}

export interface VenueCoordinationPreviewResponse {
  subject: string;
  htmlBody: string;
  toName: string;
  toEmail: string;
  replyToEmail: string;
}

export interface VenueCoordinationSendRequest {
  templateKey: string;
  locale: 'de' | 'en';
  notes?: string;
  recipients: VenueRecipientRole[];
}

export interface VenueCoordinationSendResponse {
  sentTo: VenueRecipientRole[];
}

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
