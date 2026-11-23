package ch.batbern.agent.identity;

import ch.batbern.agent.run.RunTrigger;

import java.util.Objects;
import java.util.UUID;

/**
 * Where a run's grant comes from (story 4, AD-3, AD-15). The run's trigger follows from it. Each
 * source carries the proof CUMS asks for; none of them shows that proof in {@code toString}.
 */
public sealed interface GrantSource {

    /** The trigger the run is logged with. */
    RunTrigger trigger();

    /**
     * An organizer's live login: CUMS issues an {@code ORGANIZER_LOGIN} grant for the username of
     * the forwarded Cognito JWT. The JWT is sent on the grant call only, never on a mint.
     *
     * @param cognitoJwt the organizer's Cognito token, without {@code Bearer }
     */
    record OrganizerLogin(String cognitoJwt) implements GrantSource {
        public OrganizerLogin {
            requireText(cognitoJwt, "cognitoJwt");
        }

        @Override
        public RunTrigger trigger() {
            return RunTrigger.ORGANIZER;
        }

        @Override
        public String toString() {
            return "OrganizerLogin[cognitoJwt=****]";
        }
    }

    /**
     * A run linked from a requester run (AD-15): CUMS issues a {@code LINKED_RUN} grant for the
     * organizer delegate when the parent's run id and grant secret prove the link.
     */
    record LinkedRun(String linkedRunId, String linkedRunGrantSecret) implements GrantSource {
        public LinkedRun {
            requireText(linkedRunId, "linkedRunId");
            requireText(linkedRunGrantSecret, "linkedRunGrantSecret");
        }

        @Override
        public RunTrigger trigger() {
            return RunTrigger.LINKED_RUN;
        }

        @Override
        public String toString() {
            return "LinkedRun[linkedRunId=" + linkedRunId + ", linkedRunGrantSecret=****]";
        }
    }

    /** A scheduler job listed in CUMS's {@code agent.grants.scheduler-jobs}: a {@code SCHEDULER_JOB} grant. */
    record SchedulerJob(String jobName) implements GrantSource {
        public SchedulerJob {
            requireText(jobName, "jobName");
        }

        @Override
        public RunTrigger trigger() {
            return RunTrigger.SCHEDULED;
        }
    }

    /**
     * A grant EMS obtained ({@code EMS_HANDOFF}) and handed over with the inbound mail: no grant
     * call, the run mints with it directly. The inbound contract that delivers it is epic
     * speaker-mail-agent.
     */
    record HandedOver(UUID runId, String grantSecret) implements GrantSource {
        public HandedOver {
            Objects.requireNonNull(runId, "runId");
            requireText(grantSecret, "grantSecret");
        }

        @Override
        public RunTrigger trigger() {
            return RunTrigger.INBOUND_MAIL;
        }

        @Override
        public String toString() {
            return "HandedOver[runId=" + runId + ", grantSecret=****]";
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
