/**
 * UserCreateEditModal Tests
 *
 * Covers the organizer's photo of another user: the upload runs on the upload contract, purpose
 * `profile-picture` with the user as target (Story 17.11a); the removal is
 * `DELETE /users/{username}/picture` (Story 4.10). The backend answers through the MSW harness,
 * which checks every request against the specs; the real hooks and services run.
 */

import { describe, it, expect } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { I18nextProvider } from 'react-i18next';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import i18n from '@/i18n/config';
import { api } from '@/test/msw/api';
import { server } from '@/test/msw/server';
import UserCreateEditModal from './UserCreateEditModal';
import type { User } from '@/types/user.types';

const S3_PUT = 'https://bucket.example/alice-upload';
const NEW_PICTURE = 'https://cdn.batbern.ch/profile-pictures/alice.smith/profile-up-1.png';

const user = {
  id: 'alice.smith',
  email: 'alice.smith@example.com',
  firstName: 'Alice',
  lastName: 'Smith',
  roles: ['SPEAKER'],
  profilePictureUrl: 'https://cdn.example.com/alice.jpg',
} as unknown as User;

const renderModal = () => {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={qc}>
      <I18nextProvider i18n={i18n}>
        <UserCreateEditModal open onClose={() => {}} user={user} />
      </I18nextProvider>
    </QueryClientProvider>
  );
};

describe('UserCreateEditModal photo', () => {
  it('should_uploadPictureForTheUser_when_organizerChoosesAFile', async () => {
    api.on(
      'createFileUpload',
      () => ({
        uploadId: 'up-1',
        uploadUrl: S3_PUT,
        expiresAt: '2026-11-13T10:15:00Z',
        requiredHeaders: { 'Content-Type': 'image/png' },
      }),
      { status: 201 }
    );
    server.use(http.put(S3_PUT, () => new HttpResponse(null, { status: 200 })));
    api.on('confirmFileUpload', () => ({
      uploadId: 'up-1',
      purpose: 'profile-picture' as const,
      status: 'ASSOCIATED' as const,
      fileUrl: NEW_PICTURE,
    }));
    renderModal();

    // The dialog renders in a portal, outside the render container.
    const input = document.querySelector('#modal-profile-photo-upload') as HTMLInputElement;
    await userEvent.upload(input, new File(['png'], 'alice.png', { type: 'image/png' }));

    await waitFor(() => expect(api.calls('confirmFileUpload')).toHaveLength(1));
    expect(api.calls('createFileUpload')[0].params).toEqual({ purpose: 'profile-picture' });
    expect(api.calls('createFileUpload')[0].body).toMatchObject({
      fileName: 'alice.png',
      target: { username: 'alice.smith' },
    });
    expect(api.calls('confirmFileUpload')[0].body).toEqual({
      target: { username: 'alice.smith' },
    });
    await waitFor(() => expect(document.querySelector(`img[src="${NEW_PICTURE}"]`)).not.toBeNull());
  });

  it('should_deleteUserPicture_when_removePhotoClicked', async () => {
    renderModal();

    await userEvent.click(screen.getByRole('button', { name: 'Remove profile photo' }));

    await waitFor(() => expect(api.calls('removeProfilePictureForUser')).toHaveLength(1));
    expect(api.calls('removeProfilePictureForUser')[0].params).toEqual({ username: 'alice.smith' });
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Remove profile photo' })).not.toBeInTheDocument()
    );
  });

  it('should_keepPhoto_when_deleteFails', async () => {
    api.on('removeProfilePictureForUser', () =>
      HttpResponse.json({ status: 500 }, { status: 500 })
    );
    renderModal();

    await userEvent.click(screen.getByRole('button', { name: 'Remove profile photo' }));

    await waitFor(() => expect(api.calls('removeProfilePictureForUser')).toHaveLength(1));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Remove profile photo' })).toBeEnabled()
    );
  });
});
