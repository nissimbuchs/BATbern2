-- V6: caps and the daily budget (agentic interaction, epic agent runtime, story 8; R9).
-- Reaching a limit of agent.limits ends a run HANDED_TO_ORGANIZERS with end reason CAP_REACHED and
-- the limit as end_detail: STEPS, RUN_TOKENS, REPLIES or DAILY_TOKENS. The step limit of story 2
-- (STEP_LIMIT) is one of them now. The daily budget sums agent_model_calls by started_at.
-- V1 to V5 stay untouched.

ALTER TABLE agent_runs
    DROP CONSTRAINT chk_agent_runs_end_reason;

UPDATE agent_runs
   SET end_reason = 'CAP_REACHED', end_detail = 'STEPS'
 WHERE end_reason = 'STEP_LIMIT';

ALTER TABLE agent_runs
    ADD CONSTRAINT chk_agent_runs_end_reason
        CHECK (end_reason IS NULL OR end_reason IN ('COMPLETED', 'MODEL_UNAVAILABLE', 'MODEL_MAX_TOKENS',
            'MODEL_STOPPED', 'CAP_REACHED', 'INTERNAL_ERROR', 'GRANT_REFUSED', 'TOKEN_REFUSED')),
    -- A run stopped by a limit names it
    ADD CONSTRAINT chk_agent_runs_cap_reached_detail
        CHECK (end_reason IS DISTINCT FROM 'CAP_REACHED'
            OR (end_detail IS NOT NULL AND end_detail IN ('STEPS', 'RUN_TOKENS', 'REPLIES', 'DAILY_TOKENS')));

CREATE INDEX idx_agent_model_calls_started_at ON agent_model_calls (started_at);
