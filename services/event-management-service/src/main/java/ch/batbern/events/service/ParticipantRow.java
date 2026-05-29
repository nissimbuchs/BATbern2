package ch.batbern.events.service;

/**
 * One participant materialised for badge / list export.
 *
 * <p>Shared by {@link ParticipantsExportService} (XLSX) and
 * {@link ParticipantsDocxExportService} (DOCX). Both pull the canonical list
 * from {@link ParticipantsCollector#collect(String)}, so the row order and
 * role assignment are guaranteed identical across the two exports.
 *
 * <p>{@code role} values are the German strings printed on physical badges:
 * {@code "Organisator"}, {@code "Referent"}, {@code "Teilnehmer"} — the same
 * constants {@link ParticipantsCollector} writes.
 *
 * @param firstName          attendee given name, never null but may be empty
 * @param lastName           attendee family name, never null but may be empty
 * @param companyDisplayName company display name (already resolved through
 *                           {@code UserApiClient.getCompanyDisplayNames}), may
 *                           be empty if the user has no company association
 * @param role               one of {@code "Organisator" | "Referent" | "Teilnehmer"}
 */
public record ParticipantRow(
        String firstName,
        String lastName,
        String companyDisplayName,
        String role) {
}
