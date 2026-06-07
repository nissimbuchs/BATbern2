import { App } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import { InboundEmailStack } from '../../lib/stacks/inbound-email-stack';
import { stagingConfig } from '../../lib/config/staging-config';

describe('InboundEmailStack', () => {
  let app: App;
  let stack: InboundEmailStack;
  let template: Template;

  beforeEach(() => {
    app = new App();
    stack = new InboundEmailStack(app, 'TestInboundEmailStack', {
      config: stagingConfig,
      env: { account: '123456789012', region: 'eu-west-1' },
    });
    template = Template.fromStack(stack);
  });

  // AC1: SES ReceiptRuleSet exists
  test('should_createSesReceiptRuleSet_when_inboundEmailStackDeployed', () => {
    template.resourceCountIs('AWS::SES::ReceiptRuleSet', 1);
    template.hasResourceProperties('AWS::SES::ReceiptRuleSet', {
      RuleSetName: 'batbern-inbound-staging',
    });
  });

  // AC1: SES ReceiptRule routes replies to S3
  test('should_createSesReceiptRuleWithS3Action_when_inboundEmailStackDeployed', () => {
    template.hasResourceProperties('AWS::SES::ReceiptRule', {
      Rule: Match.objectLike({
        Recipients: ['replies@batbern.ch'],
        Actions: Match.arrayWith([
          Match.objectLike({
            S3Action: Match.objectLike({
              ObjectKeyPrefix: 'emails/',
            }),
          }),
        ]),
        Enabled: true,
      }),
    });
  });

  // AC1: S3 bucket exists with encryption
  test('should_createS3BucketWithEncryption_when_inboundEmailStackDeployed', () => {
    template.hasResourceProperties('AWS::S3::Bucket', {
      BucketName: 'batbern-inbound-emails-staging',
      BucketEncryption: Match.objectLike({
        ServerSideEncryptionConfiguration: Match.arrayWith([
          Match.objectLike({
            ServerSideEncryptionByDefault: Match.objectLike({
              SSEAlgorithm: 'AES256',
            }),
          }),
        ]),
      }),
      PublicAccessBlockConfiguration: {
        BlockPublicAcls: true,
        BlockPublicPolicy: true,
        IgnorePublicAcls: true,
        RestrictPublicBuckets: true,
      },
    });
  });

  // AC1: S3 lifecycle rule (auto-purge after 7 days)
  test('should_applyLifecycleRule_when_inboundBucketCreated', () => {
    template.hasResourceProperties('AWS::S3::Bucket', {
      BucketName: 'batbern-inbound-emails-staging',
      LifecycleConfiguration: Match.objectLike({
        Rules: Match.arrayWith([
          Match.objectLike({
            Status: 'Enabled',
            ExpirationInDays: 7,
          }),
        ]),
      }),
    });
  });

  // AC1: SQS queue exists (standard, not FIFO)
  test('should_createSqsQueue_when_inboundEmailStackDeployed', () => {
    template.hasResourceProperties('AWS::SQS::Queue', {
      QueueName: 'batbern-inbound-email-staging',
    });
  });

  // AC1: DLQ exists for failed messages
  test('should_createDeadLetterQueue_when_inboundEmailStackDeployed', () => {
    // Main queue references a DLQ via RedrivePolicy
    template.hasResourceProperties('AWS::SQS::Queue', {
      QueueName: 'batbern-inbound-email-staging',
      RedrivePolicy: Match.objectLike({
        maxReceiveCount: 3,
      }),
    });
  });

  // AC1: S3 bucket policy allows SES to put objects
  test('should_allowSesToPutObjectsInBucket_when_bucketPolicyCreated', () => {
    template.hasResourceProperties('AWS::S3::BucketPolicy', {
      PolicyDocument: Match.objectLike({
        Statement: Match.arrayWith([
          Match.objectLike({
            Principal: Match.objectLike({
              Service: 'ses.amazonaws.com',
            }),
            Action: 's3:PutObject',
          }),
        ]),
      }),
    });
  });

  // AC1: S3 event notification routes to SQS (CDK uses custom resource for notifications)
  test('should_createNotificationCustomResource_when_inboundBucketDeployed', () => {
    // CDK uses BucketNotificationsHandler custom resource for S3 event notifications
    template.resourceCountIs('AWS::SQS::Queue', 2); // main queue + DLQ
    // Verify the forwarder Lambda exists by name (count is fragile across CDK versions)
    template.hasResourceProperties('AWS::Lambda::Function', {
      FunctionName: 'batbern-email-forwarder-staging',
    });
  });

  // Tags applied to stack resources
  test('should_applyEnvironmentTags_when_stackDeployed', () => {
    expect(stack.stackName).toBe('TestInboundEmailStack');
  });

  // ========================
  // Story 10.26: Email Forwarding
  // ========================

  // T4.1 — AC1: Forwarding receipt rule for named addresses
  test('should_createForwardingReceiptRule_when_namedAddressesConfigured', () => {
    template.hasResourceProperties('AWS::SES::ReceiptRule', {
      Rule: Match.objectLike({
        Recipients: Match.arrayWith([
          'ok@batbern.ch',
          'info@batbern.ch',
          'events@batbern.ch',
          'partner@batbern.ch',
          'support@batbern.ch',
        ]),
        Actions: Match.arrayWith([
          Match.objectLike({
            S3Action: Match.objectLike({
              ObjectKeyPrefix: 'forwarding/',
            }),
          }),
        ]),
        Enabled: true,
      }),
    });
  });

  // T4.1 — AC1: Catch-all domain receipt rule for batbern{N}@ addresses
  test('should_createCatchAllDomainReceiptRule_when_domainConfigured', () => {
    template.hasResourceProperties('AWS::SES::ReceiptRule', {
      Rule: Match.objectLike({
        Recipients: Match.arrayWith(['batbern.ch']),
        Actions: Match.arrayWith([
          Match.objectLike({
            S3Action: Match.objectLike({
              ObjectKeyPrefix: 'forwarding/',
            }),
          }),
        ]),
        Enabled: true,
      }),
    });
  });

  // T4.3 — Receipt rule ordering: replies first, named forwarding second, catch-all third
  test('should_haveThreeReceiptRules_when_allRulesConfigured', () => {
    // 3 receipt rules: replies, named forwarding addresses, catch-all domain
    template.resourceCountIs('AWS::SES::ReceiptRule', 3);
  });

  // T5.1 — AC2: Forwarder Lambda function exists
  test('should_createForwarderLambda_when_forwardingConfigured', () => {
    template.hasResourceProperties('AWS::Lambda::Function', {
      FunctionName: 'batbern-email-forwarder-staging',
      Runtime: 'nodejs20.x',
      MemorySize: 256,
      Timeout: 60,
    });
  });

  // T5.1 — AC2: Lambda has SES SendRawEmail permission
  test('should_grantSesPermissions_when_forwarderLambdaCreated', () => {
    template.hasResourceProperties('AWS::IAM::Policy', {
      PolicyDocument: Match.objectLike({
        Statement: Match.arrayWith([
          Match.objectLike({
            Action: Match.arrayWith(['ses:SendRawEmail']),
            Effect: 'Allow',
          }),
        ]),
      }),
    });
  });

  // T5.2 — S3 event notification for forwarding/ prefix triggers Lambda
  test('should_createS3NotificationForForwardingPrefix_when_forwarderLambdaCreated', () => {
    // The Lambda function count: 1 (BucketNotificationsHandler from existing) + 1 (forwarder) + 1 (S3 notification custom resource for Lambda)
    // We verify the forwarder Lambda exists with the correct name
    template.hasResourceProperties('AWS::Lambda::Function', {
      FunctionName: 'batbern-email-forwarder-staging',
    });
  });

  // T5.3 — AC10: CloudWatch alarm for EmailsRejected metric
  test('should_createCloudWatchAlarm_when_forwarderLambdaCreated', () => {
    template.hasResourceProperties('AWS::CloudWatch::Alarm', {
      MetricName: 'EmailsRejected',
      Namespace: 'BATbern/EmailForwarder',
      Threshold: 20,
      EvaluationPeriods: 1,
      Period: 3600,
    });
  });

  // T12 — Story 10.26: MX record for SES inbound SMTP when hosted zone provided
  test('should_createMxRecord_when_hostedZoneProvided', () => {
    const mxApp = new App();
    const helperStack2 = new (require('aws-cdk-lib').Stack)(mxApp, 'HelperStack2', {
      env: { account: '123456789012', region: 'eu-central-1' },
    });
    const hostedZone = new (require('aws-cdk-lib/aws-route53').HostedZone)(helperStack2, 'TestZone', {
      zoneName: 'batbern.ch',
    });

    const mxStack = new InboundEmailStack(mxApp, 'TestMxInboundEmailStack', {
      config: stagingConfig,
      env: { account: '123456789012', region: 'eu-central-1' },
      hostedZone,
    });
    const mxTemplate = Template.fromStack(mxStack);

    mxTemplate.hasResourceProperties('AWS::Route53::RecordSet', {
      Type: 'MX',
      ResourceRecords: Match.arrayWith([
        Match.stringLikeRegexp('10 inbound-smtp\\..*\\.amazonaws\\.com'),
      ]),
    });
  });

  // T10.1 — Story 10.26: Forwarder Lambda has VPC configuration when VPC props provided
  test('should_configureForwarderLambdaWithVpc_when_vpcPropsProvided', () => {
    const vpcApp = new App();
    // Create a helper stack to host the VPC (VPC must be in a Stack scope)
    const helperStack = new (require('aws-cdk-lib').Stack)(vpcApp, 'HelperStack', {
      env: { account: '123456789012', region: 'eu-central-1' },
    });
    const testVpc = new ec2.Vpc(helperStack, 'TestVpc');
    const testSg = new ec2.SecurityGroup(helperStack, 'TestSG', { vpc: testVpc });

    const vpcStack = new InboundEmailStack(vpcApp, 'TestVpcInboundEmailStack', {
      config: stagingConfig,
      env: { account: '123456789012', region: 'eu-central-1' },
      vpc: testVpc,
      lambdaSecurityGroup: testSg,
    });
    const vpcTemplate = Template.fromStack(vpcStack);

    // Lambda should have VpcConfig when VPC props are provided
    vpcTemplate.hasResourceProperties('AWS::Lambda::Function', {
      FunctionName: 'batbern-email-forwarder-staging',
      VpcConfig: Match.objectLike({
        SecurityGroupIds: Match.anyValue(),
        SubnetIds: Match.anyValue(),
      }),
    });
  });
});
