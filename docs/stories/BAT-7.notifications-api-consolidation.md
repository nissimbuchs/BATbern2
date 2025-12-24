# BAT-7: Notifications API Consolidation

⚠️ **IMPORTANT: Story Content Location**

This file contains **implementation details only** (Dev Agent Record). The full **product view** (User Story, Acceptance Criteria, Tasks, Definition of Done) is maintained in Linear for stakeholder visibility.

**Linear Issue (Product View)**: [BAT-7 - Notifications API Consolidation](https://linear.app/batbern/issue/BAT-7/notifications-api-consolidation)

**Legacy Story ID**: 1.15a.10

---

## Dev Agent Record

### Status
Draft

### Agent Model Used
- Created: N/A (story not yet implemented)

### Template References

**Backend Templates**:
- `docs/templates/backend/spring-boot-service-foundation.md` - Service structure, JPA entities, repositories
- `docs/templates/backend/integration-test-pattern.md` - Testcontainers PostgreSQL, MockMvc tests

### Test Implementation Details

**Test File Locations**:
```
shared-kernel/src/test/java/ch/batbern/shared/notification/
├── NotificationModuleTest.java                    # Unit tests for shared notification module
├── NotificationPreferencesTest.java               # Unit tests for preferences logic
└── SESIntegrationTest.java                        # Integration tests for AWS SES

api-gateway/src/test/java/ch/batbern/gateway/notification/
├── NotificationAggregationControllerIntegrationTest.java  # Integration tests for aggregation
└── NotificationAggregationServiceTest.java                # Unit tests for aggregation logic

services/{service}/src/test/java/ch/batbern/{domain}/notification/
├── controller/
│   └── NotificationControllerIntegrationTest.java # Integration tests per service
└── service/
    └── NotificationServiceTest.java               # Unit tests per service
```

**Testcontainers Configuration** (MANDATORY):
```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);
}
```

### Story-Specific Implementation

**Shared Notification Module** (Shared Kernel):
```java
// shared-kernel/src/main/java/ch/batbern/shared/notification/
package ch.batbern.shared.notification;

@MappedSuperclass
public abstract class NotificationEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status; // UNREAD, READ, DELETED

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private NotificationChannel channel; // EMAIL, PUSH, IN_APP

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;
}

@Embeddable
public class NotificationPreferences {
    @Column(name = "email_enabled")
    private boolean emailEnabled = true;

    @Column(name = "push_enabled")
    private boolean pushEnabled = true;

    @Column(name = "in_app_enabled")
    private boolean inAppEnabled = true;

    @Type(JsonBinaryType.class)
    @Column(name = "topic_preferences", columnDefinition = "jsonb")
    private Map<String, Boolean> topicPreferences; // event_updates, speaker_updates, etc.
}

@Component
public class NotificationSender {
    private final AmazonSES sesClient;
    private final AmazonSNS snsClient;

    public void sendEmail(String to, String subject, String body) {
        SendEmailRequest request = new SendEmailRequest()
            .withDestination(new Destination().withToAddresses(to))
            .withMessage(new Message()
                .withSubject(new Content(subject))
                .withBody(new Body().withText(new Content(body))));
        sesClient.sendEmail(request);
    }

    public void sendPush(String deviceToken, String title, String message) {
        PublishRequest request = new PublishRequest()
            .withTargetArn(deviceToken)
            .withMessage(message)
            .withSubject(title);
        snsClient.publish(request);
    }
}
```

**API Gateway Aggregation** (Custom - not in template):
```java
// api-gateway/src/main/java/ch/batbern/gateway/notification/
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationAggregationController {

    private final List<NotificationServiceClient> serviceClients; // Event, Speaker, Partner, etc.

    @GetMapping
    public Page<NotificationDTO> listNotifications(
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "all") String status,
            Pageable pageable) {

        // Aggregate notifications from all services
        List<NotificationDTO> allNotifications = serviceClients.stream()
            .flatMap(client -> client.getNotifications(filter, status, pageable).stream())
            .sorted(Comparator.comparing(NotificationDTO::getCreatedAt).reversed())
            .collect(Collectors.toList());

        return new PageImpl<>(allNotifications, pageable, allNotifications.size());
    }

    @PutMapping("/read")
    public ResponseEntity<Void> markAsRead(@RequestBody List<UUID> notificationIds) {
        // Route each notification to its owning service
        Map<String, List<UUID>> idsByService = groupIdsByService(notificationIds);

        idsByService.forEach((service, ids) ->
            getServiceClient(service).markAsRead(ids)
        );

        return ResponseEntity.ok().build();
    }

    private Map<String, List<UUID>> groupIdsByService(List<UUID> ids) {
        // Logic to determine which service owns each notification
        // (could be based on ID prefix, separate lookup table, etc.)
    }
}
```

**Per-Service Integration** (Pattern - repeat for all 5 services):
```java
// services/{service}/src/main/java/ch/batbern/{domain}/notification/
@Entity
@Table(name = "notifications")
public class {Service}Notification extends NotificationEntity {
    // Service-specific fields (e.g., event_id for EventNotification)
}

@RestController
@RequestMapping("/api/v1/{service}/notifications")
public class {Service}NotificationController {
    // Standard CRUD endpoints for this service's notifications
}
```

### API Contracts

**Consolidated Endpoints** (6 total):
```
# Notification Management (API Gateway)
GET    /api/v1/notifications?filter={}&status=unread|read|all&page={}
GET    /api/v1/notifications/count?status=unread
PUT    /api/v1/notifications/read
DELETE /api/v1/notifications/{id}
DELETE /api/v1/notifications         # Batch delete

# Preferences & History (API Gateway)
GET    /api/v1/notifications/preferences
PUT    /api/v1/notifications/preferences
GET    /api/v1/notifications/history?timeframe={}&channel=email|push|in_app
```

### Database Schema

**Shared Kernel Base**:
```sql
-- Each service has its own notifications table (extends base schema)
-- Example for event-management-service:
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,  -- UNREAD, READ, DELETED
    channel VARCHAR(50) NOT NULL, -- EMAIL, PUSH, IN_APP
    created_at TIMESTAMP NOT NULL,
    read_at TIMESTAMP,
    
    -- Service-specific columns
    event_id UUID,
    
    INDEX idx_user_status (user_id, status),
    INDEX idx_created_at (created_at)
);

-- Centralized preferences (in company-user-management-service)
CREATE TABLE notification_preferences (
    user_id VARCHAR(255) PRIMARY KEY,
    email_enabled BOOLEAN DEFAULT true,
    push_enabled BOOLEAN DEFAULT true,
    in_app_enabled BOOLEAN DEFAULT true,
    topic_preferences JSONB,
    updated_at TIMESTAMP NOT NULL
);
```

**Flyway Migrations**:
- `V016__create_notification_module_shared.sql` (Shared Kernel docs)
- `V016__create_notifications_event.sql` (Event Management Service)
- `V016__create_notifications_speaker.sql` (Speaker Coordination Service)
- `V016__create_notifications_partner.sql` (Partner Coordination Service)
- `V016__create_notifications_attendee.sql` (Attendee Experience Service)
- `V016__create_notification_preferences.sql` (Company/User Management Service)

### AWS Integration

**SES Configuration** (application.yml):
```yaml
aws:
  ses:
    region: eu-central-1
    from-email: notifications@batbern.ch
    configuration-set: batbern-notifications
```

**SNS Configuration** (application.yml):
```yaml
aws:
  sns:
    region: eu-central-1
    platform-application-arn: arn:aws:sns:eu-central-1:ACCOUNT_ID:app/PLATFORM/batbern-app
```

### File List

**Created Files**:
- (Placeholder - story not yet implemented)

**Modified Files**:
- (Placeholder - story not yet implemented)

### Debug Log References

- (No debug logs yet - story not yet implemented)

### Completion Notes

- (Placeholder - story not yet implemented)

### Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-12 | 2.0 | Clarified as cross-cutting concern (legacy format) | Winston (Architect) |
| 2024-10-04 | 1.0 | Initial story creation (legacy format) | Winston (Architect) |
| 2025-12-21 | 3.0 | Migrated to Linear-first format | James (Dev) |
