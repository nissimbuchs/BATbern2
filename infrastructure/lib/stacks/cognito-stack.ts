import * as cdk from 'aws-cdk-lib';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { BootstrapOrganizer } from '../constructs/bootstrap-organizer';

export interface CognitoStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
}

/**
 * Cognito Stack - Provides user authentication and authorization
 *
 * Implements:
 * - AC16: AWS Cognito for authentication with role-based access
 * - AC4: Security Boundaries with user pool groups and custom attributes
 */
export class CognitoStack extends cdk.Stack {
  public readonly userPool: cognito.UserPool;
  public readonly userPoolClient: cognito.UserPoolClient;
  public readonly userPoolDomain: cognito.UserPoolDomain;

  constructor(scope: Construct, id: string, props: CognitoStackProps) {
    super(scope, id, props);

    const isProd = props.config.envName === 'production';
    const envName = props.config.envName;

    // Create Pre-Signup Lambda Trigger for validation
    const preSignupLambda = new lambda.Function(this, 'PreSignupTrigger', {
      functionName: `${id}-PreSignupTrigger`,
      runtime: lambda.Runtime.NODEJS_18_X,
      handler: 'index.handler',
      code: lambda.Code.fromInline(`
        exports.handler = async (event) => {
          console.log('Pre-signup trigger:', JSON.stringify(event));

          // Validate company ID if provided
          const companyId = event.request.userAttributes['custom:companyId'];
          if (companyId && !companyId.match(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i)) {
            throw new Error('Invalid company ID format. Must be a valid UUID.');
          }

          // Validate role
          const role = event.request.userAttributes['custom:role'];
          const validRoles = ['organizer', 'speaker', 'partner', 'attendee'];
          if (role && !validRoles.includes(role)) {
            throw new Error('Invalid role. Must be one of: ' + validRoles.join(', '));
          }

          // Auto-verify email for development
          if (process.env.ENVIRONMENT === 'development') {
            event.response.autoConfirmUser = true;
            event.response.autoVerifyEmail = true;
          }

          return event;
        };
      `),
      environment: {
        ENVIRONMENT: envName,
      },
      timeout: cdk.Duration.seconds(5),
    });

    // Create Cognito User Pool
    this.userPool = new cognito.UserPool(this, 'UserPool', {
      userPoolName: `batbern-${envName}-user-pool`,
      selfSignUpEnabled: true,
      signInAliases: {
        email: true,
        username: false,
      },
      autoVerify: {
        email: true,
      },
      standardAttributes: {
        email: {
          required: true,
          mutable: true,
        },
      },
      customAttributes: {
        role: new cognito.StringAttribute({
          mutable: true,
          maxLen: 20,
        }),
        companyId: new cognito.StringAttribute({
          mutable: true,
          maxLen: 36,
        }),
        preferences: new cognito.StringAttribute({
          mutable: true,
          maxLen: 2048,
        }),
      },
      passwordPolicy: {
        minLength: 8,
        requireLowercase: true,
        requireUppercase: true,
        requireDigits: true,
        requireSymbols: true,
        tempPasswordValidity: cdk.Duration.days(7),
      },
      accountRecovery: cognito.AccountRecovery.EMAIL_ONLY,
      mfa: cognito.Mfa.OPTIONAL,
      mfaSecondFactor: {
        sms: true,
        otp: true,
      },
      lambdaTriggers: {
        preSignUp: preSignupLambda,
      },
      removalPolicy: isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
    });

    // Determine callback URLs based on environment
    const callbackUrls = envName === 'production'
      ? ['https://www.batbern.ch/auth/callback']
      : envName === 'staging'
      ? ['https://staging.batbern.ch/auth/callback']
      : ['http://localhost:3000/auth/callback'];

    const logoutUrls = envName === 'production'
      ? ['https://www.batbern.ch/logout']
      : envName === 'staging'
      ? ['https://staging.batbern.ch/logout']
      : ['http://localhost:3000/logout'];

    // Create User Pool Client
    this.userPoolClient = new cognito.UserPoolClient(this, 'UserPoolClient', {
      userPool: this.userPool,
      userPoolClientName: `batbern-${envName}-web-client`,
      authFlows: {
        userPassword: true,
        custom: true,
        userSrp: true, // Enable SRP authentication for secure password flow
      },
      generateSecret: false,
      refreshTokenValidity: cdk.Duration.days(30),
      accessTokenValidity: cdk.Duration.minutes(60),
      idTokenValidity: cdk.Duration.minutes(60),
      oAuth: {
        flows: {
          authorizationCodeGrant: true,
        },
        scopes: [
          cognito.OAuthScope.EMAIL,
          cognito.OAuthScope.OPENID,
          cognito.OAuthScope.PROFILE,
        ],
        callbackUrls,
        logoutUrls,
      },
      supportedIdentityProviders: [
        cognito.UserPoolClientIdentityProvider.COGNITO,
      ],
      readAttributes: new cognito.ClientAttributes()
        .withStandardAttributes({ email: true, emailVerified: true })
        .withCustomAttributes('role', 'companyId', 'preferences'),
      writeAttributes: new cognito.ClientAttributes()
        .withStandardAttributes({ email: true })
        .withCustomAttributes('role', 'companyId', 'preferences'),
    });

    // Create User Pool Domain
    this.userPoolDomain = new cognito.UserPoolDomain(this, 'UserPoolDomain', {
      userPool: this.userPool,
      cognitoDomain: {
        domainPrefix: `batbern-${envName}-auth`,
      },
    });

    // Create User Groups
    const groups = ['organizer', 'speaker', 'partner', 'attendee'];
    groups.forEach(groupName => {
      new cognito.CfnUserPoolGroup(this, `${groupName}Group`, {
        userPoolId: this.userPool.userPoolId,
        groupName,
        description: `Group for ${groupName} users`,
        precedence: groups.indexOf(groupName),
      });
    });

    // Create bootstrap organizer user for environment setup
    // This user is created automatically on stack deployment
    new BootstrapOrganizer(this, 'BootstrapOrganizer', {
      userPool: this.userPool,
      email: 'nissim@buchs.be',
      password: 'Ur@batbern01',
    });

    // Apply tags
    cdk.Tags.of(this).add('Environment', envName);
    cdk.Tags.of(this).add('Component', 'Authentication');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Outputs
    new cdk.CfnOutput(this, 'UserPoolId', {
      value: this.userPool.userPoolId,
      description: 'Cognito User Pool ID',
      exportName: `${envName}-UserPoolId`,
    });

    new cdk.CfnOutput(this, 'UserPoolArn', {
      value: this.userPool.userPoolArn,
      description: 'Cognito User Pool ARN',
      exportName: `${envName}-UserPoolArn`,
    });

    new cdk.CfnOutput(this, 'UserPoolClientId', {
      value: this.userPoolClient.userPoolClientId,
      description: 'Cognito User Pool Client ID',
      exportName: `${envName}-UserPoolClientId`,
    });

    new cdk.CfnOutput(this, 'UserPoolDomainUrl', {
      value: `https://${this.userPoolDomain.domainName}.auth.${this.region}.amazoncognito.com`,
      description: 'Cognito User Pool Domain URL',
      exportName: `${envName}-UserPoolDomainUrl`,
    });
  }
}