import { describe, it, expect } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useFeature } from '../useFeature';
import { ConfigProvider } from '../ConfigContext';
import type { AppConfig } from '../../config/runtime-config';
import type { ReactNode } from 'react';

describe('useFeature', () => {
  const mockConfig: AppConfig = {
    apiBaseUrl: 'https://api.test.com',
    cognitoUserPoolId: 'test-pool-id',
    cognitoUserPoolClientId: 'test-client-id',
    cognitoDomain: 'test-domain',
    environment: 'development',
    features: {
      analytics: true,
      offline: false,
      notifications: true,
    },
  };

  const wrapper = ({ children }: { children: ReactNode }) => (
    <ConfigProvider config={mockConfig}>{children}</ConfigProvider>
  );

  it('should_returnTrue_when_featureIsEnabled', () => {
    const { result } = renderHook(() => useFeature('analytics'), { wrapper });

    expect(result.current).toBe(true);
  });

  it('should_returnFalse_when_featureIsDisabled', () => {
    const { result } = renderHook(() => useFeature('offline'), { wrapper });

    expect(result.current).toBe(false);
  });

  it('should_returnCorrectValue_when_checkingNotifications', () => {
    const { result } = renderHook(() => useFeature('notifications'), { wrapper });

    expect(result.current).toBe(true);
  });
});
