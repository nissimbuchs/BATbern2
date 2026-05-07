/**
 * IAM policy completeness tests for EventManagementStack.
 *
 * Guards against the class of regression where a new AWS resource is wired into the
 * application (e.g. an SES configuration set) but the ECS task role policy is not
 * updated to allow the matching API call against the new resource ARN.
 *
 * Root-cause of 2026-05-05 registration email outage (commit a580514b):
 *   BATBERN_SES_CONFIGURATION_SET_NAME was injected as an env var, causing every
 *   SendRawEmail call to include configurationSetName. The IAM policy covered only
 *   SES identity ARNs, not the configuration-set ARN, so SES returned AccessDenied.
 *   The double-@Async pattern swallowed the exception silently.
 */

import { App, Stack } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';

import { stagingConfig } from '../../lib/config/staging-config';
import { EventManagementStack } from '../../lib/stacks/event-management-stack';

const TEST_ACCOUNT = '123456789012';
const TEST_REGION = 'eu-central-1';
const SES_CONFIG_SET = 'batbern-staging-newsletter';

function buildTemplate(sesConfigurationSetName?: string): Template {
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
  const userPool = cognito.UserPool.fromUserPoolId(parent, 'UP', `${TEST_REGION}_test`);
  const userPoolClient = cognito.UserPoolClient.fromUserPoolClientId(
    parent,
    'UPC',
    'test-client-id',
  );

  const stack = new EventManagementStack(app, 'TestEMS', {
    config: { ...stagingConfig, region: TEST_REGION },
    cluster,
    vpc,
    databaseSecurityGroup: dbSg,
    userPool,
    userPoolClient,
    sesConfigurationSetName,
    env: { account: TEST_ACCOUNT, region: TEST_REGION },
  });

  return Template.fromStack(stack);
}

describe('EventManagementStack — SES IAM policy', () => {
  // Set IMAGE_TAG so createContainerImage uses the ECR path instead of fromAsset
  // (fromAsset would try to hash the Dockerfile during synthesis, unnecessary in unit tests).
  beforeAll(() => {
    process.env.IMAGE_TAG = 'test-image-tag';
  });

  afterAll(() => {
    delete process.env.IMAGE_TAG;
  });

  describe('when sesConfigurationSetName is provided', () => {
    let template: Template;

    beforeAll(() => {
      template = buildTemplate(SES_CONFIG_SET);
    });

    // Regression guard: this specific ARN was missing on 2026-05-05, causing silent
    // AccessDenied on every registration confirmation email send.
    test('should_includeConfigurationSetArn_in_taskRolePolicy_when_sesConfigSetNameProvided', () => {
      template.hasResourceProperties('AWS::IAM::Policy', {
        PolicyDocument: {
          Statement: Match.arrayWith([
            Match.objectLike({
              Action: Match.arrayWith(['ses:SendRawEmail']),
              Resource: Match.arrayWith([
                Match.stringLikeRegexp(`configuration-set/${SES_CONFIG_SET}$`),
              ]),
            }),
          ]),
        },
      });
    });

    test('should_grantSendEmailAndSendRawEmail_when_taskRolePolicyCreated', () => {
      template.hasResourceProperties('AWS::IAM::Policy', {
        PolicyDocument: {
          Statement: Match.arrayWith([
            Match.objectLike({
              Action: Match.arrayWith(['ses:SendEmail', 'ses:SendRawEmail']),
            }),
          ]),
        },
      });
    });

    test('should_includeFromIdentityArn_when_taskRolePolicyCreated', () => {
      template.hasResourceProperties('AWS::IAM::Policy', {
        PolicyDocument: {
          Statement: Match.arrayWith([
            Match.objectLike({
              Action: Match.arrayWith(['ses:SendRawEmail']),
              Resource: Match.arrayWith([
                Match.stringLikeRegexp(`identity/batbern\\.ch$`),
              ]),
            }),
          ]),
        },
      });
    });
  });

  describe('when sesConfigurationSetName is not provided', () => {
    let template: Template;

    beforeAll(() => {
      template = buildTemplate(undefined);
    });

    test('should_notIncludeConfigurationSetArn_when_sesConfigSetNameAbsent', () => {
      // Scan all IAM policies in the template — none should reference a configuration-set ARN
      const policies = template.findResources('AWS::IAM::Policy');
      const configSetArns: string[] = [];

      for (const policy of Object.values(policies)) {
        const statements: unknown[] =
          (policy as { Properties?: { PolicyDocument?: { Statement?: unknown[] } } }).Properties
            ?.PolicyDocument?.Statement ?? [];
        for (const stmt of statements) {
          const s = stmt as { Resource?: unknown };
          const resources = Array.isArray(s.Resource) ? s.Resource : [s.Resource];
          configSetArns.push(
            ...resources.filter(
              (r): r is string => typeof r === 'string' && r.includes('configuration-set/'),
            ),
          );
        }
      }

      expect(configSetArns).toHaveLength(0);
    });
  });
});
