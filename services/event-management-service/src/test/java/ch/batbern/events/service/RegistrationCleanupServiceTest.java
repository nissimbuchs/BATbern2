package ch.batbern.events.service;

import ch.batbern.events.domain.Registration;
import ch.batbern.events.repository.RegistrationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RegistrationCleanupService
 * Story 4.1.5c: Automatic cleanup of unconfirmed registrations
 */
@ExtendWith(MockitoExtension.class)
class RegistrationCleanupServiceTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @InjectMocks
    private RegistrationCleanupService cleanupService;

    @Test
    @DisplayName("Should delete unconfirmed registrations older than 48 hours")
    void shouldDeleteUnconfirmedRegistrations_whenOlderThan48Hours() {
        // Arrange
        Instant now = Instant.now();
        Instant expiredTime = now.minus(49, ChronoUnit.HOURS);

        Registration unconfirmedReg = createRegistration(
                "BATbern57-reg-ABC123",
                "registered",
                expiredTime
        );

        when(registrationRepository
                .findByStatusAndCreatedAtBefore(eq("registered"), any(Instant.class)))
                .thenReturn(List.of(unconfirmedReg));

        // Act
        cleanupService.cleanupUnconfirmedRegistrations();

        // Assert
        verify(registrationRepository).delete(unconfirmedReg);
    }

    @Test
    @DisplayName("Should NOT delete unconfirmed registrations newer than 48 hours")
    void shouldNotDeleteUnconfirmedRegistrations_whenNewerThan48Hours() {
        // Arrange
        when(registrationRepository
                .findByStatusAndCreatedAtBefore(eq("registered"), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // Act
        cleanupService.cleanupUnconfirmedRegistrations();

        // Assert
        verify(registrationRepository, never()).delete(any(Registration.class));
    }

    @Test
    @DisplayName("Should continue cleanup even if one registration fails to delete")
    void shouldContinueCleanup_whenOneRegistrationFails() {
        // Arrange
        Instant expiredTime = Instant.now().minus(49, ChronoUnit.HOURS);

        Registration reg1 = createRegistration("REG-001", "registered", expiredTime);
        Registration reg2 = createRegistration("REG-002", "registered", expiredTime);
        Registration reg3 = createRegistration("REG-003", "registered", expiredTime);

        when(registrationRepository
                .findByStatusAndCreatedAtBefore(eq("registered"), any(Instant.class)))
                .thenReturn(List.of(reg1, reg2, reg3));

        // Simulate failure on second registration
        doNothing().when(registrationRepository).delete(reg1);
        doThrow(new RuntimeException("Database error")).when(registrationRepository).delete(reg2);
        doNothing().when(registrationRepository).delete(reg3);

        // Act
        cleanupService.cleanupUnconfirmedRegistrations();

        // Assert - all three delete attempts were made
        verify(registrationRepository).delete(reg1);
        verify(registrationRepository).delete(reg2);
        verify(registrationRepository).delete(reg3);
    }

    @Test
    @DisplayName("Should NOT delete confirmed registrations")
    void shouldNotDeleteConfirmedRegistrations() {
        // Arrange
        when(registrationRepository
                .findByStatusAndCreatedAtBefore(eq("registered"), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // Act
        cleanupService.cleanupUnconfirmedRegistrations();

        // Assert - only searches for 'registered' status
        verify(registrationRepository).findByStatusAndCreatedAtBefore(eq("registered"), any(Instant.class));
        verify(registrationRepository, never()).delete(any(Registration.class));
    }

    @Test
    @DisplayName("Should return accurate cleanup statistics")
    void shouldReturnAccurateStatistics() {
        // Arrange
        Instant expiredTime = Instant.now().minus(49, ChronoUnit.HOURS);
        Registration expiredReg = createRegistration("REG-EXPIRED", "registered", expiredTime);

        when(registrationRepository.countByStatus("registered")).thenReturn(5L);
        when(registrationRepository.countByStatus("confirmed")).thenReturn(100L);
        when(registrationRepository.countByStatus("cancelled")).thenReturn(10L);
        when(registrationRepository
                .findByStatusAndCreatedAtBefore(eq("registered"), any(Instant.class)))
                .thenReturn(List.of(expiredReg));

        // Act
        RegistrationCleanupService.CleanupStatistics stats = cleanupService.getCleanupStatistics();

        // Assert
        assertThat(stats.registeredCount()).isEqualTo(5L);
        assertThat(stats.confirmedCount()).isEqualTo(100L);
        assertThat(stats.cancelledCount()).isEqualTo(10L);
        assertThat(stats.deletableUnconfirmedCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Manual trigger should execute cleanup")
    void shouldExecuteCleanup_whenManuallyTriggered() {
        // Arrange
        when(registrationRepository
                .findByStatusAndCreatedAtBefore(eq("registered"), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // Act
        cleanupService.triggerManualCleanup();

        // Assert
        verify(registrationRepository).findByStatusAndCreatedAtBefore(eq("registered"), any(Instant.class));
    }

    // Helper method to create test registrations
    private Registration createRegistration(String code, String status, Instant createdAt) {
        return Registration.builder()
                .id(UUID.randomUUID())
                .registrationCode(code)
                .eventId(UUID.randomUUID())
                .eventCode("BATbern57")
                .attendeeUsername("test.user")
                .status(status)
                .registrationDate(createdAt)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }
}
