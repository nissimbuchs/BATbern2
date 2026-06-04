import * as cdk from 'aws-cdk-lib';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as kms from 'aws-cdk-lib/aws-kms';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as route53targets from 'aws-cdk-lib/aws-route53-targets';
import { Construct } from 'constructs';
import { NodejsFunction } from 'aws-cdk-lib/aws-lambda-nodejs';
import * as path from 'path';
import { EnvironmentConfig } from '../config/environment-config';
import { BootstrapOrganizer } from '../constructs/bootstrap-organizer';
import { CognitoUserSyncTriggers } from '../constructs/cognito-user-sync-triggers';

export interface CognitoStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  vpc?: ec2.IVpc; // For Lambda triggers in VPC
  lambdaTriggersSecurityGroup?: ec2.ISecurityGroup; // For Lambda triggers
  databaseSecret?: secretsmanager.ISecret; // For Lambda triggers to access database
  databaseEndpoint?: string; // For Lambda triggers to access database
  // Story 12.9 DF-1: us-east-1 cert (from DnsStack — a pre-created literal ARN per the
  // repo's cert practice, so no cross-region export machinery) + the hosted zone for the
  // `auth.<zone>` hosted-UI custom domain. Mirrors the StorageStack cdnCertificate/
  // hostedZone prop pattern. Optional — when absent (local dev / no DNS), only the default
  // prefix domain exists.
  authCertificate?: certificatemanager.ICertificate;
  hostedZone?: route53.IHostedZone; // Route53 hosted zone for the auth.<zone> alias record
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
  /** Story 12.9 DF-1: `auth.<zone>` hosted-UI custom domain (only when DNS is configured). */
  public readonly customUserPoolDomain?: cognito.UserPoolDomain;

  constructor(scope: Construct, id: string, props: CognitoStackProps) {
    super(scope, id, props);

    const isProd = props.config.isProduction ?? (props.config.envName === 'production');
    const envName = props.config.envName;

    // Story 12.6 (SSO Phase 2): the PreSignUp trigger is no longer an inline Lambda here.
    // It moved into the CognitoUserSyncTriggers construct (below) as a VPC + DB-secret
    // NodejsFunction (lib/lambda/triggers/pre-signup.ts) so it can do an email lookup for
    // federated account-linking (AdminLinkProviderForUser) while preserving the native
    // company-UUID validation verbatim. See infrastructure/lib/constructs/cognito-user-sync-triggers.ts.

    // Create KMS key for Cognito code encryption (CustomEmailSender trigger)
    // Story 1.2.2: Implement Forgot Password Flow - Task 1a
    const cognitoEmailKmsKey = new kms.Key(this, 'CognitoEmailKmsKey', {
      description: `BATbern ${envName} - Cognito CustomEmailSender code encryption`,
      enableKeyRotation: true,
      removalPolicy: isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
    });

    // Allow Cognito to use this KMS key for encrypting codes
    cognitoEmailKmsKey.addToResourcePolicy(
      new cdk.aws_iam.PolicyStatement({
        sid: 'Allow Cognito to use the key',
        effect: cdk.aws_iam.Effect.ALLOW,
        principals: [new cdk.aws_iam.ServicePrincipal('cognito-idp.amazonaws.com')],
        actions: ['kms:Encrypt', 'kms:Decrypt', 'kms:CreateGrant'],
        resources: ['*'],
      })
    );

    // Create Custom Email Sender Lambda Trigger for branded password reset emails
    // Story 1.2.2: Implement Forgot Password Flow - Task 1a (updated to CustomEmailSender)
    const customEmailSenderLogGroup = new logs.LogGroup(this, 'CustomEmailSenderLogGroup', {
      logGroupName: `/aws/lambda/BATbern-${envName}/custom-email-sender-trigger`,
      retention: isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // Determine frontend domain based on environment
    const isProdTraffic = props.config.isProduction ?? (envName === 'production');
    const frontendDomain = isProdTraffic
      ? `https://${props.config.domain?.frontendDomain ?? 'www.batbern.ch'}`
      : envName === 'staging'
      ? 'https://www.batbern.ch'
      : 'http://localhost:3000';

    // FROM address must use a domain verified in SES for the environment
    const fromEmail = isProdTraffic
      ? 'BATbern <noreply@batbern.ch>'
      : 'BATbern <noreply@berner-architekten-treffen.ch>';

    const customEmailSenderLambda = new NodejsFunction(this, 'CustomEmailSenderTrigger', {
      functionName: `batbern-${envName}-custom-email-sender-trigger`,
      runtime: lambda.Runtime.NODEJS_20_X,
      entry: path.join(__dirname, '../lambda/triggers/custom-email-sender.ts'),
      handler: 'handler',
      timeout: cdk.Duration.seconds(10),
      logGroup: customEmailSenderLogGroup,
      environment: {
        FRONTEND_DOMAIN: frontendDomain,
        FROM_EMAIL: fromEmail,
        KEY_ID: cognitoEmailKmsKey.keyId,
        KEY_ARN: cognitoEmailKmsKey.keyArn,
        // AWS_REGION is automatically provided by Lambda runtime
      },
      bundling: {
        externalModules: ['@aws-sdk/*'], // Use AWS SDK from Lambda runtime
        nodeModules: ['@aws-crypto/client-node'], // Bundle this module with dependencies
        minify: true,
        sourceMap: false,
        forceDockerBundling: false, // Prefer local esbuild over Docker for faster builds on ARM64
      },
    });

    // Grant SES permissions to send emails
    customEmailSenderLambda.addToRolePolicy(
      new cdk.aws_iam.PolicyStatement({
        effect: cdk.aws_iam.Effect.ALLOW,
        actions: ['ses:SendEmail', 'ses:SendRawEmail'],
        resources: ['*'],
      })
    );

    // Grant KMS decrypt permissions
    cognitoEmailKmsKey.grantDecrypt(customEmailSenderLambda);

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
      // CustomEmailSender Lambda handles all email delivery
      // No need for Cognito's SES configuration
      standardAttributes: {
        email: {
          required: true,
          mutable: true,
        },
        // Story 12.5: do NOT declare givenName/familyName here. They are built-in OIDC
        // standard attributes that every Cognito pool already has (verified on the live
        // pool: given_name/family_name present, Required:false/Mutable:true by default),
        // so the Google IdP attributeMapping below maps onto them directly. Declaring
        // them is unnecessary AND breaks deploy: UpdateUserPool rejects standard-attribute
        // schema additions on an existing pool with "Invalid AttributeDataType input"
        // (observed on PR #735 — UPDATE_FAILED + rollback). The mapping target exists
        // without the declaration.
      },
      customAttributes: {
        // Story 1.16.2: Public meaningful username (e.g., "john.doe")
        // Set by PreTokenGeneration Lambda from database user_profiles.username
        username: new cognito.StringAttribute({
          mutable: true,
          maxLen: 100,
        }),
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
        tempPasswordValidity: cdk.Duration.days(14),  // Story 11.E.1 / Resolved Q#4 — 14-day window for invitation→first-login (was 7)
      },
      accountRecovery: cognito.AccountRecovery.EMAIL_ONLY,
      mfa: cognito.Mfa.OPTIONAL,
      mfaSecondFactor: {
        sms: true,
        otp: true,
      },
      lambdaTriggers: {
        // preSignUp is wired in CognitoUserSyncTriggers (Story 12.6) via addTrigger,
        // alongside the other VPC/DB-backed triggers.
        customEmailSender: customEmailSenderLambda,
      },
      customSenderKmsKey: cognitoEmailKmsKey,
      removalPolicy: isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
    });

    // Story 12.5 (SSO Phase 1): Google OIDC identity provider, brokered by Cognito.
    // The client id/secret live ONLY in Secrets Manager (created by Story 12.4 runbook,
    // name `batbern/staging/sso/google-oauth`, JSON {clientId, clientSecret}). The single
    // real pool is in the staging account (= production), so the secret name is the literal
    // staging path — matching where 12.4 stored it. fromSecretNameV2 resolves to a CFN
    // reference (NOT the value) at synth time, so the plaintext never enters the template
    // or git (CLAUDE.md security).
    const googleOAuthSecret = secretsmanager.Secret.fromSecretNameV2(
      this,
      'GoogleOAuthSecret',
      'batbern/staging/sso/google-oauth'
    );

    const googleIdp = new cognito.UserPoolIdentityProviderGoogle(this, 'GoogleIdp', {
      userPool: this.userPool,
      // clientId prop is a plain `string`, so it must be unwrapped. The Google client id
      // is NOT sensitive (it ends in `.apps.googleusercontent.com` and is public).
      clientId: googleOAuthSecret.secretValueFromJson('clientId').unsafeUnwrap(),
      // clientSecretValue accepts a SecretValue directly — keep it as a SecretValue (do
      // NOT unwrap) so it synths to a {{resolve:secretsmanager:...}} dynamic reference and
      // the secret stays out of the CloudFormation template.
      clientSecretValue: googleOAuthSecret.secretValueFromJson('clientSecret'),
      scopes: ['openid', 'email', 'profile'],
      attributeMapping: {
        // email is REQUIRED: it keys the Phase-2 account-linking (AdminLinkProviderForUser)
        // and resolves the email sign-in alias.
        email: cognito.ProviderAttribute.GOOGLE_EMAIL,
        // Names fold to standard attributes (see standardAttributes above). The canonical
        // JIT path (Story 12.3) reads these for federated users — the federated counterpart
        // of how post-confirmation.ts reads firstName/lastName from custom:preferences for
        // native sign-ups. There is NO Cognito mapping into a custom:preferences JSON field.
        givenName: cognito.ProviderAttribute.GOOGLE_GIVEN_NAME,
        familyName: cognito.ProviderAttribute.GOOGLE_FAMILY_NAME,
      },
    });

    // Determine callback URLs based on environment
    const callbackUrls = isProdTraffic
      ? [`https://${props.config.domain?.frontendDomain ?? 'www.batbern.ch'}/auth/callback`]
      : envName === 'staging'
      ? ['https://www.batbern.ch/auth/callback']
      : ['http://localhost:3000/auth/callback'];

    const logoutUrls = isProdTraffic
      ? [`https://${props.config.domain?.frontendDomain ?? 'www.batbern.ch'}/logout`]
      : envName === 'staging'
      ? ['https://www.batbern.ch/logout']
      : ['http://localhost:3000/logout'];

    // Create User Pool Client
    this.userPoolClient = new cognito.UserPoolClient(this, 'UserPoolClient', {
      userPool: this.userPool,
      userPoolClientName: `batbern-${envName}-web-client`,
      authFlows: {
        userPassword: true,
        custom: true,
        userSrp: true, // Enable SRP authentication for secure password flow
        adminUserPassword: true,  // Story 11.E.1 / AR29 / cherry-pick d5cf0fcc — enables AdminInitiateAuth for the temp-password flow used at speaker provisioning (Story 11.E.2)
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
      // Story 12.5 AC5: GOOGLE added so the hosted-UI can broker Google OIDC sign-in.
      // COGNITO MUST stay so email/password auth keeps working. Rollback = drop GOOGLE.
      supportedIdentityProviders: [
        cognito.UserPoolClientIdentityProvider.COGNITO,
        cognito.UserPoolClientIdentityProvider.GOOGLE,
      ],
      // Story 12.1 AC5: 'role' dropped from client readAttributes so the STORED (fossil)
      // custom:role attribute stops flowing into issued tokens. The schema attribute
      // (customAttributes.role above) stays — Cognito custom attributes are permanent —
      // and the DB-projected custom:role authorization claim from the PreTokenGeneration
      // Lambda is unaffected (it injects claimsToAddOrOverride, independent of this list).
      readAttributes: new cognito.ClientAttributes()
        .withStandardAttributes({ email: true, emailVerified: true, givenName: true, familyName: true })
        .withCustomAttributes('companyId', 'preferences'),
      // Story 12.8 F1b fix: givenName/familyName MUST be writable here. AWS Cognito only
      // populates IdP-mapped attributes that the federating app client has WRITE access to
      // ("the WriteAttributes array must include all attributes that you have mapped to IdP
      // attributes" — Cognito docs). The Google IdP maps given_name/family_name (Story 12.5)
      // and Google sends them (consent grants "Name"), but with only `email` writable here
      // Cognito silently dropped the mapped names → federated users provisioned as "User
      // User" (verified 2026-06-03: names absent at PreSignUp + PostConfirmation despite a
      // full-consent first-time federation). `email` worked only because it was writable.
      writeAttributes: new cognito.ClientAttributes()
        .withStandardAttributes({ email: true, givenName: true, familyName: true })
        .withCustomAttributes('companyId', 'preferences'),
    });

    // Story 12.5 AC5: ensure CloudFormation creates the Google IdP BEFORE the client that
    // lists it in supportedIdentityProviders (the standard CDK IdP-before-client gotcha;
    // otherwise deploy fails with "the provider does not exist").
    this.userPoolClient.node.addDependency(googleIdp);

    // Create User Pool Domain
    this.userPoolDomain = new cognito.UserPoolDomain(this, 'UserPoolDomain', {
      userPool: this.userPool,
      cognitoDomain: {
        domainPrefix: `batbern-${envName}-auth`,
      },
    });

    // Story 12.9 DF-1 (2026-06-04): CUSTOM hosted-UI domain `auth.<zone>` (auth.batbern.ch).
    // Google's consent screen displays the OAuth redirect domain — previously the ugly
    // default `batbern-staging-auth.auth.eu-central-1.amazoncognito.com`; with this domain
    // it shows batbern.ch. A pool may carry BOTH a prefix domain AND a custom domain, so the
    // prefix domain above is deliberately KEPT (zero-downtime: the already-deployed frontend
    // and the existing Google redirect URI keep working until the FE + Google client switch).
    // Requirements satisfied: us-east-1 cert (DnsStack, crossRegionReferences) + an A record
    // on the parent zone apex (batbern.ch has one). NOTE (manual, one-time): the Google
    // OAuth client `batbern-cognito-web` needs `https://auth.<zone>/oauth2/idpresponse`
    // ADDED to its authorized redirect URIs (keep the old amazoncognito one during
    // transition). JWT validation is unaffected (issuer stays cognito-idp.<region>/<poolId>).
    if (props.authCertificate && props.hostedZone && props.config.domain) {
      const authDomainName = `auth.${props.config.domain.zoneName}`;
      this.customUserPoolDomain = new cognito.UserPoolDomain(this, 'CustomUserPoolDomain', {
        userPool: this.userPool,
        customDomain: {
          domainName: authDomainName,
          certificate: props.authCertificate,
        },
      });
      // Cognito custom domains front an internal CloudFront distribution — alias to it
      // (mirrors StorageStack's CdnAliasRecord: zone from props, full-domain recordName).
      new route53.ARecord(this, 'AuthDomainAliasRecord', {
        zone: props.hostedZone,
        recordName: authDomainName,
        target: route53.RecordTarget.fromAlias(
          new route53targets.UserPoolDomainTarget(this.customUserPoolDomain)
        ),
      });

      new cdk.CfnOutput(this, 'CustomUserPoolDomainUrl', {
        value: `https://${authDomainName}`,
        description: 'Cognito hosted-UI CUSTOM domain (Story 12.9 DF-1)',
        exportName: `${envName}-CustomUserPoolDomainUrl`,
      });
    }

    // REMOVED: Cognito Groups (Story 1.2.6: ADR-001 Database-centric architecture)
    // Roles are now managed exclusively in PostgreSQL and synced to JWT via PreTokenGeneration Lambda
    // NO Cognito Groups - eliminates dual storage and sync complexity

    // Story 1.2.5: Add Cognito user sync triggers
    // These triggers sync user creation/authentication between Cognito and PostgreSQL
    // Only deploy if VPC and database are configured (not available in local development)
    // Must be created before BootstrapOrganizer so we can pass the PostConfirmation Lambda ARN
    let userSyncTriggers: CognitoUserSyncTriggers | undefined;
    if (props.vpc && props.lambdaTriggersSecurityGroup && props.databaseSecret && props.databaseEndpoint) {
      userSyncTriggers = new CognitoUserSyncTriggers(this, 'UserSyncTriggers', {
        userPool: this.userPool,
        vpc: props.vpc,
        lambdaSecurityGroup: props.lambdaTriggersSecurityGroup,
        databaseSecret: props.databaseSecret,
        databaseEndpoint: props.databaseEndpoint,
        envName: props.config.envName,
        isProduction: props.config.isProduction,
      });
      // Note: Lambda security group and database ingress rule are created in NetworkStack
      // Tables are created by CompanyManagementStack Flyway migrations at runtime
    }

    // Create bootstrap organizer user for environment setup
    // This user is created automatically on stack deployment.
    // Pass the PostConfirmation Lambda ARN so the bootstrap user is also synced to the DB
    // with ORGANIZER role (AdminCreateUser does not fire the PostConfirmation trigger).
    new BootstrapOrganizer(this, 'BootstrapOrganizer', {
      userPool: this.userPool,
      email: 'nissim@buchs.be',
      password: 'TempPass123!',
      postConfirmationLambdaArn: userSyncTriggers?.postConfirmationTrigger.functionArn,
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