import { describe, it, expect } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useConfig } from '../useConfig';
import { ConfigProvider } from '../ConfigContext';
import type { AppConfig } from '../../config/runtime-config';
import type { ReactNode } from 'react';

describe('useConfig', () => {
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

  it('should_returnConfig_when_usedWithinProvider', () => {
    const { result } = renderHook(() => useConfig(), { wrapper });

    expect(result.current).toEqual(mockConfig);
    expect(result.current.apiBaseUrl).toBe('https://api.test.com');
    expect(result.current.environment).toBe('development');
  });

  it('should_throwError_when_usedOutsideProvider', () => {
    expect(() => {
      renderHook(() => useConfig());
    }).toThrow('useConfig must be used within ConfigProvider');
  });

  it('should_returnFeatures_when_accessed', () => {
    const { result } = renderHook(() => useConfig(), { wrapper });

    expect(result.current.features.analytics).toBe(true);
    expect(result.current.features.offline).toBe(false);
    expect(result.current.features.notifications).toBe(true);
  });
});
