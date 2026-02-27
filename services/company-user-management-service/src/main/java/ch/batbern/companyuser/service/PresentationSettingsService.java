package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.PresentationSettings;
import ch.batbern.companyuser.dto.PresentationSettingsRequest;
import ch.batbern.companyuser.dto.PresentationSettingsResponse;
import ch.batbern.companyuser.repository.PresentationSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for moderator presentation page settings.
 *
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Single-row table pattern: id is always 1.
 * GET: returns the row, or hardcoded defaults if the row is absent.
 * PUT: upserts (saves with id=1).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PresentationSettingsService {

    private static final int SETTINGS_ID = 1;
    private static final String DEFAULT_ABOUT_TEXT =
            "BATbern ist eine unabhängige Plattform, die Berner Architekten und Ingenieure vernetzt. "
            + "Wir veranstalten regelmässige Treffen, bei denen Fachleute Erfahrungen austauschen"
            + " und neue Impulse gewinnen.";
    private static final int DEFAULT_PARTNER_COUNT = 9;

    private final PresentationSettingsRepository repository;

    /**
     * Returns the current presentation settings, falling back to defaults if none exist.
     */
    @Transactional(readOnly = true)
    public PresentationSettingsResponse getSettings() {
        return repository.findById(SETTINGS_ID)
                .map(s -> PresentationSettingsResponse.builder()
                        .aboutText(s.getAboutText())
                        .partnerCount(s.getPartnerCount())
                        .build())
                .orElseGet(() -> {
                    log.warn("No presentation_settings row found; returning hardcoded defaults");
                    return PresentationSettingsResponse.builder()
                            .aboutText(DEFAULT_ABOUT_TEXT)
                            .partnerCount(DEFAULT_PARTNER_COUNT)
                            .build();
                });
    }

    /**
     * Upserts the presentation settings (always writes to id=1).
     */
    @Transactional
    public PresentationSettingsResponse updateSettings(PresentationSettingsRequest request) {
        PresentationSettings entity = PresentationSettings.builder()
                .id(SETTINGS_ID)
                .aboutText(request.getAboutText())
                .partnerCount(request.getPartnerCount())
                .build();

        PresentationSettings saved = repository.save(entity);
        log.info("Presentation settings updated: aboutText length={}, partnerCount={}",
                saved.getAboutText().length(), saved.getPartnerCount());

        return PresentationSettingsResponse.builder()
                .aboutText(saved.getAboutText())
                .partnerCount(saved.getPartnerCount())
                .build();
    }
}
