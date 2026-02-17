package ch.batbern.companyuser.watch.repository;

import ch.batbern.companyuser.domain.WatchPairing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for WatchPairing entities.
 * Story W2.1: Pairing Code Backend & Web Frontend
 */
public interface WatchPairingRepository extends JpaRepository<WatchPairing, UUID> {

    List<WatchPairing> findByUsernameAndPairedAtNotNull(String username);

    Optional<WatchPairing> findByPairingCode(String pairingCode);

    Optional<WatchPairing> findByPairingToken(String pairingToken);

    long countByUsernameAndPairedAtNotNull(String username);

    Optional<WatchPairing> findByUsernameAndDeviceName(String username, String deviceName);

    List<WatchPairing> findByUsername(String username);
}
