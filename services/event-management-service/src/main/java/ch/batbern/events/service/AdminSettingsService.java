package ch.batbern.events.service;

import ch.batbern.events.entity.AppSettingEntity;
import ch.batbern.events.repository.AppSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The one store for application settings (app_settings, {@code /admin/settings/{key}}).
 * Structured settings are JSON and validated before they are stored: {@link PresentationSettings}
 * under {@code presentation} (Story 4.9).
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
