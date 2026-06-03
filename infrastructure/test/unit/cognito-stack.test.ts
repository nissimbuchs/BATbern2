import { App } from 'aws-cdk-lib';
import { Template, Match, Capture } from 'aws-cdk-lib/assertions';
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
          TemporaryPasswordValidityDays: 14,  // Story 11.E.1 / Resolved Q#4 — was 7
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
    const readAttrs = new Capture();
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
      ReadAttributes: readAttrs,
      WriteAttributes: Match.arrayWith([
        'email',
      ]),
    });

    // Story 12.1 AC5: 'custom:role' is dropped from the client readAttributes so the
    // STORED (fossil) custom:role attribute stops flowing into issued tokens. The
    // DB-projected custom:role authorization claim (injected fresh by the
    // PreTokenGeneration Lambda) is UNCHANGED — this only stops the client reading the
    // stored attribute into the token.
    const attrs = readAttrs.asArray();
    expect(attrs).toContain('email');
    expect(attrs).toContain('email_verified');
    // Assert the EXACT custom-attribute set (not just presence) so a re-added or
    // misspelled custom:role — or any unexpected custom attribute — fails this test,
    // matching the stricter exact-set standard used in company-management-stack.test.ts.
    const customAttrs = attrs.filter((a: string) => a.startsWith('custom:')).sort();
    expect(customAttrs).toEqual(['custom:companyId', 'custom:preferences']);
  });

  // Test 1.4 (Story 12.6): the inline PreSignUp Lambda was removed from this stack — the
  // PreSignUp trigger now lives in the CognitoUserSyncTriggers construct as a VPC+DB-secret
  // NodejsFunction, which is ONLY created when vpc/securityGroup/databaseSecret/endpoint are
  // supplied. This no-VPC dev-config stack therefore wires NO PreSignUp trigger. The trigger
  // wiring + IAM are covered by cognito-user-sync-triggers.test.ts; the handler behaviour
  // (native UUID validation + federated linking) by pre-signup.test.ts.
  test('should_notWireInlinePreSignUpTrigger_when_noVpcConfigured', () => {
    // No legacy inline (nodejs18.x, index.handler) presignup Lambda exists anymore.
    const fns = template.findResources('AWS::Lambda::Function', {
      Properties: { FunctionName: Match.stringLikeRegexp('pre-?signup-trigger') },
    });
    expect(Object.keys(fns)).toHaveLength(0);

    // And the pool has no PreSignUp LambdaConfig in this configuration.
    const pools = template.findResources('AWS::Cognito::UserPool');
    const pool = Object.values(pools)[0] as any;
    expect(pool.Properties.LambdaConfig?.PreSignUp).toBeUndefined();
  });

  // Test for App Client configuration with OAuth flows
  test('should_configureOAuthFlows_when_appClientCreated', () => {
    template.hasResourceProperties('AWS::Cognito::UserPoolClient', {
      AllowedOAuthFlows: ['code'],
      AllowedOAuthFlowsUserPoolClient: true,
      AllowedOAuthScopes: ['email', 'openid', 'profile'],
      CallbackURLs: ['http://localhost:3000/auth/callback'],
      LogoutURLs: ['http://localhost:3000/logout'],
      // Story 12.5 AC5: GOOGLE added alongside COGNITO so the hosted-UI can broker a
      // Google OIDC sign-in. COGNITO MUST remain so email/password auth keeps working.
      SupportedIdentityProviders: ['COGNITO', 'Google'],
    });
  });

  // Story 12.5 AC1/AC2/AC7: Google IdP defined with secret-sourced credentials and
  // 1:1 attribute mapping (email required for account-linking + email alias; names
  // fold to standard given_name/family_name — the only mapping Cognito supports).
  test('should_defineGoogleIdentityProvider_when_stackDeployed', () => {
    template.hasResourceProperties('AWS::Cognito::UserPoolIdentityProvider', {
      ProviderName: 'Google',
      ProviderType: 'Google',
      AttributeMapping: Match.objectLike({
        email: 'email',
        given_name: 'given_name',
        family_name: 'family_name',
      }),
    });
  });

  // Story 12.5 AC1 (CLAUDE.md security): the Google client secret MUST synth to a
  // Secrets Manager dynamic reference, never an inlined plaintext value.
  test('should_sourceGoogleClientSecretFromSecretsManager_when_idpDefined', () => {
    template.hasResourceProperties('AWS::Cognito::UserPoolIdentityProvider', {
      ProviderName: 'Google',
      ProviderDetails: Match.objectLike({
        // fromSecretNameV2 + secretValueFromJson synth to a {{resolve:secretsmanager:...}}
        // dynamic reference rendered as an Fn::Join (the ARN is partition-interpolated), so
        // the plaintext secret never enters the template. Assert that structure.
        client_secret: {
          'Fn::Join': ['', Match.arrayWith([Match.stringLikeRegexp('resolve:secretsmanager')])],
        },
      }),
    });
  });

  // Story 12.5: given_name/family_name are NOT declared in the pool's standardAttributes —
  // they are built-in OIDC standard attributes that every pool already has, and declaring
  // them breaks UpdateUserPool on the existing pool ("Invalid AttributeDataType", PR #735).
  // The Google IdP attributeMapping (asserted above) maps onto those built-ins directly.
  // Guard the regression: the synthesized pool Schema must NOT add given_name/family_name.
  test('should_notDeclareStandardNameAttributesInSchema_when_userPoolCreated', () => {
    const pool = Object.values(template.findResources('AWS::Cognito::UserPool'))[0] as any;
    const schemaNames = (pool.Properties.Schema || []).map((a: any) => a.Name);
    expect(schemaNames).not.toContain('given_name');
    expect(schemaNames).not.toContain('family_name');
  });

  // Test: ALLOW_ADMIN_USER_PASSWORD_AUTH required for server-side Cognito authentication (Story 11.E.1 / AR29)
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