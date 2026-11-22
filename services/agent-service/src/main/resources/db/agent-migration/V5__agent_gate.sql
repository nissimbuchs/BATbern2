-- V5: the autonomy gate (agentic interaction, epic agent runtime, story 7; R5, R6, AD-7, AD-8).
-- The gate writes agent_verdicts (V2) before every non-read tool call and, for a held call, an
-- agent_approvals row in state PENDING. A held call never runs, so its tool call ends
-- PENDING_APPROVAL or REFUSED. The per-day recipient count reads verdicts by decided_at.
-- V1 to V4 stay untouched.

ALTER TABLE agent_tool_calls
    DROP CONSTRAINT chk_agent_tool_calls_status,
    ADD CONSTRAINT chk_agent_tool_calls_status
        CHECK (status IN ('STARTED', 'SUCCEEDED', 'ERRORED', 'FAILED', 'UNKNOWN_TOOL',
            'PENDING_APPROVAL', 'REFUSED'));

-- One verdict per tool call: the gate decides a call once
ALTER TABLE agent_verdicts
    ADD CONSTRAINT uq_agent_verdicts_tool_call UNIQUE (tool_call_id);

CREATE INDEX idx_agent_verdicts_decided_at ON agent_verdicts (decided_at);
