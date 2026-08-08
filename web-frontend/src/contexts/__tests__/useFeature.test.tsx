import { describe, it, expect } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useFeature } from '../useFeature';
import { ConfigProvider } from '../ConfigContext';
import { ConfigContext } from '../createConfigContext';
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
      sso: true,
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

  it('should_resolveSsoFlag_when_checkingSso', () => {
    // Story 12.9: the "Continue with Google" button is gated on useFeature('sso').
    const { result } = renderHook(() => useFeature('sso'), { wrapper });

    expect(result.current).toBe(true);
  });

  describe('config not yet loaded', () => {
    // ConfigProvider renders its children IMMEDIATELY with a null value and fills the
    // config in when GET /api/v1/config resolves (see ConfigContext's decoupling
    // rationale). Anything rendering in that window sees a null context.
    //
    // useFeature used to call the throwing useConfig(), so a cold deep-link to /login
    // or /register crashed into the ErrorBoundary with
    //   "useConfig must be used within ConfigProvider"
    // even though the provider was present — LoginForm.tsx and RegistrationStep1.tsx
    // both call useFeature('sso') during first render. Reproducible on any slow
    // connection; reliable on slow hardware.
    //
    // A feature flag must fail CLOSED while unknown: hiding an SSO button briefly is
    // correct, showing one that the backend has switched off is not.
    it('should_returnFalse_when_configHasNotLoadedYet', () => {
      const nullConfigWrapper = ({ children }: { children: ReactNode }) => (
        <ConfigContext.Provider value={null}>{children}</ConfigContext.Provider>
      );

      const { result } = renderHook(() => useFeature('sso'), { wrapper: nullConfigWrapper });

      expect(result.current).toBe(false);
    });

    it('should_notThrow_when_usedOutsideConfigProvider', () => {
      expect(() => renderHook(() => useFeature('sso'))).not.toThrow();
    });
  });
});
