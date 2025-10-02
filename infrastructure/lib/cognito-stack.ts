import * as cdk from 'aws-cdk-lib';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as path from 'path';
import { Construct } from 'constructs';

export class CognitoStack extends cdk.Stack {
  public readonly userPool: cognito.UserPool;
  public readonly userPoolClient: cognito.UserPoolClient;
  public readonly userPoolDomain: cognito.UserPoolDomain;

  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // Create Pre-Signup Lambda Trigger for validation
    const preSignupLambda = new lambda.Function(this, 'PreSignupTrigger', {
      functionName: `${id}-PreSignupTrigger`,
      runtime: lambda.Runtime.NODEJS_18_X,
      handler: 'presignup.handler',
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
          if (process.env.NODE_ENV === 'development') {
            event.response.autoConfirmUser = true;
            event.response.autoVerifyEmail = true;
          }

          return event;
        };
      `),
      timeout: cdk.Duration.seconds(5),
    });

    // Create Cognito User Pool
    this.userPool = new cognito.UserPool(this, 'UserPool', {
      userPoolName: 'batbern-user-pool',
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
    });

    // Create User Pool Client
    this.userPoolClient = new cognito.UserPoolClient(this, 'UserPoolClient', {
      userPool: this.userPool,
      userPoolClientName: 'batbern-web-client',
      authFlows: {
        userPassword: true,
        custom: true,
        userSrp: false,
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
        callbackUrls: [
          'https://www.batbern.ch/auth/callback',
          'https://staging.batbern.ch/auth/callback',
          'http://localhost:3000/auth/callback',
        ],
        logoutUrls: [
          'https://www.batbern.ch/logout',
          'https://staging.batbern.ch/logout',
          'http://localhost:3000/logout',
        ],
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
        domainPrefix: 'batbern-auth',
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

    // Outputs
    new cdk.CfnOutput(this, 'UserPoolId', {
      value: this.userPool.userPoolId,
      description: 'Cognito User Pool ID',
      exportName: `${id}-UserPoolId`,
    });

    new cdk.CfnOutput(this, 'UserPoolClientId', {
      value: this.userPoolClient.userPoolClientId,
      description: 'Cognito User Pool Client ID',
      exportName: `${id}-UserPoolClientId`,
    });

    new cdk.CfnOutput(this, 'UserPoolDomainUrl', {
      value: `https://${this.userPoolDomain.domainName}.auth.${this.region}.amazoncognito.com`,
      description: 'Cognito User Pool Domain URL',
      exportName: `${id}-UserPoolDomainUrl`,
    });
  }
}