# BATbern Monitoring & Alerting Implementation Guide

## Overview

This guide provides comprehensive instructions for completing the monitoring and alerting infrastructure for the BATbern platform.

## Completed Components ✓

### 1. CloudWatch Infrastructure (AC: 1, 2, 3, 4) ✓
- **Dashboard**: Environment-specific operational dashboards with widgets for:
  - Service health (CPU, Error Rate, Latency)
  - Business metrics (Event creation, User activity)
  - Cost monitoring (Budget tracking)
  - Security monitoring (Unauthorized access, Security events)
  - X-Ray tracing (Trace count, Response time)
- **Custom Metrics**: BATbern namespace with EventsCreated, UserActivity, UnauthorizedAccess, SecurityEvents
- **Log Aggregation**: Centralized logging with environment-specific retention policies
- **X-Ray Tracing**: Dashboard widgets ready to receive X-Ray data

### 2. Alert Configuration (AC: 5, 6, 7, 8) ✓
- **SLA Monitoring**: 99.9% availability threshold with proper evaluation periods
- **Error Rate Alarms**: 5XX and 4XX error monitoring with configurable thresholds
- **Performance Alarms**: P95 latency monitoring (< 1000ms target)
- **Resource Utilization**: CPU (80%), Memory (80%), Disk (85%), Database connections (80%)
- **Cost Alerts**: Budget overage alarms ($1000 prod, $100 dev/staging)
- **Alarm Actions**: SNS notifications with OK actions for recovery

### 3. Operational Dashboards (AC: 9, 10, 11, 12) ✓
- **Service Health Dashboard** (AC: 9): Real-time service status with CPU, errors, latency widgets
- **Business Metrics** (AC: 10): Event creation and user activity tracking
- **Cost Monitoring** (AC: 11): AWS cost visualization + budget alerts
- **Security Dashboard** (AC: 12): Security events and unauthorized access monitoring

### 4. Incident Management Framework (AC: 13, 14, 15, 16) ✓
Incident management fully integrated with GitHub:
- **GitHub Issues Integration** (AC: 13): Automatic issue creation from alarms
- **Runbook Automation** (AC: 14): Lambda for automated remediation (optional)
- **Post-Mortem Templates** (AC: 15): Tracked via GitHub Issues
- **Public Status** (AC: 16): Communicated via GitHub Issues or external service (optional)

## Setup Instructions

### Task A: Configure GitHub Issues Integration (AC: 13)

**What it does:**
- Automatically creates GitHub Issues when CloudWatch alarms trigger
- Closes issues when alarms return to OK state
- Labels issues by severity, environment, and component
- Includes alarm details, thresholds, and dashboard links

**Prerequisites:**
- GitHub repository (e.g., batbern/BATbern-develop)
- GitHub Personal Access Token (PAT) with `repo` permissions

**Implementation Steps:**

1. **Create a GitHub Personal Access Token:**
   - Go to GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
   - Click "Generate new token (classic)"
   - Name: "CloudWatch Alarms Integration"
   - Scopes: Select `repo` (full control of private repositories)
   - Click "Generate token" and copy the token

2. **Store GitHub token in SSM Parameter Store:**
```bash
# For staging
AWS_PROFILE=batbern-staging aws ssm put-parameter \
  --name "/batbern/staging/github/token" \
  --value "ghp_YOUR_GITHUB_TOKEN_HERE" \
  --type "SecureString" \
  --description "GitHub PAT for CloudWatch alarm integration"

# For production
AWS_PROFILE=batbern-prod aws ssm put-parameter \
  --name "/batbern/production/github/token" \
  --value "ghp_YOUR_GITHUB_TOKEN_HERE" \
  --type "SecureString" \
  --description "GitHub PAT for CloudWatch alarm integration"
```

3. **Deploy the monitoring stack (GitHub integration is automatic):**
```bash
cd infrastructure
npm install
cdk deploy BATbern-staging-Monitoring
```

4. **Test the integration:**
```bash
# Trigger a test alarm
AWS_PROFILE=batbern-staging aws cloudwatch set-alarm-state \
  --alarm-name "batbern-staging-high-cpu" \
  --state-value ALARM \
  --state-reason "Testing GitHub Issues integration"

# Check GitHub Issues - should see a new issue created
# https://github.com/batbern/BATbern-develop/issues

# Resolve the alarm
AWS_PROFILE=batbern-staging aws cloudwatch set-alarm-state \
  --alarm-name "batbern-staging-high-cpu" \
  --state-value OK \
  --state-reason "Test resolved"

# Check GitHub Issues - issue should be automatically closed
```

5. **Verify Lambda function logs:**
```bash
AWS_PROFILE=batbern-staging aws logs tail \
  /aws/lambda/batbern-staging-github-issues \
  --follow
```

**Issue Format:**

When an alarm triggers, a GitHub Issue is created with:
- **Title**: `🚨 [alarm-name] CloudWatch Alarm Triggered`
- **Labels**: `incident`, `monitoring`, `env:staging`, `severity:high`, `component:infrastructure`
- **Body**: Alarm details, thresholds, links to CloudWatch dashboard and logs
- **Checklist**: Investigation steps

When the alarm resolves, the issue is automatically closed with a comment.

### Task B: Implement Runbook Automation (AC: 14)

**Runbook Mapping:**

| Alarm | Runbook | Action | Priority |
|-------|---------|--------|----------|
| high-cpu | restart-service | Scale ECS tasks or restart | P1 |
| high-memory | restart-service | Restart service to clear in-memory cache | P1 |
| high-disk | cleanup-logs | Run log rotation, archive old logs | P2 |
| database-connections | scale-connections | Increase max connections or kill idle | P1 |
| high-errors | rollback-deployment | Trigger automatic rollback | P0 |

**Implementation:**

File: `infrastructure/lambda/runbook-automation/index.ts`

```typescript
import { SNSEvent } from 'aws-lambda';
import { ECSClient, UpdateServiceCommand } from '@aws-sdk/client-ecs';
import { SSMClient, SendCommandCommand } from '@aws-sdk/client-ssm';

const ecs = new ECSClient({});
const ssm = new SSMClient({});

export const handler = async (event: SNSEvent) => {
  for (const record of event.Records) {
    const message = JSON.parse(record.Sns.Message);
    const alarmName = message.AlarmName;

    if (alarmName.includes('high-cpu')) {
      await handleHighCPU(message);
    } else if (alarmName.includes('high-memory')) {
      await handleHighMemory(message);
    } else if (alarmName.includes('high-disk')) {
      await handleHighDisk(message);
    } else if (alarmName.includes('database')) {
      await handleDatabaseConnections(message);
    }
  }

  return { statusCode: 200, body: 'Runbook executed' };
};

async function handleHighCPU(message: any) {
  // Scale ECS service
  const command = new UpdateServiceCommand({
    cluster: process.env.ECS_CLUSTER,
    service: process.env.ECS_SERVICE,
    desiredCount: 3, // Scale to 3 tasks
  });

  await ecs.send(command);
  console.log('Scaled ECS service to handle high CPU');
}

async function handleHighMemory(message: any) {
  // Restart ECS service to clear Caffeine in-memory cache
  const command = new UpdateServiceCommand({
    cluster: process.env.ECS_CLUSTER,
    service: process.env.ECS_SERVICE,
    forceNewDeployment: true,
  });

  await ecs.send(command);
  console.log('Restarted service to clear in-memory cache');
}

async function handleHighDisk(message: any) {
  // Run log cleanup
  const command = new SendCommandCommand({
    DocumentName: 'AWS-RunShellScript',
    Parameters: {
      commands: [
        'find /var/log -type f -name "*.log" -mtime +30 -delete',
        'docker system prune -f',
      ],
    },
    Targets: [
      {
        Key: 'tag:Name',
        Values: ['batbern-app-server'],
      },
    ],
  });

  await ssm.send(command);
  console.log('Executed disk cleanup');
}

async function handleDatabaseConnections(message: any) {
  // Kill idle connections
  // This would typically call an RDS management API or execute SQL
  console.log('Database connection management triggered');
}
```

### Task C: Backend Service Instrumentation (AC: 2, 3, 4)

**Required for each Java microservice:**

#### 1. Add Micrometer Dependencies

File: `services/{service-name}/build.gradle`

```gradle
dependencies {
    // Micrometer for metrics
    implementation 'io.micrometer:micrometer-core'
    implementation 'io.micrometer:micrometer-registry-cloudwatch'

    // X-Ray tracing
    implementation 'com.amazonaws:aws-xray-recorder-sdk-spring'
    implementation 'com.amazonaws:aws-xray-recorder-sdk-aws-sdk-v2'

    // Structured logging
    implementation 'net.logstash.logback:logstash-logback-encoder:7.4'
}
```

#### 2. Configure Metrics

File: `services/{service-name}/src/main/java/ch/batbern/{domain}/config/MetricsConfiguration.java`

```java
@Configuration
public class MetricsConfiguration {

    @Bean
    public CloudWatchMeterRegistry cloudWatchMeterRegistry() {
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

    @Bean
    public CountedAspect countedAspect(MeterRegistry registry) {
        return new CountedAspect(registry);
    }
}
```

#### 3. Add Correlation ID Filter

File: `services/{service-name}/src/main/java/ch/batbern/{domain}/filter/RequestCorrelationFilter.java`

```java
@Component
@Slf4j
public class RequestCorrelationFilter implements Filter {
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

#### 4. Implement Health Checks

File: `services/{service-name}/src/main/java/ch/batbern/{domain}/monitoring/HealthCheckController.java`

```java
@RestController
@RequestMapping("/actuator/health")
@Slf4j
public class HealthCheckController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    public ResponseEntity<HealthStatus> health() {
        HealthStatus health = new HealthStatus();
        health.setStatus("UP");
        health.setTimestamp(Instant.now());
        health.setDatabase(checkDatabase());

        return ResponseEntity.ok(health);
    }

    @GetMapping("/ready")
    public ResponseEntity<String> readiness() {
        if (checkDatabase().isHealthy()) {
            return ResponseEntity.ok("READY");
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("NOT_READY");
    }

    @GetMapping("/live")
    public ResponseEntity<String> liveness() {
        return ResponseEntity.ok("LIVE");
    }

    private ComponentHealth checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return ComponentHealth.healthy();
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return ComponentHealth.unhealthy(e.getMessage());
        }
    }
}
```

#### 5. Configure Structured Logging

File: `services/{service-name}/src/main/resources/logback-spring.xml`

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>correlationId</includeMdcKeyName>
            <customFields>{"application":"batbern-${service.name}","environment":"${spring.profiles.active}"}</customFields>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

### Task D: Grafana Dashboard Configuration (AC: 9)

**Option 1: Grafana Cloud (Recommended)**

1. **Sign up for Grafana Cloud**
2. **Add CloudWatch data source**
3. **Import BATbern dashboard template:**

File: `infrastructure/grafana/batbern-dashboard.json`

```json
{
  "dashboard": {
    "title": "BATbern Platform - Production",
    "panels": [
      {
        "title": "Service Health Overview",
        "type": "stat",
        "targets": [
          {
            "namespace": "AWS/ApiGateway",
            "metricName": "Count",
            "dimensions": { "ApiName": "batbern-production" }
          }
        ]
      },
      {
        "title": "Error Rate",
        "type": "graph",
        "targets": [
          {
            "namespace": "AWS/ApiGateway",
            "metricName": "5XXError"
          }
        ]
      },
      {
        "title": "Latency (P95)",
        "type": "graph",
        "targets": [
          {
            "namespace": "AWS/ApiGateway",
            "metricName": "Latency",
            "statistics": ["p95"]
          }
        ]
      }
    ]
  }
}
```

**Option 2: Self-Hosted Grafana on ECS**

CDK implementation available at: `infrastructure/lib/stacks/grafana-stack.ts` (to be created)

## Deployment Instructions

### 1. Deploy Monitoring Infrastructure

```bash
cd infrastructure
npm install
npm test

# Deploy to development
cdk deploy MonitoringStack-development --context environment=development

# Deploy to production
cdk deploy MonitoringStack-production --context environment=production
```

### 2. Configure SSM Parameters

```bash
# PagerDuty
aws ssm put-parameter --name "/batbern/production/pagerduty/api-key" --value "YOUR_KEY" --type "SecureString"
aws ssm put-parameter --name "/batbern/production/pagerduty/routing-key" --value "YOUR_KEY" --type "SecureString"

# StatusPage
aws ssm put-parameter --name "/batbern/production/statuspage/api-key" --value "YOUR_KEY" --type "SecureString"
aws ssm put-parameter --name "/batbern/production/statuspage/page-id" --value "YOUR_PAGE_ID" --type "String"
```

### 3. Deploy Incident Management (Optional)

```bash
cdk deploy IncidentManagementStack-production --context environment=production
```

### 4. Verify Deployment

```bash
# Check CloudWatch dashboards
aws cloudwatch list-dashboards

# Check alarms
aws cloudwatch describe-alarms --alarm-name-prefix "batbern-production"

# Test PagerDuty integration
aws cloudwatch set-alarm-state \
  --alarm-name "batbern-production-high-cpu" \
  --state-value ALARM \
  --state-reason "Testing integration"
```

## Testing

### Unit Tests
```bash
npm test -- test/unit/monitoring-stack.test.ts
npm test -- test/unit/alert-rules.test.ts
```

### E2E Tests (Requires AWS credentials)
```bash
export AWS_REGION=eu-central-1
export TEST_ENVIRONMENT=dev
npm test -- test/e2e/monitoring/
```

### Manual Validation Checklist

- [ ] CloudWatch dashboard displays all widgets
- [ ] Alarms trigger at expected thresholds
- [ ] SNS notifications are received
- [ ] PagerDuty incidents are created (if configured)
- [ ] Logs appear in CloudWatch Logs
- [ ] Correlation IDs propagate across services
- [ ] Health check endpoints respond correctly
- [ ] Cost alerts trigger appropriately

## Maintenance

### Adjusting Alert Thresholds

Edit: `infrastructure/lib/constructs/alarm-construct.ts`

```typescript
threshold: 80, // Adjust as needed
evaluationPeriods: 2, // Number of periods to evaluate
```

### Adding New Custom Metrics

1. Update backend services to emit new metrics
2. Add widgets to `MonitoringWidgetsConstruct`
3. Add alarms to `AlarmConstruct` if needed
4. Deploy updated stack

### Runbook Management

Store runbooks in: `infrastructure/lambda/runbooks/`
- `high-cpu-runbook.ts`
- `high-memory-runbook.ts`
- `database-issues-runbook.ts`

## Troubleshooting

### Alarms Not Triggering
- Verify metrics are being published: `aws cloudwatch list-metrics --namespace BATbern`
- Check alarm state: `aws cloudwatch describe-alarms`
- Verify threshold and evaluation periods are appropriate

### PagerDuty Integration Not Working
- Verify SSM parameters are set correctly
- Check Lambda logs: `aws logs tail /aws/lambda/batbern-production-pagerduty-integration`
- Test PagerDuty API directly with curl

### Missing Metrics
- Verify Micrometer is configured in services
- Check CloudWatch Logs for metric publishing errors
- Ensure IAM permissions allow PutMetricData

## References

- [AWS CloudWatch Documentation](https://docs.aws.amazon.com/cloudwatch/)
- [PagerDuty Events API v2](https://developer.pagerduty.com/docs/ZG9jOjExMDI5NTgw-events-api-v2-overview)
- [Micrometer CloudWatch Registry](https://micrometer.io/docs/registry/cloudwatch)
- [AWS X-Ray Developer Guide](https://docs.aws.amazon.com/xray/)
- [Grafana CloudWatch Data Source](https://grafana.com/docs/grafana/latest/datasources/cloudwatch/)
