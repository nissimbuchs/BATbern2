# Null vs optional in API responses: options for the owner

2026-11-09, 17.0 deferred work. Owner decides. Nothing in API behaviour or generator config was
changed for this memo.

**Decided 2026-11-09: option (A).** Done the same day: the specs mark nullable what the services
send as null (users-api 3f6e5400, EMS specs 91fc77b3, partners-api 3cacd549), and the S2S harness no
longer lets an optional property be null (see deferred-work.md). The set came from validating every
CUMS, EMS and PCS integration response against the specs plus reading the producing code per
schema: 141 properties (123 in responses, 18 in S2S request bodies), not the 499 of the count below (most optional properties are always set,
or their class omits nulls).

## The question

The services send an unset optional field as `"field": null`. The specs mostly declare such
fields optional and not nullable, so the generated frontend types say `bio?: string`, never
`null`. The types therefore promise "absent or a string" while the wire carries "null or a
string". Both test harnesses (S2S `GeneratedCodeSemantics` rule 2, the MSW harness) accept `null`
for an optional property on purpose, so neither notices.

## What I measured (run, 2026-11-09)

**Wire, local stack on the anonymized DB** (CUMS, EMS, PCS started natively, anonymous GETs,
only key names and null counts recorded):

| Request | `null` fields in the answer |
|---|---|
| CUMS `GET /companies/{name}`, `/companies/search` | none (class-level `NON_NULL` on the company DTOs) |
| CUMS `GET /public/users/{username}` | `profilePictureUrl` |
| CUMS `GET /public/organizers` | `bio` (4 of the list) |
| EMS `GET /events`, `/events/{code}`, `/public/events` | none (`Event`, `EventDetail`, `EventResponse` are `NON_NULL`) |
| EMS `GET /events/{code}/sessions` | `description`, `room`, `capacity`, `speakers`, `materialsStatus`, `materials`, `language` |
| EMS `GET /topics` (anonymous projection) | `description`, `createdDate`, `stalenessScore`, `colorZone`, `status`, `similarityScores`, `createdAt`, `updatedAt`, `usageHistory` |
| EMS `GET /events/{code}/registrations/{code}` | `waitlistPosition`, and `eventTitle`, `eventDate` (a real bug, fixed in 233a5bed) |
| PCS `GET /partners?include=company` | `partnershipEndDate`, `company.location`, `contacts` |

Not measured: authenticated endpoints (no token used; refreshing one would touch staging
Cognito). Earlier evidence: the 17.0b1 CUMS provider contract test saw `"bio": null` in
`UserResponse`; the 17.0c1 capture of `listUsers` kept `termsAcceptedAt: null`.

**Why, from the code (read, not run):**
- `shared-kernel` `SharedObjectMapperConfig` defines a plain `ObjectMapper` bean. Spring Boot's
  Jackson 2 auto-configuration backs off, so it is the mapper of every HTTP converter in CUMS,
  EMS and PCS. It sets no inclusion, so Jackson's default `ALWAYS` applies: every unset field is
  written as `null`. Its Javadoc says so, and that `spring.jackson2.*` has no effect.
- CUMS `application.yml` sets `spring.jackson2.default-property-inclusion: non_null`. That line is
  dead configuration (the measured `bio: null` and `profilePictureUrl: null` confirm it). It reads
  as if CUMS omitted nulls; it does not. (Removed 2026-11-09 on owner instruction, behaviour
  unchanged; the decision below is still open.)
- Classes that omit nulls do so by their own `@JsonInclude(NON_NULL)`. Generated DTOs with it:
  EMS 6 of 340, PCS 1 of 27, CUMS 15 of 74 (the company DTOs, via
  `additionalModelTypeAnnotations`); plus some hand-written DTOs (`EventResponse`,
  `MailMessage`).

**Specs** (script over `docs/api/*.openapi.yml`, schemas reachable from a 2xx JSON response):
231 schemas, 699 optional properties, of which 200 are marked nullable and **499 are not**. The
heaviest: event-speakers 119, events-core 82, users 66, event-sessions 53, partners 33.
The reverse also exists: `Event` marks 11 properties `nullable` although its class is
`NON_NULL` and never sends null (one of them, `sessions`, was corrected in f70b60a0).

**Frontend** (grep of `src`, tests and generated code excluded):
- 42 `=== undefined` / `!== undefined` checks; none of them reads a field measured null on the
  wire for the caller that reaches that code (topic `usageCount` and `stalenessScore` are
  numbers for organizers, who are the only users of those screens).
- Default-value destructuring (`{ speakers = [] }`, which a `null` passes straight through): no
  case on an API field.
- `.length` / `.map` on the measured-null arrays (`speakers`, `materials`, `contacts`,
  `similarityScores`, `usageHistory`): every use is guarded by a truthiness check or `?.`.
- 85 `=== null` / `!== null` checks. One depends on `null` versus absent: `ProtectedRoute`
  forces the terms-of-service consent when `user.termsAcceptedAt === null` and deliberately lets
  `undefined` through (fail open when `/users/me` did not hydrate).

So no frontend bug from null was found in this scan. That is a statement about these patterns,
not a proof: a component that renders a measured-null value as text (`{session.room}`) shows
nothing, which is usually what absence would show too.

## Options

### (A) The specs mark these fields `nullable`

The spec tells the truth about the wire; generated types gain `| null`.

- Work: up to 499 properties, less those in `NON_NULL` classes (not separated in this count).
  Mechanical, but it has to be done per field against the producing class, not by sweep, or the
  `NON_NULL` classes get the opposite lie (as `Event` has today).
- Effect: `npm run type-check` then shows every place that assigns or passes such a value where
  `undefined` is expected. Size of that fallout not measured; it is likely in the hundreds of
  call sites, most of them `?? undefined` noise.
- API behaviour: unchanged. Java models: unchanged (`openApiNullable: false`).
- Then: S2S rule 2 and the MSW tolerance can be removed, so a new unset field that the spec
  does not call nullable fails a test.

### (B) The services omit nulls (`NON_NULL` / `NON_ABSENT`)

One switch: `SharedObjectMapperConfig` sets `NON_NULL` (the dead CUMS `spring.jackson2` block
is already gone). The wire then matches the current types.

- Work: small in code, but an API behaviour change for every consumer: the web frontend, the
  Watch app, Bruno tests, the S2S clients, anything external.
- Known break: the terms-of-service gate. `termsAcceptedAt` would be absent instead of `null`, the
  gate reads that as "not hydrated" and lets users without consent through. Every field the spec
  marks `nullable` on purpose (200) needs `@JsonInclude(ALWAYS)` or a frontend change, field by
  field, before the switch.
- Watch app (Swift `Codable`, synthesized optionals, no `decodeIfPresent`): absent and `null`
  both decode to `nil`, so it is not affected (read, not run).
- Bruno: any `res.body.x === null` assertion breaks; not counted.
- Then: the harnesses can treat `null` on a non-nullable optional as a violation.

### (C) Status quo, harnesses stay tolerant

- Work: none. Update the misleading CUMS `spring.jackson2` comment so nobody relies on it.
- Risk: the types keep lying. Today's code survives because it guards with truthiness; the next
  `=== undefined` or `= []` default on an API field is a latent bug that no harness catches.

## Recommendation

**(A), done per schema as the touch rule does for tests, plus the clean-up from (C) now.**

Reasons: it changes no API behaviour (the owner's constraint for this question, and the
consent gate shows that (B) has real, non-obvious breaks); it makes the generated types true,
which is what the MSW harness and `type-check` rely on; and it is the only option after which
the harness tolerance can be removed. Concretely:

1. Now (no decision needed, cosmetic): replace the CUMS `spring.jackson2.default-property-inclusion`
   line with a comment that the shared mapper writes nulls.
2. A story: mark `nullable` every optional property whose producing class is not `NON_NULL`,
   spec by spec (users, sessions, speakers, topics, partners first: they carry the measured
   nulls), and drop `nullable` where the class is `NON_NULL` (`Event`'s remaining ten). Fix the
   type-check fallout in the same PR.
3. After the last spec: remove S2S rule 2 and the MSW tolerance, so a spec that calls a field
   non-nullable while the service sends `null` fails a test.

If the owner prefers a smaller wire and fewer `| null` in the types, (B) is viable only with a
field-by-field audit of the 200 intentionally nullable properties first, the consent gate
included.
