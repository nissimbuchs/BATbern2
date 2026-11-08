/**
 * User Account API: admin picture removal
 *
 * On MockAdapter(apiClient). Kept apart from userAccountApi.test.ts, which mocks the apiClient
 * module.
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import apiClient from './apiClient';
import { removeProfilePictureForUser } from './userAccountApi';

describe('removeProfilePictureForUser', () => {
  let http: MockAdapter;

  beforeEach(() => {
    http = new MockAdapter(apiClient);
  });

  afterEach(() => {
    http.restore();
  });

  it('should_deleteUserPicture_when_called', async () => {
    http.onDelete('/users/alice.smith/picture').reply(204);

    await removeProfilePictureForUser('alice.smith');

    expect(http.history.delete.map((r) => r.url)).toEqual(['/users/alice.smith/picture']);
  });

  it('should_passErrorThrough_when_deleteFails', async () => {
    http.onDelete('/users/alice.smith/picture').reply(403);

    await expect(removeProfilePictureForUser('alice.smith')).rejects.toMatchObject({
      response: { status: 403 },
    });
  });
});
