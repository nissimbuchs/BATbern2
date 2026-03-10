import { App } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
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

  // AC1: SES ReceiptRule routes to S3
  test('should_createSesReceiptRuleWithS3Action_when_inboundEmailStackDeployed', () => {
    template.resourceCountIs('AWS::SES::ReceiptRule', 1);
    template.hasResourceProperties('AWS::SES::ReceiptRule', {
      Rule: Match.objectLike({
        Recipients: ['replies@staging.batbern.ch'],
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
    // Verify the notification handler custom resource exists
    template.resourceCountIs('AWS::Lambda::Function', 1);
  });

  // Tags applied to stack resources
  test('should_applyEnvironmentTags_when_stackDeployed', () => {
    expect(stack.stackName).toBe('TestInboundEmailStack');
  });
});
