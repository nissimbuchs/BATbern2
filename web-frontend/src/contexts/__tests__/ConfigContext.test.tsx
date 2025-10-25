import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { ConfigProvider } from '../ConfigContext';
import type { AppConfig } from '../../config/runtime-config';

describe('ConfigProvider', () => {
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

  it('should_renderChildren_when_configProvided', () => {
    const { getByText } = render(
      <ConfigProvider config={mockConfig}>
        <div>Test Child</div>
      </ConfigProvider>
    );

    expect(getByText('Test Child')).toBeInTheDocument();
  });

  it('should_provideConfigToContext_when_rendered', () => {
    const { container } = render(
      <ConfigProvider config={mockConfig}>
        <div>App Content</div>
      </ConfigProvider>
    );

    expect(container).toBeInTheDocument();
  });
});
