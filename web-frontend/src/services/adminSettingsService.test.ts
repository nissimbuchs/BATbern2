/**
 * Admin Settings Service Tests
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import apiClient from '@/services/api/apiClient';
import { getAdminSetting, getFeatureFlags, updateAdminSetting } from './adminSettingsService';

describe('adminSettingsService', () => {
  let http: MockAdapter;

  beforeEach(() => {
    http = new MockAdapter(apiClient);
  });

  afterEach(() => {
    http.restore();
  });

  it('should_getPublicFeatureFlags_when_getFeatureFlags', async () => {
    http.onGet('/public/settings/features').reply(200, { aiContentEnabled: true });

    await expect(getFeatureFlags()).resolves.toEqual({ aiContentEnabled: true });
  });

  it('should_passErrorThrough_when_featureFlagsFail', async () => {
    http.onGet('/public/settings/features').reply(503);

    await expect(getFeatureFlags()).rejects.toMatchObject({ response: { status: 503 } });
  });

  it('should_getSetting_when_getAdminSetting', async () => {
    http.onGet('/admin/settings/k').reply(200, { key: 'k', value: 'v' });

    await expect(getAdminSetting('k')).resolves.toEqual({ key: 'k', value: 'v' });
  });

  it('should_putValue_when_updateAdminSetting', async () => {
    http.onPut('/admin/settings/k').reply(200, { key: 'k', value: 'w' });

    await expect(updateAdminSetting('k', 'w')).resolves.toEqual({ key: 'k', value: 'w' });
    expect(JSON.parse(http.history.put[0].data)).toEqual({ value: 'w' });
  });
});
