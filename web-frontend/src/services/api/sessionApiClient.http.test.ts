/**
 * Session API: company sessions and material uploads
 *
 * On MockAdapter(apiClient). Kept apart from sessionApiClient.test.ts, which mocks the
 * apiClient module.
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import apiClient from './apiClient';
import { materialUploadTarget, sessionApiClient } from './sessionApiClient';

describe('sessionApiClient over HTTP', () => {
  let http: MockAdapter;

  beforeEach(() => {
    http = new MockAdapter(apiClient);
  });

  afterEach(() => {
    http.restore();
  });

  describe('listSessionsByCompany', () => {
    it('should_getSessionsOfCompany_when_called', async () => {
      const sessions = [{ sessionSlug: 's1', eventCode: 'BATbern56', title: 'T', speakers: [] }];
      http.onGet('/sessions').reply(200, { data: sessions });

      await expect(sessionApiClient.listSessionsByCompany('acme')).resolves.toEqual(sessions);
      expect(http.history.get[0].params).toEqual({ companyName: 'acme', limit: 50 });
    });

    it('should_returnEmptyList_when_noDataField', async () => {
      http.onGet('/sessions').reply(200, {});

      await expect(sessionApiClient.listSessionsByCompany('acme')).resolves.toEqual([]);
    });

    it('should_passErrorThrough_when_requestFails', async () => {
      http.onGet('/sessions').reply(500);

      await expect(sessionApiClient.listSessionsByCompany('acme')).rejects.toMatchObject({
        response: { status: 500 },
      });
    });
  });

  describe('materialUploadTarget', () => {
    it('should_presignAtMaterials_when_presign', async () => {
      const presigned = {
        uploadUrl: 'https://s3.example.com/put',
        fileId: 'mat-1',
        s3Key: 'materials/temp/mat-1/talk.pdf',
        fileExtension: 'pdf',
        expiresInMinutes: 15,
        requiredHeaders: {},
      };
      http.onPost('/materials/presigned-url').reply(200, presigned);

      await expect(
        materialUploadTarget.presign({
          fileName: 'talk.pdf',
          fileSize: 99,
          mimeType: 'application/pdf',
        })
      ).resolves.toEqual(presigned);
      expect(JSON.parse(http.history.post[0].data)).toEqual({
        fileName: 'talk.pdf',
        fileSize: 99,
        mimeType: 'application/pdf',
      });
    });

    it('should_confirmAtMaterials_when_confirm', async () => {
      http.onPost('/materials/mat-1/confirm').reply(200, {});

      await materialUploadTarget.confirm('mat-1', {
        fileId: 'mat-1',
        fileExtension: 'pdf',
        checksum: 'c',
      });

      expect(JSON.parse(http.history.post[0].data)).toEqual({
        fileId: 'mat-1',
        fileExtension: 'pdf',
        checksum: 'c',
      });
    });
  });
});
