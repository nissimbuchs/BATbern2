# Watch App Development Patterns

Mandatory patterns for all Watch API backend stories (W-series epics).
Derived from Epic 2 retrospective findings (2026-02-17).

---

## 1. Security Patterns

### 1.1 Use `SecureRandom` for All Security-Sensitive Random Values

Never use `ThreadLocalRandom` or `Math.random()` for values that affect access control, authentication, or pairing.

**Rule:** Any token, code, or identifier used for authentication or authorization MUST be generated with `SecureRandom`.

```java
// ✅ Correct — WatchPairingService.java
private static final SecureRandom SECURE_RANDOM = new SecureRandom();

private String generateUniqueCode() {
    for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
        int raw = SECURE_RANDOM.nextInt(900_000) + 100_000; // 100000–999999
        String code = String.valueOf(raw);
        if (watchPairingRepository.findByPairingCode(code).isEmpty()) {
            return code;
        }
    }
    throw new IllegalStateException(
            "Unable to generate a unique pairing code after " + MAX_CODE_RETRIES + " attempts");
}

// ❌ Wrong — found in W2.1 pre-review, replaced in code review
int raw = ThreadLocalRandom.current().nextInt(900_000) + 100_000;
```

**Why:** `ThreadLocalRandom` is a pseudo-random generator seeded from system time. It is predictable under a timing attack. `SecureRandom` uses OS entropy and is cryptographically unpredictable.

**Applies to:** Pairing codes, pairing tokens, session tokens, any value a client uses to prove identity or claim a resource.

---

### 1.2 Prevent TOCTOU Races with `@Transactional` and Pessimistic Locks

Time-of-Check / Time-of-Use (TOCTOU) races occur when a check and the subsequent write are not atomic. A second concurrent request can pass the check between the first request's check and write.

**Rule:** Any operation that checks a condition and then modifies state based on that check MUST execute within a single `@Transactional` boundary. Use pessimistic locking (`SELECT ... FOR UPDATE`) when enforcing limits under concurrent load.

```java
// ✅ Correct — generatePairingCode() uses pessimistic lock on the count check
@Transactional
public PairingCodeResponse generatePairingCode(String username) {
    // Pessimistic lock: concurrent requests queue here, only one passes at a time
    long pairedCount = watchPairingRepository.countPairedWatchesForUpdate(username);
    if (pairedCount >= MAX_WATCHES) {
        throw new MaxWatchesExceededException();
    }
    // ... generate and save — all in one transaction
}

// ✅ Correct — claimPairingCode() validates AND clears atomically in one transaction
@Transactional
public Optional<WatchPairing> claimPairingCode(String code, String pairingToken) {
    Optional<WatchPairing> opt = watchPairingRepository.findByPairingCode(code)
            .filter(p -> !p.isCodeExpired());
    if (opt.isEmpty()) {
        return Optional.empty();
    }
    WatchPairing p = opt.get();
    p.setPairingToken(pairingToken);
    p.setPairedAt(LocalDateTime.now());
    p.clearPairingCode(); // Single-use — clear atomically in same transaction
    watchPairingRepository.save(p);
    return Optional.of(p);
}

// ❌ Wrong — two separate transactions allow two requests to both claim the same code
public Optional<WatchPairing> claimPairingCode(String code, String pairingToken) {
    Optional<WatchPairing> opt = validateCode(code);   // transaction 1 ends here
    if (opt.isEmpty()) return Optional.empty();
    return Optional.of(saveClaimedCode(opt.get(), pairingToken)); // transaction 2 — race window
}
```

**TOCTOU checklist for Watch stories:**

| Scenario | Mitigation |
|----------|-----------|
| Enforcing a max-count limit (e.g., max 2 watches) | `SELECT ... FOR UPDATE` via a `ForUpdate` repository method |
| Single-use code/token validation | Validate + clear + save in one `@Transactional` method |
| State transition that must only happen once | Optimistic lock (`@Version`) or pessimistic lock |
| Resource claim (only one requester succeeds) | `@Transactional` + check-then-write in same method |

---

### 1.3 Input Validation

Validate all Watch client inputs at the controller boundary before passing to the service layer.

**Checklist per Watch endpoint:**
- 6-digit pairing codes: reject anything not matching `\d{6}` before DB lookup
- `eventCode`: validate format (non-null, matches known pattern) before any query
- Speaker usernames in arrival confirmations: non-null, non-empty
- Pagination parameters (if added): positive integers with a max cap

Use `@Valid` + Jakarta Bean Validation annotations on request DTOs, not inline null checks in service methods.

---

## 2. Database Query Patterns

### 2.1 Always Batch-Load Speaker Collections — Never N+1

Any Watch API endpoint that returns speaker data across multiple sessions MUST load all speakers in a single query. Loading speakers one-by-one inside a session loop is an N+1 and will degrade under realistic event sizes (10+ sessions × 2-4 speakers each = 20-40 queries).

**The pattern (from `WatchEventController.mapToActiveEventDetail`):**

```java
// ✅ Correct — batch load all speakers for an event in one query
Set<String> speakerUsernames = sessions.stream()
        .flatMap(s -> s.getSessionUsers().stream())
        .map(SessionUser::getUsername)
        .collect(Collectors.toSet());

Map<String, Speaker> speakerMap = speakerRepository.findAllByUsernameIn(speakerUsernames)
        .stream()
        .collect(Collectors.toMap(Speaker::getUsername, Function.identity()));

// Then pass the map into each session mapper — zero additional queries
List<SessionDetail> sessionDetails = sessions.stream()
        .map(session -> mapToSessionDetail(session, speakerMap))
        .collect(Collectors.toList());

// ❌ Wrong — N+1: one query per session
List<SessionDetail> sessionDetails = sessions.stream()
        .map(session -> {
            List<Speaker> speakers = session.getSessionUsers().stream()
                    .map(su -> speakerRepository.findByUsername(su.getUsername()).orElse(null))
                    .toList();
            return mapToSessionDetail(session, speakers);
        })
        .collect(Collectors.toList());
```

**Repository requirement:** Any repository used to load speaker collections for Watch endpoints MUST expose a `findAllByUsernameIn(Collection<String> usernames)` method (Spring Data derived query — no custom JPQL needed).

```java
// SpeakerRepository.java
List<Speaker> findAllByUsernameIn(Collection<String> usernames);
```

---

### 2.2 Batch-Load Rule Scope

This pattern applies whenever a Watch endpoint:
- Returns a list of sessions, each of which contains speakers
- Returns arrival/presence status across multiple speakers
- Returns any aggregation over speaker data (counts, roles)

**It does NOT apply to:** Single-speaker lookups (e.g., fetching one speaker's bio by username). Those are fine as direct queries.

---

### 2.3 Count Queries for Scalar Aggregations

When you only need a count, use a `@Query` count method rather than loading entities and calling `.size()`.

```java
// ✅ Correct — W2.4: countDistinctSpeakersByEventCode added to fix N+1 on counter
@Query("SELECT COUNT(DISTINCT su.username) FROM SessionUser su WHERE su.session.event.eventCode = :eventCode")
long countDistinctSpeakersByEventCode(@Param("eventCode") String eventCode);

// ❌ Wrong — loads all speaker entities just to count them
int count = speakerRepository.findAllByEventCode(eventCode).size();
```

---

## Story Template Checklist

Add these checks to the pre-implementation adversarial review for every W-series story:

### Security
- [ ] Any randomly generated code or token uses `SecureRandom` (not `ThreadLocalRandom`, not `Math.random()`)
- [ ] Any check-then-write operation is within a single `@Transactional` boundary
- [ ] Limit-enforcement queries (max count checks) use a pessimistic lock (`ForUpdate` method)
- [ ] All request DTOs have `@Valid` + Bean Validation annotations on the controller parameter

### Queries
- [ ] No `findByUsername()` or similar single-entity lookup inside a loop over sessions or speakers
- [ ] Speaker collections loaded with `findAllByUsernameIn()` and mapped via a `Map<String, Speaker>`
- [ ] Count-only aggregations use `@Query COUNT` methods, not `.size()` on loaded collections

---

*Document created: 2026-02-17 | Source: Epic 2 retrospective (D2/D1 debt items, security bugs W2.1/W2.2)*
