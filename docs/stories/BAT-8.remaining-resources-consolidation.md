# BAT-8: Remaining Resources API Consolidation

⚠️ **IMPORTANT: Story Content Location**

This file contains **implementation details only** (Dev Agent Record). The full **product view** (User Story, Acceptance Criteria, Tasks, Definition of Done) is maintained in Linear for stakeholder visibility.

**Linear Issue (Product View)**: [BAT-8 - Remaining Resources API Consolidation](https://linear.app/batbern/issue/BAT-8/remaining-resources-api-consolidation)

**Legacy Story ID**: 1.15a.11

---

## Dev Agent Record

### Status
Draft

### Agent Model Used
- Created: N/A (story not yet implemented)

### Template References

**Backend Templates**:
- `docs/templates/backend/spring-boot-service-foundation.md` - Service structure (API Gateway level)
- `docs/templates/backend/integration-test-pattern.md` - Testcontainers PostgreSQL, MockMvc tests

### Test Implementation Details

**Test File Locations**:
```
api-gateway/src/test/java/ch/batbern/gateway/admin/
├── controller/
│   ├── AdminSystemControllerIntegrationTest.java
│   ├── AdminAuditControllerIntegrationTest.java
│   ├── AdminFeatureFlagControllerIntegrationTest.java
│   ├── AdminConfigControllerIntegrationTest.java
│   └── AdminCacheControllerIntegrationTest.java
└── service/
    ├── AdminSystemServiceTest.java
    ├── AdminAuditServiceTest.java
    └── FeatureFlagServiceTest.java
```

### Story-Specific Implementation

**Admin System Controller** (API Gateway):
```java
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemController {
    
    @GetMapping("/system/status")
    public SystemStatusDTO getSystemStatus() {
        return SystemStatusDTO.builder()
            .status("healthy")
            .uptime(uptimeCalculator.getUptime())
            .version(versionProvider.getVersion())
            .build();
    }
    
    @GetMapping("/system/metrics")
    public SystemMetricsDTO getMetrics() {
        return metricsCollector.collect();
    }
    
    @GetMapping("/system/health")
    public ResponseEntity<HealthDTO> getHealth() {
        HealthDTO health = healthChecker.check();
        return ResponseEntity
            .status(health.isHealthy() ? 200 : 503)
            .body(health);
    }
}
```

**Feature Flag Management**:
```java
@RestController
@RequestMapping("/api/v1/admin/feature-flags")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeatureFlagController {
    
    @GetMapping
    public List<FeatureFlagDTO> listFlags() {
        return featureFlagService.findAll();
    }
    
    @PostMapping
    public FeatureFlagDTO createFlag(@Valid @RequestBody CreateFlagRequest request) {
        return featureFlagService.create(request);
    }
    
    @PutMapping("/{id}")
    public FeatureFlagDTO updateFlag(@PathVariable UUID id, 
                                      @Valid @RequestBody UpdateFlagRequest request) {
        return featureFlagService.update(id, request);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlag(@PathVariable UUID id) {
        featureFlagService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

### API Contracts

**Admin Endpoints** (13 total):
```
# System Health & Monitoring
GET    /api/v1/admin/system/status
GET    /api/v1/admin/system/metrics
GET    /api/v1/admin/system/health

# Audit & Logging
GET    /api/v1/admin/audit-logs?filter={}&page={}
GET    /api/v1/admin/audit-logs/{id}

# Feature Management
GET    /api/v1/admin/feature-flags
POST   /api/v1/admin/feature-flags
PUT    /api/v1/admin/feature-flags/{id}
DELETE /api/v1/admin/feature-flags/{id}

# System Configuration
GET    /api/v1/admin/config
PUT    /api/v1/admin/config/{key}

# Cache Management
POST   /api/v1/admin/cache/clear
GET    /api/v1/admin/cache/stats
```

### Database Schema

**Feature Flags Table**:
```sql
CREATE TABLE feature_flags (
    id UUID PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    description TEXT,
    enabled BOOLEAN DEFAULT false,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_name (name)
);
```

**Flyway Migration**: `V017__create_admin_tables.sql`

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
| 2025-10-12 | 2.0 | Restructured to focus on admin endpoints only (legacy format) | Winston (Architect) |
| 2024-10-04 | 1.0 | Initial story creation (legacy format) | Winston (Architect) |
| 2025-12-21 | 3.0 | Migrated to Linear-first format | James (Dev) |
