package ch.batbern.companyuser.watch.repository;

import ch.batbern.companyuser.domain.WatchPairing;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    /** Pessimistic lock prevents concurrent pairing requests from both passing the max-2 check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COUNT(w) FROM WatchPairing w WHERE w.username = :username AND w.pairedAt IS NOT NULL")
    long countPairedWatchesForUpdate(@Param("username") String username);

    long countByUsernameAndPairedAtNotNull(String username);

    Optional<WatchPairing> findByUsernameAndDeviceName(String username, String deviceName);

    List<WatchPairing> findByUsername(String username);

    /** Delete any pending (unpaired, not yet expired or expired) code rows for this user (H3 fix). */
    @Modifying
    @Query("DELETE FROM WatchPairing w WHERE w.username = :username AND w.pairedAt IS NULL")
    void deleteAllPendingCodesByUsername(@Param("username") String username);

    /** Cleanup job: delete expired pending codes (C1 fix — Task 4.6). */
    @Modifying
    @Query("DELETE FROM WatchPairing w WHERE w.pairedAt IS NULL AND w.pairingCodeExpiresAt < :now")
    int deleteExpiredPendingCodes(@Param("now") LocalDateTime now);
}
