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
- DNS output for Hostpoint configuration

## Environments

| Environment | Frontend URL | Backend URL | Purpose |
|-------------|--------------|-------------|---------|
| Development | http://localhost:3000 | http://localhost:8080 | Local development |
| Staging | https://staging.berner-architekten-treffen.ch | https://api-staging.berner-architekten-treffen.ch | Pre-production testing |
| Production | https://www.berner-architekten-treffen.ch | https://api.berner-architekten-treffen.ch | Live Swiss conference platform |

## Infrastructure as Code with DNS and Certificate Management

### DNS Strategy with External Domain Provider

**Certificate Stack (us-east-1 only):**
```typescript
// Certificate for berner-architekten-treffen.ch domain
const certificate = new Certificate(this, 'BATbernCertificate', {
  domainName: 'www.berner-architekten-treffen.ch',
  subjectAlternativeNames: [
    'dev.berner-architekten-treffen.ch',
    'staging.berner-architekten-treffen.ch',
    'api.berner-architekten-treffen.ch',
    'api-dev.berner-architekten-treffen.ch',
    'api-staging.berner-architekten-treffen.ch'
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
      "www.berner-architekten-treffen.ch": productionCloudFront,
      "staging.berner-architekten-treffen.ch": stagingCloudFront,
      "dev.berner-architekten-treffen.ch": devCloudFront,
      "api.berner-architekten-treffen.ch": productionApiGateway,
      "api-staging.berner-architekten-treffen.ch": stagingApiGateway,
      "api-dev.berner-architekten-treffen.ch": devApiGateway
    }
  }, null, 2),
  description: 'Copy these CNAME records to your Hostpoint DNS configuration'
});
```

### Required CNAME Records in Hostpoint
```
Record Type: CNAME
Name: www
Target: [Output from ProductionWebsiteCNAME]
TTL: 300

Record Type: CNAME
Name: staging
Target: [Output from StagingWebsiteCNAME]
TTL: 300

Record Type: CNAME
Name: api
Target: [Output from ProductionApiCNAME]
TTL: 300

Record Type: CNAME
Name: api-staging
Target: [Output from StagingApiCNAME]
TTL: 300
```

This approach gives you domain control at Hostpoint while maintaining AWS infrastructure flexibility with proper Swiss hosting compliance.

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
      tracingOrigins: ["https://api.berner-architekten-treffen.ch"],
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