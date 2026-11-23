-- V3: the run's identity (agentic interaction, epic agent runtime, story 4; R2, AD-3, AD-15).
-- A run acts only with a CUMS-minted agent token. Before its first model call it obtains a run
-- grant from CUMS (or is handed one by EMS) and mints a token against it. The grant's run id is
-- stored here, so `agent:<run>` in another service's audit column leads to this row. A refused
-- grant or mint hands the run to the organizers (GRANT_REFUSED, TOKEN_REFUSED) and stores the CUMS
-- code as end_detail. V1 and V2 stay untouched.

ALTER TABLE agent_runs
    -- The run id of the CUMS grant (AgentGrant.runId, act.run of the run's tokens); null until
    -- the grant is obtained, and for a run whose grant was refused
    ADD COLUMN grant_run_id UUID,
    -- The machine-readable detail of the end reason: the CUMS details.code of a refusal,
    -- HTTP_<status>, CUMS_UNREACHABLE, CALLER_SECRET_MISSING or UNKNOWN_CODE; never free text
    ADD COLUMN end_detail   VARCHAR(64),
    -- One grant, one run
    ADD CONSTRAINT uq_agent_runs_grant_run_id UNIQUE (grant_run_id),
    ADD CONSTRAINT chk_agent_runs_end_detail
        CHECK (end_detail IS NULL OR end_detail ~ '^[A-Z][A-Z0-9_]{0,63}$');

-- The refusal reasons join the end reasons
ALTER TABLE agent_runs
    DROP CONSTRAINT chk_agent_runs_end_reason,
    ADD CONSTRAINT chk_agent_runs_end_reason
        CHECK (end_reason IS NULL OR end_reason IN ('COMPLETED', 'MODEL_UNAVAILABLE', 'MODEL_MAX_TOKENS',
            'MODEL_STOPPED', 'STEP_LIMIT', 'INTERNAL_ERROR', 'GRANT_REFUSED', 'TOKEN_REFUSED'));
