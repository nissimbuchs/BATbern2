/**
 * AWS Amplify Configuration
 * Story 1.2: AWS Cognito Integration Setup
 */

import { Amplify, type ResourcesConfig } from 'aws-amplify';

// Environment-specific configuration
const getAmplifyConfig = (): ResourcesConfig => {
  const environment = import.meta.env.VITE_APP_ENV || 'development';

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
        userPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID || '',
        userPoolClientId: import.meta.env.VITE_COGNITO_WEB_CLIENT_ID || '',
        loginWith: {
          oauth: {
            domain: import.meta.env.VITE_COGNITO_DOMAIN || '',
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

// Configure Amplify
export const configureAmplify = () => {
  const config = getAmplifyConfig();

  try {
    Amplify.configure(config);
    console.log('✅ AWS Amplify configured successfully');
  } catch (error) {
    console.error('❌ Failed to configure AWS Amplify:', error);
    throw new Error('Amplify configuration failed');
  }
};

// Export configuration for testing
export const amplifyConfig = getAmplifyConfig();
