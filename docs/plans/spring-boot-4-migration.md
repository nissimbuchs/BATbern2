# Spring Boot 4 Migration Plan (Epic 13)

**Created:** 2026-06-07 · **Status:** planned · **Driver:** Spring Boot 3.5 OSS support ends **2026-06-30** ([spring.io support policy](https://spring.io/support-policy/)) — after that, no CVE patches on Maven Central for our current line (3.5.7).

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
| **Deprecated API removal** (~88% of 2.x/3.x deprecations gone) | Unknown until measured — that is Story 13-1's job | Zero-deprecation baseline BEFORE switching the version |
| **Modularized starters** | `build.gradle` per module; possible missing auto-config modules at boot | Pilot story discovers the real list once |
| **Servlet 6.1 / Tomcat 11** | Embedded Tomcat (we don't use Undertow — no exposure there) | Boot-smoke in integration tests; Testcontainers parity |
| **Hibernate 7 / JPA 3.2** | All entities, `AttributeConverter`s (enum lowercase mapping!), `@ElementCollection` LAZY rules | Testcontainers integration suites (mandatory PostgreSQL parity pays off here) |
| **Third-party matrix** | springdoc/OpenAPI Generator 7.2 (generated `*Api` interfaces!), Flyway, Testcontainers, JJWT, AWS SDK v2, resilience4j | Story 13-1 builds the compatibility matrix; OpenAPI Generator output must compile against Spring 7 annotations |

## Stories

> **Strategy revision (2026-06-13, per product owner).** The original 13-2/13-3 design was an
> *incremental mixed fleet* (pilot service on SB4, rest on 3.5.7). The PO chose a **single
> version source with no per-module pinning** — `org.springframework.boot` is declared
> version-less in every module and resolved once from `settings.gradle` pluginManagement (the
> way `event-management` already does it), so Dependabot's `chore` updates auto-bump it within
> 4.x. Consequence: the **whole estate moves to SB4 together** (no mixed fleet), so 13-2/13-3/13-4
> collapse into one unified SB4 migration vetted as a unit on the `feature/epic-13-spring-boot-4`
> branch before a single merge (the chosen hybrid). The shared-kernel binary-compat constraint
> above is **moot under this strategy** (everything is on SB4/Jackson together). Revised execution:
> **Phase 1** unify the plugin mechanism on the single settings.gradle source (still 3.5.7, green
> refactor) → **Phase 2** bump that one coordinate to 4.0.7 and migrate the estate (Jackson via
> the compat module first, springdoc 3.x, resilience4j-spring-boot4, Spring Security 7, Hibernate
> 7, Testcontainers 2.0) → **Phase 3** full test + Bruno/Playwright validation, then flip
> Jackson-2-compat defaults OFF.

### 13-1 — Zero-deprecation baseline + compatibility matrix (on 3.5.7)
- Turn on `-Xlint:deprecation`/`-Werror`-equivalent visibility; fix **every** Java deprecation warning across all modules (SB4 removes those APIs).
- Build the third-party compatibility matrix (springdoc, OpenAPI Generator, Flyway, Testcontainers/JUnit, JJWT, AWS SDK, resilience4j, Lombok, Checkstyle/Spotless toolchain) — pin the SB4-compatible target versions.
- Run OpenRewrite `UpgradeSpringBoot_3_5` (no-op confirm) and dry-run `UpgradeSpringBoot_4_0` to size the mechanical diff.
- **Deliverable:** green build with zero deprecation warnings; matrix table appended to this plan. Low risk, deployable.

### 13-2 — shared-kernel + pilot service (attendee-experience) to SB4
- Apply OpenRewrite `UpgradeSpringBoot_4_0` to `shared-kernel` + `services/attendee-experience-service` (smallest service); manual fixes on top.
- Jackson-2-compatible defaults flag ON. Shared-kernel types (`ErrorResponse`, EmailService, JwtRolesConverter — Pattern 3b must survive!) are the highest-leverage validation.
- `:shared-kernel:publishToMavenLocal` → pilot service full Testcontainers suite → deploy → Bruno + Playwright gates.
- **Gate:** staging (=prod) runs the pilot service on SB4 for ≥2 days with the other services on 3.5.7 (mixed fleet is fine — services share no Spring wire format, only HTTP+JSON).
- **Target version:** Spring Boot **4.0.7** (matches the spring-cloud 2025.1.2 train).

> **⚠️ shared-kernel binary-compatibility constraint (analysed 2026-06-13, governs the whole epic).**
> shared-kernel is NOT just a wire contract — it is a **compile-time `project(':shared-kernel')`
> dependency bundled into every service's bootJar**. While the 4 non-pilot services stay on
> 3.5.7 (Spring 6 / Jackson 2), shared-kernel's *compiled bytecode* must reference only symbols
> present in BOTH Spring 6/7 and Jackson 2/3, or those services hit `NoSuchMethodError` /
> `ClassNotFoundException` at runtime. Audit of shared-kernel's 59 classes:
> - **Jackson:** ~24 of ~34 imports are *annotations* (`@JsonProperty/@JsonIgnore/@JsonInclude`)
>   which keep the `com.fasterxml.jackson` coordinates even in Jackson 3 → safe. The ~10
>   `databind` usages (`ObjectMapper`, `JsonNode`, `SerializationFeature`, `JavaTimeModule`,
>   `JsonProcessingException`) are namespace-sensitive. **HARD RULE: keep shared-kernel's
>   databind on Jackson 2 (`com.fasterxml`) via SB4's `spring-boot-jackson2` compat module —
>   do NOT let OpenRewrite rewrite these to `tools.jackson`.** One jar then serves both fleets.
> - **Spring Security / context / web:** only binary-stable APIs (`Authentication`,
>   `GrantedAuthority`, `SimpleGrantedAuthority`, `SecurityContextHolder`, `oauth2.jwt.Jwt`,
>   `core.convert.Converter`, `@Configuration`, `web.bind`) — no 6→7 signature breaks. Safe.
> - **Consequence:** the pilot SERVICE (attendee) may rewrite ITS OWN databind to `tools.jackson`
>   (it's not a shared dependency), but shared-kernel may not until 13-4 flips the whole fleet.

### 13-3 — Remaining domain services (4 PRs: company-user-management, event-management, speaker-coordination, partner-coordination)
- Same recipe per service, one PR each, sequenced by blast radius (CUMS first — auth-critical, best-tested; EMS last — biggest).
- Each PR: full service suite + Bruno collections for that service + deploy + gate. Watch the Cognito trigger paths (CUMS) and email rendering (EMS) closely.

### 13-4 — api-gateway + estate-wide closeout
- Gateway last (it fronts everything; by now the pattern is proven). Spring Security 7 CSRF/DSL audit happens here for the final time.
- Flip the Jackson-2-compatible defaults flag OFF estate-wide (separate commit, full Bruno run = the payload-diff gate).
- Remove the compat module, update `docs/architecture/tech-stack.md` + `versions.json` + `_bmad-output/project-context.md` (Spring Boot 4.x line), close the epic.

## Risks & escape hatches

- **Rollback:** each story is one service = ECS auto-rollback on failed gate; image-level revert is one `update-ecs-task.sh` away.
- **Mixed-fleet window:** intentional (13-2/13-3); services interact only via HTTP+JSON, so framework versions may diverge safely. Jackson-3 payload drift across the fleet is the one cross-service risk — held down by the compat flag until 13-4.
- **3.5 EOL gap:** if 13-x slips past 2026-06-30, exposure is bounded — 3.5.7 is the final patch; monitor CVEs manually until 13-4 closes.

## References

- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Jackson 3 support in Spring](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/)
- [Spring Framework 7.0 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)
- [Spring support policy / EOL dates](https://spring.io/support-policy/)
- OpenRewrite recipe: `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0`

---

## Story 13-1 — Deliverables (on 3.5.7)

**Status:** in progress · branch `feature/epic-13-1-zero-deprecation-baseline` · started 2026-06-13

### Zero-deprecation baseline

`-Xlint:deprecation` is now enabled across **all 7 modules** via the root `build.gradle`
`allprojects { tasks.withType(JavaCompile) }` block, with a `-PfailOnDeprecation` flag
that adds `-Werror` (the enforcement gate — run `./gradlew classes testClasses -PfailOnDeprecation`).

Baseline measured 2026-06-13: **27 unique deprecation sites in 9 categories**, all resolved:

| Deprecated API | Sites | Fix |
|---|---|---|
| `new java.net.URL(String)` | 9 (1 main, 8 test) | `URI.create(s).toURL()` (test); kept + `@SuppressWarnings` in `SessionMaterialsService` where lenient parsing of space-bearing URLs is required (URL ctor is deprecated **not** for-removal) |
| `@MockBean` / `@SpyBean` (`o.s.boot.test.mock.mockito`, *marked for removal*) | 6 | → `@MockitoBean` / `@MockitoSpyBean` (`o.s.test.context.bean.override.mockito`) |
| `UriComponentsBuilder.fromHttpUrl(String)` | 4 | → `fromUriString(String)` |
| `SXSSFWorkbook.dispose()` | 2 | removed — redundant under existing try-with-resources `close()` (POI 5.x disposes temp files) |
| Lombok `@Builder` ignores field initializer | 2 (`SpeakerPool`) | added `@Builder.Default` (matches the existing `source` field pattern) |
| `XmlCursor.dispose()` | 1 | → `close()` |
| `BatchImportSessionRequest.getPdf()` (our own `@Deprecated` legacy field) | 1 | `@SuppressWarnings` — intentional backward-compat read of legacy `pdf` (superseded by `materialUrl`, Story 5.9) |
| `AuthorizationManager.check(...)` (Spring Security) | 1 | implemented logic in `authorize(...)` (the SS7 abstract method); thin `check()` delegate retained for SS6 — **13-3 deletes the `check` override** |
| `@Mock(lenient = true)` (Mockito) | 1 | → `@Mock(strictness = Mock.Strictness.LENIENT)` |

### Third-party SB4 compatibility matrix

Researched against the [SB4 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) / release notes, mid-2026.

| Dependency | Current | SB4 target | Coord. change? | Notes |
|---|---|---|---|---|
| spring-boot plugin + starters | 3.5.7 | **4.0.x** | No | Java 21 fine (SB4 baseline 17) |
| dependency-management-plugin | 1.1.7 | 1.1.7 | No | Consider SB-provided BOM instead |
| spring-cloud-dependencies BOM | 2025.1.1 | **2025.1.2** (Oakwood, 2026-06-11) | No | 2025.1.x is the SB4-aligned train; 2025.0.x is NOT |
| **springdoc-openapi-starter-webmvc-ui** | 2.8.14 | **3.0.x** (verify Jackson-3 patch) | No | **#1 friction.** Early 3.0.0/3.0.1 still bundled Jackson 2 → `ClassNotFoundException ...ObjectNode` on SB4. Conditional blocker — spike `/v3/api-docs` + Swagger UI first |
| swagger-annotations | 2.2.20 | 2.2.3x (let springdoc manage) | No | Annotations namespace-neutral |
| openapi-generator-gradle-plugin | 7.2.0 | **7.16/7.17** | No | Use `useSpringBoot3`/`useJakartaEe`; verify generated `*Api` compiles vs Spring 7 + Jackson 3 (issue #22294) |
| flyway plugin / core / db-postgresql | plugin 11.18.0; SK pins db-postgresql **12.5.0** | one Flyway 11.x line (SB4 manages 11.11) | No | **Reconcile the 12.5.0 skew in `shared-kernel` buildscript vs 11.x core** |
| **resilience4j-spring-boot3** | 2.4.0 | **resilience4j-spring-boot4 2.4.0** | **YES — artifact rename** | **#2 friction.** New module in 2.4.0; the resilience4j BOM omits it (#2423) → depend explicitly |
| aws-sdk BOM | 2.43.2 | 2.43.2+ | No | Independent of Spring |
| auth0 java-jwt / jwks-rsa | 4.5.2 / 0.23.1 | same | No | **Plan's "JJWT" assumption was wrong — gateway uses auth0 java-jwt, JJWT not present** |
| lombok | 1.18.36 | **1.18.40+** | No | Bump for SB4 / forward JDK AP |
| guava | 33.6.0-jre | BOM-managed | No | |
| angus-mail / jakarta.mail-api | 2.0.3 / 2.1.3 | BOM-managed | No | Already Jakarta namespace |
| caffeine | 3.2.3 | BOM-managed | No | |
| micrometer-registry-prometheus | managed | Micrometer 1.16 (BOM) | No | Don't pin |
| logstash-logback-encoder / logback-awslogs | 9.0 / 1.6.0 | 9.x / 1.6.0 | No | Verify vs SB4's managed Logback |
| postgresql | 42.7.11 / 42.7.7 | 42.7.x (BOM) | No | Consolidate to one 42.7.x |
| **testcontainers** | 1.21.3 | **2.0.x** | No (same group) | **Major bump** — review `AbstractIntegrationTest` singleton pattern |
| junit-jupiter | 5.11.3 | 5.13.x (BOM) | No | Don't pin |
| mockito | 5.23.0 | 5.20 managed (newer fine) | No | |
| assertj | 3.27.7 | 3.27.x (BOM) | No | |
| rest-assured | 6.0.0 | 6.0.x (BOM) | No | |
| checkstyle / jacoco / git-properties | 10.12.5 / 0.8.10 / 2.5.3 | build-tooling, no SB4 coupling | No | jacoco → 0.8.12+ if past Java 21 |

**Blockers / conditional:** springdoc 3.0.x Jackson-3 readiness (verify patch via spike); openapi-generator Jackson-3 model output (watch).
**Coordinate changes:** (1) resilience4j `-spring-boot3` → `-spring-boot4`; (2) Jackson `com.fasterxml.jackson:*` → `tools.jackson:*` (except `jackson-annotations`, which keeps `com.fasterxml.jackson.core`).
**Major version jumps to plan for:** Testcontainers 2.0; Spring Security 6→7; Hibernate 6→7.1; Tomcat 11; Micrometer 1.16.

### OpenRewrite dry-run sizing — deferred to 13-2 setup

Attempted via a throwaway init script (apply `org.openrewrite:plugin` + `rewrite-spring`
to every Java module, run `rewriteDryRun`). The `UpgradeSpringBoot_3_5` no-op confirm
**failed at the tooling layer** with `NoSuchMethodError: Environment.activateRecipes(Iterable)`
— the plugin's bundled rewrite-core and `rewrite-spring:latest.release` resolve to
incompatible rewrite-core versions when applied via init script.

**Decision:** the OpenRewrite *sizing* dry-run is folded into **Story 13-2 setup**, which
applies OpenRewrite for real on `shared-kernel` + the pilot service. 13-2 must wire it via
the `org.openrewrite:rewrite-recipe-bom` (in the `rewrite` configuration) with a plugin
version matched to the BOM so plugin-core == recipe-core. Doing the version-matrix work
there — where the recipe actually runs against source — is higher-value than an init-script
estimate here. The zero-deprecation baseline above already de-risks the mechanical diff
(the deprecation removals are the bulk of what `UpgradeSpringBoot_4_0` would mechanically
rewrite, and they are now pre-cleared on 3.5.7).

### 13-2 Phase 2 progress + remaining-work map (2026-06-13, branch `feature/epic-13-spring-boot-4`)

**Done & verified:**
- Gradle wrapper **8.12 → 8.14.5** (SB4 plugin requires 8.14+ / 9.x).
- Single source `settings.gradle` pluginManagement `org.springframework.boot` **3.5.7 → 4.0.7**.
- Jackson-2 compat: `org.springframework.boot:spring-boot-jackson2` added to root (services),
  api-gateway, shared-kernel — keeps `com.fasterxml` databind during the migration.
- springdoc-openapi `2.8.14 → 3.0.3` (root + api-gateway); resilience4j `-spring-boot3 →
  -spring-boot4` (shared-kernel); `spring-boot-starter-aop` (removed in SB4) → `aspectjweaver`.
- Lombok **un-pinned** in root build.gradle (was hard-pinned 1.18.36, which silently stopped
  processing under SB4 → ~90 phantom "cannot find symbol"); now BOM-managed like the others.
- **`shared-kernel` main compiles green on SB4.** Estate compile errors 326 → 238.

**✅ ALL MAIN SOURCE COMPILES GREEN ON SB 4.0.7** (326 → 0 main-source errors). Fixes applied:
- **Hibernate 7 `BindableType`** root cause — `hypersistence-utils-hibernate-63:3.9.0` →
  `-hibernate-71:3.15.3` (artifact is Hibernate-version-specific; SB4 ships Hibernate 7.2). This
  one fix collapsed event-management 196 → 14 (the rest were a Lombok-abort cascade).
- **`RestTemplateBuilder`** `org.springframework.boot.web.client` → `org.springframework.boot.restclient`
  (+ added `spring-boot-starter-restclient` to root services & api-gateway; not transitive from
  starter-web anymore). Sites: events/partner `RestClientConfig`, gateway `WebClientConfig`.
- **`@EntityScan`** → `org.springframework.boot.persistence.autoconfigure.EntityScan`.
- **Autoconfigure relocations**: `DataSourceAutoConfiguration` → `…boot.jdbc.autoconfigure`,
  `HibernateJpaAutoConfiguration` → `…boot.hibernate.autoconfigure`. Gateway's `exclude=` dropped
  entirely (no DB modules on its classpath in SB4 → nothing to exclude); attendee keeps the
  DataSource/Hibernate excludes but drops Flyway (the `spring-boot-flyway` autoconfig module isn't
  on its classpath).
- **`PropertyReferenceException`** → `org.springframework.data.core` (Spring Data 4).
- **Spring Security 7 `authorize`** — `VpcInternalAuthorizationManager` now overrides
  `authorize(Supplier<? extends Authentication>, …)` (the `? extends` wildcard) and the `check()`
  delegate is removed (gone from the SS7 interface). *(This supersedes the 13-1 deferral note.)*

**Remaining = TEST LAYER ONLY** (every module's MAIN compiles; service test sources mostly compile):
1. **Testcontainers 1.21.3 → 2.0.5** — SB 4.0.7 manages TC 2.0.5 via `testcontainers-bom`, but
   `io.spring.dependency-management` isn't exposing the nested module versions →
   `org.testcontainers:postgresql`/`junit-jupiter` resolve to empty. Blocks `shared-kernel`
   testFixtures (which blocks all service test compiles). Fix the BOM wiring (likely import
   `testcontainers-bom` explicitly or pin via platform), then expect TC 2.0 API changes in
   `AbstractIntegrationTest` (singleton container pattern) + the explicit `:1.21.3` pins in root.
2. **`@WebMvcTest` / `@AutoConfigureMockMvc`** moved out of `spring-boot-test-autoconfigure` into a
   dedicated SB4 webmvc-test module — add that dep (api-gateway hit it first; ~20 errors there).
3. **`TestRestTemplate`** `org.springframework.boot.test.web.client` → relocated (api-gateway
   `OpenApiConfigTest`).
- Then: per-`SecurityConfig` CSRF/DSL audit (6 configs), full test + Bruno (payload-diff) +
  Playwright, finally flip Jackson-2-compat OFF.

### Pre-existing tidy-ups surfaced (fold into 13-2/13-3)
- Flyway version skew: `shared-kernel/build.gradle` buildscript pins `flyway-database-postgresql:12.5.0` while the root plugin is `11.18.0` — reconcile to one 11.x line.
- `lombok 1.18.36` → `1.18.40+`.
- OpenRewrite tooling: pin plugin + `rewrite-recipe-bom` to matched versions (see above) before running `rewriteDryRun`/`rewriteRun`.
