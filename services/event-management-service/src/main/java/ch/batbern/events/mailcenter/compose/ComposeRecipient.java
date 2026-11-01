package ch.batbern.events.mailcenter.compose;

import java.util.List;

/**
 * One recipient as the composer shows it. {@code key} is the adapter's own handle (subscriber,
 * registration, speaker pool entry); it never leaves the service (ADR-003).
 */
public record ComposeRecipient(String displayName, String address, List<String> cc, Object key) {

    public ComposeRecipient {
        cc = cc == null ? List.of() : List.copyOf(cc);
    }
}
