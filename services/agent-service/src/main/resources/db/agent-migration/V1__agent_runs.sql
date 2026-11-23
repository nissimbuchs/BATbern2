-- V1: agent-service (agentic interaction, epic agent runtime, story 1). The runs of the agent loop,
-- read by organizers through GET /api/v1/agent/runs. agent-service owns every agent_run* table;
-- no other service reads it (AD-9). Story 2 extends it with V2.
CREATE TABLE agent_runs (
    id          UUID         PRIMARY KEY,
    status      VARCHAR(32)  NOT NULL,
    started_at  TIMESTAMPTZ  NOT NULL,
    ended_at    TIMESTAMPTZ,
    CONSTRAINT chk_agent_runs_status
        CHECK (status IN ('RUNNING', 'DONE', 'FAILED', 'HANDED_TO_ORGANIZERS')),
    CONSTRAINT chk_agent_runs_ended_after_start
        CHECK (ended_at IS NULL OR ended_at >= started_at)
);

-- The read API lists newest first
CREATE INDEX idx_agent_runs_started_at ON agent_runs (started_at DESC);
