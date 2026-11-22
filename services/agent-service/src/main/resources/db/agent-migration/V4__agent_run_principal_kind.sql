-- V4: whom a run acts for (agentic interaction, epic agent runtime, story 6; R6, AD-8, AD-15).
-- The principal kind of the run's CUMS grant (AgentGrant.principalKind), stored with grant_run_id.
-- Precedents match a proposed call on its tool and its run's principal kind (PrecedentMatchRule);
-- the tool name index serves that lookup. V1 to V3 stay untouched.

ALTER TABLE agent_runs
    -- Null until the grant is obtained, and for a run whose grant was refused
    ADD COLUMN principal_kind VARCHAR(32),
    ADD CONSTRAINT chk_agent_runs_principal_kind
        CHECK (principal_kind IS NULL OR principal_kind IN ('REQUESTER', 'ORGANIZER', 'ORGANIZER_DELEGATE')),
    -- A run with a grant knows whom it acts for
    ADD CONSTRAINT chk_agent_runs_principal_kind_with_grant
        CHECK ((grant_run_id IS NULL) = (principal_kind IS NULL));

CREATE INDEX idx_agent_tool_calls_tool_name ON agent_tool_calls (tool_name);
