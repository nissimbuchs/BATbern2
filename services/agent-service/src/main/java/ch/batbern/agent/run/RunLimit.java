package ch.batbern.agent.run;

import java.time.ZoneId;

/**
 * The limits that stop a run (story 8, R9, {@code agent.limits.*}); the {@code agent_runs.end_detail}
 * of a run that ended {@link RunEndReason#CAP_REACHED}.
 */
public enum RunLimit {
    /** Model calls per run ({@code agent.limits.run-steps}). */
    STEPS,
    /** Model tokens per run ({@code agent.limits.run-tokens}). */
    RUN_TOKENS,
    /** {@code reply} calls let run per run ({@code agent.limits.run-replies}). */
    REPLIES,
    /** Model tokens of all runs per day ({@code agent.limits.daily-tokens}). */
    DAILY_TOKENS;

    /** The day of the daily budget and of the gate's per-day recipient count. */
    public static final ZoneId DAY_ZONE = ZoneId.of("Europe/Zurich");
}
