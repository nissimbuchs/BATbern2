package ch.batbern.events.service;

import ch.batbern.events.dto.venuecoordination.VenueCoordinationConfig;
import ch.batbern.events.entity.AppSettingEntity;
import ch.batbern.events.repository.AppSettingRepository;
import ch.batbern.shared.util.ReservedEmailDomain;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * The one store for application settings (app_settings, {@code /admin/settings/{key}}).
 * Structured settings are JSON and validated before they are stored: {@link PresentationSettings}
 * under {@code presentation} and {@link VenueCoordinationConfig} under
 * {@code venue.coordination.config} (Story 4.9).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSettingsService {

    private final AppSettingRepository appSettingRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<String> getSetting(String key) {
        return appSettingRepository.findBySettingKey(key)
                .map(AppSettingEntity::getSettingValue);
    }

    /**
     * Stores a setting.
     *
     * @throws IllegalArgumentException when a structured setting's value is not valid (400)
     */
    @Transactional
    public String setSetting(String key, String value, String updatedBy) {
        if (PresentationSettings.KEY.equals(key)) {
            PresentationSettings.parse(objectMapper, value);
        }
        if (VenueCoordinationConfig.SETTING_KEY.equals(key)) {
            requireVenueCoordinationConfig(value);
        }
        AppSettingEntity entity = appSettingRepository.findBySettingKey(key)
                .orElseGet(() -> AppSettingEntity.builder()
                        .settingKey(key)
                        .build());

        entity.setSettingValue(value);
        entity.setUpdatedBy(updatedBy);
        appSettingRepository.save(entity);

        return value;
    }

    /**
     * The same parse {@code VenueCoordinationService} applies on read; a blank value means "not
     * configured", as there. A contact on a reserved domain (RFC 2606/6761) is refused.
     */
    private void requireVenueCoordinationConfig(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        VenueCoordinationConfig config;
        try {
            config = objectMapper.readValue(value, VenueCoordinationConfig.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid value for setting '"
                    + VenueCoordinationConfig.SETTING_KEY + "': not a venue coordination config");
        }
        // 16.8c: the venue mail goes to these addresses; one SES would never deliver to is refused here
        Stream.of(config.getVenue(), config.getCatering())
                .filter(contact -> contact != null && ReservedEmailDomain.isReserved(contact.getEmail()))
                .findFirst()
                .ifPresent(contact -> {
                    throw new IllegalArgumentException("Invalid value for setting '"
                            + VenueCoordinationConfig.SETTING_KEY + "': the address of " + contact.getName()
                            + " is on the reserved domain " + domainOf(contact.getEmail()));
                });
    }

    private static String domainOf(String email) {
        return email.substring(email.lastIndexOf('@') + 1);
    }

    /**
     * The moderator presentation settings, or {@link PresentationSettings#DEFAULTS} when nothing
     * is stored. A stored value that does not parse (only possible by writing the table directly,
     * every API write is validated) is logged as an error and answered with the defaults, so the
     * public presentation page keeps working.
     */
    @Transactional(readOnly = true)
    public PresentationSettings getPresentationSettings() {
        return getSetting(PresentationSettings.KEY)
                .map(value -> {
                    try {
                        return PresentationSettings.parse(objectMapper, value);
                    } catch (IllegalArgumentException e) {
                        log.error("Stored app setting '{}' is invalid, serving defaults: {}",
                                PresentationSettings.KEY, e.getMessage());
                        return PresentationSettings.DEFAULTS;
                    }
                })
                .orElse(PresentationSettings.DEFAULTS);
    }
}
