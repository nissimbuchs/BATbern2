# Infrastructure & Deployment

## Deployment Strategy

**Frontend Deployment:**
- **Platform:** AWS CloudFront + S3
- **Build Command:** `npm run build`
- **Output Directory:** `dist/`
- **CDN/Edge:** CloudFront with edge locations for global distribution

**Backend Deployment:**
- **Platform:** AWS ECS Fargate with Application Load Balancer
- **Build Command:** `./gradlew bootBuildImage`
- **Deployment Method:** Blue/Green deployment with health checks

## CI/CD Pipeline

**Daily Build Pipeline:**
- Shared kernel build and test
- Domain services parallel build
- Frontend build and test
- Integration tests
- Deployment to staging (on main branch)

**Production Deployment:**
- Manual approval required
- Blue/Green deployment
- Health checks and monitoring
- Route53 DNS management with AWS integration

## Environments

| Environment | Frontend URL | Backend URL | Purpose |
|-------------|--------------|-------------|---------|
| Development | http://localhost:3000 | http://localhost:8080 | Local development |
| Staging | https://staging.batbern.ch | https://api-staging.batbern.ch | Pre-production testing |
| Production | https://www.batbern.ch | https://api.batbern.ch | Live Swiss conference platform |

## Infrastructure as Code with DNS and Certificate Management

### DNS Strategy with AWS Route53

**Certificate Stack (us-east-1 only):**
```typescript
// Certificate for batbern.ch domain
const certificate = new Certificate(this, 'BATbernCertificate', {
  domainName: 'www.batbern.ch',
  subjectAlternativeNames: [
    'dev.batbern.ch',
    'staging.batbern.ch',
    'api.batbern.ch',
    'api-dev.batbern.ch',
    'api-staging.batbern.ch'
  ],
  validation: CertificateValidation.fromDns(), // Manual DNS validation
});
```

**DNS Output Stack for Hostpoint Configuration:**
```typescript
// Output CNAME records for Hostpoint DNS configuration
new CfnOutput(this, 'HostpointCNAMERecords', {
  value: JSON.stringify({
    "CNAME Records for Hostpoint DNS": {
      "www.batbern.ch": productionCloudFront,
      "staging.batbern.ch": stagingCloudFront,
      "dev.batbern.ch": devCloudFront,
      "api.batbern.ch": productionApiGateway,
      "api-staging.batbern.ch": stagingApiGateway,
      "api-dev.batbern.ch": devApiGateway
    }
  }, null, 2),
  description: 'Route53 DNS records automatically configured'
});

// Route53 Hosted Zone for batbern.ch
const hostedZone = new HostedZone(this, 'BATbernHostedZone', {
  zoneName: 'batbern.ch',
  comment: 'BATbern Event Management Platform hosted zone'
});

// DNS Records - automatically configured with Route53
new ARecord(this, 'ProductionARecord', {
  zone: hostedZone,
  recordName: 'www',
  target: RecordTarget.fromAlias(new CloudFrontTarget(productionCloudFront))
});

new ARecord(this, 'StagingARecord', {
  zone: hostedZone,
  recordName: 'staging',
  target: RecordTarget.fromAlias(new CloudFrontTarget(stagingCloudFront))
});

new ARecord(this, 'DevARecord', {
  zone: hostedZone,
  recordName: 'dev',
  target: RecordTarget.fromAlias(new CloudFrontTarget(devCloudFront))
});

new ARecord(this, 'ProductionApiARecord', {
  zone: hostedZone,
  recordName: 'api',
  target: RecordTarget.fromAlias(new ApiGatewayDomain(productionApiGateway))
});

new ARecord(this, 'StagingApiARecord', {
  zone: hostedZone,
  recordName: 'api-staging',
  target: RecordTarget.fromAlias(new ApiGatewayDomain(stagingApiGateway))
});

new ARecord(this, 'DevApiARecord', {
  zone: hostedZone,
  recordName: 'api-dev',
  target: RecordTarget.fromAlias(new ApiGatewayDomain(devApiGateway))
});
```

### AWS Route53 Configuration Benefits
- **Automatic DNS management** - No manual CNAME configuration required
- **Health checks and failover** - Built-in monitoring and automatic failover
- **SSL certificate validation** - Automatic domain validation for Let's Encrypt
- **Global DNS resolution** - AWS Route53 global network for fast DNS resolution
- **Cost optimization** - Eliminate external DNS provider costs

This approach provides full AWS infrastructure integration with native Route53 DNS management, eliminating external dependencies while maintaining proper Swiss hosting compliance.

## AWS SES Email Infrastructure

### SES Configuration Architecture

The BATbern platform uses AWS Simple Email Service (SES) for all transactional and marketing email delivery with comprehensive tracking and deliverability monitoring.

#### Domain Verification and Authentication

```typescript
// CDK Stack for SES Email Infrastructure
import * as ses from 'aws-cdk-lib/aws-ses';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';

export class EmailInfrastructureStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: StackProps) {
    super(scope, id, props);

    // Import existing Route53 hosted zone
    const hostedZone = route53.HostedZone.fromLookup(this, 'BATbernZone', {
      domainName: 'batbern.ch',
    });

    // SES Domain Identity with DKIM
    const emailIdentity = new ses.EmailIdentity(this, 'BATbernEmailIdentity', {
      identity: ses.Identity.publicHostedZone(hostedZone),
      mailFromDomain: 'mail.batbern.ch',
      dkimSigning: true,
    });

    // Configuration Set for tracking and reputation management
    const configSet = new ses.ConfigurationSet(this, 'BATbernConfigSet', {
      configurationSetName: 'batbern-notifications',
      reputationMetrics: true,
      sendingEnabled: true,
      customTrackingRedirectDomain: 'track.batbern.ch',
    });

    // SNS Topics for bounce and complaint handling
    const bouncesTopic = new sns.Topic(this, 'EmailBounces', {
      displayName: 'BATbern Email Bounces',
    });

    const complaintsTopic = new sns.Topic(this, 'EmailComplaints', {
      displayName: 'BATbern Email Complaints',
    });

    // Event destinations for bounce/complaint handling
    configSet.addEventDestination('BounceEvents', {
      destination: ses.EventDestination.snsTopic(bouncesTopic),
      events: [ses.EmailSendingEvent.BOUNCE, ses.EmailSendingEvent.REJECT],
    });

    configSet.addEventDestination('ComplaintEvents', {
      destination: ses.EventDestination.snsTopic(complaintsTopic),
      events: [ses.EmailSendingEvent.COMPLAINT],
    });

    configSet.addEventDestination('DeliveryEvents', {
      destination: ses.EventDestination.snsTopic(new sns.Topic(this, 'EmailDeliveries')),
      events: [ses.EmailSendingEvent.DELIVERY],
    });

    // CloudWatch Alarms for delivery monitoring
    new cloudwatch.Alarm(this, 'HighBounceRate', {
      metric: new cloudwatch.Metric({
        namespace: 'AWS/SES',
        metricName: 'Reputation.BounceRate',
        statistic: 'Average',
        period: cdk.Duration.hours(1),
      }),
      threshold: 0.05, // 5% bounce rate
      evaluationPeriods: 1,
      alarmDescription: 'Email bounce rate exceeds 5% threshold',
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });

    new cloudwatch.Alarm(this, 'HighComplaintRate', {
      metric: new cloudwatch.Metric({
        namespace: 'AWS/SES',
        metricName: 'Reputation.ComplaintRate',
        statistic: 'Average',
        period: cdk.Duration.hours(1),
      }),
      threshold: 0.001, // 0.1% complaint rate
      evaluationPeriods: 1,
      alarmDescription: 'Email complaint rate exceeds 0.1% threshold',
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });

    // Daily sending quota alarm
    new cloudwatch.Alarm(this, 'SendingQuotaExceeded', {
      metric: new cloudwatch.Metric({
        namespace: 'AWS/SES',
        metricName: 'Send',
        statistic: 'Sum',
        period: cdk.Duration.hours(24),
      }),
      threshold: 45000, // 90% of 50,000 daily quota
      evaluationPeriods: 1,
      alarmDescription: 'Daily sending approaching quota limit',
    });
  }
}
```

#### SES Template Management Service

```java
package ch.batbern.notification.service;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SESTemplateService {
    private final AmazonSimpleEmailService sesClient;
    private final EmailTemplateRepository templateRepository;

    public SESTemplateService(AmazonSimpleEmailService sesClient,
                              EmailTemplateRepository templateRepository) {
        this.sesClient = sesClient;
        this.templateRepository = templateRepository;
    }

    @Transactional
    public void syncTemplateToSES(EmailTemplate template) {
        String sesTemplateName = generateSESTemplateName(template);

        Template sesTemplate = new Template()
            .withTemplateName(sesTemplateName)
            .withSubjectPart(template.getSubject())
            .withHtmlPart(template.getHtmlBody())
            .withTextPart(template.getTextBody());

        try {
            // Try to update existing template first
            UpdateTemplateRequest updateRequest = new UpdateTemplateRequest()
                .withTemplate(sesTemplate);
            sesClient.updateTemplate(updateRequest);

        } catch (TemplateDoesNotExistException e) {
            // Template doesn't exist, create it
            CreateTemplateRequest createRequest = new CreateTemplateRequest()
                .withTemplate(sesTemplate);
            sesClient.createTemplate(createRequest);
        }
    }

    public void sendTemplatedEmail(String templateId, String recipient,
                                    Map<String, String> templateData) {
        EmailTemplate template = templateRepository.findById(UUID.fromString(templateId))
            .orElseThrow(() -> new TemplateNotFoundException(templateId));

        String sesTemplateName = generateSESTemplateName(template);

        SendTemplatedEmailRequest request = new SendTemplatedEmailRequest()
            .withSource("noreply@batbern.ch")
            .withDestination(new Destination().withToAddresses(recipient))
            .withTemplate(sesTemplateName)
            .withTemplateData(new ObjectMapper().writeValueAsString(templateData))
            .withConfigurationSetName("batbern-notifications");

        SendTemplatedEmailResult result = sesClient.sendTemplatedEmail(request);

        // Log notification with SES Message ID
        logNotification(recipient, templateId, result.getMessageId());
    }

    private String generateSESTemplateName(EmailTemplate template) {
        return String.format("%s_%s_v%d",
            template.getTemplateType(),
            template.getLanguage(),
            template.getVersion());
    }
}
```

#### Bounce and Complaint Handling

```java
package ch.batbern.notification.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import org.springframework.stereotype.Component;

@Component
public class SESBounceHandler implements RequestHandler<SNSEvent, Void> {

    private final NotificationLogRepository logRepository;
    private final UserService userService;

    @Override
    public Void handleRequest(SNSEvent event, Context context) {
        for (SNSEvent.SNSRecord record : event.getRecords()) {
            String message = record.getSNS().getMessage();
            SESNotification notification = parseNotification(message);

            if ("Bounce".equals(notification.getNotificationType())) {
                handleBounce(notification.getBounce());
            } else if ("Complaint".equals(notification.getNotificationType())) {
                handleComplaint(notification.getComplaint());
            }
        }
        return null;
    }

    private void handleBounce(Bounce bounce) {
        for (BouncedRecipient recipient : bounce.getBouncedRecipients()) {
            // Update notification log
            logRepository.updateDeliveryStatus(
                bounce.getFeedbackId(),
                DeliveryStatus.BOUNCED,
                recipient.getDiagnosticCode()
            );

            // If hard bounce, disable email notifications for user
            if ("Permanent".equals(bounce.getBounceType())) {
                userService.disableEmailNotifications(recipient.getEmailAddress());
            }
        }
    }

    private void handleComplaint(Complaint complaint) {
        for (ComplainedRecipient recipient : complaint.getComplainedRecipients()) {
            // Update notification log
            logRepository.updateDeliveryStatus(
                complaint.getFeedbackId(),
                DeliveryStatus.COMPLAINED,
                "User marked email as spam"
            );

            // Automatically unsubscribe user from all marketing emails
            userService.unsubscribeFromMarketing(recipient.getEmailAddress());
        }
    }
}
```

### Production Configuration

#### Environment Variables
```bash
# SES Configuration
AWS_SES_REGION=eu-central-1
AWS_SES_CONFIGURATION_SET=batbern-notifications
AWS_SES_FROM_ADDRESS=noreply@batbern.ch
AWS_SES_REPLY_TO_ADDRESS=info@batbern.ch
AWS_SES_DAILY_QUOTA=50000
AWS_SES_RATE_LIMIT=14  # emails per second

# Monitoring
AWS_SES_BOUNCE_TOPIC_ARN=arn:aws:sns:eu-central-1:ACCOUNT:EmailBounces
AWS_SES_COMPLAINT_TOPIC_ARN=arn:aws:sns:eu-central-1:ACCOUNT:EmailComplaints
```

#### Deliverability Best Practices
1. **Warm-up Period**: Gradually increase sending volume over 2-4 weeks
2. **List Hygiene**: Remove bounced addresses immediately, validate emails at registration
3. **Engagement Tracking**: Monitor open/click rates, reduce frequency for low-engagement users
4. **Double Opt-in**: Require confirmation for newsletter subscriptions
5. **Unsubscribe Links**: One-click unsubscribe in all marketing emails
6. **Content Quality**: Avoid spam trigger words, maintain text/HTML balance
7. **Authentication**: DKIM, SPF, DMARC properly configured
8. **Reputation Monitoring**: Stay below 5% bounce rate and 0.1% complaint rate

## AWS S3 Content Storage Infrastructure

### S3 Bucket Architecture with CDK

The BATbern platform uses AWS S3 for scalable, durable content storage across multiple content types with CloudFront CDN integration for global delivery.

#### CDK Stack for Content Storage

```typescript
// infrastructure/lib/content-storage-stack.ts
import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as sns from 'aws-cdk-lib/aws-sns';
import { Construct } from 'constructs';

export interface ContentStorageStackProps extends cdk.StackProps {
  environment: string; // 'development' | 'staging' | 'production'
}

export class ContentStorageStack extends cdk.Stack {
  public readonly presentationsBucket: s3.Bucket;
  public readonly logosBucket: s3.Bucket;
  public readonly profilesBucket: s3.Bucket;
  public readonly archivesBucket: s3.Bucket;
  public readonly contentDistribution: cloudfront.Distribution;

  constructor(scope: Construct, id: string, props: ContentStorageStackProps) {
    super(scope, id, props);

    const { environment } = props;

    // Create content buckets with consistent configuration
    this.presentationsBucket = this.createContentBucket('presentations', environment, {
      maxSizeBytes: 100 * 1024 * 1024, // 100 MB
      lifecycleTransitionDays: 365,
    });

    this.logosBucket = this.createContentBucket('logos', environment, {
      maxSizeBytes: 5 * 1024 * 1024, // 5 MB
      lifecycleTransitionDays: 730, // 2 years
    });

    this.profilesBucket = this.createContentBucket('profiles', environment, {
      maxSizeBytes: 10 * 1024 * 1024, // 10 MB
      lifecycleTransitionDays: 365,
    });

    this.archivesBucket = this.createContentBucket('archives', environment, {
      maxSizeBytes: 20 * 1024 * 1024, // 20 MB per file
      lifecycleTransitionDays: 365,
    });

    // CloudFront Origin Access Identity for secure S3 access
    const originAccessIdentity = new cloudfront.OriginAccessIdentity(this, 'ContentOAI', {
      comment: `OAI for BATbern ${environment} content delivery`,
    });

    // Grant CloudFront read access to buckets
    [this.presentationsBucket, this.logosBucket, this.profilesBucket, this.archivesBucket]
      .forEach(bucket => {
        bucket.grantRead(originAccessIdentity);
      });

    // Lambda@Edge for image optimization
    const imageOptimizationFunction = new cloudfront.experimental.EdgeFunction(
      this,
      'ImageOptimization',
      {
        runtime: lambda.Runtime.NODEJS_18_X,
        handler: 'index.handler',
        code: lambda.Code.fromAsset('lambda/image-optimization'),
        memorySize: 512,
        timeout: cdk.Duration.seconds(5),
      }
    );

    // CloudFront distribution for content delivery
    this.contentDistribution = new cloudfront.Distribution(this, 'ContentDistribution', {
      comment: `BATbern ${environment} Content CDN`,
      defaultBehavior: {
        origin: new origins.S3Origin(this.presentationsBucket, {
          originAccessIdentity,
        }),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD_OPTIONS,
        cachedMethods: cloudfront.CachedMethods.CACHE_GET_HEAD_OPTIONS,
        compress: true,
        cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
        responseHeadersPolicy: this.createSecurityHeadersPolicy(),
      },
      additionalBehaviors: {
        '/logos/*': {
          origin: new origins.S3Origin(this.logosBucket, { originAccessIdentity }),
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          compress: true,
          cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
          edgeLambdas: [{
            functionVersion: imageOptimizationFunction.currentVersion,
            eventType: cloudfront.LambdaEdgeEventType.ORIGIN_RESPONSE,
          }],
        },
        '/profiles/*': {
          origin: new origins.S3Origin(this.profilesBucket, { originAccessIdentity }),
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          compress: true,
          cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
          edgeLambdas: [{
            functionVersion: imageOptimizationFunction.currentVersion,
            eventType: cloudfront.LambdaEdgeEventType.ORIGIN_RESPONSE,
          }],
        },
        '/archives/*': {
          origin: new origins.S3Origin(this.archivesBucket, { originAccessIdentity }),
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          compress: true,
          cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
        },
      },
      priceClass: cloudfront.PriceClass.PRICE_CLASS_100, // Europe + US
      enableLogging: true,
      logBucket: new s3.Bucket(this, 'CDNLogBucket', {
        bucketName: `batbern-${environment}-cdn-logs`,
        encryption: s3.BucketEncryption.S3_MANAGED,
        lifecycleRules: [{
          expiration: cdk.Duration.days(90),
        }],
        blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      }),
      certificate: this.getCertificate(environment),
      domainNames: this.getDomainNames(environment),
    });

    // Cross-region replication for production
    if (environment === 'production') {
      this.setupCrossRegionReplication(this.presentationsBucket, 'eu-central-1');
      this.setupCrossRegionReplication(this.logosBucket, 'eu-central-1');
      this.setupCrossRegionReplication(this.profilesBucket, 'eu-central-1');
      this.setupCrossRegionReplication(this.archivesBucket, 'eu-central-1');
    }

    // CloudWatch alarms for storage monitoring
    this.createStorageAlarms(environment);

    // Outputs
    new cdk.CfnOutput(this, 'CDNDomain', {
      value: this.contentDistribution.distributionDomainName,
      description: 'CloudFront CDN domain for content delivery',
      exportName: `${environment}-cdn-domain`,
    });

    new cdk.CfnOutput(this, 'PresentationsBucketName', {
      value: this.presentationsBucket.bucketName,
      exportName: `${environment}-presentations-bucket`,
    });

    new cdk.CfnOutput(this, 'LogosBucketName', {
      value: this.logosBucket.bucketName,
      exportName: `${environment}-logos-bucket`,
    });

    new cdk.CfnOutput(this, 'ProfilesBucketName', {
      value: this.profilesBucket.bucketName,
      exportName: `${environment}-profiles-bucket`,
    });

    new cdk.CfnOutput(this, 'ArchivesBucketName', {
      value: this.archivesBucket.bucketName,
      exportName: `${environment}-archives-bucket`,
    });
  }

  private createContentBucket(
    contentType: string,
    environment: string,
    config: { maxSizeBytes: number; lifecycleTransitionDays: number }
  ): s3.Bucket {
    const bucket = new s3.Bucket(this, `${contentType}Bucket`, {
      bucketName: `batbern-${environment}-${contentType}`,
      encryption: s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      versioned: true,
      lifecycleRules: [
        {
          id: 'TransitionToIA',
          enabled: true,
          transitions: [{
            storageClass: s3.StorageClass.INFREQUENT_ACCESS,
            transitionAfter: cdk.Duration.days(config.lifecycleTransitionDays),
          }],
        },
        {
          id: 'TransitionToGlacier',
          enabled: true,
          transitions: [{
            storageClass: s3.StorageClass.GLACIER,
            transitionAfter: cdk.Duration.days(config.lifecycleTransitionDays + 730), // +2 years
          }],
        },
        {
          id: 'CleanupIncompleteUploads',
          enabled: true,
          abortIncompleteMultipartUploadAfter: cdk.Duration.days(7),
        },
        {
          id: 'DeleteOldVersions',
          enabled: true,
          noncurrentVersionExpiration: cdk.Duration.days(30),
        },
      ],
      cors: [{
        allowedMethods: [s3.HttpMethods.GET, s3.HttpMethods.PUT, s3.HttpMethods.POST],
        allowedOrigins: this.getCorsOrigins(environment),
        allowedHeaders: ['*'],
        exposedHeaders: ['ETag', 'x-amz-version-id'],
        maxAge: 3000,
      }],
    });

    // Enable S3 inventory for disaster recovery
    bucket.addInventory({
      inventoryId: `${contentType}-inventory`,
      frequency: s3.InventoryFrequency.DAILY,
      destination: {
        bucket,
        prefix: 'inventory/',
      },
      includeObjectVersions: s3.InventoryObjectVersion.CURRENT,
      optionalFields: [
        'Size',
        'LastModifiedDate',
        'ETag',
        'StorageClass',
      ],
    });

    // Add bucket metrics for monitoring
    bucket.addMetric({
      id: `${contentType}-all-requests`,
    });

    return bucket;
  }

  private createSecurityHeadersPolicy(): cloudfront.ResponseHeadersPolicy {
    return new cloudfront.ResponseHeadersPolicy(this, 'SecurityHeaders', {
      securityHeadersBehavior: {
        contentTypeOptions: { override: true },
        frameOptions: {
          frameOption: cloudfront.HeadersFrameOption.DENY,
          override: true,
        },
        referrerPolicy: {
          referrerPolicy: cloudfront.HeadersReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN,
          override: true,
        },
        strictTransportSecurity: {
          accessControlMaxAge: cdk.Duration.days(365),
          includeSubdomains: true,
          override: true,
        },
        xssProtection: {
          protection: true,
          modeBlock: true,
          override: true,
        },
      },
      customHeadersBehavior: {
        customHeaders: [{
          header: 'Cache-Control',
          value: 'public, max-age=604800, immutable',
          override: false,
        }],
      },
    });
  }

  private setupCrossRegionReplication(bucket: s3.Bucket, targetRegion: string): void {
    // Create replication destination bucket in secondary region
    const replicationBucket = new s3.Bucket(this, `${bucket.node.id}Replica`, {
      bucketName: `${bucket.bucketName}-replica`,
      encryption: s3.BucketEncryption.S3_MANAGED,
      versioned: true,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
    });

    // IAM role for replication
    const replicationRole = new iam.Role(this, `${bucket.node.id}ReplicationRole`, {
      assumedBy: new iam.ServicePrincipal('s3.amazonaws.com'),
      description: `Replication role for ${bucket.bucketName}`,
    });

    bucket.grantRead(replicationRole);
    replicationBucket.grantWrite(replicationRole);

    // Configure replication rule
    const cfnBucket = bucket.node.defaultChild as s3.CfnBucket;
    cfnBucket.replicationConfiguration = {
      role: replicationRole.roleArn,
      rules: [{
        id: 'ReplicateAll',
        status: 'Enabled',
        priority: 1,
        filter: {},
        destination: {
          bucket: replicationBucket.bucketArn,
          replicationTime: {
            status: 'Enabled',
            time: { minutes: 15 },
          },
          metrics: {
            status: 'Enabled',
            eventThreshold: { minutes: 15 },
          },
          storageClass: 'STANDARD_IA',
        },
      }],
    };
  }

  private createStorageAlarms(environment: string): void {
    const alarmTopic = new sns.Topic(this, 'StorageAlarmTopic', {
      displayName: `BATbern ${environment} Storage Alarms`,
    });

    // Alarm for high storage usage on presentations bucket
    new cloudwatch.Alarm(this, 'HighPresentationsStorage', {
      metric: this.presentationsBucket.metricBucketSizeBytes({
        statistic: 'Average',
        period: cdk.Duration.days(1),
      }),
      threshold: 100 * 1024 * 1024 * 1024, // 100 GB
      evaluationPeriods: 1,
      alarmDescription: 'Presentations bucket exceeds 100GB storage',
      actionsEnabled: true,
    }).addAlarmAction(new cloudwatch.SnsAction(alarmTopic));

    // Alarm for high error rate
    new cloudwatch.Alarm(this, 'High4xxErrorRate', {
      metric: new cloudwatch.Metric({
        namespace: 'AWS/S3',
        metricName: '4xxErrors',
        statistic: 'Sum',
        period: cdk.Duration.minutes(5),
        dimensionsMap: {
          BucketName: this.presentationsBucket.bucketName,
        },
      }),
      threshold: 100,
      evaluationPeriods: 2,
      alarmDescription: 'High rate of 4xx errors on S3 requests',
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    }).addAlarmAction(new cloudwatch.SnsAction(alarmTopic));

    // Alarm for replication lag (production only)
    if (environment === 'production') {
      new cloudwatch.Alarm(this, 'ReplicationLag', {
        metric: new cloudwatch.Metric({
          namespace: 'AWS/S3',
          metricName: 'ReplicationLatency',
          statistic: 'Maximum',
          period: cdk.Duration.minutes(5),
          dimensionsMap: {
            SourceBucket: this.presentationsBucket.bucketName,
          },
        }),
        threshold: 3600, // 1 hour in seconds
        evaluationPeriods: 1,
        alarmDescription: 'S3 replication lag exceeds 1 hour',
        treatMissingData: cloudwatch.TreatMissingData.BREACHING,
      }).addAlarmAction(new cloudwatch.SnsAction(alarmTopic));
    }
  }

  private getCorsOrigins(environment: string): string[] {
    switch (environment) {
      case 'production':
        return ['https://www.batbern.ch', 'https://api.batbern.ch'];
      case 'staging':
        return ['https://staging.batbern.ch', 'https://api-staging.batbern.ch'];
      case 'development':
        return ['https://dev.batbern.ch', 'https://api-dev.batbern.ch', 'http://localhost:3000'];
      default:
        return ['http://localhost:3000'];
    }
  }

  private getCertificate(environment: string): cloudfront.ICertificate {
    // Import existing certificate from us-east-1 (required for CloudFront)
    // Certificate should be created in a separate stack in us-east-1
    return cloudfront.Certificate.fromCertificateArn(
      this,
      'CDNCertificate',
      `arn:aws:acm:us-east-1:ACCOUNT:certificate/${environment}-cdn-cert-id`
    );
  }

  private getDomainNames(environment: string): string[] {
    switch (environment) {
      case 'production':
        return ['cdn.batbern.ch'];
      case 'staging':
        return ['cdn-staging.batbern.ch'];
      case 'development':
        return ['cdn-dev.batbern.ch'];
      default:
        return [];
    }
  }
}
```

#### Lambda@Edge Image Optimization

```javascript
// lambda/image-optimization/index.js
const AWS = require('aws-sdk');
const sharp = require('sharp');

exports.handler = async (event) => {
  const { request, response } = event.Records[0].cf;

  // Only process image files
  const isImage = /\.(jpg|jpeg|png|webp)$/i.test(request.uri);
  if (!isImage) {
    return response;
  }

  // Parse query string for transformations
  const params = new URLSearchParams(request.querystring);
  const width = parseInt(params.get('w')) || null;
  const height = parseInt(params.get('h')) || null;
  const quality = parseInt(params.get('q')) || 80;
  const format = params.get('f') || 'auto';

  // If no transformations requested, return original
  if (!width && !height && format === 'auto') {
    return response;
  }

  try {
    // Get original image from S3
    const s3 = new AWS.S3();
    const bucket = response.headers['x-amz-bucket'][0].value;
    const key = request.uri.substring(1); // Remove leading /

    const s3Object = await s3.getObject({
      Bucket: bucket,
      Key: key,
    }).promise();

    // Transform image
    let transformer = sharp(s3Object.Body);

    if (width || height) {
      transformer = transformer.resize(width, height, {
        fit: 'inside',
        withoutEnlargement: true,
      });
    }

    // Format conversion
    if (format === 'webp' || (format === 'auto' && request.headers['accept']?.[0]?.value?.includes('image/webp'))) {
      transformer = transformer.webp({ quality });
      response.headers['content-type'] = [{ key: 'Content-Type', value: 'image/webp' }];
    } else if (format === 'avif') {
      transformer = transformer.avif({ quality });
      response.headers['content-type'] = [{ key: 'Content-Type', value: 'image/avif' }];
    } else {
      transformer = transformer.jpeg({ quality });
    }

    const transformedImage = await transformer.toBuffer();

    // Update response
    response.status = 200;
    response.body = transformedImage.toString('base64');
    response.bodyEncoding = 'base64';
    response.headers['cache-control'] = [{ key: 'Cache-Control', value: 'public, max-age=31536000, immutable' }];

    return response;
  } catch (error) {
    console.error('Image transformation error:', error);
    return response; // Return original on error
  }
};
```

### File Upload Service Implementation

```java
package ch.batbern.content.service;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ContentStorageService {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ContentMetadataRepository metadataRepository;
    private final StorageQuotaService quotaService;

    @Value("${aws.s3.buckets.presentations}")
    private String presentationsBucket;

    @Value("${aws.s3.buckets.logos}")
    private String logosBucket;

    @Value("${aws.s3.buckets.profiles}")
    private String profilesBucket;

    @Value("${aws.s3.buckets.archives}")
    private String archivesBucket;

    @Value("${aws.cloudfront.domain}")
    private String cloudFrontDomain;

    public ContentStorageService(
        S3Client s3Client,
        S3Presigner s3Presigner,
        ContentMetadataRepository metadataRepository,
        StorageQuotaService quotaService
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.metadataRepository = metadataRepository;
        this.quotaService = quotaService;
    }

    @Transactional
    public PresignedUploadUrl generatePresignedUploadUrl(
            String userId,
            ContentType contentType,
            String filename,
            long fileSizeBytes,
            String mimeType) {

        // Validate file size against content type limits
        validateFileSize(contentType, fileSizeBytes);

        // Check storage quota
        quotaService.validateQuota(userId, contentType, fileSizeBytes);

        // Generate unique S3 key
        String s3Key = generateS3Key(contentType, userId, filename);
        String bucket = getBucketForContentType(contentType);

        // Generate unique file ID
        String fileId = UUID.randomUUID().toString();

        // Create presigned PUT URL (15 minute expiration)
        PutObjectRequest objectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(s3Key)
            .contentType(mimeType)
            .metadata(Map.of(
                "file-id", fileId,
                "uploaded-by", userId,
                "original-filename", filename,
                "upload-timestamp", Instant.now().toString()
            ))
            .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(15))
            .putObjectRequest(objectRequest)
            .build();

        PresignedPutObjectRequest presignedRequest =
            s3Presigner.presignPutObject(presignRequest);

        // Store metadata in database
        ContentMetadata metadata = new ContentMetadata();
        metadata.setFileId(fileId);
        metadata.setS3Key(s3Key);
        metadata.setS3Bucket(bucket);
        metadata.setOriginalFilename(filename);
        metadata.setFileSizeBytes(fileSizeBytes);
        metadata.setMimeType(mimeType);
        metadata.setUploadedBy(userId);
        metadata.setContentType(contentType);
        metadata.setUploadStatus(UploadStatus.PENDING);
        metadata.setCreatedAt(Instant.now());
        metadataRepository.save(metadata);

        return new PresignedUploadUrl(
            presignedRequest.url().toString(),
            fileId,
            Duration.ofMinutes(15).toSeconds(),
            Map.of("Content-Type", mimeType)
        );
    }

    @Transactional
    public ContentMetadata confirmUpload(String fileId, String checksum) {
        ContentMetadata metadata = metadataRepository.findByFileId(fileId)
            .orElseThrow(() -> new FileNotFoundException("File not found: " + fileId));

        // Verify file exists in S3
        HeadObjectRequest headRequest = HeadObjectRequest.builder()
            .bucket(metadata.getS3Bucket())
            .key(metadata.getS3Key())
            .build();

        try {
            HeadObjectResponse response = s3Client.headObject(headRequest);

            // Verify checksum if provided
            if (checksum != null && !response.eTag().equals(checksum)) {
                throw new ChecksumMismatchException("Checksum verification failed");
            }

            // Update metadata
            metadata.setUploadStatus(UploadStatus.COMPLETED);
            metadata.setChecksum(response.eTag());
            metadata.setUploadedAt(Instant.now());

            // Generate CloudFront URL
            String cloudFrontUrl = String.format("https://%s/%s/%s",
                cloudFrontDomain,
                metadata.getContentType().getFolderName(),
                metadata.getS3Key().substring(metadata.getS3Key().lastIndexOf('/') + 1)
            );
            metadata.setCloudFrontUrl(cloudFrontUrl);

            metadataRepository.save(metadata);

            // Update user quota
            quotaService.incrementUsage(
                metadata.getUploadedBy(),
                metadata.getFileSizeBytes()
            );

            return metadata;
        } catch (S3Exception e) {
            metadata.setUploadStatus(UploadStatus.FAILED);
            metadataRepository.save(metadata);
            throw new FileUploadException("Failed to verify upload: " + e.getMessage());
        }
    }

    public PresignedDownloadUrl generatePresignedDownloadUrl(String fileId) {
        ContentMetadata metadata = metadataRepository.findByFileId(fileId)
            .orElseThrow(() -> new FileNotFoundException("File not found: " + fileId));

        if (metadata.getUploadStatus() != UploadStatus.COMPLETED) {
            throw new IllegalStateException("File upload not completed");
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(metadata.getS3Bucket())
            .key(metadata.getS3Key())
            .responseContentDisposition("attachment; filename=\"" + metadata.getOriginalFilename() + "\"")
            .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(15))
            .getObjectRequest(getObjectRequest)
            .build();

        PresignedGetObjectRequest presignedRequest =
            s3Presigner.presignGetObject(presignRequest);

        return new PresignedDownloadUrl(
            presignedRequest.url().toString(),
            metadata.getOriginalFilename(),
            metadata.getFileSizeBytes(),
            metadata.getMimeType(),
            Duration.ofMinutes(15).toSeconds()
        );
    }

    @Transactional
    public void deleteFile(String fileId, String userId) {
        ContentMetadata metadata = metadataRepository.findByFileId(fileId)
            .orElseThrow(() -> new FileNotFoundException("File not found: " + fileId));

        // Verify ownership
        if (!metadata.getUploadedBy().equals(userId)) {
            throw new UnauthorizedException("Not authorized to delete this file");
        }

        // Delete from S3
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
            .bucket(metadata.getS3Bucket())
            .key(metadata.getS3Key())
            .build();

        s3Client.deleteObject(deleteRequest);

        // Update quota
        quotaService.decrementUsage(userId, metadata.getFileSizeBytes());

        // Mark as deleted in database (soft delete for audit trail)
        metadata.setUploadStatus(UploadStatus.DELETED);
        metadata.setUpdatedAt(Instant.now());
        metadataRepository.save(metadata);
    }

    private String generateS3Key(ContentType contentType, String userId, String filename) {
        int year = java.time.Year.now().getValue();
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = filename.substring(filename.lastIndexOf('.'));
        String sanitizedFilename = sanitizeFilename(
            filename.substring(0, filename.lastIndexOf('.'))
        );

        return String.format("%s/%d/%s/%s-%s%s",
            contentType.getFolderName(),
            year,
            userId,
            sanitizedFilename,
            uuid,
            extension
        );
    }

    private String sanitizeFilename(String filename) {
        return filename
            .replaceAll("[^a-zA-Z0-9.-]", "-")
            .replaceAll("-+", "-")
            .toLowerCase();
    }

    private String getBucketForContentType(ContentType contentType) {
        return switch (contentType) {
            case PRESENTATION -> presentationsBucket;
            case LOGO -> logosBucket;
            case SPEAKER_PHOTO, SPEAKER_CV -> profilesBucket;
            case EVENT_PHOTO, ARCHIVE_MATERIAL -> archivesBucket;
        };
    }

    private void validateFileSize(ContentType contentType, long fileSizeBytes) {
        long maxSize = switch (contentType) {
            case LOGO -> 5 * 1024 * 1024; // 5 MB
            case SPEAKER_CV -> 5 * 1024 * 1024; // 5 MB
            case SPEAKER_PHOTO -> 10 * 1024 * 1024; // 10 MB
            case EVENT_PHOTO -> 20 * 1024 * 1024; // 20 MB
            case PRESENTATION -> 100 * 1024 * 1024; // 100 MB
            case ARCHIVE_MATERIAL -> 20 * 1024 * 1024; // 20 MB
        };

        if (fileSizeBytes > maxSize) {
            throw new FileSizeExceededException(
                String.format("File size %d bytes exceeds maximum %d bytes for %s",
                    fileSizeBytes, maxSize, contentType)
            );
        }
    }
}
```

### Storage Quota Service

```java
package ch.batbern.content.service;

import ch.batbern.shared.model.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class StorageQuotaService {
    private final StorageQuotaRepository quotaRepository;
    private final StorageUsageLogRepository usageLogRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    private static final Map<UserRole, Long> QUOTA_LIMITS = Map.of(
        UserRole.ORGANIZER, -1L, // Unlimited
        UserRole.SPEAKER, 200L * 1024 * 1024, // 200 MB
        UserRole.PARTNER, 50L * 1024 * 1024,  // 50 MB
        UserRole.ATTENDEE, 10L * 1024 * 1024   // 10 MB
    );

    private static final double SOFT_LIMIT_THRESHOLD = 0.8; // 80%

    public StorageQuotaService(
        StorageQuotaRepository quotaRepository,
        StorageUsageLogRepository usageLogRepository,
        UserService userService,
        NotificationService notificationService
    ) {
        this.quotaRepository = quotaRepository;
        this.usageLogRepository = usageLogRepository;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public void validateQuota(String userId, ContentType contentType, long fileSizeBytes) {
        UserRole role = userService.getUserRole(userId);
        long quotaLimit = QUOTA_LIMITS.get(role);

        // Organizers have unlimited storage
        if (quotaLimit == -1L) {
            return;
        }

        StorageQuota quota = quotaRepository.findByUserId(userId)
            .orElseGet(() -> createDefaultQuota(userId, role));

        long newUsage = quota.getCurrentUsageBytes() + fileSizeBytes;

        if (newUsage > quotaLimit) {
            throw new StorageQuotaExceededException(
                String.format("Storage quota exceeded. Limit: %d MB, Current: %d MB, Requested: %d MB",
                    quotaLimit / 1024 / 1024,
                    quota.getCurrentUsageBytes() / 1024 / 1024,
                    fileSizeBytes / 1024 / 1024
                )
            );
        }

        // Soft limit warning at 80%
        double currentPercentage = (double) quota.getCurrentUsageBytes() / quotaLimit;
        double newPercentage = (double) newUsage / quotaLimit;

        if (newPercentage >= SOFT_LIMIT_THRESHOLD && currentPercentage < SOFT_LIMIT_THRESHOLD) {
            notificationService.sendQuotaWarning(
                userId,
                quota.getCurrentUsageBytes(),
                quotaLimit
            );

            quota.setQuotaWarningIssued(true);
            quotaRepository.save(quota);
        }
    }

    @Transactional
    public void incrementUsage(String userId, long fileSizeBytes) {
        StorageQuota quota = quotaRepository.findByUserId(userId)
            .orElseThrow(() -> new QuotaNotFoundException("Quota not found for user: " + userId));

        long newUsage = quota.getCurrentUsageBytes() + fileSizeBytes;
        quota.setCurrentUsageBytes(newUsage);
        quota.setFileCount(quota.getFileCount() + 1);
        quota.setLastUpdated(Instant.now());

        quotaRepository.save(quota);

        // Log usage change
        StorageUsageLog log = new StorageUsageLog();
        log.setUserId(userId);
        log.setAction("upload");
        log.setFileSizeBytes(fileSizeBytes);
        log.setNewTotalUsageBytes(newUsage);
        log.setTimestamp(Instant.now());
        usageLogRepository.save(log);
    }

    @Transactional
    public void decrementUsage(String userId, long fileSizeBytes) {
        StorageQuota quota = quotaRepository.findByUserId(userId)
            .orElseThrow(() -> new QuotaNotFoundException("Quota not found for user: " + userId));

        long newUsage = Math.max(0, quota.getCurrentUsageBytes() - fileSizeBytes);
        quota.setCurrentUsageBytes(newUsage);
        quota.setFileCount(Math.max(0, quota.getFileCount() - 1));
        quota.setLastUpdated(Instant.now());

        // Reset warning flag if usage drops below threshold
        UserRole role = userService.getUserRole(userId);
        long quotaLimit = QUOTA_LIMITS.get(role);
        if (quotaLimit > 0 && newUsage < (quotaLimit * SOFT_LIMIT_THRESHOLD)) {
            quota.setQuotaWarningIssued(false);
        }

        quotaRepository.save(quota);

        // Log usage change
        StorageUsageLog log = new StorageUsageLog();
        log.setUserId(userId);
        log.setAction("delete");
        log.setFileSizeBytes(fileSizeBytes);
        log.setNewTotalUsageBytes(newUsage);
        log.setTimestamp(Instant.now());
        usageLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public StorageQuotaInfo getQuotaInfo(String userId) {
        UserRole role = userService.getUserRole(userId);
        long quotaLimit = QUOTA_LIMITS.get(role);

        StorageQuota quota = quotaRepository.findByUserId(userId)
            .orElseGet(() -> createDefaultQuota(userId, role));

        double percentageUsed = quotaLimit > 0
            ? (double) quota.getCurrentUsageBytes() / quotaLimit * 100
            : 0.0;

        return new StorageQuotaInfo(
            quotaLimit,
            quota.getCurrentUsageBytes(),
            quota.getFileCount(),
            percentageUsed,
            SOFT_LIMIT_THRESHOLD * 100,
            Math.max(0, quotaLimit - quota.getCurrentUsageBytes())
        );
    }

    private StorageQuota createDefaultQuota(String userId, UserRole role) {
        StorageQuota quota = new StorageQuota();
        quota.setUserId(userId);
        quota.setUserRole(role);
        quota.setQuotaLimitBytes(QUOTA_LIMITS.get(role));
        quota.setCurrentUsageBytes(0L);
        quota.setFileCount(0);
        quota.setQuotaWarningIssued(false);
        quota.setLastUpdated(Instant.now());
        return quotaRepository.save(quota);
    }
}
```

### CloudFront CDN Configuration

The CloudFront distribution provides global content delivery with edge caching and security:

**Key Features:**
- **Origin Access Identity (OAI)**: Secure S3 access without public bucket policies
- **Lambda@Edge**: Image optimization and resizing at edge locations
- **HTTPS Only**: Automatic HTTP to HTTPS redirection
- **Compression**: Automatic gzip/brotli compression
- **Custom Domain**: `cdn.batbern.ch` with SSL certificate
- **Logging**: Comprehensive access logs for analytics

**Performance Optimizations:**
- **Cache TTL**: 7 days for static content (presentations, logos)
- **Edge Locations**: AWS global network (100+ locations in Europe & US via PRICE_CLASS_100)
- **HTTP/2**: Multiplexing for reduced latency
- **Query String Forwarding**: Support for image transformation parameters (`?w=800&h=600&q=80&f=webp`)

**Security Headers Applied:**
```
Content-Security-Policy: default-src 'self'
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Referrer-Policy: strict-origin-when-cross-origin
```

### Backup and Disaster Recovery

**Cross-Region Replication:**
- Production buckets replicate to secondary region (eu-central-1)
- Replication time: < 15 minutes for 99.99% of objects
- Automatic failover via CloudFront multi-origin configuration
- Storage class optimization (STANDARD_IA for replicas)

**Versioning Strategy:**
- S3 versioning enabled on all buckets
- Noncurrent versions retained for 30 days
- Allows rollback to previous file versions
- Protects against accidental deletion

**Monitoring and Alerts:**
```yaml
# CloudWatch Alarms for S3 Storage
Alarms:
  HighStorageUsage:
    Metric: BucketSizeBytes
    Threshold: 100 GB
    Period: 1 day
    Action: SNS notification to ops team

  ReplicationLag:
    Metric: ReplicationLatency
    Threshold: 1 hour
    Period: 5 minutes
    Action: PagerDuty alert (production only)

  HighErrorRate:
    Metric: 4xxErrors
    Threshold: 100 errors per 5 minutes
    Period: 2 evaluation periods
    Action: Slack #dev-alerts

  HighCDNRequestRate:
    Metric: CloudFront Requests
    Threshold: 10,000 requests/minute
    Period: 5 minutes
    Action: SNS notification (potential DDoS or viral content)
```

**Recovery Procedures:**
- **RTO (Recovery Time Objective)**: 4 hours for full content restoration
- **RPO (Recovery Point Objective)**: 1 hour maximum data loss
- **Runbooks**: Documented restoration procedures in `docs/runbooks/s3-recovery.md`
- **Testing**: Quarterly disaster recovery drills

**Data Integrity:**
- SHA-256 checksums stored in database for all files
- Monthly automated integrity checks comparing S3 ETags with database checksums
- Orphan detection: Identify S3 objects without database references
- Orphan cleanup: Scheduled Lambda function to remove orphaned objects after 90 days

## Monitoring and Observability Strategy

### Comprehensive Monitoring Stack

**Infrastructure Monitoring:**
- **AWS CloudWatch**: Native AWS service metrics, custom metrics, log aggregation
- **Grafana Cloud**: Advanced visualization, cross-service dashboards, alerting
- **AWS X-Ray**: Distributed tracing across microservices and API Gateway
- **Datadog APM**: Application performance monitoring with code-level insights

**Frontend Monitoring:**
```typescript
// Error Tracking Configuration
import * as Sentry from "@sentry/react";

Sentry.init({
  dsn: process.env.REACT_APP_SENTRY_DSN,
  environment: process.env.NODE_ENV,
  integrations: [
    new Sentry.BrowserTracing({
      tracingOrigins: ["https://api.batbern.ch"],
      routingInstrumentation: Sentry.reactRouterV6Instrumentation(
        React.useEffect,
        useLocation,
        useNavigationType,
        createRoutesFromChildren,
        matchRoutes
      ),
    }),
  ],
  tracesSampleRate: 0.1,
  beforeSend: (event) => {
    // Filter out sensitive data
    if (event.user) {
      delete event.user.email;
    }
    return event;
  },
});
```

**Backend Monitoring:**
```java
// Custom Metrics Configuration (Micrometer + CloudWatch)
@Configuration
@EnableConfigurationProperties(MetricsProperties.class)
public class MetricsConfiguration {

    @Bean
    public MeterRegistry meterRegistry() {
        return CloudWatchMeterRegistry.builder(CloudWatchConfig.DEFAULT)
            .cloudWatchClient(CloudWatchAsyncClient.create())
            .namespace("BATbern")
            .step(Duration.ofMinutes(1))
            .build();
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}

// Service-level metrics
@Service
@Timed(value = "speaker.coordination.service", description = "Speaker coordination service operations")
public class SpeakerCoordinationService {

    private final Counter invitationsSent = Counter.builder("invitations.sent")
        .description("Total invitations sent")
        .tag("service", "speaker-coordination")
        .register(Metrics.globalRegistry);

    @Timed(value = "speaker.invitation.send", description = "Time to send invitation")
    public void sendInvitation(SpeakerInvitation invitation) {
        // Business logic
        invitationsSent.increment();
    }
}
```

### Detailed Metrics and SLAs

**Frontend Performance Metrics:**

| Metric | Target | Critical Threshold | Monitoring Tool | Alert Channel |
|--------|--------|-------------------|-----------------|---------------|
| First Contentful Paint (FCP) | < 1.5s | > 3s | Web Vitals API | Slack #dev-alerts |
| Largest Contentful Paint (LCP) | < 2.5s | > 4s | Core Web Vitals | PagerDuty |
| Cumulative Layout Shift (CLS) | < 0.1 | > 0.25 | Real User Monitoring | Email |
| First Input Delay (FID) | < 100ms | > 300ms | Performance Observer | Slack #dev-alerts |
| JavaScript Error Rate | < 0.1% | > 1% | Sentry | PagerDuty |
| Bundle Size | < 250KB | > 500KB | Webpack Bundle Analyzer | Email |

**Backend Service Metrics:**

| Service | Response Time (P95) | Error Rate | Throughput | Availability |
|---------|-------------------|------------|------------|--------------|
| API Gateway | < 200ms | < 0.1% | 1000 req/min | 99.9% |
| Event Management | < 150ms | < 0.5% | 500 req/min | 99.5% |
| Speaker Coordination | < 100ms | < 0.2% | 200 req/min | 99.5% |
| Partner Analytics | < 300ms | < 0.1% | 100 req/min | 99.0% |
| Attendee Experience | < 200ms | < 0.1% | 2000 req/min | 99.9% |

**Infrastructure Metrics:**

```yaml
# CloudWatch Custom Dashboard Configuration
Dashboards:
  BATbernPlatformOverview:
    Widgets:
      - Type: "metric"
        Properties:
          metrics:
            - ["AWS/ECS", "CPUUtilization", "ServiceName", "event-management"]
            - ["AWS/ECS", "MemoryUtilization", "ServiceName", "event-management"]
          period: 300
          stat: "Average"
          region: "eu-central-1"
          title: "Event Management Service Health"

      - Type: "log"
        Properties:
          query: |
            SOURCE '/aws/ecs/event-management'
            | fields @timestamp, @message
            | filter @message like /ERROR/
            | sort @timestamp desc
            | limit 20
          region: "eu-central-1"
          title: "Recent Errors"
```

### Alerting and Incident Response

**Alert Hierarchy:**
1. **P0 - Critical**: Service completely down, data loss risk
2. **P1 - High**: Major feature unavailable, performance severely degraded
3. **P2 - Medium**: Minor feature issues, performance warnings
4. **P3 - Low**: Monitoring issues, capacity planning alerts

**Alert Rules Configuration:**
```yaml
# Grafana Alert Rules
groups:
  - name: batbern-critical-alerts
    rules:
      - alert: ServiceDown
        expr: up{job="batbern-services"} == 0
        for: 1m
        severity: P0
        annotations:
          summary: "Service {{ $labels.service }} is down"
          description: "Service has been down for more than 1 minute"

      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.01
        for: 2m
        severity: P1
        annotations:
          summary: "High error rate on {{ $labels.service }}"

      - alert: DatabaseConnectionPoolExhausted
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 3m
        severity: P1
        annotations:
          summary: "Database connection pool nearly exhausted"
```

### Logging Strategy

**Structured Logging Configuration:**
```xml
<!-- logback-spring.xml -->
<configuration>
    <springProfile name="!local">
        <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
                <providers>
                    <timestamp/>
                    <logLevel/>
                    <loggerName/>
                    <mdc/>
                    <arguments/>
                    <message/>
                    <stackTrace/>
                </providers>
            </encoder>
        </appender>
    </springProfile>

    <logger name="ch.batbern" level="INFO"/>
    <logger name="org.springframework.web" level="DEBUG"/>
    <logger name="org.springframework.security" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

**Log Correlation:**
```java
@Component
@Slf4j
public class RequestCorrelationFilter implements Filter {
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        String correlationId = ((HttpServletRequest) request).getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put("correlationId", correlationId);
        ((HttpServletResponse) response).setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

### Business Metrics and KPIs

**Event Management KPIs:**
- Event creation to publication time (Target: < 14 days)
- Speaker response rate (Target: > 80%)
- Automated workflow completion rate (Target: > 95%)

**Partner Analytics KPIs:**
- Dashboard engagement rate (Target: > 60% monthly active users)
- ROI report generation time (Target: < 30 seconds)
- Data accuracy score (Target: > 99%)

**Attendee Experience KPIs:**
- Content discovery conversion rate (Target: > 15%)
- Search result relevance score (Target: > 85%)
- Mobile performance score (Target: > 90%)

### Health Checks and Monitoring Endpoints

```java
@RestController
@RequestMapping("/actuator/health")
public class HealthController {

    @Autowired
    private DatabaseHealthIndicator databaseHealth;

    @Autowired
    private RedisHealthIndicator redisHealth;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("database", databaseHealth.health());
        health.put("cache", redisHealth.health());
        health.put("timestamp", Instant.now());

        return ResponseEntity.ok(health);
    }

    @GetMapping("/ready")
    public ResponseEntity<String> readiness() {
        // Check if service can handle requests
        return ResponseEntity.ok("READY");
    }

    @GetMapping("/live")
    public ResponseEntity<String> liveness() {
        // Check if service should be restarted
        return ResponseEntity.ok("LIVE");
    }
}
```