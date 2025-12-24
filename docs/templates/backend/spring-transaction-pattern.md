# Spring Transaction Management Pattern

**Category**: Backend
**Used in Stories**: 5.5
**Last Updated**: 2025-12-24

## Overview

Comprehensive pattern for managing database transactions in Spring Boot services, including isolation levels, rollback strategies, timeout configuration, and transaction boundary best practices. Essential for maintaining data integrity in complex multi-step operations.

## Prerequisites

- Spring Boot 3.x with Spring Data JPA
- PostgreSQL database
- Understanding of ACID properties
- Knowledge of isolation levels and transaction propagation

## When to Use This Pattern

Use explicit `@Transactional` configuration when:
- Operation involves multiple database writes that must succeed/fail together
- External service calls should NOT be in transaction scope
- Concurrent updates require specific isolation levels
- Long-running operations need timeout protection
- Rollback behavior must be customized

## Implementation Steps

### Step 1: Service Method with Transaction Configuration

```java
@Service
@Slf4j
public class SpeakerContentSubmissionService {

    private final SpeakerPoolRepository speakerPoolRepository;
    private final SessionRepository sessionRepository;
    private final SessionUserRepository sessionUserRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserApiClient userApiClient; // External service

    @Transactional(
        isolation = Isolation.READ_COMMITTED,  // Prevent dirty reads
        rollbackFor = Exception.class,          // Rollback on any exception
        timeout = 10                            // 10 seconds max
    )
    public SpeakerContentDto submitContent(String eventId, String poolId,
                                          SubmitContentRequest request) {
        try {
            // Step 1: Validate speaker_pool entry exists and is in correct state
            SpeakerPool speaker = speakerPoolRepository.findById(poolId)
                .orElseThrow(() -> new EntityNotFoundException("Speaker not found"));

            if (!speaker.getStatus().equals("accepted")) {
                throw new InvalidWorkflowStateException(
                    "Speaker must be accepted before content submission"
                );
            }

            // Step 2: Get/create user (EXTERNAL CALL - outside transaction scope)
            // Call separate method with REQUIRES_NEW propagation
            String username = getOrCreateSpeakerUsername(request);

            // Step 3: Create session (database write)
            Session session = sessionRepository.save(Session.builder()
                .eventId(UUID.fromString(eventId))
                .title(request.getPresentationTitle())
                .description(request.getPresentationAbstract())
                .sessionType("presentation")
                .build());

            // Step 4: Create session_users link (database write)
            sessionUserRepository.save(SessionUser.builder()
                .sessionId(session.getId())
                .username(username)
                .speakerRole("primary_speaker")
                .isConfirmed(false)
                .build());

            // Step 5: Update speaker_pool (database write)
            speaker.setSessionId(session.getId());
            speaker.setStatus("content_submitted");
            speakerPoolRepository.save(speaker);

            // Step 6: Publish event (only if all above succeeded)
            // Events published after transaction commit via @TransactionalEventListener
            eventPublisher.publishEvent(new SpeakerWorkflowStateChangeEvent(
                poolId, eventId, "accepted", "content_submitted", username
            ));

            return toDto(speaker, session);

        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation during content submission for pool {}: {}",
                     poolId, e.getMessage(), e);
            // Transaction will auto-rollback due to rollbackFor = Exception.class
            throw new ContentSubmissionException(
                "Unable to submit content due to data inconsistency. Please contact support.",
                "DATA_INTEGRITY_VIOLATION",
                e
            );
        } catch (Exception e) {
            log.error("Unexpected error during content submission for pool {}: {}",
                     poolId, e.getMessage(), e);
            // Transaction will auto-rollback
            throw new ContentSubmissionException(
                "Content submission failed. Please try again.",
                "SUBMISSION_FAILED",
                e
            );
        }
    }

    // External service call with separate transaction
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected String getOrCreateSpeakerUsername(SubmitContentRequest request) {
        // This runs in separate transaction to avoid holding DB connection
        // during external HTTP call
        return userApiClient.getOrCreateUser(request.getEmail());
    }
}
```

### Step 2: Exception Handling with Rollback Control

```java
@Transactional(
    rollbackFor = {Exception.class},           // Rollback on all exceptions
    noRollbackFor = {OptimisticLockException.class} // But not optimistic lock (retry instead)
)
public void processWorkflowTransition(String poolId) {
    try {
        SpeakerPool speaker = speakerPoolRepository.findById(poolId)
            .orElseThrow(() -> new EntityNotFoundException("Speaker not found"));

        // Business logic that modifies database
        speaker.setStatus("confirmed");
        speakerPoolRepository.save(speaker);

    } catch (OptimisticLockException e) {
        // Don't rollback - retry with fresh data instead
        log.info("Concurrent update detected for speaker {}. Retrying.", poolId);
        throw e; // Will be caught by retry mechanism
    } catch (BusinessRuleException e) {
        // Rollback transaction
        log.error("Business rule violation: {}", e.getMessage());
        throw new WorkflowException("Cannot transition workflow", e);
    }
}
```

### Step 3: Transaction Isolation Levels

```java
// Default: READ_COMMITTED (prevents dirty reads)
@Transactional(isolation = Isolation.READ_COMMITTED)
public void standardOperation() {
    // Most common - balances consistency and performance
}

// REPEATABLE_READ (prevents non-repeatable reads)
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void consistentReadOperation() {
    // Use when you need consistent view across multiple reads
    // Example: Generating reports with consistent data
}

// SERIALIZABLE (highest isolation, use sparingly)
@Transactional(isolation = Isolation.SERIALIZABLE)
public void criticalOperation() {
    // Use only for critical operations requiring full isolation
    // Example: Financial transactions, inventory updates
    // Warning: Potential for serialization failures and retries
}
```

### Step 4: Transaction Propagation

```java
// REQUIRED (default) - Join existing transaction or create new one
@Transactional(propagation = Propagation.REQUIRED)
public void joinExistingTransaction() {
    // Most common - participates in caller's transaction
}

// REQUIRES_NEW - Always create new transaction (suspend existing)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void independentTransaction() {
    // Use for: Audit logging, external service calls, notifications
    // Commits independently even if caller transaction rolls back
}

// MANDATORY - Must be called within existing transaction
@Transactional(propagation = Propagation.MANDATORY)
public void mustHaveTransaction() {
    // Throws exception if called without active transaction
    // Use for: Internal methods that should never be called standalone
}

// NOT_SUPPORTED - Suspend transaction for this method
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public void noTransactionNeeded() {
    // Use for: Read-only operations, external API calls
    // Improves performance by not holding DB connection
}
```

### Step 5: Read-Only Transactions for Performance

```java
@Transactional(readOnly = true)
public List<SpeakerDto> getReviewQueue(String eventId) {
    // Read-only hint allows database optimizations
    // PostgreSQL can skip acquiring write locks
    // Improves performance for queries
    return speakerPoolRepository
        .findByEventIdAndStatus(eventId, "content_submitted")
        .stream()
        .map(this::toDto)
        .collect(Collectors.toList());
}
```

### Step 6: Timeout Configuration

```java
@Transactional(timeout = 5) // 5 seconds max
public void quickOperation() {
    // Short timeout for operations that should be fast
    // Prevents resource exhaustion from stuck transactions
}

@Transactional(timeout = 30) // 30 seconds for complex operations
public void complexOperation() {
    // Longer timeout for legitimate long-running operations
    // Example: Batch processing, report generation
}

// No timeout (use with caution)
@Transactional
public void longRunningOperation() {
    // Default timeout from spring.transaction.default-timeout
    // Usually 30-60 seconds
}
```

### Step 7: Event Publishing After Transaction Commit

```java
// Service publishes events
@Service
public class QualityReviewService {

    @Transactional
    public void approveContent(String poolId, String moderatorUsername) {
        SpeakerPool speaker = speakerPoolRepository.findById(poolId)
            .orElseThrow(() -> new EntityNotFoundException("Speaker not found"));

        speaker.setStatus("quality_reviewed");
        speakerPoolRepository.save(speaker);

        // Event published DURING transaction
        eventPublisher.publishEvent(new SpeakerWorkflowStateChangeEvent(
            poolId, speaker.getEventId().toString(), "content_submitted",
            "quality_reviewed", moderatorUsername
        ));
    }
}

// Event listener processes AFTER transaction commits
@Component
public class NotificationEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSpeakerStateChange(SpeakerWorkflowStateChangeEvent event) {
        // This runs AFTER transaction commits
        // Safe to send emails, call external APIs, etc.
        notificationService.notifyContentApproved(event.getSpeakerId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void onTransactionRollback(SpeakerWorkflowStateChangeEvent event) {
        // This runs if transaction rolls back
        log.warn("Transaction rolled back for speaker {}", event.getSpeakerId());
    }
}
```

## Configuration

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/batbern
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      # Connection pool settings affect transaction performance
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000  # 30 seconds
      idle-timeout: 600000       # 10 minutes
      max-lifetime: 1800000      # 30 minutes

  jpa:
    # Transaction-related JPA settings
    properties:
      hibernate:
        # Enable query logging to debug transaction boundaries
        show_sql: false
        format_sql: true
        # Connection release mode
        connection:
          handling_mode: DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION

  transaction:
    # Global transaction timeout (overridden by method-level timeout)
    default-timeout: 30
    # Rollback on checked exceptions (Spring default is unchecked only)
    rollback-on-commit-failure: true
```

## Testing Transactions

### Integration Test with Transaction Verification

```java
@SpringBootTest
@Testcontainers
class TransactionIntegrationTest extends AbstractIntegrationTest {

    @Test
    void should_rollbackAllChanges_when_exceptionThrown() {
        // Given: Initial state
        SpeakerPool speaker = createAcceptedSpeaker();
        String initialStatus = speaker.getStatus();

        // When: Operation fails mid-transaction
        assertThrows(ContentSubmissionException.class, () ->
            contentService.submitContent(eventId, speaker.getId(),
                invalidRequest()) // Will fail validation
        );

        // Then: All changes rolled back
        SpeakerPool unchanged = speakerPoolRepository.findById(speaker.getId())
            .orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(initialStatus);

        // And: No orphaned session records
        long sessionCount = sessionRepository.count();
        assertThat(sessionCount).isZero();

        // And: No orphaned session_users records
        long sessionUserCount = sessionUserRepository.count();
        assertThat(sessionUserCount).isZero();
    }

    @Test
    void should_commitAllChanges_when_operationSucceeds() {
        // Given: Valid request
        SpeakerPool speaker = createAcceptedSpeaker();
        SubmitContentRequest request = createValidRequest();

        // When: Content submitted successfully
        SpeakerContentDto result = contentService.submitContent(
            eventId, speaker.getId(), request
        );

        // Then: All changes persisted
        assertThat(result.getSessionId()).isNotNull();

        // Session created
        Session session = sessionRepository.findById(result.getSessionId())
            .orElseThrow();
        assertThat(session.getTitle()).isEqualTo(request.getPresentationTitle());

        // session_users link created
        List<SessionUser> sessionUsers = sessionUserRepository
            .findBySessionId(session.getId());
        assertThat(sessionUsers).hasSize(1);

        // speaker_pool updated
        SpeakerPool updated = speakerPoolRepository.findById(speaker.getId())
            .orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("content_submitted");
        assertThat(updated.getSessionId()).isEqualTo(session.getId());
    }

    @Test
    void should_handleConcurrentUpdates_when_optimisticLockingEnabled() {
        // Given: Speaker in initial state
        SpeakerPool speaker = createSpeaker();

        // When: Two transactions try to update simultaneously
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger lockExceptionCount = new AtomicInteger(0);

        CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
            try {
                speakerService.updateStatus(speaker.getId(), "content_submitted");
                successCount.incrementAndGet();
            } catch (OptimisticLockException e) {
                lockExceptionCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> {
            try {
                speakerService.updateStatus(speaker.getId(), "quality_reviewed");
                successCount.incrementAndGet();
            } catch (OptimisticLockException e) {
                lockExceptionCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        // Then: One succeeds, one gets optimistic lock exception
        latch.await(5, TimeUnit.SECONDS);
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(lockExceptionCount.get()).isEqualTo(1);
    }
}
```

## Common Pitfalls

### Pitfall 1: External Service Calls Inside Transactions
**Problem**: Holding database connection during slow external HTTP calls
```java
❌ BAD:
@Transactional
public void submitContent() {
    // Database write
    speaker.setStatus("content_submitted");
    speakerPoolRepository.save(speaker);

    // External HTTP call (holds DB connection!)
    userApiClient.createUser(request); // Slow!

    // More database writes
    sessionRepository.save(session);
}

✅ GOOD:
@Transactional
public void submitContent() {
    // External call BEFORE transaction (or in separate transaction)
    String username = getOrCreateUser(request);

    // Database writes in transaction
    speaker.setStatus("content_submitted");
    speakerPoolRepository.save(speaker);
    sessionRepository.save(session);
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
protected String getOrCreateUser(Request request) {
    return userApiClient.createUser(request);
}
```

### Pitfall 2: Swallowing Exceptions Without Rollback
**Problem**: Transaction commits despite business logic failure
```java
❌ BAD:
@Transactional
public void updateSpeaker(String poolId) {
    try {
        speaker.setStatus("confirmed");
        speakerPoolRepository.save(speaker);
    } catch (Exception e) {
        log.error("Error: {}", e.getMessage());
        // Exception swallowed - transaction COMMITS!
    }
}

✅ GOOD:
@Transactional(rollbackFor = Exception.class)
public void updateSpeaker(String poolId) {
    try {
        speaker.setStatus("confirmed");
        speakerPoolRepository.save(speaker);
    } catch (Exception e) {
        log.error("Error: {}", e.getMessage(), e);
        throw new WorkflowException("Update failed", e); // Rethrow!
    }
}
```

### Pitfall 3: Publishing Events Before Transaction Commits
**Problem**: Events sent even if transaction rolls back
```java
❌ BAD:
@Transactional
public void approveContent(String poolId) {
    speaker.setStatus("approved");
    speakerPoolRepository.save(speaker);

    // Email sent immediately (even if transaction rolls back later!)
    emailService.sendApprovalEmail(speaker);
}

✅ GOOD:
@Transactional
public void approveContent(String poolId) {
    speaker.setStatus("approved");
    speakerPoolRepository.save(speaker);

    // Event published during transaction
    eventPublisher.publishEvent(new ContentApprovedEvent(poolId));
}

// Listener waits for commit
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onContentApproved(ContentApprovedEvent event) {
    emailService.sendApprovalEmail(event.getSpeakerId());
}
```

### Pitfall 4: Missing @Transactional on Public Methods
**Problem**: No transaction management for database writes
```java
❌ BAD:
public void updateStatus(String poolId, String status) {
    // No transaction - each repository call is separate transaction!
    SpeakerPool speaker = speakerPoolRepository.findById(poolId).orElseThrow();
    speaker.setStatus(status);
    speakerPoolRepository.save(speaker); // Separate transaction

    sessionUserRepository.updateIsConfirmed(speaker.getSessionId(), true); // Another separate transaction
    // Data inconsistency if second update fails!
}

✅ GOOD:
@Transactional
public void updateStatus(String poolId, String status) {
    // Single transaction for all database operations
    SpeakerPool speaker = speakerPoolRepository.findById(poolId).orElseThrow();
    speaker.setStatus(status);
    speakerPoolRepository.save(speaker);
    sessionUserRepository.updateIsConfirmed(speaker.getSessionId(), true);
}
```

### Pitfall 5: Long-Running Transactions
**Problem**: Locking resources, blocking other transactions
```java
❌ BAD:
@Transactional(timeout = 300) // 5 minutes!
public void processLargeBatch(List<Speaker> speakers) {
    for (Speaker speaker : speakers) { // 1000 speakers
        speaker.setStatus("processed");
        speakerPoolRepository.save(speaker);
        Thread.sleep(100); // Simulating work
    }
}

✅ GOOD:
public void processLargeBatch(List<Speaker> speakers) {
    // Process in smaller batches with separate transactions
    Lists.partition(speakers, 100).forEach(batch ->
        processBatch(batch)
    );
}

@Transactional(timeout = 10)
protected void processBatch(List<Speaker> batch) {
    batch.forEach(speaker -> {
        speaker.setStatus("processed");
        speakerPoolRepository.save(speaker);
    });
}
```

## Story-Specific Adaptations

### Story 5.5: Content Submission with External User Service

**Multi-Step Transaction with External Call:**
```java
@Transactional(
    isolation = Isolation.READ_COMMITTED,
    rollbackFor = Exception.class,
    timeout = 10
)
public SpeakerContentDto submitContent(String eventId, String poolId,
                                      SubmitContentRequest request) {
    // 1. Validate (in transaction)
    SpeakerPool speaker = validateSpeaker(poolId);

    // 2. External call (separate transaction via REQUIRES_NEW)
    String username = getOrCreateSpeakerUsername(request);

    // 3-5. Database writes (in transaction)
    Session session = createSession(eventId, request);
    createSessionUserLink(session, username);
    updateSpeakerStatus(speaker, session);

    // 6. Event publishing (processed after commit)
    publishStateChangeEvent(poolId, eventId, username);

    return toDto(speaker, session);
}
```

## Related Patterns

- See also: `backend/spring-boot-service-foundation.md` - Service layer structure
- See also: `backend/integration-test-pattern.md` - Testing transactional code
- See also: `backend/event-driven-idempotency-pattern.md` - Event handling patterns
