/**
 * UserCreateEditModal Tests
 *
 * Covers the organizer's "remove photo" of another user, whose DELETE moved into
 * userAccountApi.removeProfilePictureForUser. HTTP is stubbed at the boundary with
 * axios-mock-adapter on apiClient; the real hooks and services run.
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { I18nextProvider } from 'react-i18next';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import MockAdapter from 'axios-mock-adapter';
import i18n from '@/i18n/config';
import apiClient from '@/services/api/apiClient';
import UserCreateEditModal from './UserCreateEditModal';
import type { User } from '@/types/user.types';

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

describe('UserCreateEditModal photo removal', () => {
  let http: MockAdapter;

  beforeEach(() => {
    http = new MockAdapter(apiClient);
  });

  afterEach(() => {
    http.restore();
  });

  it('should_deleteUserPicture_when_removePhotoClicked', async () => {
    http.onDelete('/users/alice.smith/picture').reply(204);
    renderModal();

    await userEvent.click(screen.getByRole('button', { name: 'Remove profile photo' }));

    await waitFor(() => expect(http.history.delete).toHaveLength(1));
    expect(http.history.delete[0].url).toBe('/users/alice.smith/picture');
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Remove profile photo' })).not.toBeInTheDocument()
    );
  });

  it('should_keepPhoto_when_deleteFails', async () => {
    http.onDelete('/users/alice.smith/picture').reply(500);
    renderModal();

    await userEvent.click(screen.getByRole('button', { name: 'Remove profile photo' }));

    await waitFor(() => expect(http.history.delete).toHaveLength(1));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Remove profile photo' })).toBeEnabled()
    );
  });
});
