package ch.batbern.events.domain;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.repository.SpeakerRepository;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Speaker entity - Story 6.0.
 *
 * Tests Speaker JPA entity with PostgreSQL via Testcontainers.
 * Validates ADR-003/ADR-004 compliance: username-based reference, no cross-service FK.
 */
@Transactional
class SpeakerTest extends AbstractIntegrationTest {

    @Autowired
    private SpeakerRepository speakerRepository;

    // AC1 Tests: Speaker Entity

    @Test
    void should_createSpeakerEntity_when_validUsernameProvided() {
        // Given - ADR-003: Use username as meaningful identifier
        Speaker speaker = Speaker.builder()
                .username("john.doe")
                .build();

        // When
        Speaker saved = speakerRepository.save(speaker);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("john.doe");
        assertThat(saved.getAvailability()).isEqualTo(SpeakerAvailability.AVAILABLE);
        assertThat(saved.getWorkflowState()).isEqualTo(SpeakerWorkflowState.IDENTIFIED);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_persistArrayFields_when_expertiseAreasProvided() {
        // Given - PostgreSQL TEXT[] arrays
        Speaker speaker = Speaker.builder()
                .username("jane.smith")
                .expertiseAreas(List.of("Security", "Cloud Architecture", "DevOps"))
                .speakingTopics(List.of("AWS", "Kubernetes", "Zero Trust"))
                .certifications(List.of("AWS Solutions Architect", "CISSP"))
                .languages(List.of("de", "en", "fr"))
                .build();

        // When
        Speaker saved = speakerRepository.save(speaker);
        speakerRepository.flush();

        // Then - verify arrays persisted correctly
        Speaker found = speakerRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getExpertiseAreas()).containsExactly("Security", "Cloud Architecture", "DevOps");
        assertThat(found.getSpeakingTopics()).containsExactly("AWS", "Kubernetes", "Zero Trust");
        assertThat(found.getCertifications()).containsExactly("AWS Solutions Architect", "CISSP");
        assertThat(found.getLanguages()).containsExactly("de", "en", "fr");
    }

    @Test
    void should_enforceUniqueConstraint_when_duplicateUsernameProvided() {
        // Given - ADR-003: Username must be unique
        speakerRepository.save(Speaker.builder().username("duplicate.user").build());
        speakerRepository.flush();

        // When/Then
        assertThatThrownBy(() -> {
            speakerRepository.save(Speaker.builder().username("duplicate.user").build());
            speakerRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_persistSpeakerWithAllFields_when_fullProfileProvided() {
        // Given - Complete speaker profile
        Speaker speaker = Speaker.builder()
                .username("complete.speaker")
                .availability(SpeakerAvailability.BUSY)
                .workflowState(SpeakerWorkflowState.CONTACTED)
                .expertiseAreas(List.of("AI/ML", "Data Engineering"))
                .speakingTopics(List.of("LLMs", "RAG Architecture"))
                .linkedInUrl("https://linkedin.com/in/complete-speaker")
                .twitterHandle("@completespeaker")
                .certifications(List.of("Google Cloud ML Engineer"))
                .languages(List.of("en"))
                .speakingHistory("[]")
                .build();

        // When
        Speaker saved = speakerRepository.save(speaker);
        speakerRepository.flush();

        // Then
        Speaker found = speakerRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getAvailability()).isEqualTo(SpeakerAvailability.BUSY);
        assertThat(found.getWorkflowState()).isEqualTo(SpeakerWorkflowState.CONTACTED);
        assertThat(found.getLinkedInUrl()).isEqualTo("https://linkedin.com/in/complete-speaker");
        assertThat(found.getTwitterHandle()).isEqualTo("@completespeaker");
        assertThat(found.getSpeakingHistory()).isEqualTo("[]");
    }

    @Test
    void should_supportSoftDelete_when_deletedAtSet() {
        // Given
        Speaker speaker = Speaker.builder()
                .username("soft.delete.test")
                .build();
        Speaker saved = speakerRepository.save(speaker);
        speakerRepository.flush();

        // When - soft delete by setting deletedAt
        saved.setDeletedAt(java.time.Instant.now());
        speakerRepository.save(saved);
        speakerRepository.flush();

        // Then - record still exists but is marked as deleted
        Speaker found = speakerRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getDeletedAt()).isNotNull();
    }

    @Test
    void should_updateTimestamps_when_speakerModified() throws InterruptedException {
        // Given
        Speaker speaker = Speaker.builder()
                .username("timestamp.test")
                .build();
        Speaker saved = speakerRepository.save(speaker);
        speakerRepository.flush();

        java.time.Instant originalUpdatedAt = saved.getUpdatedAt();

        // Small delay to ensure timestamp difference
        Thread.sleep(10);

        // When
        saved.setAvailability(SpeakerAvailability.UNAVAILABLE);
        speakerRepository.save(saved);
        speakerRepository.flush();

        // Then
        Speaker found = speakerRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void should_useDefaultValues_when_minimalSpeakerCreated() {
        // Given - Only username provided (minimal required field)
        Speaker speaker = Speaker.builder()
                .username("minimal.speaker")
                .build();

        // When
        Speaker saved = speakerRepository.save(speaker);
        speakerRepository.flush();

        // Then - verify defaults
        Speaker found = speakerRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getAvailability()).isEqualTo(SpeakerAvailability.AVAILABLE);
        assertThat(found.getWorkflowState()).isEqualTo(SpeakerWorkflowState.IDENTIFIED);
        assertThat(found.getLanguages()).containsExactly("de", "en");
        assertThat(found.getExpertiseAreas()).isEmpty();
        assertThat(found.getSpeakingTopics()).isEmpty();
        assertThat(found.getCertifications()).isEmpty();
        assertThat(found.getDeletedAt()).isNull();
    }
}
