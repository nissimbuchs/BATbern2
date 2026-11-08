/**
 * Company API: logo upload calls
 *
 * logoUploadTarget (useFileUpload phase 1 and 3) and uploadLogoFromUrl (batch import), on
 * MockAdapter(apiClient). Kept apart from companyApi.test.ts, which mocks the apiClient module.
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import apiClient from './apiClient';
import { logoUploadTarget, uploadLogoFromUrl } from './companyApi';

describe('companyApi logo uploads', () => {
  let http: MockAdapter;

  beforeEach(() => {
    http = new MockAdapter(apiClient);
  });

  afterEach(() => {
    http.restore();
  });

  it('should_postFileMetadata_when_presignLogo', async () => {
    const presigned = {
      uploadUrl: 'https://s3.example.com/put',
      fileId: 'up-1',
      s3Key: 'logos/temp/up-1/logo.png',
      fileExtension: 'png',
      expiresInMinutes: 15,
      requiredHeaders: { 'Content-Type': 'image/png' },
    };
    http.onPost('/logos/presigned-url').reply(200, presigned);

    await expect(
      logoUploadTarget.presign({ fileName: 'logo.png', fileSize: 10, mimeType: 'image/png' })
    ).resolves.toEqual(presigned);
    expect(JSON.parse(http.history.post[0].data)).toEqual({
      fileName: 'logo.png',
      fileSize: 10,
      mimeType: 'image/png',
    });
  });

  it('should_postConfirmBody_when_confirmLogo', async () => {
    http.onPost('/logos/up-1/confirm').reply(200, {});

    await logoUploadTarget.confirm('up-1', { fileId: 'up-1', fileExtension: 'png', checksum: 'c' });

    expect(JSON.parse(http.history.post[0].data)).toEqual({
      fileId: 'up-1',
      fileExtension: 'png',
      checksum: 'c',
    });
  });

  it('should_passErrorThrough_when_presignFails', async () => {
    http.onPost('/logos/presigned-url').reply(400, { message: 'too big' });

    await expect(
      logoUploadTarget.presign({ fileName: 'logo.png', fileSize: 10, mimeType: 'image/png' })
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should_returnUploadId_when_uploadLogoFromUrl', async () => {
    http.onPost('/logos/upload-from-url').reply(200, { uploadId: 'up-9' });

    await expect(uploadLogoFromUrl('https://img.example.com/l.png', 'Acme-logo')).resolves.toBe(
      'up-9'
    );
    expect(JSON.parse(http.history.post[0].data)).toEqual({
      url: 'https://img.example.com/l.png',
      filename: 'Acme-logo',
    });
  });

  it('should_passErrorThrough_when_uploadLogoFromUrlFails', async () => {
    http.onPost('/logos/upload-from-url').reply(502);

    await expect(uploadLogoFromUrl('https://img.example.com/l.png', 'Acme-logo')).rejects.toThrow();
  });
});
