package ch.batbern.events.mailcenter.compose;

import java.util.List;

/** The audience in send order, and who is left out. */
public record Recipients(List<ComposeRecipient> recipients, List<SkippedRecipient> skipped) {

    public Recipients {
        recipients = List.copyOf(recipients);
        skipped = List.copyOf(skipped);
    }
}
