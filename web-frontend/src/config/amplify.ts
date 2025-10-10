/**
 * AWS Amplify Configuration
 * Story 1.2: AWS Cognito Integration Setup
 * Updated to use runtime config instead of build-time env vars
 */

import { Amplify, type ResourcesConfig } from 'aws-amplify';
import type { AppConfig } from './runtime-config';

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
    console.log(
      '✅ AWS Amplify configured successfully for environment:',
      runtimeConfig.environment
    );
  } catch (error) {
    console.error('❌ Failed to configure AWS Amplify:', error);
    throw new Error('Amplify configuration failed');
  }
};
