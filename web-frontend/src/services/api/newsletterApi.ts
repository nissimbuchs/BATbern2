/**
 * Newsletter API Service (Organizer-only endpoints)
 *
 * Separate from services/newsletterService.ts which handles public/user endpoints.
 *
 * Story 10.28: Newsletter Subscriber Management Page
 */

import apiClient from './apiClient';
import type { NewsletterSubscriberFilters } from '@/stores/newsletterSubscriberStore';
import type { components } from '@/types/generated/events-api.types';

type SubscriberResponse = components['schemas']['SubscriberResponse'];
type PaginationMetadata = components['schemas']['PaginationMetadata'];

export interface PagedNewsletterSubscribersResponse {
  data: SubscriberResponse[];
  pagination: PaginationMetadata;
}

const NEWSLETTER_API_PATH = '/newsletter';

export const listNewsletterSubscribers = async (
  filters: NewsletterSubscriberFilters,
  pagination: { page: number; limit: number }
): Promise<PagedNewsletterSubscribersResponse> => {
  const params: Record<string, string | number> = {
    page: pagination.page,
    limit: pagination.limit,
  };
  if (filters.searchQuery?.trim()) params.search = filters.searchQuery;
  if (filters.status && filters.status !== 'all') params.status = filters.status;
  if (filters.sortBy) params.sortBy = filters.sortBy;
  if (filters.sortDir) params.sortDir = filters.sortDir;

  const response = await apiClient.get<PagedNewsletterSubscribersResponse>(
    `${NEWSLETTER_API_PATH}/subscribers`,
    { params }
  );
  return response.data;
};

export const unsubscribeNewsletterSubscriber = async (id: string): Promise<SubscriberResponse> => {
  const response = await apiClient.post<SubscriberResponse>(
    `${NEWSLETTER_API_PATH}/subscribers/${id}/unsubscribe`
  );
  return response.data;
};

export const resubscribeNewsletterSubscriber = async (id: string): Promise<SubscriberResponse> => {
  const response = await apiClient.post<SubscriberResponse>(
    `${NEWSLETTER_API_PATH}/subscribers/${id}/resubscribe`
  );
  return response.data;
};

export const deleteNewsletterSubscriber = async (id: string): Promise<void> => {
  await apiClient.delete(`${NEWSLETTER_API_PATH}/subscribers/${id}`);
};
