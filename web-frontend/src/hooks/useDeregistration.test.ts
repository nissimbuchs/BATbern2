/**
 * Deregistration Hook Tests (Story 10.12)
 *
 * Tests for useVerifyDeregistrationToken, useDeregisterByToken,
 * and useDeregistrationByEmail.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import {
  useVerifyDeregistrationToken,
  useDeregisterByToken,
  useDeregistrationByEmail,
} from './useDeregistration';

vi.mock('@/services/deregistrationService', () => ({
  verifyDeregistrationToken: vi.fn(),
  deregisterByToken: vi.fn(),
  deregisterByEmail: vi.fn(),
}));

import * as deregistrationService from '@/services/deregistrationService';

const mockVerify = vi.mocked(deregistrationService.verifyDeregistrationToken);
const mockDeregisterToken = vi.mocked(deregistrationService.deregisterByToken);
const mockDeregisterEmail = vi.mocked(deregistrationService.deregisterByEmail);

const createQC = () =>
  new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });

const createWrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

describe('useDeregistration', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  describe('useVerifyDeregistrationToken', () => {
    it('should fetch token verification data when token is non-null', async () => {
      const mockData = {
        registrationCode: 'REG-001',
        eventCode: 'BATbern142',
        eventTitle: 'BATbern #142',
        eventDate: '2026-04-15T18:00:00Z',
        attendeeFirstName: 'Alice',
      };
      mockVerify.mockResolvedValue(mockData);

      const { result } = renderHook(() => useVerifyDeregistrationToken('valid-token'), {
        wrapper: createWrapper(qc),
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockData);
      expect(mockVerify).toHaveBeenCalledWith('valid-token');
    });

    it('should not fetch when token is null', () => {
      const { result } = renderHook(() => useVerifyDeregistrationToken(null), {
        wrapper: createWrapper(qc),
      });

      expect(result.current.isLoading).toBe(false);
      expect(mockVerify).not.toHaveBeenCalled();
    });

    it('should set isError when verification fails', async () => {
      mockVerify.mockRejectedValue(new Error('Invalid token'));

      const { result } = renderHook(() => useVerifyDeregistrationToken('bad-token'), {
        wrapper: createWrapper(qc),
      });

      await waitFor(() => expect(result.current.isError).toBe(true));
    });
  });

  describe('useDeregisterByToken', () => {
    it('should call deregisterByToken and invalidate my-registration cache', async () => {
      mockDeregisterToken.mockResolvedValue(undefined);
      const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

      const { result } = renderHook(() => useDeregisterByToken('BATbern142'), {
        wrapper: createWrapper(qc),
      });

      await act(async () => {
        result.current.mutate('dereg-token');
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockDeregisterToken).toHaveBeenCalledWith('dereg-token', expect.anything());
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: ['my-registration', 'BATbern142'],
      });
    });

    it('should not invalidate queries when no eventCode provided', async () => {
      mockDeregisterToken.mockResolvedValue(undefined);
      const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

      const { result } = renderHook(
        () => useDeregisterByToken(), // no eventCode
        { wrapper: createWrapper(qc) }
      );

      await act(async () => {
        result.current.mutate('dereg-token');
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).not.toHaveBeenCalled();
    });
  });

  describe('useDeregistrationByEmail', () => {
    it('should call deregisterByEmail with email and eventCode', async () => {
      mockDeregisterEmail.mockResolvedValue(undefined);

      const { result } = renderHook(() => useDeregistrationByEmail(), {
        wrapper: createWrapper(qc),
      });

      await act(async () => {
        result.current.mutate({ email: 'alice@example.com', eventCode: 'BATbern142' });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockDeregisterEmail).toHaveBeenCalledWith('alice@example.com', 'BATbern142');
    });
  });
});
