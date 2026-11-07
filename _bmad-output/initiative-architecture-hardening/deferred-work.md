- source_plan: `_bmad-output/initiative-architecture-hardening/tickets/epic-test-fidelity/tickets/story-test-rules-and-ratchet-plan.md`
  summary: The ratchet does not count own-code mocks made outside `@MockitoBean`: `TestUserApiClientConfig` (`@Bean @Primary UserApiClient mock(...)`, referenced by 10 EMS test files) and `@MockitoSpyBean` (5 files), so a new IT importing that config raises no count.
  evidence: 17.0a review finding 4 (verified: `services/event-management-service/src/test/java/ch/batbern/events/config/TestUserApiClientConfig.java`). Natural home is 17.0b part 1 (0b1), which replaces the `UserApiClient` mock with the contract harness; count `@Import(TestUserApiClientConfig.class)` or retire the config there.
- source_plan: `_bmad-output/initiative-architecture-hardening/tickets/epic-dead-surface-and-gates/tickets/story-event-bus-gone-domain-events-in-process-plan.md`
  summary: `infrastructure/CLAUDE.md` stack list (around lines 182-191) jumps from 8 (SES) to 10 (Cluster Stack) after the EventBus stack entry was removed.
  evidence: 17.2 part B review; cosmetic numbering in an agent-context file, deferred per the review rules.
