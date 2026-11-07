package ch.batbern.events.service;

import ch.batbern.events.dto.generated.users.AdditionalEmail;
import ch.batbern.events.dto.generated.users.UserResponse;

import java.util.List;
import java.util.Objects;

/**
 * #1244 — the additional addresses that get a copy (CC) of an event mail: confirmed
 * ({@code verifiedAt} set) and switched on by the owner ({@code receiveEventMail}). Event mails
 * (registration, waitlist, deregistration, speaker invitation, acceptance, reminder, quality
 * review, composer mails to speakers, the participants alias) go to the primary address plus
 * these. Owner decision 2026-11-07; before, every additional address got a copy.
 *
 * <p>Identity lookups (which person owns an address) use {@link PersonAddresses} and the
 * forwarding directory instead: there every confirmed address counts.
 */
public final class EventMailCopies {

    private EventMailCopies() {
    }

    /** The addresses to copy; empty for a null user or a user without qualifying addresses. */
    public static List<String> of(UserResponse user) {
        if (user == null || user.getAdditionalEmails() == null) {
            return List.of();
        }
        return user.getAdditionalEmails().stream()
                .filter(a -> a.getVerifiedAt() != null)
                .filter(a -> Boolean.TRUE.equals(a.getReceiveEventMail()))
                .map(AdditionalEmail::getEmail)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
