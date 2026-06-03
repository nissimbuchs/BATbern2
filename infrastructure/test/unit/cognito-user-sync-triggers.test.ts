/**
 * IAM tests for the CognitoUserSyncTriggers construct — Story 12.1 AC6.
 *
 * Guards the invariant that the PostConfirmation Lambda is granted
 * `cognito-idp:AdminUpdateUserAttributes`. The handler writes the custom:role='UNUSED'
 * sentinel after the user_profiles INSERT, and the write is DELIBERATELY non-blocking
 * (it must never fail Cognito confirmation). That means a missing grant would surface
 * only as a silently-swallowed AccessDeniedException at runtime — the self-registered
 * chokepoint would never actually stamp the sentinel, yet every handler unit test
 * (which mocks the Cognito client) would still pass. This template-level test is the
 * only guard that catches the missing-permission regression.
 */

import { App, Stack } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';

import { CognitoUserSyncTriggers } from '../../lib/constructs/cognito-user-sync-triggers';

const TEST_ACCOUNT = '123456789012';
const TEST_REGION = 'eu-central-1';

function buildTemplate(): Template {
  const app = new App();
  const stack = new Stack(app, 'TestSyncTriggers', {
    env: { account: TEST_ACCOUNT, region: TEST_REGION },
  });

  // A real UserPool is required: the construct calls userPool.addTrigger(), which is
  // unsupported on imported pools.
  const userPool = new cognito.UserPool(stack, 'Pool', {
    userPoolName: 'batbern-test-user-pool',
  });

  const vpc = ec2.Vpc.fromVpcAttributes(stack, 'Vpc', {
    vpcId: 'vpc-test',
    vpcCidrBlock: '10.0.0.0/16',
    availabilityZones: ['eu-central-1a', 'eu-central-1b'],
    privateSubnetIds: ['subnet-aaa', 'subnet-bbb'],
    privateSubnetRouteTableIds: ['rtb-aaa', 'rtb-bbb'],
  });
  const lambdaSecurityGroup = ec2.SecurityGroup.fromSecurityGroupId(stack, 'Sg', 'sg-test12345');
  const databaseSecret = secretsmanager.Secret.fromSecretCompleteArn(
    stack,
    'DbSecret',
    `arn:aws:secretsmanager:${TEST_REGION}:${TEST_ACCOUNT}:secret:batbern-db-secret-AbCdEf`
  );

  new CognitoUserSyncTriggers(stack, 'UserSyncTriggers', {
    userPool,
    vpc,
    lambdaSecurityGroup,
    databaseSecret,
    databaseEndpoint: 'db.test.local',
    envName: 'staging',
    isProduction: true,
  });

  return Template.fromStack(stack);
}

describe('CognitoUserSyncTriggers — IAM (Story 12.1 AC6)', () => {
  let template: Template;

  beforeAll(() => {
    template = buildTemplate();
  });

  test('should_grantAdminUpdateUserAttributes_when_postConfirmationWritesUnusedSentinel', () => {
    template.hasResourceProperties('AWS::IAM::Policy', {
      PolicyDocument: Match.objectLike({
        Statement: Match.arrayWith([
          Match.objectLike({
            Effect: 'Allow',
            Action: 'cognito-idp:AdminUpdateUserAttributes',
          }),
        ]),
      }),
    });
  });
});

describe('CognitoUserSyncTriggers — PreSignUp account-linking trigger (Story 12.6 AC6)', () => {
  let template: Template;

  beforeAll(() => {
    template = buildTemplate();
  });

  test('should_createBundledPreSignUpFunction_notInlineZipFile', () => {
    // A bundled NodejsFunction has S3-asset code, NOT inline ZipFile. The former inline
    // lambda.Code.fromInline trigger is gone (it moved here with DB access).
    template.hasResourceProperties('AWS::Lambda::Function', {
      FunctionName: 'batbern-staging-pre-signup-trigger',
      Runtime: 'nodejs20.x',
      Handler: 'index.handler',
      Code: Match.objectLike({ S3Bucket: Match.anyValue() }),
    });
  });

  test('should_wirePreSignUpAsPoolLambdaConfig', () => {
    template.hasResourceProperties('AWS::Cognito::UserPool', {
      LambdaConfig: Match.objectLike({
        PreSignUp: Match.anyValue(),
      }),
    });
  });

  test('should_grantAdminLinkProviderForUserAndListUsers_forFederatedLinking', () => {
    template.hasResourceProperties('AWS::IAM::Policy', {
      PolicyDocument: Match.objectLike({
        Statement: Match.arrayWith([
          Match.objectLike({
            Effect: 'Allow',
            Action: Match.arrayWith([
              'cognito-idp:AdminLinkProviderForUser',
              'cognito-idp:ListUsers',
            ]),
          }),
        ]),
      }),
    });
  });
});
