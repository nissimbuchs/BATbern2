/**
 * AWS Amplify Configuration
 * Story 1.2: AWS Cognito Integration Setup
 */

import { Amplify } from 'aws-amplify'

// Environment-specific configuration
const getAmplifyConfig = () => {
  const environment = import.meta.env.VITE_APP_ENV || 'development'

  const baseConfig = {
    Auth: {
      region: 'eu-central-1',
      userPoolWebClientId: import.meta.env.VITE_COGNITO_WEB_CLIENT_ID || '',
      userPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID || '',
      mandatorySignIn: true,
      authenticationFlowType: 'USER_PASSWORD_AUTH' as const,
      oauth: {
        domain: import.meta.env.VITE_COGNITO_DOMAIN || '',
        scope: ['email', 'openid', 'profile'] as const,
        redirectSignIn: '',
        redirectSignOut: '',
        responseType: 'code' as const,
      },
    },
    API: {
      endpoints: [
        {
          name: 'BATbernAPI',
          endpoint: import.meta.env.VITE_API_GATEWAY_URL || '',
          region: 'eu-central-1',
        },
      ],
    },
  }

  // Environment-specific OAuth redirect URLs
  switch (environment) {
    case 'production':
      baseConfig.Auth.oauth.redirectSignIn = 'https://www.batbern.ch/auth/callback'
      baseConfig.Auth.oauth.redirectSignOut = 'https://www.batbern.ch/'
      break
    case 'staging':
      baseConfig.Auth.oauth.redirectSignIn = 'https://staging.batbern.ch/auth/callback'
      baseConfig.Auth.oauth.redirectSignOut = 'https://staging.batbern.ch/'
      break
    default: // development
      baseConfig.Auth.oauth.redirectSignIn = 'http://localhost:3000/auth/callback'
      baseConfig.Auth.oauth.redirectSignOut = 'http://localhost:3000/'
  }

  return baseConfig
}

// Configure Amplify
export const configureAmplify = () => {
  const config = getAmplifyConfig()

  try {
    Amplify.configure(config)
    console.log('✅ AWS Amplify configured successfully')
  } catch (error) {
    console.error('❌ Failed to configure AWS Amplify:', error)
    throw new Error('Amplify configuration failed')
  }
}

// Export configuration for testing
export const amplifyConfig = getAmplifyConfig()