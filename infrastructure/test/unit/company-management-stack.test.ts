/**
 * IAM policy tests for CompanyManagementStack — Cognito admin permissions (Story 11.E.1 / AR30).
 *
 * Guards two least-privilege invariants:
 *  1. The four Cognito admin actions needed by Story 11.E.2's provisioning hook are granted
 *     to the CUMS task role and scoped to the User Pool ARN.
 *  2. `cognito-idp:AdminAddUserToGroup` is NOT granted — roles live in PostgreSQL `user_roles`
 *     per ADR-001 (Resolved Q#1, PM 2026-05-17). Granting the permission would be a useless
 *     least-privilege violation. This is a regression guard against future "let's add Cognito
 *     groups back" drift.
 */

import { App, Stack } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';

import { stagingConfig } from '../../lib/config/staging-config';
import { CompanyManagementStack } from '../../lib/stacks/company-management-stack';

const TEST_ACCOUNT = '123456789012';
const TEST_REGION = 'eu-central-1';

function buildTemplate(): Template {
  const app = new App();
  const parent = new Stack(app, 'Parent', {
    env: { account: TEST_ACCOUNT, region: TEST_REGION },
  });

  const vpc = ec2.Vpc.fromVpcAttributes(parent, 'Vpc', {
    vpcId: 'vpc-test',
    vpcCidrBlock: '10.0.0.0/16',
    availabilityZones: ['eu-central-1a', 'eu-central-1b'],
    privateSubnetIds: ['subnet-aaa', 'subnet-bbb'],
    privateSubnetRouteTableIds: ['rtb-aaa', 'rtb-bbb'],
  });
  const cluster = ecs.Cluster.fromClusterAttributes(parent, 'Cluster', {
    clusterName: 'test-cluster',
    vpc,
    securityGroups: [],
  });
  const dbSg = ec2.SecurityGroup.fromSecurityGroupId(parent, 'DbSg', 'sg-db12345');
  // CompanyManagementStack types userPool as the concrete `cognito.UserPool` class, but only
  // accesses `userPoolArn` at runtime. Cast the imported IUserPool through `unknown` so the test
  // stays self-contained without spinning up a real Cognito User Pool resource.
  const userPool = cognito.UserPool.fromUserPoolId(
    parent,
    'UP',
    `${TEST_REGION}_test`,
  ) as unknown as cognito.UserPool;
  const userPoolClient = cognito.UserPoolClient.fromUserPoolClientId(
    parent,
    'UPC',
    'test-client-id',
  );

  const stack = new CompanyManagementStack(app, 'TestCUMS', {
    config: { ...stagingConfig, region: TEST_REGION },
    cluster,
    vpc,
    databaseSecurityGroup: dbSg,
    userPool,
    userPoolClient,
    env: { account: TEST_ACCOUNT, region: TEST_REGION },
  });

  return Template.fromStack(stack);
}

describe('CompanyManagementStack — Cognito admin IAM policy (Story 11.E.1)', () => {
  // Set IMAGE_TAG so createContainerImage uses the ECR path instead of fromAsset
  // (fromAsset would try to hash the Dockerfile during synthesis, unnecessary in unit tests).
  let template: Template;

  beforeAll(() => {
    process.env.IMAGE_TAG = 'test-image-tag';
    template = buildTemplate();
  });

  afterAll(() => {
    delete process.env.IMAGE_TAG;
  });

  // Positive regression guard: least-privilege set-equality on the four-action policy +
  // resource scope. If anyone later adds a fifth action (e.g. cognito-idp:* or
  // AdminAddUserToGroup) to this statement, or drops the scope to '*', this test fails
  // loudly and forces an explicit NFR5 review.
  test('should_grantCognitoAdminPerms_when_companyManagementStackDeployed', () => {
    template.hasResourceProperties('AWS::IAM::Policy', {
      PolicyDocument: {
        Statement: Match.arrayWith([
          Match.objectLike({
            Effect: 'Allow',
            Action: [
              'cognito-idp:AdminCreateUser',
              'cognito-idp:AdminSetUserPassword',
              'cognito-idp:AdminInitiateAuth',
              'cognito-idp:AdminGetUser',
            ],
            // Resource MUST be scoped to the User Pool ARN — never '*'. Spec AC2 invariant.
            // CDK synthesises an Fn::Join with the imported pool's ID at the tail:
            //   { "Fn::Join": ["", ["arn:", {"Ref":"AWS::Partition"}, ":cognito-idp:...:userpool/eu-central-1_test"]] }
            // The objectLike + arrayWith nesting fails if the Resource collapses to '*' (no
            // Fn::Join key) OR if the pool path is broadened (regex tail mismatch).
            Resource: Match.objectLike({
              'Fn::Join': Match.arrayWith([
                Match.arrayWith([
                  Match.stringLikeRegexp(':cognito-idp:.*:userpool/eu-central-1_test$'),
                ]),
              ]),
            }),
          }),
        ]),
      },
    });
  });

  // Negative regression guard for Resolved Q#1 (ADR-001 wins over PRD AR30 aspirational
  // wording): roles live in PostgreSQL user_roles; no Cognito groups exist; granting the
  // permission would be a useless least-privilege violation.
  //
  // Scans BOTH standalone AWS::IAM::Policy resources AND inline policies on AWS::IAM::Role
  // resources. Without the inline-role sweep a future `role.attachInlinePolicy(...)` grant
  // would silently bypass the guard. Within CompanyManagementStack the only IAM roles are
  // the CUMS service's task role + execution role, so the scan is effectively scoped to
  // CUMS without an explicit logical-ID filter.
  test('should_notGrantAdminAddUserToGroup_when_companyManagementStackDeployed', () => {
    const allActions: string[] = [];

    const collectFromStatements = (statements: unknown[]): void => {
      for (const stmt of statements) {
        const s = stmt as { Action?: unknown };
        const actions = Array.isArray(s.Action) ? s.Action : [s.Action];
        allActions.push(...actions.filter((a): a is string => typeof a === 'string'));
      }
    };

    const standalonePolicies = template.findResources('AWS::IAM::Policy');
    for (const policy of Object.values(standalonePolicies)) {
      const statements: unknown[] =
        (policy as { Properties?: { PolicyDocument?: { Statement?: unknown[] } } })
          .Properties?.PolicyDocument?.Statement ?? [];
      collectFromStatements(statements);
    }

    const roles = template.findResources('AWS::IAM::Role');
    for (const role of Object.values(roles)) {
      const inlinePolicies: unknown[] =
        (role as { Properties?: { Policies?: unknown[] } }).Properties?.Policies ?? [];
      for (const policy of inlinePolicies) {
        const statements: unknown[] =
          (policy as { PolicyDocument?: { Statement?: unknown[] } }).PolicyDocument
            ?.Statement ?? [];
        collectFromStatements(statements);
      }
    }

    expect(allActions).not.toContain('cognito-idp:AdminAddUserToGroup');
  });
});
