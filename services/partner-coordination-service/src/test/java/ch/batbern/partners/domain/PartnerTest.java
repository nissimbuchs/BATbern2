package ch.batbern.partners.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Partner entity domain logic.
 *
 * Tests ADR-003 compliance (stores companyName, NOT companyId UUID)
 * and business rules for partnership dates and status.
 */
class PartnerTest {

    @Test
    void should_storeCompanyNameAsString_when_creating() {
        // Given
        String companyName = "GoogleZH";
        LocalDate startDate = LocalDate.of(2024, 1, 1);

        // When
        Partner partner = Partner.builder()
                .companyName(companyName)
                .partnershipLevel(PartnershipLevel.GOLD)
                .partnershipStartDate(startDate)
                .build();

        // Then
        assertThat(partner.getCompanyName())
                .as("Partner must store companyName (meaningful ID) per ADR-003")
                .isEqualTo("GoogleZH");
        assertThat(partner.getCompanyName())
                .as("companyName must be String, NOT UUID")
                .isInstanceOf(String.class);
    }

    @Test
    void should_calculateIsActive_when_withinDates() {
        // Given - Active partnership (started, not ended)
        Partner activePartner = Partner.builder()
                .companyName("ActiveCo")
                .partnershipLevel(PartnershipLevel.GOLD)
                .partnershipStartDate(LocalDate.now().minusMonths(6))
                .partnershipEndDate(LocalDate.now().plusMonths(6))
                .build();

        // Then
        assertThat(activePartner.isActive())
                .as("Partnership is active when current date is between start and end dates")
                .isTrue();

        // Given - Expired partnership
        Partner expiredPartner = Partner.builder()
                .companyName("ExpiredCo")
                .partnershipLevel(PartnershipLevel.SILVER)
                .partnershipStartDate(LocalDate.now().minusYears(2))
                .partnershipEndDate(LocalDate.now().minusMonths(1))
                .build();

        // Then
        assertThat(expiredPartner.isActive())
                .as("Partnership is inactive when end date has passed")
                .isFalse();

        // Given - Future partnership
        Partner futurePartner = Partner.builder()
                .companyName("FutureCo")
                .partnershipLevel(PartnershipLevel.PLATINUM)
                .partnershipStartDate(LocalDate.now().plusMonths(1))
                .build();

        // Then
        assertThat(futurePartner.isActive())
                .as("Partnership is inactive when start date is in the future")
                .isFalse();
    }

    @Test
    void should_calculateIsActive_when_noEndDate() {
        // Given - Open-ended partnership (no end date)
        Partner openEndedPartner = Partner.builder()
                .companyName("OpenEndedCo")
                .partnershipLevel(PartnershipLevel.STRATEGIC)
                .partnershipStartDate(LocalDate.now().minusYears(1))
                .partnershipEndDate(null)  // No end date
                .build();

        // Then
        assertThat(openEndedPartner.isActive())
                .as("Partnership with no end date is active if started")
                .isTrue();
    }

    @Test
    void should_validatePartnershipLevel_when_invalidValue() {
        // Given/When/Then
        assertThatThrownBy(() ->
                Partner.builder()
                        .companyName("TestCo")
                        .partnershipLevel(null)  // Invalid: null
                        .partnershipStartDate(LocalDate.now())
                        .build()
                        .validatePartnershipLevel()
        )
                .as("Partnership level is required")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Partnership level is required");
    }

    @Test
    void should_throwValidationException_when_endDateBeforeStartDate() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 6, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 1);  // Before start date

        // When/Then
        assertThatThrownBy(() ->
                Partner.builder()
                        .companyName("InvalidDatesCo")
                        .partnershipLevel(PartnershipLevel.BRONZE)
                        .partnershipStartDate(startDate)
                        .partnershipEndDate(endDate)
                        .build()
                        .validateDates()
        )
                .as("End date cannot be before start date")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("end date cannot be before start date");
    }

    @Test
    void should_requireCompanyName_when_creating() {
        // Given/When/Then
        assertThatThrownBy(() ->
                Partner.builder()
                        .companyName(null)  // Missing required field
                        .partnershipLevel(PartnershipLevel.GOLD)
                        .partnershipStartDate(LocalDate.now())
                        .build()
                        .validateCompanyName()
        )
                .as("Company name is required per ADR-003")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company name is required");
    }

    @Test
    void should_enforceMaxLength_when_companyNameTooLong() {
        // Given
        String tooLongName = "A".repeat(13);  // Max is 12 chars

        // When/Then
        assertThatThrownBy(() ->
                Partner.builder()
                        .companyName(tooLongName)
                        .partnershipLevel(PartnershipLevel.GOLD)
                        .partnershipStartDate(LocalDate.now())
                        .build()
                        .validateCompanyName()
        )
                .as("Company name must not exceed 12 characters")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12 characters");
    }
}
