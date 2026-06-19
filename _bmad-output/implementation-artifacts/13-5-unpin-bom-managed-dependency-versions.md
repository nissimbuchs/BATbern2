# Story 13.5: Unpin BOM-Managed Dependency Versions (Single Version Source Cleanup)

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **maintainer of the BATbern Java estate**,
I want the dependency versions that the Spring Boot BOM already manages to be **delegated to the BOM** instead of hard-pinned in our build files,
so that a single bump of the one Spring Boot coordinate moves the whole estate's transitive stack together — eliminating silent version drift and stale overrides like the `postgresql:42.7.7` pin that is currently holding us a patch line behind the BOM's `42.7.11`.

**Source:** Epic 13 (Spring Boot 4 Migration) follow-up, raised 2026-06-19 during the post-merge "are we on latest?" review. Extends the version-source pattern that 13-2/13-3/13-4 established (single SB coordinate in `settings.gradle pluginManagement` + version-less `plugins{}`). This is the dependency-coordinate analogue of that plugin-coordinate work.

> **Design (PO decision 2026-06-19):** This is a **targeted "unpin-to-BOM"**, NOT a "float everything" change. Dynamic/`latest.release`/`+` versions are explicitly rejected — they break build reproducibility and undermine the Bruno payload-diff + Playwright auto-rollback gates (a deploy could shift under us with no PR; staging IS prod). We remove ONLY the redundant explicit versions on coordinates the Spring Boot BOM already manages, and we **codify a pin policy** so future devs know which remaining pins are deliberate. Coordinates the BOM does NOT own — OpenAPI Generator, the Flyway Gradle plugin, build-tooling — stay explicitly pinned and are bumped via individual Dependabot PRs through the staging gate.

## Background data (verified against Maven Central, 2026-06-19)

The Spring Boot **4.0.7** BOM (`spring-boot-dependencies:4.0.7`) pins:

| Coordinate | BOM-managed version | Our current state |
|---|---|---|
| `org.postgresql:postgresql` | **42.7.11** (= Maven Central latest) | hard-pinned `42.7.7` (override → 4 patches behind) |
| `org.flywaydb:flyway-core` / `flyway-database-postgresql` | **11.14.1** | already version-less (BOM-managed) ✅ |
| `org.testcontainers:*` | **2.0.5** | BOM imported at 2.0.5 ✅ (latest) |
| `spring-framework` | 7.0.8 (= latest) | BOM-managed ✅ |
| `spring-security` | 7.0.6 | BOM-managed ✅ |
| `hibernate` | 7.2.19.Final | BOM-managed ✅ |
| `jackson` (2 compat / 3 native) | 2.21.4 / 3.1.4 | BOM-managed ✅ |
| `lombok` | 1.18.46 | BOM-managed ✅ (un-pinned in 13-2/3) |

Coordinates the BOM does NOT manage (must stay explicitly pinned):

| Coordinate | Pinned | Latest | Keep pinned because |
|---|---|---|---|
| OpenAPI Generator plugin (`org.openapitools:openapi-generator-gradle-plugin` + `id 'org.openapi.generator'`) | `7.2.0` | `7.14.0` | regenerates `*Api` interfaces — a bump can change generated signatures/nullability/payloads → deliberate story + regen + Bruno diff only |
| Flyway Gradle **plugin** (`id 'org.flywaydb.flyway'` + `flyway-gradle-plugin` classpath) | `11.18.0` | `12.9.0` | **HARD CONSTRAINT: Flyway 12.x is incompatible with Spring Boot 4 / Jackson 3 (already analysed) — must stay on the 11.x line, never float to 12.x.** Plugins aren't BOM-managed; the plugin only runs manual `flywayMigrate`/`flywayRepair` — runtime migrations use the BOM's `flyway-core 11.14.1` |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | `3.0.3` | `3.0.3` (latest) | third-party, not in SB BOM — already current |
| Checkstyle / JaCoCo toolchain | `10.12.5` / `0.8.10` | — | build tooling, intentionally stable |
| Gradle wrapper | `8.14.5` | — | build tooling, deliberate |

## Acceptance Criteria

1. The hard version override `runtimeOnly 'org.postgresql:postgresql:42.7.7'` (root `build.gradle`, service-subprojects block ~L118) is changed to the version-less `runtimeOnly 'org.postgresql:postgresql'` so it resolves to the SB BOM version (`42.7.11`). The **buildscript `classpath 'org.postgresql:postgresql:42.7.7'`** (~L17) is **left at `42.7.7`** (PO decision 2026-06-19): the `buildscript {}` block has no BOM so it must carry an explicit version, and the build-time Flyway-plugin driver does not need to track the runtime driver. A one-line comment marks it as a deliberate, BOM-less pin.
2. `flyway-core` / `flyway-database-postgresql` runtime deps remain version-less (no regression) and a code comment documents that they are BOM-managed (`11.14.1` under SB 4.0.7).
3. The **pin-policy** is documented in **BOTH** (PO decision 2026-06-19, Q3): (a) a short comment block in root `build.gradle`/`settings.gradle` next to the code it governs, and (b) `docs/architecture/tech-stack.md` where humans reason about upgrades. Both state: *what is delegated to the SB BOM* vs *what is deliberately pinned and why* (OpenAPI Generator, Flyway plugin, Checkstyle/JaCoCo, Gradle wrapper), that **Flyway must stay on 11.x (12.x breaks SB4/Jackson 3)**, and that **dynamic/floating versions are forbidden** (reproducibility + gate integrity).
4. `io.swagger.core.v3:swagger-annotations:2.2.20` is **folded into the unpin** (PO decision 2026-06-19, Q4): the explicit pin is removed and the annotation version comes transitively from `springdoc-openapi-starter-webmvc-ui`. The resolved `swagger-annotations` version after removal is recorded (verified via `dependencies` task), and the build + OpenAPI generation still succeed. No coordinate that the SB BOM does NOT manage is *silently* un-pinned — OpenAPI Generator `7.2.0`, Flyway plugin `11.18.0`, springdoc `3.0.3` stay exactly as-is.
5. `./gradlew :shared-kernel:publishToMavenLocal` then a full `./gradlew build` succeeds for the whole estate; the effective resolved `org.postgresql:postgresql` version is confirmed to be `42.7.11` (e.g. via `./gradlew :services:event-management-service:dependencies --configuration runtimeClasspath | grep postgresql`).
6. Every service's full test suite (unit + Testcontainers integration) stays green — the PostgreSQL JDBC patch bump (`42.7.7 → 42.7.11`) introduces no regression. Integration tests run against the real Testcontainers PostgreSQL (per project standard).
7. The Bruno API-contract suite passes unchanged on staging (payload-diff gate) and the Playwright @smoke gate is green — confirming the JDBC patch bump is behavior-neutral at the API boundary. (Runs through the existing deploy-staging gate; no special handling.)
8. No Flyway migration files are touched; no `**/db/migration/**` content changes (the JDBC driver bump must not be confused with a schema change). The Flyway plugin↔runtime skew (plugin `11.18.0` vs BOM runtime `11.14.1`) is **left as-is** — both are on the working **11.x** line and the plugin is NOT downgraded (PO decision 2026-06-19, Q2; no churn for no benefit). A comment records the hard constraint that **Flyway must not move to 12.x** (incompatible with SB4/Jackson 3) AND a guard against a future SB-BOM bump silently dragging `flyway-core` to 12.x (see Dev Notes / Task 2).

## Tasks / Subtasks

- [ ] **Task 1: Delegate postgresql to the BOM** (AC: 1, 5)
  - [ ] In root `build.gradle` service-subprojects block, drop the `:42.7.7` suffix from `runtimeOnly 'org.postgresql:postgresql'`.
  - [ ] **Leave** the buildscript-classpath postgresql (`~L17`) at `42.7.7` (Q1 decision) — add a one-line comment: `// buildscript has no BOM — explicit pin required (Flyway-plugin JDBC, build-time only)`.
  - [ ] Confirm resolved runtime version is `42.7.11` via `./gradlew :services:event-management-service:dependencies --configuration runtimeClasspath`.
- [ ] **Task 2: Document the BOM-managed runtime Flyway + 12.x guard** (AC: 2, 8)
  - [ ] Add/confirm a comment on the `flyway-core` / `flyway-database-postgresql` deps noting they are BOM-managed (`11.14.1` under SB 4.0.7) and that the runtime engine — not the Gradle plugin — executes migrations at boot.
  - [ ] **12.x guard:** because the BOM controls `flyway-core`, a future SB bump could silently drag it to 12.x (incompatible with SB4/Jackson 3). Add an explicit `dependencyManagement` override OR a comment+CI assertion that the resolved `flyway-core` stays `11.x`. (Pick the lightest mechanism that actually fails the build if Flyway resolves to ≥12.)
- [ ] **Task 3: Pin-policy in BOTH places** (AC: 3, 4)
  - [ ] (a) Concise policy comment block in root `build.gradle` (top of the service dependencies block) + `settings.gradle`: BOM-delegated vs deliberately-pinned, reason per deliberate pin, Flyway-stays-11.x, dynamic-versions-forbidden.
  - [ ] (b) Mirror the policy as a short section in `docs/architecture/tech-stack.md` (the human-facing home).
- [ ] **Task 3b: Fold in swagger-annotations** (AC: 4)
  - [ ] Remove the explicit `io.swagger.core.v3:swagger-annotations:2.2.20`; let it come transitively from springdoc. Record the resolved version (`dependencies` task) and confirm OpenAPI generation + Swagger UI still build/work.
- [ ] **Task 4: Verify the estate builds + tests green** (AC: 5, 6)
  - [ ] `./gradlew :shared-kernel:publishToMavenLocal` → `make build-java` → `make test-java` (Docker up for Testcontainers). Tee output to `/tmp`, grep for `FAILED`. Confirm no regression from the JDBC bump (`42.7.7→42.7.11`) or the swagger-annotations delegation.
- [ ] **Task 5: Gate verification** (AC: 7)
  - [ ] Bruno contract suite (`./scripts/ci/run-bruno-tests.sh`) + Playwright @smoke through the existing staging gate. The payload-diff gate is the authoritative check that the driver bump is behavior-neutral.
- [ ] **Task 6: Doc-drift** (AC: all)
  - [ ] Consult `.github/doc-drift-mappings.yml` for the changed paths (`build.gradle`, `settings.gradle`). If a tech-stack doc maps (e.g. `docs/architecture/tech-stack.md` lists pinned versions), update it in the same commit; otherwise add `[no-doc]`.

## Dev Notes

### What this is (and is NOT)
- **IS:** removing redundant explicit versions on SB-BOM-managed coordinates so the single SB coordinate is the version source; codifying the pin policy. Net code change is tiny (≈ a handful of lines) — the value is *correctness of the version-source model* + a durable policy comment.
- **IS NOT:** floating/dynamic versions, bumping OpenAPI Generator, bumping the Flyway plugin to 12.x, or jumping SB 4.0→4.1. Those are each their own deliberate, gated change (and 4.0→4.1 is out of Epic 13 scope per the plan's non-goals).

### Why the postgresql pin matters specifically
- The override `42.7.7` predates SB 4.0.7. The 4.0.7 BOM now pins `42.7.11` — so our override is *strictly worse* (4 patch releases of bugfixes/CVE behind) for zero benefit. Removing it is the single highest-value line in this story.

### Reproducibility / gate-integrity rationale (the "why not unpin all" answer, recorded for posterity)
- Dynamic versions make the same git SHA resolve different jars across builds → non-deterministic CI and "green yesterday, red today" with no code change.
- The migration's safety net (Bruno payload-diff + Playwright + ECS auto-rollback) only has meaning if the dependency inputs are FIXED between the known-good run and the next deploy. Floating deps means production (= staging) can shift with no reviewable PR. Hence: delegate to the BOM (still a fixed, tested-together set), never float.

### Project Structure Notes
- All changes are in `build.gradle` (root) + `settings.gradle` (root). No service-level `build.gradle` change expected. No migration, no Java, no frontend.
- Gradle commands run from repo root only (never `cd` into a service).

### References
- [Source: docs/plans/spring-boot-4-migration.md] (Epic 13 plan; this is the follow-up)
- [Source: settings.gradle] (single SB version source — `org.springframework.boot 4.0.7`)
- [Source: build.gradle] (service-subprojects dependency block ~L96-156; postgresql override ~L118; buildscript classpath ~L17)
- [Source: _bmad-output/project-context.md#technology-stack--versions]
- Spring Boot 4.0.7 BOM (`spring-boot-dependencies:4.0.7`): postgresql 42.7.11, flyway 11.14.1, framework 7.0.8, hibernate 7.2.19.Final, testcontainers 2.0.5 — verified Maven Central 2026-06-19.

## Open Questions

_All resolved by Nissim (PO) on 2026-06-19 — folded into the ACs/Tasks above; recorded here for traceability._

1. **Buildscript-classpath postgresql** → **Leave at `42.7.7`.** The buildscript has no BOM (build-time Flyway-plugin JDBC only, never production), so it keeps an explicit pin; no need to track the runtime driver. (AC1, Task 1.)
2. **Flyway plugin↔runtime skew** → **Leave as-is; do NOT downgrade the plugin.** Already analysed: **Flyway 12.x does not work with Spring Boot 4 / Jackson 3**, so Flyway stays on the 11.x line. Plugin `11.18.0` and BOM runtime `11.14.1` are both 11.x and both work; reconciling them is churn for no benefit. Add a guard so a future SB-BOM bump can't silently pull `flyway-core` to 12.x. (AC8, Task 2.)
3. **Pin-policy home** → **Both** — comment block in `build.gradle`/`settings.gradle` AND a section in `docs/architecture/tech-stack.md`. (AC3, Task 3.)
4. **swagger-annotations** → **Fold in.** Remove the explicit `2.2.20` pin; take it transitively from springdoc; record the resolved version and confirm OpenAPI gen still works. (AC4, Task 3b.)
