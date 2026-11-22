package ch.batbern.agent.gate;

import ch.batbern.agent.run.RunTrigger;
import ch.batbern.agent.tools.OperationClass;

import java.util.Objects;
import java.util.Set;

/**
 * Everything the gate rules decide on (story 7, AD-7), and nothing else: no model text, no inbound
 * text. Each value comes from agent-service's own code or tables: the registry tool's derived class,
 * the run row, the mail the tool resolved, the decision log.
 *
 * @param toolClass                 the tool's class, derived from the operation class table
 * @param trigger                   what started the run, read from its row
 * @param mail                      the mail the call would send; {@code null} when it sends none
 * @param mailUnresolved            whether the tool could not say whom its mail would reach
 * @param runRecipientsBefore       non-organizer addresses this run already mailed (RUN or APPROVED verdicts)
 * @param dayRecipientsBefore       non-organizer addresses all runs mailed today (Europe/Zurich), likewise
 * @param matchingApprovals         matching approved precedents
 * @param matchingDenials           matching denied precedents
 */
public record GateFacts(OperationClass toolClass, RunTrigger trigger, Mail mail, boolean mailUnresolved,
                        Set<String> runRecipientsBefore, Set<String> dayRecipientsBefore,
                        int matchingApprovals, int matchingDenials) {

    /**
     * The mail of the call, as counted.
     *
     * @param recipients            every recipient the tool listed
     * @param organizerRecipients   the distinct ones it resolved as organizers
     * @param nonOrganizerAddresses the distinct addresses the gate counts
     */
    public record Mail(int recipients, long organizerRecipients, Set<String> nonOrganizerAddresses) {
        public Mail {
            nonOrganizerAddresses = Set.copyOf(nonOrganizerAddresses);
        }
    }

    public GateFacts {
        Objects.requireNonNull(toolClass, "toolClass");
        Objects.requireNonNull(trigger, "trigger");
        runRecipientsBefore = Set.copyOf(runRecipientsBefore);
        dayRecipientsBefore = Set.copyOf(dayRecipientsBefore);
        if (mailUnresolved && mail != null) {
            throw new IllegalArgumentException("An unresolved mail has no recipients");
        }
    }

    /** Whether the call sends a mail, resolved or not. */
    public boolean sendsMail() {
        return mail != null || mailUnresolved;
    }
}
