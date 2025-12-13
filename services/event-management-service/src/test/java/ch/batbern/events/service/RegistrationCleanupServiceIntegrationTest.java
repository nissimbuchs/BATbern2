package ch.batbern.events.service;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RegistrationCleanupService
 * Story 4.1.5c: Automatic cleanup of unconfirmed registrations
 * Uses Testcontainers PostgreSQL for production parity
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RegistrationCleanupServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RegistrationCleanupService cleanupService;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EntityManager entityManager;

    private Event testEvent;

    @BeforeEach
    void setUp() {
        // Clean up any existing data
        registrationRepository.deleteAll();
        eventRepository.deleteAll();

        // Create test event
        testEvent = Event.builder()
                .eventCode("BATbern57")
                .eventNumber(57)
                .title("Test Event 2025")
                .organizerUsername("organizer.user")
                .date(Instant.now().plus(30, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(20, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address 123, Bern")
                .venueCapacity(100)
                .status("published")
                .eventType(EventType.EVENING)
                .build();
        testEvent = eventRepository.save(testEvent);
    }

    @AfterEach
    void tearDown() {
        registrationRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    @DisplayName("Should delete unconfirmed registrations older than 48 hours")
    void shouldDeleteOldUnconfirmedRegistrations() {
        // Arrange - Create registration 49 hours ago (should be deleted)
        Instant expiredTime = Instant.now().minus(49, ChronoUnit.HOURS);
        Registration oldUnconfirmed = createAndSaveRegistration(
                "REG-OLD-001",
                "registered",
                expiredTime
        );

        // Act
        cleanupService.cleanupUnconfirmedRegistrations();

        // Assert
        List<Registration> remaining = registrationRepository.findAll();
        assertThat(remaining).isEmpty();
        assertThat(registrationRepository.findById(oldUnconfirmed.getId())).isEmpty();
    }

    @Test
    @DisplayName("Should NOT delete recent unconfirmed registrations")
    void shouldNotDeleteRecentUnconfirmedRegistrations() {
        // Arrange - Create registration 47 hours ago (should NOT be deleted)
        Instant recentTime = Instant.now().minus(47, ChronoUnit.HOURS);
        Registration recentUnconfirmed = createAndSaveRegistration(
                "REG-RECENT-001",
                "registered",
                recentTime
        );

        // Act
        cleanupService.cleanupUnconfirmedRegistrations();

        // Assert
        List<Registration> remaining = registrationRepository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getId()).isEqualTo(recentUnconfirmed.getId());
    }

    @Test
    @DisplayName("Should NOT delete confirmed registrations regardless of age")
    void shouldNotDeleteConfirmedRegistrations() {
        // Arrange - Create old confirmed registration (should NOT be deleted)
        Instant oldTime = Instant.now().minus(100, ChronoUnit.DAYS);
        Registration oldConfirmed = createAndSaveRegistration(
                "REG-CONFIRMED-001",
                "confirmed",
                oldTime
        );

        // Act
        cleanupService.cleanupUnconfirmedRegistrations();

        // Assert
        List<Registration> remaining = registrationRepository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getId()).isEqualTo(oldConfirmed.getId());
        assertThat(remaining.get(0).getStatus()).isEqualTo("confirmed");
    }

    @Test
    @DisplayName("Should handle mixed scenario: delete old unconfirmed, keep recent and confirmed")
    void shouldHandleMixedScenario() {
        // Arrange
        Instant oldTime = Instant.now().minus(49, ChronoUnit.HOURS);
        Instant recentTime = Instant.now().minus(47, ChronoUnit.HOURS);

        // Create various registrations
        Registration oldUnconfirmed1 = createAndSaveRegistration("REG-OLD-UNC-001", "registered", oldTime);
        Registration oldUnconfirmed2 = createAndSaveRegistration("REG-OLD-UNC-002", "registered", oldTime);
        Registration recentUnconfirmed = createAndSaveRegistration("REG-RECENT-UNC-001", "registered", recentTime);
        Registration oldConfirmed = createAndSaveRegistration("REG-OLD-CONF-001", "confirmed", oldTime);
        Registration recentConfirmed = createAndSaveRegistration("REG-RECENT-CONF-001", "confirmed", recentTime);

        // Act
        cleanupService.cleanupUnconfirmedRegistrations();

        // Assert
        List<Registration> remaining = registrationRepository.findAll();
        assertThat(remaining).hasSize(3);

        // Verify deleted
        assertThat(registrationRepository.findById(oldUnconfirmed1.getId())).isEmpty();
        assertThat(registrationRepository.findById(oldUnconfirmed2.getId())).isEmpty();

        // Verify kept
        assertThat(registrationRepository.findById(recentUnconfirmed.getId())).isPresent();
        assertThat(registrationRepository.findById(oldConfirmed.getId())).isPresent();
        assertThat(registrationRepository.findById(recentConfirmed.getId())).isPresent();
    }

    @Test
    @DisplayName("Should return accurate statistics after cleanup")
    void shouldReturnAccurateStatistics() {
        // Arrange
        Instant oldTime = Instant.now().minus(49, ChronoUnit.HOURS);
        Instant recentTime = Instant.now().minus(47, ChronoUnit.HOURS);

        createAndSaveRegistration("REG-OLD-001", "registered", oldTime);
        createAndSaveRegistration("REG-OLD-002", "registered", oldTime);
        createAndSaveRegistration("REG-RECENT-001", "registered", recentTime);
        createAndSaveRegistration("REG-CONFIRMED-001", "confirmed", oldTime);

        // Act - Get stats before cleanup
        RegistrationCleanupService.CleanupStatistics statsBefore = cleanupService.getCleanupStatistics();

        // Assert before
        assertThat(statsBefore.registeredCount()).isEqualTo(3);
        assertThat(statsBefore.confirmedCount()).isEqualTo(1);
        assertThat(statsBefore.deletableUnconfirmedCount()).isEqualTo(2);

        // Act - Run cleanup
        cleanupService.cleanupUnconfirmedRegistrations();

        // Get stats after cleanup
        RegistrationCleanupService.CleanupStatistics statsAfter = cleanupService.getCleanupStatistics();

        // Assert after
        assertThat(statsAfter.registeredCount()).isEqualTo(1); // Only recent one left
        assertThat(statsAfter.confirmedCount()).isEqualTo(1); // Confirmed unchanged
        assertThat(statsAfter.deletableUnconfirmedCount()).isEqualTo(0); // All old ones deleted
    }

    @Test
    @DisplayName("Manual trigger should work same as scheduled job")
    void shouldWorkViaManualTrigger() {
        // Arrange
        Instant oldTime = Instant.now().minus(49, ChronoUnit.HOURS);
        createAndSaveRegistration("REG-MANUAL-001", "registered", oldTime);

        // Act
        cleanupService.triggerManualCleanup();

        // Assert
        List<Registration> remaining = registrationRepository.findAll();
        assertThat(remaining).isEmpty();
    }

    // Helper method to create and save registration with specific createdAt timestamp
    Registration createAndSaveRegistration(String code, String status, Instant createdAt) {
        // Create and save registration normally (PrePersist will set timestamps to now())
        Registration registration = new Registration();
        registration.setRegistrationCode(code);
        registration.setEventId(testEvent.getId());
        registration.setEventCode(testEvent.getEventCode());
        registration.setAttendeeUsername("test.user." + UUID.randomUUID());
        registration.setStatus(status);
        registration.setRegistrationDate(createdAt);

        Registration saved = registrationRepository.saveAndFlush(registration);

        // Update timestamps using native SQL to bypass JPA lifecycle callbacks
        entityManager.createNativeQuery(
                        "UPDATE registrations SET created_at = :createdAt, updated_at = :updatedAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("updatedAt", createdAt)
                .setParameter("id", saved.getId())
                .executeUpdate();

        entityManager.flush();
        entityManager.clear();

        // Reload to get updated timestamps
        return registrationRepository.findById(saved.getId()).orElseThrow();
    }
}
