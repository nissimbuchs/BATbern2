package ch.batbern.partners.repository;

import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for PartnerRepository using PostgreSQL via Testcontainers.
 *
 * Tests verify:
 * - JPA entity mapping correctness
 * - Database constraints (unique company_name)
 * - Flyway migration alignment
 * - PostgreSQL-specific features
 *
 * CRITICAL: Uses real PostgreSQL (NOT H2) for production parity.
 */
@SpringBootTest
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class PartnerRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private PartnerRepository partnerRepository;

    @Test
    void should_savePartner_when_validEntityProvided() {
        // Given
        Partner partner = Partner.builder()
                .companyName("GoogleZH")
                .partnershipLevel(PartnershipLevel.GOLD)
                .partnershipStartDate(LocalDate.of(2024, 1, 1))
                .partnershipEndDate(LocalDate.of(2025, 12, 31))
                .build();

        // When
        Partner saved = partnerRepository.save(partner);

        // Then
        assertThat(saved.getId())
                .as("Partner should have auto-generated UUID")
                .isNotNull();
        assertThat(saved.getCompanyName())
                .as("Company name should be persisted as VARCHAR")
                .isEqualTo("GoogleZH");
        assertThat(saved.getPartnershipLevel())
                .as("Partnership level should be persisted as enum")
                .isEqualTo(PartnershipLevel.GOLD);
        assertThat(saved.getCreatedAt())
                .as("Created timestamp should be auto-populated")
                .isNotNull();
    }

    @Test
    void should_findByCompanyName_when_partnerExists() {
        // Given
        Partner partner = Partner.builder()
                .companyName("MicrosoftZH")
                .partnershipLevel(PartnershipLevel.PLATINUM)
                .partnershipStartDate(LocalDate.of(2023, 1, 1))
                .build();
        partnerRepository.save(partner);

        // When
        Optional<Partner> found = partnerRepository.findByCompanyName("MicrosoftZH");

        // Then
        assertThat(found)
                .as("Should find partner by company name (meaningful ID)")
                .isPresent();
        assertThat(found.get().getCompanyName())
                .isEqualTo("MicrosoftZH");
        assertThat(found.get().getPartnershipLevel())
                .isEqualTo(PartnershipLevel.PLATINUM);
    }

    @Test
    void should_returnEmpty_when_companyNameNotFound() {
        // When
        Optional<Partner> found = partnerRepository.findByCompanyName("NonExistentCo");

        // Then
        assertThat(found)
                .as("Should return empty Optional when company name doesn't exist")
                .isEmpty();
    }

    @Test
    void should_enforceUniqueConstraint_when_duplicateCompanyNameInserted() {
        // Given - First partner with company name
        Partner first = Partner.builder()
                .companyName("UniqueTestCo")
                .partnershipLevel(PartnershipLevel.SILVER)
                .partnershipStartDate(LocalDate.now())
                .build();
        partnerRepository.saveAndFlush(first);

        // When - Try to insert duplicate company name
        Partner duplicate = Partner.builder()
                .companyName("UniqueTestCo")  // Duplicate!
                .partnershipLevel(PartnershipLevel.GOLD)
                .partnershipStartDate(LocalDate.now())
                .build();

        // Then
        assertThatThrownBy(() -> {
            partnerRepository.saveAndFlush(duplicate);
        })
                .as("Database should enforce UNIQUE constraint on company_name")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("company_name");
    }

    @Test
    void should_setCreatedAt_when_entityPersisted() {
        // Given
        Partner partner = Partner.builder()
                .companyName("TimestampCo")  // Max 12 chars
                .partnershipLevel(PartnershipLevel.BRONZE)
                .partnershipStartDate(LocalDate.now())
                .build();

        // When
        Partner saved = partnerRepository.save(partner);

        // Then
        assertThat(saved.getCreatedAt())
                .as("createdAt should be auto-populated on insert")
                .isNotNull()
                .isBeforeOrEqualTo(java.time.Instant.now());
    }

    @Test
    void should_setUpdatedAt_when_entityModified() throws InterruptedException {
        // Given - Save initial partner
        Partner partner = Partner.builder()
                .companyName("UpdateTestCo")
                .partnershipLevel(PartnershipLevel.SILVER)
                .partnershipStartDate(LocalDate.now())
                .build();
        Partner saved = partnerRepository.saveAndFlush(partner);
        java.time.Instant initialUpdatedAt = saved.getUpdatedAt();

        // Wait a bit to ensure timestamp difference
        Thread.sleep(100);

        // When - Update partnership level
        saved.setPartnershipLevel(PartnershipLevel.GOLD);
        Partner updated = partnerRepository.saveAndFlush(saved);

        // Then
        assertThat(updated.getUpdatedAt())
                .as("updatedAt should be updated on modification")
                .isNotNull()
                .isAfter(initialUpdatedAt);
    }

    @Test
    void should_persistPartnershipDates_when_saving() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 6, 1);
        LocalDate endDate = LocalDate.of(2025, 5, 31);
        Partner partner = Partner.builder()
                .companyName("DatesTestCo")
                .partnershipLevel(PartnershipLevel.STRATEGIC)
                .partnershipStartDate(startDate)
                .partnershipEndDate(endDate)
                .build();

        // When
        Partner saved = partnerRepository.save(partner);

        // Then
        assertThat(saved.getPartnershipStartDate())
                .as("Partnership start date should be persisted as DATE")
                .isEqualTo(startDate);
        assertThat(saved.getPartnershipEndDate())
                .as("Partnership end date should be persisted as DATE")
                .isEqualTo(endDate);
    }

    @Test
    void should_allowNullEndDate_when_openEndedPartnership() {
        // Given - Partnership with no end date
        Partner partner = Partner.builder()
                .companyName("OpenEndedCo")
                .partnershipLevel(PartnershipLevel.STRATEGIC)
                .partnershipStartDate(LocalDate.now())
                .partnershipEndDate(null)  // Open-ended
                .build();

        // When
        Partner saved = partnerRepository.save(partner);

        // Then
        assertThat(saved.getPartnershipEndDate())
                .as("Partnership end date should allow NULL for open-ended partnerships")
                .isNull();
    }
}
