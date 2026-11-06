package ch.batbern.events.service;

import ch.batbern.shared.types.Role;

/**
 * One participant materialised for badge / list export.
 *
 * <p>Shared by {@link ParticipantsExportService} (XLSX) and
 * {@link ParticipantsDocxExportService} (DOCX). Both pull the canonical list
 * from {@link ParticipantsCollector#collect(String)}, so the row order and
 * role assignment are guaranteed identical across the two exports.
 *
 * <p>{@code role} is the platform role (#1236); the German word printed on the badge and in the
 * XLSX comes from {@link #badgeLabel()}.
 *
 * @param firstName          attendee given name, never null but may be empty
 * @param lastName           attendee family name, never null but may be empty
 * @param companyDisplayName company display name (already resolved through
 *                           {@code UserApiClient.getCompanyDisplayNames}), may
 *                           be empty if the user has no company association
 * @param role               ORGANIZER, SPEAKER, PARTNER or ATTENDEE; null only for the blank
 *                           filler badge
 */
public record ParticipantRow(
        String firstName,
        String lastName,
        String companyDisplayName,
        Role role) {

    /** The role as printed on the badge: Organisator, Referent, Partner, Teilnehmer; empty for none. */
    public String badgeLabel() {
        if (role == null) {
            return "";
        }
        return switch (role) {
            case ORGANIZER -> "Organisator";
            case SPEAKER -> "Referent";
            case PARTNER -> "Partner";
            case ATTENDEE -> "Teilnehmer";
        };
    }
}
