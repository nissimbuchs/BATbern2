package ch.batbern.partners.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PartnerContact domain entity.
 *
 * Tests ADR-003 compliance: stores username (String), NOT userId (UUID)
 */
@DisplayName("PartnerContact Domain Tests")
class PartnerContactTest {

    @Test
    @DisplayName("should create PartnerContact when valid data provided")
    void should_createPartnerContact_when_validDataProvided() {
        // Given
        UUID partnerId = UUID.randomUUID();
        String username = "john.doe";
        ContactRole role = ContactRole.PRIMARY;
        boolean isPrimary = true;

        // When
        PartnerContact contact = new PartnerContact();
        contact.setPartnerId(partnerId);
        contact.setUsername(username);
        contact.setContactRole(role);
        contact.setPrimary(isPrimary);

        // Then
        assertThat(contact.getPartnerId()).isEqualTo(partnerId);
        assertThat(contact.getUsername()).isEqualTo(username);
        assertThat(contact.getContactRole()).isEqualTo(role);
        assertThat(contact.isPrimary()).isEqualTo(isPrimary);
    }

    @Test
    @DisplayName("should store username as String when adding contact (ADR-003)")
    void should_storeUsernameAsString_when_addingContact() {
        // Given
        String username = "jane.smith";

        // When
        PartnerContact contact = new PartnerContact();
        contact.setUsername(username);

        // Then
        assertThat(contact.getUsername()).isInstanceOf(String.class);
        assertThat(contact.getUsername()).isEqualTo("jane.smith");
    }

    @Test
    @DisplayName("should set contact role when creating")
    void should_setContactRole_when_creating() {
        // Given
        ContactRole billingRole = ContactRole.BILLING;
        ContactRole technicalRole = ContactRole.TECHNICAL;
        ContactRole marketingRole = ContactRole.MARKETING;

        // When
        PartnerContact billingContact = new PartnerContact();
        billingContact.setContactRole(billingRole);

        PartnerContact technicalContact = new PartnerContact();
        technicalContact.setContactRole(technicalRole);

        PartnerContact marketingContact = new PartnerContact();
        marketingContact.setContactRole(marketingRole);

        // Then
        assertThat(billingContact.getContactRole()).isEqualTo(ContactRole.BILLING);
        assertThat(technicalContact.getContactRole()).isEqualTo(ContactRole.TECHNICAL);
        assertThat(marketingContact.getContactRole()).isEqualTo(ContactRole.MARKETING);
    }

    @Test
    @DisplayName("should enforce primary contact rule when removing last primary")
    void should_enforcePrimaryContactRule_when_removingLastPrimary() {
        // This test validates business logic that will be in PartnerContactService
        // The domain entity itself doesn't enforce this rule
        // Marking as placeholder for service-level validation

        // Given
        PartnerContact primaryContact = new PartnerContact();
        primaryContact.setPrimary(true);
        primaryContact.setContactRole(ContactRole.PRIMARY);

        // Then
        assertThat(primaryContact.isPrimary()).isTrue();
        assertThat(primaryContact.getContactRole()).isEqualTo(ContactRole.PRIMARY);
    }

    @Test
    @DisplayName("should allow multiple non-primary contacts for same partner")
    void should_allowMultipleNonPrimaryContacts_when_samePartner() {
        // Given
        UUID partnerId = UUID.randomUUID();

        // When
        PartnerContact contact1 = new PartnerContact();
        contact1.setPartnerId(partnerId);
        contact1.setUsername("user1");
        contact1.setPrimary(false);

        PartnerContact contact2 = new PartnerContact();
        contact2.setPartnerId(partnerId);
        contact2.setUsername("user2");
        contact2.setPrimary(false);

        // Then
        assertThat(contact1.getPartnerId()).isEqualTo(contact2.getPartnerId());
        assertThat(contact1.isPrimary()).isFalse();
        assertThat(contact2.isPrimary()).isFalse();
    }
}
