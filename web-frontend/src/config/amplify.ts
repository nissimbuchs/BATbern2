/**
 * AWS Amplify Configuration
 * Story 1.2: AWS Cognito Integration Setup
 * Updated to use runtime config instead of build-time env vars
 */

import { Amplify, type ResourcesConfig } from 'aws-amplify';
import { cognitoUserPoolsTokenProvider } from 'aws-amplify/auth/cognito';
import type { AppConfig } from './runtime-config';

// Storage adapter for Amplify token persistence
function createStorageAdapter(storage: Storage) {
  return {
    setItem(key: string, value: string): Promise<void> {
      return Promise.resolve(storage.setItem(key, value));
    },
    getItem(key: string): Promise<string | null> {
      return Promise.resolve(storage.getItem(key));
    },
    removeItem(key: string): Promise<void> {
      return Promise.resolve(storage.removeItem(key));
    },
    clear(): Promise<void> {
      return Promise.resolve(storage.clear());
    },
  };
}

// Build Amplify config from runtime config
const getAmplifyConfig = (runtimeConfig: AppConfig): ResourcesConfig => {
  const { environment, cognito } = runtimeConfig;

  // Determine OAuth redirect URLs based on environment
  let redirectSignIn = 'http://localhost:3000/auth/callback';
  let redirectSignOut = 'http://localhost:3000/';

  switch (environment) {
    case 'production':
      redirectSignIn = 'https://www.batbern.ch/auth/callback';
      redirectSignOut = 'https://www.batbern.ch/';
      break;
    case 'staging':
      redirectSignIn = 'https://staging.batbern.ch/auth/callback';
      redirectSignOut = 'https://staging.batbern.ch/';
      break;
  }

  const config: ResourcesConfig = {
    Auth: {
      Cognito: {
        userPoolId: cognito.userPoolId,
        userPoolClientId: cognito.clientId,
        loginWith: {
          oauth: {
            domain: `batbern-${environment}.auth.${cognito.region}.amazoncognito.com`,
            scopes: ['email', 'openid', 'profile'],
            redirectSignIn: [redirectSignIn],
            redirectSignOut: [redirectSignOut],
            responseType: 'code',
          },
        },
      },
    },
  };

  return config;
};

// Configure Amplify with runtime config
export const configureAmplify = (runtimeConfig: AppConfig) => {
  const config = getAmplifyConfig(runtimeConfig);

  try {
    Amplify.configure(config);

    // Configure default storage to use localStorage for persistent sessions
    // This ensures tokens persist across page reloads (users stay logged in)
    const storageAdapter = createStorageAdapter(localStorage);
    cognitoUserPoolsTokenProvider.setKeyValueStorage(storageAdapter);

    console.log(
      '✅ AWS Amplify configured successfully for environment:',
      runtimeConfig.environment
    );
  } catch (error) {
    console.error('❌ Failed to configure AWS Amplify:', error);
    throw new Error('Amplify configuration failed');
  }
};
