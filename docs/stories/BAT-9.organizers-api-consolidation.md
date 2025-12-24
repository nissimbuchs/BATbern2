# BAT-9: Organizers API Consolidation

⚠️ **IMPORTANT: Story Content Location**

This file contains **implementation details only** (Dev Agent Record). The full **product view** (User Story, Acceptance Criteria, Tasks, Definition of Done) is maintained in Linear for stakeholder visibility.

**Linear Issue (Product View)**: [BAT-9 - Organizers API Consolidation](https://linear.app/batbern/issue/BAT-9/organizers-api-consolidation)

**Legacy Story ID**: 1.15a.8

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
services/event-management-service/src/test/java/ch/batbern/event/organizer/
├── controller/
│   └── OrganizerControllerIntegrationTest.java     # Integration tests for all 15 endpoints
├── service/
│   ├── OrganizerServiceTest.java                   # Unit tests for organizer logic
│   ├── TeamManagementServiceTest.java              # Unit tests for team management
│   └── TaskManagementServiceTest.java              # Unit tests for task management
└── repository/
    ├── OrganizerRepositoryTest.java                # Repository tests
    └── TeamMemberRepositoryTest.java               # Team repository tests
```

### Story-Specific Implementation

**Dashboard Aggregation** (custom - not in template):
```java
@Service
public class OrganizerDashboardService {
    
    public OrganizerDashboardDTO getDashboard(UUID organizerId, 
                                              List<String> metrics, 
                                              String timeframe) {
        OrganizerDashboardDTO dashboard = new OrganizerDashboardDTO();
        
        if (metrics.contains("events")) {
            dashboard.setEvents(eventService.getOrganizerEvents(organizerId, timeframe));
        }
        if (metrics.contains("tasks")) {
            dashboard.setTasks(taskService.getPendingTasks(organizerId));
        }
        if (metrics.contains("notifications")) {
            dashboard.setNotifications(notificationService.getUnread(organizerId));
        }
        
        // Cache dashboard with Caffeine
        return dashboardCache.get(organizerId, k -> dashboard);
    }
}
```

**Team Permissions Management**:
```java
@Entity
@Table(name = "team_members")
public class TeamMember {
    @Id
    private UUID id;
    
    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Type(JsonBinaryType.class)
    @Column(name = "permissions", columnDefinition = "jsonb")
    private Map<String, Boolean> permissions; // event_create, event_edit, team_manage, etc.
}
```

**Calendar View Query**:
```java
public interface OrganizerRepository extends JpaRepository<Organizer, UUID> {
    
    @Query("""
        SELECT new ch.batbern.event.dto.CalendarEventDTO(
            e.id, e.title, e.date, e.status
        )
        FROM Event e
        JOIN e.organizers o
        WHERE o.id = :organizerId
        AND e.date BETWEEN :startDate AND :endDate
        ORDER BY e.date
    """)
    List<CalendarEventDTO> getCalendarEvents(
        @Param("organizerId") UUID organizerId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
```

### API Contracts

**Consolidated Endpoints** (15 total):
```
# Organizer Management
GET    /api/v1/organizers/dashboard?metrics=events,tasks,notifications&timeframe={}
GET    /api/v1/organizers/{id}
PUT    /api/v1/organizers/{id}

# Team Management
GET    /api/v1/organizers/{id}/team?filter={}&page={}
POST   /api/v1/organizers/{id}/team
PUT    /api/v1/organizers/{id}/team/{memberId}
DELETE /api/v1/organizers/{id}/team/{memberId}
GET    /api/v1/organizers/{id}/team/{memberId}/permissions
PUT    /api/v1/organizers/{id}/team/{memberId}/permissions

# Task Management
GET    /api/v1/organizers/{id}/tasks?status=pending|completed
POST   /api/v1/organizers/{id}/tasks
PUT    /api/v1/organizers/{id}/tasks/{taskId}
DELETE /api/v1/organizers/{id}/tasks/{taskId}
PATCH  /api/v1/organizers/{id}/tasks

# Additional Endpoints
GET    /api/v1/organizers/{id}/notifications?filter={}&page={}
PUT    /api/v1/organizers/{id}/notifications/read
GET    /api/v1/organizers/{id}/events?role={}
GET    /api/v1/organizers/{id}/calendar?startDate={}&endDate={}
GET    /api/v1/organizers/{id}/activity?timeframe={}
GET    /api/v1/organizers/{id}/analytics?metrics={}
POST   /api/v1/organizers/{id}/export?format=csv|json
```

### Database Schema

**Tables**:
- `organizers` (id, user_id, name, email, role)
- `team_members` (id, organizer_id, user_id, permissions_jsonb)
- `organizer_tasks` (id, organizer_id, title, status, due_date)

**Flyway Migration**: `V018__create_organizer_tables.sql`

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
| 2024-10-04 | 1.0 | Initial story creation (legacy format) | Winston (Architect) |
| 2025-12-21 | 2.0 | Migrated to Linear-first format | James (Dev) |
