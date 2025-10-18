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

### 4. Incident Management Framework (AC: 13, 14, 15, 16) ⚠️ Partial
Framework created with Lambda function placeholders for:
- **PagerDuty Integration** (AC: 13): Lambda subscribed to alarm topic
- **Runbook Automation** (AC: 14): Lambda for automated remediation
- **Post-Mortem Templates** (AC: 15): Lambda for incident documentation
- **StatusPage Integration** (AC: 16): Lambda for public status updates

## Pending Implementation

### Task A: Complete PagerDuty Integration (AC: 13)

**Prerequisites:**
- PagerDuty account with API access
- Service integration key
- On-call schedule configured

**Implementation Steps:**

1. **Store PagerDuty credentials in SSM Parameter Store:**
```bash
aws ssm put-parameter \
  --name "/batbern/production/pagerduty/api-key" \
  --value "YOUR_PAGERDUTY_API_KEY" \
  --type "SecureString"

aws ssm put-parameter \
  --name "/batbern/production/pagerduty/routing-key" \
  --value "YOUR_ROUTING_KEY" \
  --type "SecureString"
```

2. **Update Lambda function code:**

File: `infrastructure/lambda/pagerduty-integration/index.ts`

```typescript
import { SNSEvent } from 'aws-lambda';
import axios from 'axios';

interface CloudWatchAlarm {
  AlarmName: string;
  NewStateValue: string;
  NewStateReason: string;
  StateChangeTime: string;
}

export const handler = async (event: SNSEvent) => {
  const pagerDutyApiKey = process.env.PAGERDUTY_API_KEY!;
  const routingKey = process.env.PAGERDUTY_ROUTING_KEY!;

  for (const record of event.Records) {
    const message: CloudWatchAlarm = JSON.parse(record.Sns.Message);

    const pagerDutyEvent = {
      routing_key: routingKey,
      event_action: message.NewStateValue === 'ALARM' ? 'trigger' : 'resolve',
      dedup_key: message.AlarmName,
      payload: {
        summary: `${message.AlarmName}: ${message.NewStateReason}`,
        severity: getSeverity(message.AlarmName),
        timestamp: message.StateChangeTime,
        source: 'AWS CloudWatch',
        component: getComponent(message.AlarmName),
        custom_details: message,
      },
    };

    await axios.post('https://events.pagerduty.com/v2/enqueue', pagerDutyEvent, {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Token token=${pagerDutyApiKey}`,
      },
    });
  }

  return { statusCode: 200, body: 'Success' };
};

function getSeverity(alarmName: string): string {
  if (alarmName.includes('availability') || alarmName.includes('error')) {
    return 'critical';
  }
  if (alarmName.includes('latency') || alarmName.includes('cpu')) {
    return 'warning';
  }
  return 'info';
}

function getComponent(alarmName: string): string {
  if (alarmName.includes('database')) return 'database';
  if (alarmName.includes('api')) return 'api-gateway';
  return 'infrastructure';
}
```

3. **Deploy with proper dependencies:**
```bash
cd infrastructure
npm install axios @types/aws-lambda
cdk deploy IncidentManagementStack-production
```

4. **Test integration:**
```bash
# Trigger test alarm
aws cloudwatch set-alarm-state \
  --alarm-name "batbern-production-high-cpu" \
  --state-value ALARM \
  --state-reason "Testing PagerDuty integration"
```

### Task B: Implement Runbook Automation (AC: 14)

**Runbook Mapping:**

| Alarm | Runbook | Action | Priority |
|-------|---------|--------|----------|
| high-cpu | restart-service | Scale ECS tasks or restart | P1 |
| high-memory | clear-cache | Clear Redis cache, restart if needed | P1 |
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
  // Clear Redis cache
  const command = new SendCommandCommand({
    DocumentName: 'AWS-RunShellScript',
    Parameters: {
      commands: ['redis-cli FLUSHALL'],
    },
    Targets: [
      {
        Key: 'tag:Name',
        Values: ['batbern-redis'],
      },
    ],
  });

  await ssm.send(command);
  console.log('Cleared Redis cache');
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
