-- V2: the agent decision log (agentic interaction, epic agent runtime, story 2; R7, AD-9, AD-14).
-- Every run, inbound message, step, model call (prompt, tool choice, tokens) and tool call, plus the
-- tables for gate verdicts, approvals and undos whose writers arrive with the gate (story 7) and the
-- oversight console. agent-service owns these tables; no other service reads them (AD-9). Names
-- do not collide with CUMS's agent_settings, agent_grants and agent_allowlist.
--
-- Each row is written when it happens (one transaction per write), so a run that crashes still
-- leaves its trace. No ON DELETE CASCADE: the log is never deleted through a run.

-- ── Runs: what started them and why they ended ──────────────────────────────
-- agent_runs has no writer before this story, so it is empty and trigger can be NOT NULL.
ALTER TABLE agent_runs
    ADD COLUMN trigger    VARCHAR(32) NOT NULL,
    ADD COLUMN end_reason VARCHAR(32),
    ADD CONSTRAINT chk_agent_runs_trigger
        CHECK (trigger IN ('INBOUND_MAIL', 'ORGANIZER', 'SCHEDULED', 'LINKED_RUN')),
    ADD CONSTRAINT chk_agent_runs_end_reason
        CHECK (end_reason IS NULL OR end_reason IN ('COMPLETED', 'MODEL_UNAVAILABLE', 'MODEL_MAX_TOKENS',
            'MODEL_STOPPED', 'STEP_LIMIT', 'INTERNAL_ERROR')),
    -- A running run has no reason; an ended run has one
    ADD CONSTRAINT chk_agent_runs_end_reason_iff_ended
        CHECK ((status = 'RUNNING') = (end_reason IS NULL));

-- ── Inbound messages: what the run was given ────────────────────────────────
CREATE TABLE agent_inbound_messages (
    id           UUID         PRIMARY KEY,
    run_id       UUID         NOT NULL REFERENCES agent_runs (id),
    channel      VARCHAR(32)  NOT NULL,
    -- The idempotency key of the source (the mail's message id); null for a chat message
    external_id  VARCHAR(512),
    received_at  TIMESTAMPTZ  NOT NULL,
    content      JSONB        NOT NULL,
    recorded_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_agent_inbound_messages_channel CHECK (channel IN ('MAIL', 'CHAT', 'CARD_ACTION'))
);
CREATE INDEX idx_agent_inbound_messages_run_id ON agent_inbound_messages (run_id);

-- ── Steps: one model call plus the tool calls it asked for ──────────────────
CREATE TABLE agent_steps (
    id           UUID         PRIMARY KEY,
    run_id       UUID         NOT NULL REFERENCES agent_runs (id),
    step_number  INTEGER      NOT NULL,
    started_at   TIMESTAMPTZ  NOT NULL,
    ended_at     TIMESTAMPTZ,
    CONSTRAINT uq_agent_steps_run_step UNIQUE (run_id, step_number),
    CONSTRAINT chk_agent_steps_number CHECK (step_number >= 1),
    CONSTRAINT chk_agent_steps_ended_after_start CHECK (ended_at IS NULL OR ended_at >= started_at)
);
CREATE INDEX idx_agent_steps_run_id ON agent_steps (run_id);

-- ── Model calls: the full prompt, the answer, the tool choice and the tokens ─
CREATE TABLE agent_model_calls (
    id                           UUID          PRIMARY KEY,
    run_id                       UUID          NOT NULL REFERENCES agent_runs (id),
    step_id                      UUID          NOT NULL REFERENCES agent_steps (id),
    status                       VARCHAR(32)   NOT NULL,
    -- system, messages, tools and max_tokens as sent through LlmPort
    prompt                       JSONB         NOT NULL,
    -- the answer: id, model, content blocks, stop reason, usage
    response                     JSONB,
    model                        VARCHAR(128),
    stop_reason                  VARCHAR(32),
    -- the tools the model chose: [{"id": ..., "name": ...}], empty when it chose none
    chosen_tools                 JSONB,
    input_tokens                 BIGINT,
    output_tokens                BIGINT,
    cache_creation_input_tokens  BIGINT,
    cache_read_input_tokens      BIGINT,
    error                        TEXT,
    started_at                   TIMESTAMPTZ   NOT NULL,
    ended_at                     TIMESTAMPTZ,
    CONSTRAINT chk_agent_model_calls_status CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_agent_model_calls_stop_reason CHECK (stop_reason IS NULL OR stop_reason IN
        ('END_TURN', 'TOOL_USE', 'MAX_TOKENS', 'STOP_SEQUENCE', 'PAUSE_TURN', 'REFUSAL', 'UNKNOWN')),
    CONSTRAINT chk_agent_model_calls_ended_after_start CHECK (ended_at IS NULL OR ended_at >= started_at)
);
CREATE INDEX idx_agent_model_calls_run_id ON agent_model_calls (run_id);
CREATE INDEX idx_agent_model_calls_step_id ON agent_model_calls (step_id);

-- ── Tool calls: what the model asked for, what the tool returned ────────────
CREATE TABLE agent_tool_calls (
    id           UUID          PRIMARY KEY,
    run_id       UUID          NOT NULL REFERENCES agent_runs (id),
    step_id      UUID          NOT NULL REFERENCES agent_steps (id),
    -- the model's tool_use id, which the tool_result answers
    -- TEXT: both come from the model, an unknown tool's name can be any length
    tool_use_id  TEXT          NOT NULL,
    tool_name    TEXT          NOT NULL,
    input        JSONB         NOT NULL,
    status       VARCHAR(32)   NOT NULL,
    output       TEXT,
    error        TEXT,
    started_at   TIMESTAMPTZ   NOT NULL,
    ended_at     TIMESTAMPTZ,
    CONSTRAINT chk_agent_tool_calls_status
        CHECK (status IN ('STARTED', 'SUCCEEDED', 'ERRORED', 'FAILED', 'UNKNOWN_TOOL')),
    CONSTRAINT chk_agent_tool_calls_ended_after_start CHECK (ended_at IS NULL OR ended_at >= started_at)
);
CREATE INDEX idx_agent_tool_calls_run_id ON agent_tool_calls (run_id);
CREATE INDEX idx_agent_tool_calls_step_id ON agent_tool_calls (step_id);

-- ── Gate verdicts (AD-7, AD-8): written by the gate, story 7 ─────────────────
CREATE TABLE agent_verdicts (
    id                 UUID          PRIMARY KEY,
    run_id             UUID          NOT NULL REFERENCES agent_runs (id),
    tool_call_id       UUID          NOT NULL REFERENCES agent_tool_calls (id),
    operation_class    VARCHAR(16)   NOT NULL,
    outcome            VARCHAR(32)   NOT NULL,
    -- 0..1, computed from matching precedents and the class; null where the class needs none
    confidence         NUMERIC(5, 4),
    inputs             JSONB         NOT NULL,
    cited_precedents   JSONB         NOT NULL,
    decided_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_agent_verdicts_class
        CHECK (operation_class IN ('READ', 'REVERSIBLE', 'REPLY', 'BINDING', 'AUDIENCE')),
    CONSTRAINT chk_agent_verdicts_outcome CHECK (outcome IN ('RUN', 'PENDING_APPROVAL', 'REFUSED')),
    CONSTRAINT chk_agent_verdicts_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);
CREATE INDEX idx_agent_verdicts_run_id ON agent_verdicts (run_id);
CREATE INDEX idx_agent_verdicts_tool_call_id ON agent_verdicts (tool_call_id);

-- ── Approvals (AD-7, AD-15): a pending verdict and the organizer's decision ──
CREATE TABLE agent_approvals (
    id           UUID          PRIMARY KEY,
    run_id       UUID          NOT NULL REFERENCES agent_runs (id),
    verdict_id   UUID          NOT NULL REFERENCES agent_verdicts (id),
    state        VARCHAR(16)   NOT NULL,
    -- shared-kernel Actors value of the deciding organizer
    decided_by   VARCHAR(255),
    created_at   TIMESTAMPTZ   NOT NULL,
    decided_at   TIMESTAMPTZ,
    CONSTRAINT chk_agent_approvals_state CHECK (state IN ('PENDING', 'APPROVED', 'DENIED', 'EXPIRED')),
    CONSTRAINT chk_agent_approvals_decided
        CHECK ((state = 'PENDING') = (decided_at IS NULL))
);
CREATE INDEX idx_agent_approvals_run_id ON agent_approvals (run_id);
CREATE INDEX idx_agent_approvals_verdict_id ON agent_approvals (verdict_id);

-- ── Undos (parent R9): the compensating call of a reversible tool call ───────
CREATE TABLE agent_undos (
    id                 UUID          PRIMARY KEY,
    run_id             UUID          NOT NULL REFERENCES agent_runs (id),
    -- the call that is undone, and the compensating call that undid it (through the gate)
    tool_call_id       UUID          NOT NULL REFERENCES agent_tool_calls (id),
    undo_tool_call_id  UUID          REFERENCES agent_tool_calls (id),
    undo_operation     VARCHAR(128)  NOT NULL,
    outcome            VARCHAR(16)   NOT NULL,
    performed_by       VARCHAR(255)  NOT NULL,
    performed_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_agent_undos_outcome CHECK (outcome IN ('PENDING', 'SUCCEEDED', 'FAILED'))
);
CREATE INDEX idx_agent_undos_run_id ON agent_undos (run_id);
CREATE INDEX idx_agent_undos_tool_call_id ON agent_undos (tool_call_id);
