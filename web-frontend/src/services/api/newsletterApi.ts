/**
 * Newsletter API Service (Organizer-only endpoints)
 *
 * Separate from services/newsletterService.ts which handles public/user endpoints.
 *
 * Story 10.28: Newsletter Subscriber Management Page
 */

import apiClient from './apiClient';
import type { NewsletterSubscriberFilters } from '@/stores/newsletterSubscriberStore';
import type { components } from '@/types/generated/event-newsletter-api.types';

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
  // ADR-013 §3: single `sort` vocabulary (e.g. `-subscribedAt`) instead of sortBy+sortDir.
  if (filters.sortBy)
    params.sort = filters.sortDir === 'asc' ? filters.sortBy : `-${filters.sortBy}`;

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

/** Story 10.29 AC8: Unsuppress a subscriber (clears bounce state). */
export const unsuppressNewsletterSubscriber = async (id: string): Promise<SubscriberResponse> => {
  const response = await apiClient.post<SubscriberResponse>(
    `${NEWSLETTER_API_PATH}/subscribers/${id}/unsuppress`
  );
  return response.data;
};
