import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as subscriptions from 'aws-cdk-lib/aws-sns-subscriptions';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as ssm from 'aws-cdk-lib/aws-ssm';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface IncidentManagementStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  alarmTopic: sns.Topic;
}

/**
 * Incident Management Stack
 *
 * Implements incident response automation including:
 * - PagerDuty integration (AC: 13)
 * - Runbook automation (AC: 14)
 * - Post-mortem templates (AC: 15)
 * - StatusPage integration (AC: 16)
 *
 * Note: This is a framework implementation. Full integration requires:
 * - PagerDuty API key configuration
 * - StatusPage API key configuration
 * - Runbook scripts implementation
 * - Post-mortem workflow configuration
 */
export class IncidentManagementStack extends cdk.Stack {
  public readonly pagerDutyIntegrationFunction: lambda.Function;
  public readonly runbookAutomationFunction: lambda.Function;
  public readonly postMortemFunction: lambda.Function;
  public readonly statusPageIntegrationFunction: lambda.Function;
  public readonly incidentBucket: s3.Bucket;

  constructor(scope: Construct, id: string, props: IncidentManagementStackProps) {
    super(scope, id, props);

    const isProd = props.config.envName === 'production';
    const envName = props.config.envName;

    // Create stable log groups for all Lambda functions
    const pagerDutyLogGroup = new logs.LogGroup(this, 'PagerDutyLogGroup', {
      logGroupName: `/aws/lambda/BATbern-${envName}/pagerduty-integration`,
      retention: isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    const runbookLogGroup = new logs.LogGroup(this, 'RunbookLogGroup', {
      logGroupName: `/aws/lambda/BATbern-${envName}/runbook-automation`,
      retention: isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    const postMortemLogGroup = new logs.LogGroup(this, 'PostMortemLogGroup', {
      logGroupName: `/aws/lambda/BATbern-${envName}/postmortem-creation`,
      retention: isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    const statusPageLogGroup = new logs.LogGroup(this, 'StatusPageLogGroup', {
      logGroupName: `/aws/lambda/BATbern-${envName}/statuspage-integration`,
      retention: isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // S3 Bucket for storing incident data, post-mortems, and runbook logs
    this.incidentBucket = new s3.Bucket(this, 'IncidentBucket', {
      bucketName: `batbern-${props.config.envName}-incidents`,
      encryption: s3.BucketEncryption.S3_MANAGED,
      removalPolicy: isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: !isProd,
      versioned: true,
      lifecycleRules: [
        {
          id: 'archive-old-incidents',
          transitions: [
            {
              storageClass: s3.StorageClass.INFREQUENT_ACCESS,
              transitionAfter: cdk.Duration.days(90),
            },
            {
              storageClass: s3.StorageClass.GLACIER,
              transitionAfter: cdk.Duration.days(365),
            },
          ],
        },
      ],
    });

    // PagerDuty Integration Lambda (AC: 13)
    this.pagerDutyIntegrationFunction = new lambda.Function(this, 'PagerDutyIntegration', {
      functionName: `batbern-${envName}-pagerduty-integration`,
      runtime: lambda.Runtime.NODEJS_20_X,
      handler: 'index.handler',
      logGroup: pagerDutyLogGroup,
      code: lambda.Code.fromInline(`
        // PagerDuty Integration Lambda
        // TODO: Implement full PagerDuty API integration
        exports.handler = async (event) => {
          console.log('PagerDuty Integration triggered:', JSON.stringify(event, null, 2));

          // Parse SNS message
          const message = JSON.parse(event.Records[0].Sns.Message);

          // TODO: Send to PagerDuty API
          // const pagerDutyApiKey = process.env.PAGERDUTY_API_KEY;
          // const routingKey = process.env.PAGERDUTY_ROUTING_KEY;

          return {
            statusCode: 200,
            body: JSON.stringify({ message: 'PagerDuty integration placeholder' })
          };
        };
      `),
      environment: {
        PAGERDUTY_API_KEY: ssm.StringParameter.valueForStringParameter(
          this,
          `/batbern/${props.config.envName}/pagerduty/api-key`,
          1
        ),
        PAGERDUTY_ROUTING_KEY: ssm.StringParameter.valueForStringParameter(
          this,
          `/batbern/${props.config.envName}/pagerduty/routing-key`,
          1
        ),
        INCIDENT_BUCKET: this.incidentBucket.bucketName,
      },
      timeout: cdk.Duration.seconds(30),
    });

    // Subscribe PagerDuty function to alarm topic
    props.alarmTopic.addSubscription(
      new subscriptions.LambdaSubscription(this.pagerDutyIntegrationFunction)
    );

    // Runbook Automation Lambda (AC: 14)
    this.runbookAutomationFunction = new lambda.Function(this, 'RunbookAutomation', {
      functionName: `batbern-${envName}-runbook-automation`,
      runtime: lambda.Runtime.NODEJS_20_X,
      handler: 'index.handler',
      logGroup: runbookLogGroup,
      code: lambda.Code.fromInline(`
        // Runbook Automation Lambda
        // TODO: Implement runbook execution logic
        exports.handler = async (event) => {
          console.log('Runbook Automation triggered:', JSON.stringify(event, null, 2));

          const message = JSON.parse(event.Records[0].Sns.Message);
          const alarmName = message.AlarmName;

          // TODO: Map alarm to runbook and execute
          // Runbook mappings:
          // - high-cpu -> restart-service
          // - high-memory -> clear-cache
          // - high-disk -> cleanup-logs
          // - database-connections -> scale-connections

          return {
            statusCode: 200,
            body: JSON.stringify({ message: 'Runbook automation placeholder' })
          };
        };
      `),
      environment: {
        INCIDENT_BUCKET: this.incidentBucket.bucketName,
      },
      timeout: cdk.Duration.minutes(5),
    });

    // Subscribe runbook function to alarm topic
    props.alarmTopic.addSubscription(
      new subscriptions.LambdaSubscription(this.runbookAutomationFunction)
    );

    // Post-Mortem Creation Lambda (AC: 15)
    this.postMortemFunction = new lambda.Function(this, 'PostMortemCreation', {
      functionName: `batbern-${envName}-create-postmortem`,
      runtime: lambda.Runtime.NODEJS_20_X,
      handler: 'index.handler',
      logGroup: postMortemLogGroup,
      code: lambda.Code.fromInline(`
        // Post-Mortem Creation Lambda
        // TODO: Implement post-mortem template generation
        exports.handler = async (event) => {
          console.log('Post-Mortem Creation triggered:', JSON.stringify(event, null, 2));

          // Post-mortem template structure:
          // - Incident Summary
          // - Timeline
          // - Root Cause Analysis
          // - Resolution Steps
          // - Action Items
          // - Lessons Learned

          const postMortemTemplate = {
            incidentId: event.incidentId || 'INC-' + Date.now(),
            severity: event.severity || 'medium',
            createdAt: new Date().toISOString(),
            sections: {
              summary: '',
              timeline: [],
              rootCause: '',
              resolution: '',
              actionItems: [],
              lessonsLearned: ''
            }
          };

          // TODO: Save to S3 and notify stakeholders

          return {
            statusCode: 200,
            body: JSON.stringify(postMortemTemplate)
          };
        };
      `),
      environment: {
        INCIDENT_BUCKET: this.incidentBucket.bucketName,
      },
      timeout: cdk.Duration.seconds(30),
    });

    // Grant S3 permissions to Lambda functions
    this.incidentBucket.grantReadWrite(this.pagerDutyIntegrationFunction);
    this.incidentBucket.grantReadWrite(this.runbookAutomationFunction);
    this.incidentBucket.grantReadWrite(this.postMortemFunction);

    // StatusPage Integration Lambda (AC: 16)
    this.statusPageIntegrationFunction = new lambda.Function(this, 'StatusPageIntegration', {
      functionName: `batbern-${envName}-update-statuspage`,
      runtime: lambda.Runtime.NODEJS_20_X,
      handler: 'index.handler',
      logGroup: statusPageLogGroup,
      code: lambda.Code.fromInline(`
        // StatusPage Integration Lambda
        // TODO: Implement StatusPage API integration
        exports.handler = async (event) => {
          console.log('StatusPage Integration triggered:', JSON.stringify(event, null, 2));

          // TODO: Update StatusPage with incident status
          // const statusPageApiKey = process.env.STATUSPAGE_API_KEY;
          // const pageId = process.env.STATUSPAGE_PAGE_ID;

          // Component status levels:
          // - operational
          // - degraded_performance
          // - partial_outage
          // - major_outage

          return {
            statusCode: 200,
            body: JSON.stringify({ message: 'StatusPage integration placeholder' })
          };
        };
      `),
      environment: {
        STATUSPAGE_API_KEY: ssm.StringParameter.valueForStringParameter(
          this,
          `/batbern/${props.config.envName}/statuspage/api-key`,
          1
        ),
        STATUSPAGE_PAGE_ID: ssm.StringParameter.valueForStringParameter(
          this,
          `/batbern/${props.config.envName}/statuspage/page-id`,
          1
        ),
        INCIDENT_BUCKET: this.incidentBucket.bucketName,
      },
      timeout: cdk.Duration.seconds(30),
    });

    // Subscribe StatusPage function to alarm topic for critical alarms only
    props.alarmTopic.addSubscription(
      new subscriptions.LambdaSubscription(this.statusPageIntegrationFunction, {
        filterPolicy: {
          severity: sns.SubscriptionFilter.stringFilter({
            allowlist: ['critical', 'high'],
          }),
        },
      })
    );

    this.incidentBucket.grantReadWrite(this.statusPageIntegrationFunction);

    // Apply tags
    cdk.Tags.of(this).add('Environment', props.config.envName);
    cdk.Tags.of(this).add('Component', 'IncidentManagement');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Outputs
    new cdk.CfnOutput(this, 'IncidentBucketName', {
      value: this.incidentBucket.bucketName,
      description: 'S3 bucket for incident data and post-mortems',
      exportName: `${props.config.envName}-IncidentBucket`,
    });

    new cdk.CfnOutput(this, 'PagerDutyFunctionArn', {
      value: this.pagerDutyIntegrationFunction.functionArn,
      description: 'PagerDuty integration Lambda function ARN',
      exportName: `${props.config.envName}-PagerDutyFunction`,
    });

    new cdk.CfnOutput(this, 'RunbookAutomationFunctionArn', {
      value: this.runbookAutomationFunction.functionArn,
      description: 'Runbook automation Lambda function ARN',
      exportName: `${props.config.envName}-RunbookFunction`,
    });
  }
}
