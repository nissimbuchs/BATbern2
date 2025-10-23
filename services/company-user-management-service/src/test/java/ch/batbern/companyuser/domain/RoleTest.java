package ch.batbern.companyuser.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Role enum
 * Tests cover basic enum functionality and business rules
 * Story 1.14-2, AC: 8 (Role Management)
 */
@DisplayName("Role Enum Tests")
class RoleTest {

    @Test
    @DisplayName("should_haveAllRoles_when_enumDefined")
    void should_haveAllRoles_when_enumDefined() {
        // When
        Role[] roles = Role.values();

        // Then
        assertThat(roles).hasSize(4);
        assertThat(roles).contains(
                Role.ORGANIZER,
                Role.SPEAKER,
                Role.PARTNER,
                Role.ATTENDEE
        );
    }

    @Test
    @DisplayName("should_convertToString_when_nameCalled")
    void should_convertToString_when_nameCalled() {
        // When/Then
        assertThat(Role.ORGANIZER.name()).isEqualTo("ORGANIZER");
        assertThat(Role.SPEAKER.name()).isEqualTo("SPEAKER");
        assertThat(Role.PARTNER.name()).isEqualTo("PARTNER");
        assertThat(Role.ATTENDEE.name()).isEqualTo("ATTENDEE");
    }

    @Test
    @DisplayName("should_parseFromString_when_valueOfCalled")
    void should_parseFromString_when_valueOfCalled() {
        // When/Then
        assertThat(Role.valueOf("ORGANIZER")).isEqualTo(Role.ORGANIZER);
        assertThat(Role.valueOf("SPEAKER")).isEqualTo(Role.SPEAKER);
        assertThat(Role.valueOf("PARTNER")).isEqualTo(Role.PARTNER);
        assertThat(Role.valueOf("ATTENDEE")).isEqualTo(Role.ATTENDEE);
    }

    @Test
    @DisplayName("should_throwException_when_invalidRoleProvided")
    void should_throwException_when_invalidRoleProvided() {
        // When/Then
        assertThatThrownBy(() -> Role.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
