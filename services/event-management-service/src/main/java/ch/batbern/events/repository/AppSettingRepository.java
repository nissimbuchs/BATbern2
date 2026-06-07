package ch.batbern.events.repository;

import ch.batbern.events.entity.AppSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppSettingRepository extends JpaRepository<AppSettingEntity, UUID> {

    Optional<AppSettingEntity> findBySettingKey(String settingKey);
}
