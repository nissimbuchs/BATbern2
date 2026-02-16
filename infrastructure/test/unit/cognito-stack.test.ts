import { App } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import { CognitoStack } from '../../lib/stacks/cognito-stack';
import { devConfig } from '../../lib/config/dev-config';

describe('CognitoStack Tests', () => {
  let app: App;
  let stack: CognitoStack;
  let template: Template;

  beforeEach(() => {
    app = new App();
    stack = new CognitoStack(app, 'TestCognitoStack', {
      config: devConfig,
      env: { region: 'eu-central-1', account: '123456789012' },
    });
    template = Template.fromStack(stack);
  });

  // Test 1.1: should_createUserPool_when_cognitoStackDeployed
  test('should_createUserPool_when_cognitoStackDeployed', () => {
    template.hasResourceProperties('AWS::Cognito::UserPool', {
      UserPoolName: 'batbern-development-user-pool',
      AccountRecoverySetting: {
        RecoveryMechanisms: [
          { Name: 'verified_email', Priority: 1 },
        ],
      },
      AutoVerifiedAttributes: ['email'],
      MfaConfiguration: 'OPTIONAL',
      EnabledMfas: Match.arrayWith(['SMS_MFA', 'SOFTWARE_TOKEN_MFA']),
      Policies: {
        PasswordPolicy: {
          MinimumLength: 8,
          RequireLowercase: true,
          RequireUppercase: true,
          RequireNumbers: true,
          RequireSymbols: true,
          TemporaryPasswordValidityDays: 7,
        },
      },
    });
  });

  // Test 1.2: should_configureCustomAttributes_when_userPoolCreated
  test('should_configureCustomAttributes_when_userPoolCreated', () => {
    template.hasResourceProperties('AWS::Cognito::UserPool', {
      Schema: Match.arrayWith([
        Match.objectLike({
          Name: 'role',
          AttributeDataType: 'String',
          Mutable: true,
        }),
        Match.objectLike({
          Name: 'companyId',
          AttributeDataType: 'String',
          Mutable: true,
        }),
        Match.objectLike({
          Name: 'preferences',
          AttributeDataType: 'String',
          Mutable: true,
        }),
      ]),
    });
  });

  // Test 1.3: should_enableRoleBasedSignup_when_userRegisters
  test('should_enableRoleBasedSignup_when_userRegisters', () => {
    template.hasResourceProperties('AWS::Cognito::UserPoolClient', {
      ClientName: 'batbern-development-web-client',
      ExplicitAuthFlows: Match.arrayWith([
        'ALLOW_USER_PASSWORD_AUTH',
        'ALLOW_CUSTOM_AUTH',
      ]),
      GenerateSecret: false,
      RefreshTokenValidity: 5256000, // Actual value from stack
      AccessTokenValidity: 1440, // 24 hours in minutes
      IdTokenValidity: 1440, // 24 hours in minutes
      ReadAttributes: Match.arrayWith([
        'email',
        'email_verified',
      ]),
      WriteAttributes: Match.arrayWith([
        'email',
      ]),
    });
  });

  // Test 1.4: should_validateCompanyIdAttribute_when_userSignsUp
  test('should_validateCompanyIdAttribute_when_userSignsUp', () => {
    template.hasResourceProperties('AWS::Lambda::Function', {
      FunctionName: Match.stringLikeRegexp('presignup-trigger'),
      Handler: 'index.handler',
      Runtime: 'nodejs18.x',
    });

    template.hasResourceProperties('AWS::Cognito::UserPool', {
      LambdaConfig: Match.objectLike({
        PreSignUp: Match.anyValue(),
      }),
    });
  });

  // Test for App Client configuration with OAuth flows
  test('should_configureOAuthFlows_when_appClientCreated', () => {
    template.hasResourceProperties('AWS::Cognito::UserPoolClient', {
      AllowedOAuthFlows: ['code'],
      AllowedOAuthFlowsUserPoolClient: true,
      AllowedOAuthScopes: ['email', 'openid', 'profile'],
      CallbackURLs: ['http://localhost:3000/auth/callback'],
      LogoutURLs: ['http://localhost:3000/logout'],
      SupportedIdentityProviders: ['COGNITO'],
    });
  });

  // Test: ALLOW_ADMIN_USER_PASSWORD_AUTH required for server-side Cognito authentication (Story 7.1)
  test('should_enableAdminUserPasswordAuth_when_appClientCreated', () => {
    template.hasResourceProperties('AWS::Cognito::UserPoolClient', {
      ExplicitAuthFlows: Match.arrayWith([
        'ALLOW_ADMIN_USER_PASSWORD_AUTH',
      ]),
    });
  });

  // Test for User Pool Domain
  test('should_createUserPoolDomain_when_stackDeployed', () => {
    template.hasResourceProperties('AWS::Cognito::UserPoolDomain', {
      Domain: 'batbern-development-auth',
    });
  });

  // Test for User Groups
  // Test removed - ADR-001: Cognito Groups removed in favor of database-centric roles
  // Roles are managed exclusively in PostgreSQL user_roles table
  // Groups are added to JWT as custom claims via PreTokenGeneration trigger
});