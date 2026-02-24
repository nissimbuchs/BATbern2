package ch.batbern.events.repository;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.domain.SpeakerAvailability;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SpeakerRepository - Story 6.0.
 *
 * Tests repository methods against PostgreSQL via Testcontainers.
 * Validates ADR-003 compliance: username-based queries.
 */
@Transactional
class SpeakerRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SpeakerRepository speakerRepository;

    @BeforeEach
    void setUp() {
        speakerRepository.deleteAll();
    }

    // AC2 Tests: findByUsername

    @Test
    void should_findSpeakerByUsername_when_speakerExists() {
        // Given - ADR-003: Query by username
        Speaker speaker = createSpeaker("john.doe");
        speakerRepository.save(speaker);

        // When
        Optional<Speaker> result = speakerRepository.findByUsername("john.doe");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("john.doe");
    }

    @Test
    void should_returnEmpty_when_usernameNotFound() {
        // When
        Optional<Speaker> result = speakerRepository.findByUsername("nonexistent.user");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void should_checkExistsByUsername_when_speakerExists() {
        // Given
        speakerRepository.save(createSpeaker("existing.user"));

        // When/Then
        assertThat(speakerRepository.existsByUsername("existing.user")).isTrue();
        assertThat(speakerRepository.existsByUsername("nonexistent.user")).isFalse();
    }

    // Soft delete tests

    @Test
    void should_excludeSoftDeleted_when_findByUsernameAndDeletedAtIsNull() {
        // Given
        Speaker activeUser = createSpeaker("active.user");
        speakerRepository.save(activeUser);

        Speaker deletedUser = createSpeaker("deleted.user");
        deletedUser.setDeletedAt(Instant.now());
        speakerRepository.save(deletedUser);

        // When
        Optional<Speaker> activeResult = speakerRepository.findByUsernameAndDeletedAtIsNull("active.user");
        Optional<Speaker> deletedResult = speakerRepository.findByUsernameAndDeletedAtIsNull("deleted.user");

        // Then
        assertThat(activeResult).isPresent();
        assertThat(deletedResult).isEmpty();
    }

    // Pagination tests (AC2)

    @Test
    void should_returnPagedResults_when_paginationRequested() {
        // Given - Create 25 speakers
        for (int i = 1; i <= 25; i++) {
            speakerRepository.save(createSpeaker("speaker" + String.format("%02d", i) + ".test"));
        }

        // When - Request page 0, size 10
        Page<Speaker> page0 = speakerRepository.findAll(PageRequest.of(0, 10, Sort.by("username")));

        // Then
        assertThat(page0.getContent()).hasSize(10);
        assertThat(page0.getTotalElements()).isEqualTo(25);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.isFirst()).isTrue();
        assertThat(page0.hasNext()).isTrue();
    }

    @Test
    void should_returnSortedResults_when_sortRequested() {
        // Given
        speakerRepository.save(createSpeaker("charlie.speaker"));
        speakerRepository.save(createSpeaker("alice.speaker"));
        speakerRepository.save(createSpeaker("bob.speaker"));

        // When - Sort by username ascending
        Page<Speaker> sortedPage = speakerRepository.findAll(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "username"))
        );

        // Then
        List<String> usernames = sortedPage.getContent().stream()
                .map(Speaker::getUsername)
                .toList();
        assertThat(usernames).containsExactly("alice.speaker", "bob.speaker", "charlie.speaker");
    }

    // Filter by availability tests

    @Test
    void should_filterByAvailability_when_specificationUsed() {
        // Given
        Speaker available1 = createSpeaker("available1.speaker");
        available1.setAvailability(SpeakerAvailability.AVAILABLE);
        speakerRepository.save(available1);

        Speaker available2 = createSpeaker("available2.speaker");
        available2.setAvailability(SpeakerAvailability.AVAILABLE);
        speakerRepository.save(available2);

        Speaker busy = createSpeaker("busy.speaker");
        busy.setAvailability(SpeakerAvailability.BUSY);
        speakerRepository.save(busy);

        // When - Using JpaSpecificationExecutor
        List<Speaker> availableSpeakers = speakerRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("availability"), SpeakerAvailability.AVAILABLE)
        );

        // Then
        assertThat(availableSpeakers).hasSize(2);
        assertThat(availableSpeakers).allMatch(s -> s.getAvailability() == SpeakerAvailability.AVAILABLE);
    }

    // Filter by workflow state tests

    @Test
    void should_filterByWorkflowState_when_specificationUsed() {
        // Given
        Speaker identified = createSpeaker("identified.speaker");
        identified.setWorkflowState(SpeakerWorkflowState.IDENTIFIED);
        speakerRepository.save(identified);

        Speaker contacted = createSpeaker("contacted.speaker");
        contacted.setWorkflowState(SpeakerWorkflowState.CONTACTED);
        speakerRepository.save(contacted);

        Speaker confirmed = createSpeaker("confirmed.speaker");
        confirmed.setWorkflowState(SpeakerWorkflowState.CONFIRMED);
        speakerRepository.save(confirmed);

        // When
        List<Speaker> contactedSpeakers = speakerRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("workflowState"), SpeakerWorkflowState.CONTACTED)
        );

        // Then
        assertThat(contactedSpeakers).hasSize(1);
        assertThat(contactedSpeakers.get(0).getUsername()).isEqualTo("contacted.speaker");
    }

    // Array field tests

    @Test
    void should_persistAndRetrieveArrayFields_when_speakerSaved() {
        // Given
        Speaker speaker = Speaker.builder()
                .username("array.test")
                .expertiseAreas(List.of("Security", "Cloud", "DevOps"))
                .speakingTopics(List.of("Kubernetes", "AWS", "Terraform"))
                .languages(List.of("de", "en", "fr"))
                .certifications(List.of("CKA", "AWS SA"))
                .build();

        // When
        Speaker saved = speakerRepository.save(speaker);
        speakerRepository.flush();

        // Then - Clear persistence context and reload
        Speaker found = speakerRepository.findByUsername("array.test").orElseThrow();
        assertThat(found.getExpertiseAreas()).containsExactlyInAnyOrder("Security", "Cloud", "DevOps");
        assertThat(found.getSpeakingTopics()).containsExactlyInAnyOrder("Kubernetes", "AWS", "Terraform");
        assertThat(found.getLanguages()).containsExactlyInAnyOrder("de", "en", "fr");
        assertThat(found.getCertifications()).containsExactlyInAnyOrder("CKA", "AWS SA");
    }

    // Helper method

    private Speaker createSpeaker(String username) {
        return Speaker.builder()
                .username(username)
                .availability(SpeakerAvailability.AVAILABLE)
                .workflowState(SpeakerWorkflowState.IDENTIFIED)
                .expertiseAreas(List.of("General"))
                .speakingTopics(List.of("Tech"))
                .languages(List.of("de", "en"))
                .build();
    }
}
