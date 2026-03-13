/**
 * Newsletter API Service Tests
 *
 * Story 10.28: Newsletter Subscriber Management Page
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import apiClient from './apiClient';
import {
  listNewsletterSubscribers,
  unsubscribeNewsletterSubscriber,
  resubscribeNewsletterSubscriber,
  deleteNewsletterSubscriber,
} from './newsletterApi';

describe('Newsletter API Service', () => {
  beforeEach(() => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: {} });
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: {} });
    vi.spyOn(apiClient, 'delete').mockResolvedValue({ data: {} });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('listNewsletterSubscribers', () => {
    it('should_callGetWithPaginationParams_when_invoked', async () => {
      const mockResponse = {
        data: [],
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 0,
          totalPages: 0,
          hasNext: false,
          hasPrev: false,
        },
      };
      vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockResponse });

      const result = await listNewsletterSubscribers({ status: 'all' }, { page: 1, limit: 20 });

      expect(apiClient.get).toHaveBeenCalledWith('/newsletter/subscribers', {
        params: { page: 1, limit: 20 },
      });
      expect(result).toEqual(mockResponse);
    });

    it('should_includeSearchParam_when_searchQueryProvided', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: [], pagination: {} } });

      await listNewsletterSubscribers(
        { searchQuery: 'alice', status: 'all' },
        { page: 1, limit: 20 }
      );

      expect(apiClient.get).toHaveBeenCalledWith('/newsletter/subscribers', {
        params: expect.objectContaining({ search: 'alice' }),
      });
    });

    it('should_notIncludeSearchParam_when_searchQueryEmpty', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: [], pagination: {} } });

      await listNewsletterSubscribers({ searchQuery: '  ', status: 'all' }, { page: 1, limit: 20 });

      const callParams = vi.mocked(apiClient.get).mock.calls[0][1]?.params;
      expect(callParams.search).toBeUndefined();
    });

    it('should_includeStatusParam_when_notAll', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: [], pagination: {} } });

      await listNewsletterSubscribers({ status: 'active' }, { page: 1, limit: 20 });

      expect(apiClient.get).toHaveBeenCalledWith('/newsletter/subscribers', {
        params: expect.objectContaining({ status: 'active' }),
      });
    });

    it('should_notIncludeStatusParam_when_statusIsAll', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: [], pagination: {} } });

      await listNewsletterSubscribers({ status: 'all' }, { page: 1, limit: 20 });

      const callParams = vi.mocked(apiClient.get).mock.calls[0][1]?.params;
      expect(callParams.status).toBeUndefined();
    });

    it('should_includeSortParams_when_provided', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: [], pagination: {} } });

      await listNewsletterSubscribers({ sortBy: 'email', sortDir: 'asc' }, { page: 1, limit: 20 });

      expect(apiClient.get).toHaveBeenCalledWith('/newsletter/subscribers', {
        params: expect.objectContaining({ sortBy: 'email', sortDir: 'asc' }),
      });
    });
  });

  describe('unsubscribeNewsletterSubscriber', () => {
    it('should_callPostWithId_when_invoked', async () => {
      const mockSub = { id: 'sub-1', email: 'a@b.com' };
      vi.mocked(apiClient.post).mockResolvedValueOnce({ data: mockSub });

      const result = await unsubscribeNewsletterSubscriber('sub-1');

      expect(apiClient.post).toHaveBeenCalledWith('/newsletter/subscribers/sub-1/unsubscribe');
      expect(result).toEqual(mockSub);
    });
  });

  describe('resubscribeNewsletterSubscriber', () => {
    it('should_callPostWithId_when_invoked', async () => {
      const mockSub = { id: 'sub-1', email: 'a@b.com' };
      vi.mocked(apiClient.post).mockResolvedValueOnce({ data: mockSub });

      const result = await resubscribeNewsletterSubscriber('sub-1');

      expect(apiClient.post).toHaveBeenCalledWith('/newsletter/subscribers/sub-1/resubscribe');
      expect(result).toEqual(mockSub);
    });
  });

  describe('deleteNewsletterSubscriber', () => {
    it('should_callDeleteWithId_when_invoked', async () => {
      vi.mocked(apiClient.delete).mockResolvedValueOnce({ data: undefined });

      await deleteNewsletterSubscriber('sub-1');

      expect(apiClient.delete).toHaveBeenCalledWith('/newsletter/subscribers/sub-1');
    });
  });
});
