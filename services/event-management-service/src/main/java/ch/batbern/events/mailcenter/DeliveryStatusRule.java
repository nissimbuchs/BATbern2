package ch.batbern.events.mailcenter;

import ch.batbern.events.domain.MailRecipient.Status;

import java.util.EnumSet;
import java.util.Set;

/**
 * Story 16.1b AC6: a recipient's status only moves forward, SENT → DELIVERED → FAILED. FAILED is
 * final, so a DELIVERED that SES sends after a bounce (multi-recipient mails) does not hide it, and
 * the first failure reason is kept. A complaint after delivery still moves DELIVERED to FAILED.
 *
 * <p>The repository applies the rule in the UPDATE's WHERE clause via {@link #sourcesFor}, so two
 * events processed at the same time cannot move a status backwards either.
 */
final class DeliveryStatusRule {

    private DeliveryStatusRule() {
    }

    static boolean mayMove(Status from, Status to) {
        return rank(to) > rank(from);
    }

    /** The current statuses from which a recipient may move to {@code to}. */
    static Set<Status> sourcesFor(Status to) {
        Set<Status> sources = EnumSet.noneOf(Status.class);
        for (Status from : Status.values()) {
            if (mayMove(from, to)) {
                sources.add(from);
            }
        }
        return sources;
    }

    private static int rank(Status status) {
        return switch (status) {
            case SENT -> 0;
            case DELIVERED -> 1;
            case FAILED -> 2;
        };
    }
}
