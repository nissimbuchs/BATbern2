import * as cdk from 'aws-cdk-lib';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { BootstrapOrganizer } from '../constructs/bootstrap-organizer';
import { CognitoUserSyncTriggers } from '../constructs/cognito-user-sync-triggers';

export interface CognitoStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  vpc?: ec2.IVpc; // For Lambda triggers in VPC
  lambdaTriggersSecurityGroup?: ec2.ISecurityGroup; // For Lambda triggers
  databaseSecret?: secretsmanager.ISecret; // For Lambda triggers to access database
  databaseEndpoint?: string; // For Lambda triggers to access database
}

/**
 * Cognito Stack - Provides user authentication and authorization
 *
 * Implements:
 * - AC16: AWS Cognito for authentication with role-based access
 * - AC4: Security Boundaries with custom attributes (Story 1.2.6: NO Cognito Groups)
 * - ADR-001: Database-centric roles synced to JWT via PreTokenGeneration Lambda
 */
export class CognitoStack extends cdk.Stack {
  public readonly userPool: cognito.UserPool;
  public readonly userPoolClient: cognito.UserPoolClient;
  public readonly userPoolDomain: cognito.UserPoolDomain;

  constructor(scope: Construct, id: string, props: CognitoStackProps) {
    super(scope, id, props);

    const isProd = props.config.envName === 'production';
    const envName = props.config.envName;

    // Create stable log group for Pre-Signup Lambda Trigger
    const preSignupLogGroup = new logs.LogGroup(this, 'PreSignupLogGroup', {
      logGroupName: `/aws/lambda/BATbern-${envName}/presignup-trigger`,
      retention: isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // Create Pre-Signup Lambda Trigger for validation
    const preSignupLambda = new lambda.Function(this, 'PreSignupTrigger', {
      functionName: `batbern-${envName}-presignup-trigger`,
      runtime: lambda.Runtime.NODEJS_18_X,
      handler: 'index.handler',
      logGroup: preSignupLogGroup,
      code: lambda.Code.fromInline(`
        exports.handler = async (event) => {
          console.log('Pre-signup trigger:', JSON.stringify(event));

          // Validate company ID if provided
          const companyId = event.request.userAttributes['custom:companyId'];
          if (companyId && !companyId.match(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i)) {
            throw new Error('Invalid company ID format. Must be a valid UUID.');
          }

          // Role validation removed - Story 1.2.6: ADR-001 database-centric architecture
          // Roles are managed in PostgreSQL and synced to JWT via PreTokenGeneration Lambda
          // Self-registered users receive ATTENDEE role (assigned by PostConfirmation trigger)

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
        // DEPRECATED: Legacy role attribute - not used per ADR-001 (Story 1.2.6)
        // Roles are managed in database and added to JWT via PreTokenGeneration Lambda
        // Cannot be removed due to AWS Cognito limitation (custom attributes are permanent)
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
      refreshTokenValidity: cdk.Duration.days(3650), // 10 years for long-lived test tokens
      accessTokenValidity: cdk.Duration.hours(24), // Max allowed
      idTokenValidity: cdk.Duration.hours(24), // Max allowed
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
        .withCustomAttributes('companyId', 'preferences', 'role'),
      writeAttributes: new cognito.ClientAttributes()
        .withStandardAttributes({ email: true })
        .withCustomAttributes('companyId', 'preferences'),
    });

    // Create User Pool Domain
    this.userPoolDomain = new cognito.UserPoolDomain(this, 'UserPoolDomain', {
      userPool: this.userPool,
      cognitoDomain: {
        domainPrefix: `batbern-${envName}-auth`,
      },
    });

    // REMOVED: Cognito Groups (Story 1.2.6: ADR-001 Database-centric architecture)
    // Roles are now managed exclusively in PostgreSQL and synced to JWT via PreTokenGeneration Lambda
    // NO Cognito Groups - eliminates dual storage and sync complexity

    // Create bootstrap organizer user for environment setup
    // This user is created automatically on stack deployment
    new BootstrapOrganizer(this, 'BootstrapOrganizer', {
      userPool: this.userPool,
      email: 'nissim@buchs.be',
      password: 'TempPass123!',
    });

    // Story 1.2.5: Add Cognito user sync triggers
    // These triggers sync user creation/authentication between Cognito and PostgreSQL
    // Only deploy if VPC and database are configured (not available in local development)
    if (props.vpc && props.lambdaTriggersSecurityGroup && props.databaseSecret && props.databaseEndpoint) {
      new CognitoUserSyncTriggers(this, 'UserSyncTriggers', {
        userPool: this.userPool,
        vpc: props.vpc,
        lambdaSecurityGroup: props.lambdaTriggersSecurityGroup,
        databaseSecret: props.databaseSecret,
        databaseEndpoint: props.databaseEndpoint,
        envName: props.config.envName,
      });
      // Note: Lambda security group and database ingress rule are created in NetworkStack
      // Tables are created by CompanyManagementStack Flyway migrations at runtime
    }

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