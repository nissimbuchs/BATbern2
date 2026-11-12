/**
 * The upload contract calls (Story 17.11): `uploadTarget` (useFileUpload phase 1 and 3) and
 * `importUpload` (batch import), against the MSW harness, which checks every request against
 * `docs/api/uploads-api.openapi.yml`.
 */
import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { importUpload, uploadTarget } from './uploadService';

describe('uploadService', () => {
  it('should_reserveWithContractBody_when_presigning', async () => {
    api.on(
      'createFileUpload',
      () => ({
        uploadId: 'up-1',
        uploadUrl: 'https://bucket.example/put',
        expiresAt: '2026-11-12T10:15:00Z',
        requiredHeaders: { 'Content-Type': 'image/png' },
      }),
      { status: 201 }
    );

    const presigned = await uploadTarget('company-logo').presign({
      fileName: 'Logo Ölund.png',
      fileSize: 10,
      mimeType: 'image/png',
    });

    expect(presigned).toEqual({
      uploadUrl: 'https://bucket.example/put',
      fileId: 'up-1',
      requiredHeaders: { 'Content-Type': 'image/png' },
    });
    const call = api.calls('createFileUpload')[0];
    expect(call.params).toEqual({ purpose: 'company-logo' });
    expect(call.body).toEqual({ fileName: 'Logo Ölund.png', contentType: 'image/png', size: 10 });
  });

  it('should_sendTarget_when_targetGiven', async () => {
    const target = uploadTarget('company-logo', { companyName: 'acme' });

    await target.presign({ fileName: 'logo.svg', fileSize: 10, mimeType: 'image/svg+xml' });
    const result = await target.confirm('up-1', {
      fileId: 'up-1',
      fileExtension: 'svg',
      checksum: 'c',
    });

    expect(api.calls('createFileUpload')[0].body?.target).toEqual({ companyName: 'acme' });
    const confirm = api.calls('confirmFileUpload')[0];
    expect(confirm.params).toEqual({ purpose: 'company-logo' as const, uploadId: 'up-1' });
    expect(confirm.body).toEqual({ target: { companyName: 'acme' } });
    // The spec example answers ASSOCIATED with its CDN URL
    expect(result).toEqual({ fileUrl: expect.stringContaining('https://cdn.batbern.ch/') });
  });

  it('should_confirmWithoutTarget_when_noneGiven', async () => {
    api.on('confirmFileUpload', () => ({
      uploadId: 'up-2',
      purpose: 'event-theme-image' as const,
      status: 'CONFIRMED' as const,
      fileUrl: null,
    }));

    const result = await uploadTarget('event-theme-image').confirm('up-2', {
      fileId: 'up-2',
      fileExtension: 'png',
      checksum: 'c',
    });

    expect(api.calls('confirmFileUpload')[0].body).toEqual({});
    expect(result).toEqual({ fileUrl: null });
  });

  it('should_passErrorThrough_when_presignRefused', async () => {
    api.on('createFileUpload', () =>
      HttpResponse.json({ status: 400, message: 'too big' }, { status: 400 })
    );

    await expect(
      uploadTarget('company-logo').presign({
        fileName: 'a.png',
        fileSize: 10,
        mimeType: 'image/png',
      })
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should_returnUploadId_when_imported', async () => {
    api.on(
      'importFileUpload',
      () => ({
        uploadId: 'up-9',
        purpose: 'company-logo' as const,
        status: 'CONFIRMED' as const,
        fileUrl: null,
      }),
      { status: 201 }
    );

    await expect(
      importUpload('company-logo', 'https://img.example.com/l.png', 'Acme-logo')
    ).resolves.toBe('up-9');
    expect(api.calls('importFileUpload')[0].body).toEqual({
      url: 'https://img.example.com/l.png',
      fileName: 'Acme-logo',
    });
  });

  it('should_passErrorThrough_when_importFails', async () => {
    api.on('importFileUpload', () => HttpResponse.json({ status: 502 }, { status: 502 }));

    await expect(importUpload('company-logo', 'https://img.example.com/l.png')).rejects.toThrow();
  });
});
