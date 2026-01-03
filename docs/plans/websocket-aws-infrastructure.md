# WebSocket Infrastructure Implementation Plan

**Story**: BAT-7 - Notifications API Consolidation
**Feature**: Real-time WebSocket Push Notifications
**Date**: 2026-01-02
**Updated**: 2026-01-03
**Status**: ⏸️ DEFERRED - Using Polling-Based Approach (Option 1)
**Decision**: Stay with REST API polling, no WebSocket gateway deployment for now

## Executive Summary

WebSocket push notifications are **fully implemented and working** in local development. However, AWS staging/production environments require additional infrastructure because:

- **Local Development**: WebSocket client connects directly to event-management-service (port BASE_PORT+2)
- **AWS Staging/Production**: WebSocket client needs to connect through AWS API Gateway infrastructure

**DECISION (2026-01-03)**: **Stay with Option 1** - Polling-based notifications via REST API. No WebSocket gateway deployment for now.

**Rationale**:
- ✅ App fully functional with REST API polling (1-minute refresh)
- ✅ Zero additional cost vs. $43-50/month for WebSocket infrastructure
- ✅ Graceful degradation confirmed - no errors or crashes without gateway
- ✅ WebSocket infrastructure can be deployed later if real-time becomes critical

This plan remains as **reference documentation** for future WebSocket gateway deployment if needed.

## Current State

### ✅ Local Development (Working)

**Architecture**:
```
Client (Browser)
  ↓ WebSocket (ws://localhost:8002/ws)
Event Management Service (ECS/Native)
  ↓ STOMP over SockJS
Notification Listeners
```

**Implementation**:
- ✅ Backend: `WebSocketConfig.java` - STOMP over WebSocket configuration
- ✅ Backend: `SecurityConfig.java` - `/ws/**` endpoint permitted without JWT
- ✅ Backend: `NotificationService.java` - Push notifications via WebSocket
- ✅ Frontend: `notificationWebSocketClient.ts` - Singleton WebSocket client
- ✅ Frontend: `useNotificationWebSocket.ts` - React hook for components
- ✅ Frontend: Dynamic port calculation (BASE_PORT+2)

**Tested**: All features working in local development.

### ❌ AWS Staging/Production (Not Implemented)

**Current Infrastructure**:
```
Client → AWS HTTP API Gateway → API Gateway Service (ALB) → Microservices (Service Connect)
```

**Problem**:
- AWS HTTP API Gateway (`apigatewayv2.HttpApi`) does NOT support WebSocket protocol
- Event Management Service uses Service Connect (internal VPC DNS), no public endpoint
- WebSocket requires persistent connections, different from HTTP request/response

**Current Behavior in Staging/Production (Verified 2026-01-03)**:
- Frontend attempts WebSocket connection to `wss://api.staging.batbern.ch/ws`
- Connection fails (endpoint doesn't support WebSocket upgrade)
- `ConnectionState` → `ERROR` or `DISCONNECTED`
- `isConnected` → `false`
- Real-time subscription skipped (`if (!isConnected) return;`)
- **Fallback to REST API polling** via `useNotifications` hook (1-minute refresh)
- ✅ **No application errors or crashes** - graceful degradation confirmed

---

## Graceful Degradation Analysis (2026-01-03)

### Two-Tier Notification Architecture

The application has **TWO independent notification systems** that work together:

1. **WebSocket (Real-time)** - Story BAT-7
   - Push notifications via WebSocket
   - Instant updates without polling
   - **Optional** - enhances UX but not required

2. **REST API (Polling)** - Story 1.17
   - Traditional HTTP polling via React Query
   - 1-minute stale time (auto-refetch)
   - **Required** - always works, no infrastructure dependencies

### Frontend Implementation (EventManagementDashboard.tsx)

```typescript
// WebSocket connection attempt (graceful)
const { onNotification, isConnected } = useNotificationWebSocket(user?.username);

// Only subscribe if connected - no errors if not connected
useEffect(() => {
  if (!isConnected) {
    return; // ← Gracefully skips WebSocket subscription
  }

  const unsubscribe = onNotification((notification) => {
    console.log('Real-time notification received:', notification);
    queryClient.invalidateQueries({ queryKey: ['notifications'] });
  });

  return unsubscribe;
}, [isConnected, onNotification, queryClient]);

// REST API polling (always works)
const { data: notificationsData } = useNotifications(
  { username: user?.username || '', status: 'UNREAD' },
  { page: 1, limit: 10 }
);
```

### User Experience Comparison

| Feature | With WebSocket Gateway | Without Gateway (Current) |
|---------|----------------------|--------------------------|
| **Notifications** | ✅ Instant (real-time) | ✅ Delayed (1-minute polling) |
| **Dashboard Updates** | ✅ Instant | ✅ 1-minute auto-refresh |
| **App Functionality** | ✅ All features work | ✅ All features work |
| **Errors/Crashes** | ❌ None | ❌ None |
| **Browser Console** | Clean | WebSocket connection errors (diagnostics only) |
| **Monthly Cost** | ~$43-50/month | $0/month |

### Verified Behavior

**Tested Scenarios**:
1. ✅ Dashboard loads without WebSocket gateway
2. ✅ Notifications appear via REST API polling
3. ✅ No application errors or crashes
4. ✅ Connection state managed gracefully (`DISCONNECTED`/`ERROR`)
5. ✅ Auto-reconnect attempts with 5-second delay (expected behavior)
6. ✅ All dashboard features functional

**Conclusion**: Application is **production-ready** without WebSocket infrastructure. WebSocket gateway deployment is **optional enhancement** for instant notifications.

---

## Option 1: Polling-Based Approach (CURRENT - No Gateway Deployment)

**Status**: ✅ Active - No infrastructure changes needed

**Architecture**:
```
Client (Browser)
  ↓ REST API (1-minute polling)
API Gateway Service
  ↓ Service Connect
Event Management Service
  ↓ Database queries
Notifications stored in DB
```

**Pros**:
- ✅ **Zero additional cost** - no AWS infrastructure needed
- ✅ **Already implemented** - working in staging/production now
- ✅ **Simple architecture** - no WebSocket complexity
- ✅ **Proven reliability** - standard REST API pattern
- ✅ **Easy to maintain** - no additional monitoring needed

**Cons**:
- ⏳ **1-minute delay** - notifications not instant
- 📊 **Slightly higher DB load** - polling queries every minute
- 🔄 **No true real-time** - page must refresh to see updates

**Cost**: $0/month additional

**When to Use**:
- Low to medium traffic applications
- Cost-conscious environments
- Acceptable notification delay (1 minute)
- Testing and staging environments

**Decision**: ✅ **SELECTED** - Sufficient for current BATbern requirements

---

## Future Options: WebSocket Gateway Deployment (If Needed)

The following options remain available if real-time notifications become critical in the future:

### Option 2: AWS WebSocket API Gateway (RECOMMENDED if deploying)

**Architecture**:
```
Client (Browser)
  ↓ WebSocket (wss://ws.staging.batbern.ch)
AWS WebSocket API Gateway
  ↓ VPC Link or ALB Integration
Event Management Service (ECS)
  ↓ STOMP over SockJS
Notification Listeners
```

**Pros**:
- ✅ Native AWS WebSocket support
- ✅ Managed scaling and connections
- ✅ Custom domain support (wss://ws.staging.batbern.ch)
- ✅ AWS-standard architecture
- ✅ Monitoring via CloudWatch

**Cons**:
- ❌ Additional AWS cost (~$1-3/month for API Gateway + data transfer)
- ❌ Requires CDK infrastructure changes
- ❌ Different API type than HTTP API (separate resource)

**Cost Estimate**:
- WebSocket API Gateway: $1.00/million messages (generous free tier)
- Data transfer: $0.09/GB outbound
- VPC Link (if used): $0.01/hour (~$7.20/month) - shared across environments
- **Total**: ~$7-10/month for staging + production combined

### Option 3: Application Load Balancer for Event Management Service

**Architecture**:
```
Client (Browser)
  ↓ WebSocket (wss://events-ws.staging.batbern.ch)
Application Load Balancer (ALB)
  ↓ WebSocket upgrade
Event Management Service (ECS)
  ↓ STOMP over SockJS
Notification Listeners
```

**Pros**:
- ✅ Direct connection (lowest latency)
- ✅ ALB natively supports WebSocket upgrade
- ✅ No API Gateway needed
- ✅ Simple architecture

**Cons**:
- ❌ ALB cost: ~$16-20/month per environment
- ❌ Breaks current "ALB-less" microservices pattern
- ❌ Event Management Service needs public ALB (security consideration)
- ❌ Higher cost than WebSocket API Gateway

**Cost Estimate**:
- ALB: $16.20/month base + $0.008/LCU-hour
- **Total**: ~$32-40/month for staging + production

### Option 4: Route Through Existing API Gateway Service ALB

**Architecture**:
```
Client (Browser)
  ↓ WebSocket (wss://api.staging.batbern.ch/ws)
API Gateway Service ALB
  ↓ Proxy to Event Management
Spring Boot API Gateway (ECS)
  ↓ WebSocket proxy
Event Management Service (Service Connect)
  ↓ STOMP over SockJS
Notification Listeners
```

**Pros**:
- ✅ Reuses existing ALB infrastructure ($0 additional cost)
- ✅ Single entry point for all traffic

**Cons**:
- ❌ Spring Boot API Gateway doesn't support WebSocket proxying
- ❌ Requires significant Spring Boot code changes
- ❌ WebSocket is fundamentally different from HTTP request/response
- ❌ Complex implementation with limited benefit
- ❌ Mixing concerns (HTTP routing + WebSocket proxying)

## Recommended Approach: AWS WebSocket API Gateway

### Implementation Strategy

**Step 1: Create WebSocket API Gateway Infrastructure**
- New CDK Stack: `WebSocketApiStack`
- Resource Type: `apigatewayv2.WebSocketApi` (NOT `HttpApi`)
- Routes: `$connect`, `$disconnect`, `$default`
- Integration: VPC Link to Event Management Service

**Step 2: Choose Integration Method**

**Option A: VPC Link (Recommended for Service Connect)**
```typescript
// Create VPC Link to access private Service Connect endpoint
const vpcLink = new apigatewayv2.VpcLink(this, 'WebSocketVpcLink', {
  vpc: props.vpc,
  subnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
});

// Integrate with Service Connect DNS
// Note: This requires Network Load Balancer as VPC Link target
```

**Option B: Internal ALB (Simpler, but requires ALB)**
```typescript
// Add internal ALB to Event Management Service
const alb = new elbv2.ApplicationLoadBalancer(this, 'EventManagementALB', {
  vpc: props.vpc,
  internetFacing: false, // Internal only
});

// WebSocket API integrates with ALB
const integration = new apigatewayv2_integrations.HttpAlbIntegration(
  'EventManagementIntegration',
  alb.listeners[0]
);
```

**Recommendation**: Use **Option B (Internal ALB)** because:
- VPC Link requires Network Load Balancer (NLB), not directly compatible with Service Connect
- Internal ALB is simpler to configure
- Cost difference is minimal (~$8-10/month vs $7/month for VPC Link)
- Internal ALB provides better health checking and routing

**Step 3: Update Event Management Stack**

Modify `EventManagementStack` to optionally create an internal ALB:

```typescript
export class EventManagementStack extends cdk.Stack {
  public readonly service: ecs.FargateService;
  public readonly loadBalancer?: elbv2.ApplicationLoadBalancer; // For WebSocket

  constructor(scope: Construct, id: string, props: EventManagementStackProps) {
    // ... existing service creation ...

    // Create internal ALB for WebSocket (staging/production only)
    if (EnvironmentHelper.isCloudEnvironment(envName)) {
      this.loadBalancer = new elbv2.ApplicationLoadBalancer(this, 'LoadBalancer', {
        vpc: props.vpc,
        internetFacing: false, // Internal only - accessed via WebSocket API Gateway
        vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      });

      const listener = this.loadBalancer.addListener('Listener', {
        port: 80,
        protocol: elbv2.ApplicationProtocol.HTTP,
      });

      // Add service as target
      listener.addTargets('EventManagementTarget', {
        port: 8080,
        protocol: elbv2.ApplicationProtocol.HTTP,
        targets: [this.service],
        healthCheck: {
          path: '/actuator/health',
          interval: cdk.Duration.seconds(30),
        },
      });
    }
  }
}
```

**Step 4: Create WebSocketApiStack**

```typescript
export class WebSocketApiStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: WebSocketApiStackProps) {
    // Create WebSocket API
    const webSocketApi = new apigatewayv2.WebSocketApi(this, 'WebSocketApi', {
      apiName: `BATbern WebSocket API - ${envName}`,
      routeSelectionExpression: '$request.body.action',
    });

    // HTTP integration to internal ALB
    const integration = new apigatewayv2.CfnIntegration(this, 'AlbIntegration', {
      apiId: webSocketApi.apiId,
      integrationType: 'HTTP_PROXY',
      integrationUri: `http://${props.eventManagementAlb.loadBalancerDnsName}/ws`,
      integrationMethod: 'POST',
    });

    // Routes: $connect, $disconnect, $default
    ['$connect', '$disconnect', '$default'].forEach(routeKey => {
      new apigatewayv2.CfnRoute(this, `${routeKey}Route`, {
        apiId: webSocketApi.apiId,
        routeKey,
        target: `integrations/${integration.ref}`,
      });
    });

    // Production stage
    const stage = new apigatewayv2.WebSocketStage(this, 'ProductionStage', {
      webSocketApi,
      stageName: 'production',
      autoDeploy: true,
    });

    // Custom domain (wss://ws.staging.batbern.ch)
    if (props.domainName && props.certificateArn) {
      const domainName = new apigatewayv2.DomainName(this, 'WebSocketDomain', {
        domainName: props.domainName,
        certificate: certificatemanager.Certificate.fromCertificateArn(
          this,
          'Certificate',
          props.certificateArn
        ),
      });

      new apigatewayv2.ApiMapping(this, 'WebSocketMapping', {
        api: webSocketApi,
        domainName,
        stage,
      });
    }
  }
}
```

**Step 5: Register in CDK App**

```typescript
// bin/batbern-infrastructure.ts

if (EnvironmentHelper.shouldDeployWebInfrastructure(config.envName)) {
  // ... after eventManagementStack ...

  const webSocketApiStack = new WebSocketApiStack(app, `${stackPrefix}-WebSocketApi`, {
    config,
    eventManagementAlb: eventManagementStack.loadBalancer!,
    domainName: `ws.${config.domain?.zoneName}`,
    hostedZoneId: config.domain?.hostedZoneId,
    certificateArn: networkStack.apiCertificate?.certificateArn,
    env,
  });
  webSocketApiStack.addDependency(eventManagementStack);
}
```

**Step 6: Update Frontend WebSocket Client**

```typescript
// web-frontend/src/services/notificationWebSocketClient.ts

function getWebSocketUrl(): string {
  const hostname = window.location.hostname;

  if (hostname === 'localhost' || hostname === '127.0.0.1') {
    // Local development: Connect directly to event-management-service
    const apiPort = parseInt(import.meta.env.VITE_API_PORT || '8000', 10);
    const eventMgmtPort = apiPort + 2;
    return `http://localhost:${eventMgmtPort}`;
  }

  if (hostname === 'staging.batbern.ch') {
    return 'wss://ws.staging.batbern.ch'; // WebSocket API Gateway
  }

  return 'wss://ws.batbern.ch'; // Production WebSocket API Gateway
}
```

**Step 7: Update DNS Configuration**

Add WebSocket subdomain to staging/production config:

```typescript
// infrastructure/lib/config/staging-config.ts
export const stagingConfig: EnvironmentConfig = {
  // ... existing config ...
  domain: {
    frontendDomain: 'staging.batbern.ch',
    apiDomain: 'api.staging.batbern.ch',
    cdnDomain: 'cdn.staging.batbern.ch',
    wsDomain: 'ws.staging.batbern.ch', // NEW
    zoneName: 'staging.batbern.ch',
  },
};
```

## Deployment Strategy

### Phase 1: Infrastructure Deployment (1-2 hours)

1. **Create Internal ALB for Event Management Service**
   ```bash
   cd infrastructure
   npm run deploy:staging:layer4-services
   ```

2. **Deploy WebSocket API Gateway**
   ```bash
   npm run deploy:staging:layer5-gateway
   ```

3. **Verify Infrastructure**
   - Check CloudFormation stacks deployed successfully
   - Verify ALB health checks pass
   - Verify WebSocket API Gateway created

### Phase 2: DNS Configuration (15 minutes)

1. **Add DNS Record** (if not using CDK DNS stack)
   - Add CNAME: `ws.staging.batbern.ch` → WebSocket API Gateway domain

2. **Verify DNS Propagation**
   ```bash
   dig ws.staging.batbern.ch
   ```

### Phase 3: Frontend Deployment (10 minutes)

1. **Deploy Updated Frontend**
   ```bash
   cd web-frontend
   npm run build
   # Deploy to S3/CloudFront
   ```

2. **Invalidate CloudFront Cache**
   ```bash
   aws cloudfront create-invalidation \
     --distribution-id <ID> \
     --paths "/*"
   ```

### Phase 4: Testing (30 minutes)

1. **Test WebSocket Connection**
   - Open browser DevTools → Network → WS tab
   - Navigate to staging dashboard
   - Verify WebSocket connection established

2. **Test Real-time Notifications**
   - Trigger event creation (should send notification)
   - Verify notification appears in real-time
   - Check browser console for WebSocket messages

3. **Test Reconnection**
   - Close WebSocket connection
   - Verify auto-reconnect works

4. **Load Testing** (optional)
   - Use WebSocket load testing tool
   - Verify API Gateway scales

## Cost Analysis

### One-Time Costs
- Development time: 4-6 hours
- Testing time: 2-3 hours

### Ongoing Monthly Costs (Per Environment)

**Staging**:
- Internal ALB: $16.20/month base + ~$2/month for LCU
- WebSocket API Gateway: $1/million messages (likely $0-1/month)
- Data transfer: $0.09/GB (likely $1-2/month)
- **Total**: ~$19-21/month

**Production**:
- Internal ALB: $16.20/month base + ~$5/month for LCU
- WebSocket API Gateway: $1/million messages (likely $1-3/month)
- Data transfer: $0.09/GB (likely $3-5/month)
- **Total**: ~$24-29/month

**Combined Staging + Production**: ~$43-50/month

### Cost Optimization Notes
- Free tier: 1M messages/month for WebSocket API Gateway (likely covers staging)
- Internal ALB can be shared if multiple services need WebSocket (future)
- Consider ALB deletion during non-business hours in staging (save ~$11/month)

## Security Considerations

### Authentication
- WebSocket handshake happens over `/ws` endpoint (currently permitAll in SecurityConfig)
- After connection, STOMP messages can include user authentication
- Consider adding JWT validation to WebSocket handshake in future

### Network Security
- Internal ALB (not internet-facing) - only accessible via WebSocket API Gateway
- Security groups restrict traffic to VPC only
- WebSocket API Gateway provides DDoS protection

### Encryption
- TLS 1.2+ for WebSocket connections (wss://)
- Traffic encrypted in transit

## Rollback Plan

If WebSocket infrastructure fails:

1. **Immediate**: Frontend falls back gracefully (no WebSocket = polling)
2. **Infrastructure**: Delete WebSocketApiStack via CDK
3. **Cost**: Delete internal ALB to stop charges
4. **Users**: No impact - notifications still work via polling

## Success Criteria

- ✅ WebSocket connection established from staging frontend
- ✅ Real-time notifications appear in dashboard
- ✅ Auto-reconnection works after disconnect
- ✅ CloudWatch metrics show WebSocket connections
- ✅ No errors in browser console
- ✅ Infrastructure deployed successfully

## Future Enhancements

1. **Authentication**: Add JWT validation to WebSocket handshake
2. **Scaling**: Add auto-scaling for WebSocket connections
3. **Monitoring**: CloudWatch alarms for connection failures
4. **Shared ALB**: Use same ALB for multiple WebSocket-enabled services
5. **Message Compression**: Reduce data transfer costs

## References

- [AWS WebSocket API Documentation](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-websocket-api.html)
- [Spring Boot WebSocket Documentation](https://docs.spring.io/spring-framework/reference/web/websocket.html)
- [STOMP Protocol Specification](https://stomp.github.io/stomp-specification-1.2.html)
- Story BAT-7: `docs/stories/BAT-7.notifications-api-consolidation.md`

## When to Reconsider WebSocket Gateway Deployment

Deploy WebSocket infrastructure if ANY of these conditions occur:

1. **User Feedback**: Users frequently request faster notification updates
2. **Scale**: Monthly active users exceed 500+ concurrent sessions
3. **Real-time Critical**: Instant notifications become business requirement
4. **Budget Approved**: $43-50/month infrastructure cost approved
5. **Competitive Advantage**: Real-time updates become differentiator

**Re-evaluation Trigger**: Review this decision quarterly or when user base doubles.

---

## Questions for Future Review (If Deploying)

1. **Cost Approval**: Is $43-50/month acceptable for WebSocket infrastructure?
2. **Domain Name**: Confirm `ws.staging.batbern.ch` and `ws.batbern.ch` are acceptable
3. **Security**: Should we add JWT validation to WebSocket handshake now or later?
4. **Monitoring**: What CloudWatch alarms are needed for WebSocket?

---

## Current Status (2026-01-03)

**Decision**: ⏸️ **DEFERRED** - Stay with Option 1 (Polling-based notifications)

**Deployment Status**:
- ✅ Local Development: WebSocket working
- ✅ AWS Staging/Production: Polling-based notifications working
- ⏸️ WebSocket Gateway: Not deployed (deferred)

**Next Steps**:
1. ✅ Monitor user feedback on notification delays
2. ✅ Track notification volume and latency
3. ⏸️ Re-evaluate quarterly or when requirements change
4. 📋 Keep this plan updated for future reference

**Plan Maintained By**: Development Team
**Last Updated**: 2026-01-03
**Next Review**: 2026-04-03 (Quarterly)
