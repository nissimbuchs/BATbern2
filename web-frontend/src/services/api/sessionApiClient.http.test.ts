/**
 * Session API over HTTP: company sessions (the other operations, the association of uploaded
 * materials included, are in sessionApiClient.test.ts).
 *
 * The real apiClient runs against the MSW harness (Epic 17 test fidelity): every request is
 * checked against event-sessions-api and each test reads what went over the wire.
 */

import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { sessionApiClient } from './sessionApiClient';

const pagination = {
  page: 1,
  limit: 50,
  totalItems: 1,
  totalPages: 1,
  hasNext: false,
  hasPrev: false,
};

describe('sessionApiClient over HTTP', () => {
  describe('listSessionsByCompany', () => {
    it('should_getSessionsOfCompany_when_called', async () => {
      const sessions = [{ sessionSlug: 's1', eventCode: 'BATbern56', title: 'T', speakers: [] }];
      api.on('searchSessionsByCompany', () => ({ data: sessions, pagination }));

      await expect(sessionApiClient.listSessionsByCompany('Müller AG')).resolves.toEqual(sessions);
      const query = api.calls('searchSessionsByCompany')[0].query;
      expect(query.get('companyName')).toBe('Müller AG');
      expect(query.get('limit')).toBe('50');
    });

    it('should_passErrorThrough_when_requestFails', async () => {
      api.on('searchSessionsByCompany', () => HttpResponse.json({ status: 500 }, { status: 500 }));

      await expect(sessionApiClient.listSessionsByCompany('acme')).rejects.toMatchObject({
        response: { status: 500 },
      });
    });
  });
});
