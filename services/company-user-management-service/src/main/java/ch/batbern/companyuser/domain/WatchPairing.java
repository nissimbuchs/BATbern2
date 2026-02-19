package ch.batbern.companyuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity for Watch pairing records.
 * Story W2.1: Pairing Code Backend & Web Frontend
 *
 * Lifecycle:
 * 1. Code generation: Insert with pairingCode + pairingCodeExpiresAt
 * 2. Successful pairing (W2.2): set pairingToken + pairedAt, clear pairingCode
 * 3. Unpair: hard delete (GDPR compliant)
 */
@Entity
@Table(name = "watch_pairings")
@Getter
@Setter
@NoArgsConstructor
public class WatchPairing {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(length = 6)
    private String pairingCode;

    @Column
    private LocalDateTime pairingCodeExpiresAt;

    @Column(unique = true, length = 256)
    private String pairingToken;

    @Column(length = 100)
    private String deviceName;

    @Column
    private LocalDateTime pairedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // --- Business methods ---

    public boolean isCodeExpired() {
        return pairingCodeExpiresAt != null && pairingCodeExpiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isPaired() {
        return pairedAt != null;
    }

    public void clearPairingCode() {
        this.pairingCode = null;
        this.pairingCodeExpiresAt = null;
    }
}
