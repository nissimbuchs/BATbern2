-- V42: agentic interaction, epic agent identity, story 5. A run grant: one agent run for one
-- principal, valid for at most one hour. The agent token of a run (story 6) is minted only against an
-- active grant. The grant secret is returned once by POST /internal/agent-grants and stored here only
-- as its SHA-256 hash (hex).
--
-- run_id is a random UUID string (36 characters, fits the 147-character actor of V40). The trigger
-- decides the principal: ems_handoff -> requester, organizer_login -> organizer, linked_run and
-- scheduler_job -> organizer_delegate. linked_run_id is set exactly for linked_run, job_name exactly
-- for scheduler_job. The username is a profile: the grant goes with a deleted profile and follows a
-- renamed one (as agent_allowlist, V41). No row is seeded (C3).
CREATE TABLE agent_grants (
    run_id         VARCHAR(36)  PRIMARY KEY,
    principal_kind VARCHAR(30)  NOT NULL,
    username       VARCHAR(100) NOT NULL,
    trigger_kind   VARCHAR(30)  NOT NULL,
    linked_run_id  VARCHAR(36),
    job_name       VARCHAR(100),
    secret_hash    VARCHAR(64)  NOT NULL,
    requested_by   VARCHAR(255) NOT NULL,
    issued_at      TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_agent_grants_user FOREIGN KEY (username)
        REFERENCES user_profiles (username) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT chk_agent_grants_principal_kind
        CHECK (principal_kind IN ('requester', 'organizer', 'organizer_delegate')),
    CONSTRAINT chk_agent_grants_trigger_kind
        CHECK (trigger_kind IN ('ems_handoff', 'organizer_login', 'linked_run', 'scheduler_job')),
    CONSTRAINT chk_agent_grants_principal_for_trigger CHECK (
        (trigger_kind = 'ems_handoff' AND principal_kind = 'requester')
        OR (trigger_kind = 'organizer_login' AND principal_kind = 'organizer')
        OR (trigger_kind IN ('linked_run', 'scheduler_job') AND principal_kind = 'organizer_delegate')),
    CONSTRAINT chk_agent_grants_linked_run
        CHECK ((trigger_kind = 'linked_run') = (linked_run_id IS NOT NULL)),
    CONSTRAINT chk_agent_grants_job
        CHECK ((trigger_kind = 'scheduler_job') = (job_name IS NOT NULL)),
    CONSTRAINT chk_agent_grants_secret_hash CHECK (secret_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_agent_grants_lifetime
        CHECK (expires_at > issued_at AND expires_at <= issued_at + interval '1 hour')
);
