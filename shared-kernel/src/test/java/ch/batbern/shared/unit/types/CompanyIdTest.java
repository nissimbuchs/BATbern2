package ch.batbern.shared.unit.types;

import ch.batbern.shared.exceptions.ValidationException;
import ch.batbern.shared.types.CompanyId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

class CompanyIdTest {

    @Test
    @DisplayName("should_validateCompanyId_when_instantiated")
    void should_validateCompanyId_when_instantiated() {
        String validSwissUid = "CHE-123.456.789";

        CompanyId companyId = CompanyId.from(validSwissUid);

        assertThat(companyId).isNotNull();
        assertThat(companyId.getValue()).isEqualTo(validSwissUid);
    }

    @Test
    @DisplayName("should_acceptValidSwissUIDFormats_when_created")
    void should_acceptValidSwissUIDFormats_when_created() {
        String[] validFormats = {
            "CHE-123.456.789",
            "CHE123456789",
            "CHE 123 456 789",
            "CHE-100.000.001"
        };

        for (String uid : validFormats) {
            CompanyId companyId = CompanyId.from(uid);
            assertThat(companyId).isNotNull();
        }
    }

    @Test
    @DisplayName("should_throwValidationException_when_invalidSwissUIDProvided")
    void should_throwValidationException_when_invalidSwissUIDProvided() {
        String[] invalidUids = {
            "CHE-000.000.000",
            "CHF-123.456.789",
            "123.456.789",
            "CHE-1234.567.890",
            "invalid-uid"
        };

        for (String invalidUid : invalidUids) {
            assertThatThrownBy(() -> CompanyId.from(invalidUid))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid Swiss UID format");
        }
    }

    @Test
    @DisplayName("should_throwValidationException_when_nullProvided")
    void should_throwValidationException_when_nullProvided() {
        assertThatThrownBy(() -> CompanyId.from(null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("CompanyId cannot be null");
    }

    @Test
    @DisplayName("should_throwValidationException_when_emptyStringProvided")
    void should_throwValidationException_when_emptyStringProvided() {
        assertThatThrownBy(() -> CompanyId.from(""))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("CompanyId cannot be empty");
    }

    @Test
    @DisplayName("should_normalizeSwissUID_when_differentFormatsProvided")
    void should_normalizeSwissUID_when_differentFormatsProvided() {
        CompanyId id1 = CompanyId.from("CHE-123.456.789");
        CompanyId id2 = CompanyId.from("CHE123456789");
        CompanyId id3 = CompanyId.from("CHE 123 456 789");

        assertThat(id1.getNormalizedValue()).isEqualTo("CHE-123.456.789");
        assertThat(id2.getNormalizedValue()).isEqualTo("CHE-123.456.789");
        assertThat(id3.getNormalizedValue()).isEqualTo("CHE-123.456.789");
        assertThat(id1).isEqualTo(id2);
        assertThat(id2).isEqualTo(id3);
    }

    @Test
    @DisplayName("should_implementEqualsCorrectly_when_comparingCompanyIds")
    void should_implementEqualsCorrectly_when_comparingCompanyIds() {
        CompanyId companyId1 = CompanyId.from("CHE-123.456.789");
        CompanyId companyId2 = CompanyId.from("CHE123456789");
        CompanyId companyId3 = CompanyId.from("CHE-987.654.321");

        assertThat(companyId1).isEqualTo(companyId2);
        assertThat(companyId1).isNotEqualTo(companyId3);
        assertThat(companyId1.hashCode()).isEqualTo(companyId2.hashCode());
        assertThat(companyId1.hashCode()).isNotEqualTo(companyId3.hashCode());
    }

    @Test
    @DisplayName("should_beImmutable_when_created")
    void should_beImmutable_when_created() {
        CompanyId companyId = CompanyId.from("CHE-123.456.789");
        String originalValue = companyId.getValue();

        assertThat(companyId.getValue()).isEqualTo(originalValue);
        assertThat(companyId.getValue()).isSameAs(originalValue);
    }

    @Test
    @DisplayName("should_provideFormattedUID_when_toStringCalled")
    void should_provideFormattedUID_when_toStringCalled() {
        CompanyId companyId = CompanyId.from("CHE123456789");

        assertThat(companyId.toString()).isEqualTo("CHE-123.456.789");
    }
}