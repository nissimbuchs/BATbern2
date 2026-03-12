package ch.batbern.events.service;

import ch.batbern.events.entity.AppSettingEntity;
import ch.batbern.events.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminSettingsService {

    private final AppSettingRepository appSettingRepository;

    @Transactional(readOnly = true)
    public Optional<String> getSetting(String key) {
        return appSettingRepository.findBySettingKey(key)
                .map(AppSettingEntity::getSettingValue);
    }

    @Transactional
    public String setSetting(String key, String value, String updatedBy) {
        AppSettingEntity entity = appSettingRepository.findBySettingKey(key)
                .orElseGet(() -> AppSettingEntity.builder()
                        .settingKey(key)
                        .build());

        entity.setSettingValue(value);
        entity.setUpdatedBy(updatedBy);
        appSettingRepository.save(entity);

        return value;
    }
}
