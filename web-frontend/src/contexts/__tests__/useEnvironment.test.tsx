import { describe, it, expect } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useEnvironment } from '../useEnvironment';
import { ConfigProvider } from '../ConfigContext';
import type { AppConfig } from '../../config/runtime-config';
import type { ReactNode } from 'react';

describe('useEnvironment', () => {
  const createWrapper = (environment: AppConfig['environment']) => {
    const mockConfig: AppConfig = {
      apiBaseUrl: 'https://api.test.com',
      cognitoUserPoolId: 'test-pool-id',
      cognitoUserPoolClientId: 'test-client-id',
      cognitoDomain: 'test-domain',
      environment,
      features: {
        analytics: true,
        offline: false,
        notifications: true,
      },
    };

    return ({ children }: { children: ReactNode }) => (
      <ConfigProvider config={mockConfig}>{children}</ConfigProvider>
    );
  };

  it('should_returnDevelopment_when_envIsDevelopment', () => {
    const wrapper = createWrapper('development');
    const { result } = renderHook(() => useEnvironment(), { wrapper });

    expect(result.current).toBe('development');
  });

  it('should_returnStaging_when_envIsStaging', () => {
    const wrapper = createWrapper('staging');
    const { result } = renderHook(() => useEnvironment(), { wrapper });

    expect(result.current).toBe('staging');
  });

  it('should_returnProduction_when_envIsProduction', () => {
    const wrapper = createWrapper('production');
    const { result } = renderHook(() => useEnvironment(), { wrapper });

    expect(result.current).toBe('production');
  });
});
