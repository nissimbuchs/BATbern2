# Spring Boot 4 Migration Plan (Epic 14)

**Created:** 2026-06-07 · **Status:** planned · **Start gate: NOT before 2026-06-19** (BATbern #57 event day — nothing that could endanger prod deploys before the event has passed) · **Driver:** Spring Boot 3.5 OSS support ends **2026-06-30** ([spring.io support policy](https://spring.io/support-policy/)) — after that, no CVE patches on Maven Central for our current line (3.5.7).

## Scope & Goal

Migrate the whole Java estate — `shared-kernel`, `api-gateway`, 5 domain services — from Spring Boot 3.5.7 to Spring Boot 4.0.x (Spring Framework 7, Spring Security 7, Jackson 3, Jakarta EE 11 / Servlet 6.1, Hibernate ORM 7). Java 21 LTS stays (SB4 baseline is 17; 21 is fine — evaluate 25 separately, NOT in this epic).

**Non-goals:** no feature work, no refactors beyond what the upgrade forces, no Java 25, no dependency adventures unrelated to the SB4 tree. Frontend/CDK untouched except where build tooling interacts (none expected).

## Why staged, and why now

- One big-bang PR across 7 Gradle modules is unreviewable and undeployable-by-parts. Each story below is an independently deployable, independently revertable PR.
- The blocking Bruno + Playwright staging gates with auto-rollback (PRs #664–#674, #691–#703) are precisely the safety net a framework migration needs — especially for Jackson 3, whose risk is **silent payload changes**, which Bruno contract tests detect by construction.
- The deferred-work backlog stays parked until this epic completes (exception: Cluster A auth-hardening may interleave; nothing else).

## Known breaking changes that hit US specifically

| Change | Our exposure | Mitigation |
|---|---|---|
| **Jackson 3** (`com.fasterxml.jackson` → `tools.jackson`, immutable `JsonMapper`, LanguageTag locale serialization, changed defaults) | Every API payload; the project's enum-flow rule (UPPER_CASE JSON) must survive byte-identical; shared-kernel `ErrorResponse`/`PaginationMetadata` | Start with SB4's Jackson-2-compatible-defaults flag ON; Bruno suite = payload diff detector; flip flag OFF as a separate, final story task |
| **Spring Security 7** — CSRF enabled for API endpoints by default; lambda-only DSL | api-gateway + all 6 service `SecurityConfig`s (incl. the dual-permitAll pattern, token-credentialed public endpoints) | Explicit `csrf.disable()` audit per config (stateless JWT APIs); our configs are already lambda-DSL |
| **Deprecated API removal** (~88% of 2.x/3.x deprecations gone) | Unknown until measured — that is Story 14-1's job | Zero-deprecation baseline BEFORE switching the version |
| **Modularized starters** | `build.gradle` per module; possible missing auto-config modules at boot | Pilot story discovers the real list once |
| **Servlet 6.1 / Tomcat 11** | Embedded Tomcat (we don't use Undertow — no exposure there) | Boot-smoke in integration tests; Testcontainers parity |
| **Hibernate 7 / JPA 3.2** | All entities, `AttributeConverter`s (enum lowercase mapping!), `@ElementCollection` LAZY rules | Testcontainers integration suites (mandatory PostgreSQL parity pays off here) |
| **Third-party matrix** | springdoc/OpenAPI Generator 7.2 (generated `*Api` interfaces!), Flyway, Testcontainers, JJWT, AWS SDK v2, resilience4j | Story 14-1 builds the compatibility matrix; OpenAPI Generator output must compile against Spring 7 annotations |

## Stories

### 14-1 — Zero-deprecation baseline + compatibility matrix (on 3.5.7)
- Turn on `-Xlint:deprecation`/`-Werror`-equivalent visibility; fix **every** Java deprecation warning across all modules (SB4 removes those APIs).
- Build the third-party compatibility matrix (springdoc, OpenAPI Generator, Flyway, Testcontainers/JUnit, JJWT, AWS SDK, resilience4j, Lombok, Checkstyle/Spotless toolchain) — pin the SB4-compatible target versions.
- Run OpenRewrite `UpgradeSpringBoot_3_5` (no-op confirm) and dry-run `UpgradeSpringBoot_4_0` to size the mechanical diff.
- **Deliverable:** green build with zero deprecation warnings; matrix table appended to this plan. Low risk, deployable.

### 14-2 — shared-kernel + pilot service (attendee-experience) to SB4
- Apply OpenRewrite `UpgradeSpringBoot_4_0` to `shared-kernel` + `services/attendee-experience-service` (smallest service); manual fixes on top.
- Jackson-2-compatible defaults flag ON. Shared-kernel types (`ErrorResponse`, EmailService, JwtRolesConverter — Pattern 3b must survive!) are the highest-leverage validation.
- `:shared-kernel:publishToMavenLocal` → pilot service full Testcontainers suite → deploy → Bruno + Playwright gates.
- **Gate:** staging (=prod) runs the pilot service on SB4 for ≥2 days with the other services on 3.5.7 (mixed fleet is fine — services share no Spring wire format, only HTTP+JSON).

### 14-3 — Remaining domain services (4 PRs: company-user-management, event-management, speaker-coordination, partner-coordination)
- Same recipe per service, one PR each, sequenced by blast radius (CUMS first — auth-critical, best-tested; EMS last — biggest).
- Each PR: full service suite + Bruno collections for that service + deploy + gate. Watch the Cognito trigger paths (CUMS) and email rendering (EMS) closely.

### 14-4 — api-gateway + estate-wide closeout
- Gateway last (it fronts everything; by now the pattern is proven). Spring Security 7 CSRF/DSL audit happens here for the final time.
- Flip the Jackson-2-compatible defaults flag OFF estate-wide (separate commit, full Bruno run = the payload-diff gate).
- Remove the compat module, update `docs/architecture/tech-stack.md` + `versions.json` + `_bmad-output/project-context.md` (Spring Boot 4.x line), close the epic.

## Risks & escape hatches

- **Rollback:** each story is one service = ECS auto-rollback on failed gate; image-level revert is one `update-ecs-task.sh` away.
- **Mixed-fleet window:** intentional (14-2/14-3); services interact only via HTTP+JSON, so framework versions may diverge safely. Jackson-3 payload drift across the fleet is the one cross-service risk — held down by the compat flag until 14-4.
- **3.5 EOL gap:** if 14-x slips past 2026-06-30, exposure is bounded — 3.5.7 is the final patch; monitor CVEs manually until 14-4 closes.

## References

- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Jackson 3 support in Spring](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/)
- [Spring Framework 7.0 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)
- [Spring support policy / EOL dates](https://spring.io/support-policy/)
- OpenRewrite recipe: `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0`
