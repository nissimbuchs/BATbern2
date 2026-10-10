package ch.batbern.events.service;

import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.entity.EventAgendaConfig;
import ch.batbern.events.entity.EventTypeConfiguration;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Event start/end time resolution for emails and the public event page.
 *
 * <p>Bug fixes 2026-10-07:
 * <ul>
 *   <li>the per-event agenda config (Story 15.2) was ignored; only the shared template was read,
 *       so emails could show different times than the slot grid;</li>
 *   <li>the last-resort fallback was 16:00 for every event type (the mirror image of the
 *       timetable's 09:00-for-everything fallback).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EventTimeResolverTest {

    @Mock
    private AgendaConfigResolver agendaConfigResolver;

    @Mock
    private SessionRepository sessionRepository;

    private EventTimeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new EventTimeResolver(agendaConfigResolver, sessionRepository);
    }

    private Event event(EventType type) {
        return Event.builder()
                .id(UUID.randomUUID())
                .eventCode("BATbern60")
                .eventType(type)
                .workflowState(EventWorkflowState.SLOT_ASSIGNMENT)
                .date(Instant.parse("2026-11-06T15:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("should_usePerEventAgendaConfig_when_eventHasItsOwnConfig")
    void should_usePerEventAgendaConfig_when_eventHasItsOwnConfig() {
        Event event = event(EventType.EVENING);
        EventAgendaConfig override = new EventAgendaConfig();
        override.setTypicalStartTime(LocalTime.of(17, 30));
        override.setTypicalEndTime(LocalTime.of(21, 0));
        when(agendaConfigResolver.resolve(event)).thenReturn(override);

        assertThat(resolver.formatStartTime(event)).isEqualTo("17:30");
        assertThat(resolver.formatEndTime(event)).isEqualTo("21:00");
    }

    @Test
    @DisplayName("should_useTemplate_when_noPerEventConfig")
    void should_useTemplate_when_noPerEventConfig() {
        Event event = event(EventType.AFTERNOON);
        when(agendaConfigResolver.resolve(event)).thenReturn(EventTypeConfiguration.builder()
                .type(EventType.AFTERNOON)
                .typicalStartTime(LocalTime.of(13, 0))
                .typicalEndTime(LocalTime.of(19, 0))
                .build());

        assertThat(resolver.formatStartTime(event)).isEqualTo("13:00");
        assertThat(resolver.formatEndTime(event)).isEqualTo("19:00");
    }

    @ParameterizedTest
    @CsvSource({"EVENING, 16:00", "AFTERNOON, 13:00", "FULL_DAY, 09:00"})
    @DisplayName("should_fallBackToEventTypeDefault_when_configHasNoStartTime")
    void should_fallBackToEventTypeDefault_when_configHasNoStartTime(EventType type, String expected) {
        Event event = event(type);
        when(agendaConfigResolver.resolve(event)).thenReturn(EventTypeConfiguration.builder().type(type).build());

        assertThat(resolver.formatStartTime(event)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should_fallBackToEventTypeDefault_when_noConfigExists")
    void should_fallBackToEventTypeDefault_when_noConfigExists() {
        Event event = event(EventType.FULL_DAY);
        when(agendaConfigResolver.resolve(event)).thenThrow(new NotFoundException("no template"));

        assertThat(resolver.formatStartTime(event)).isEqualTo("09:00");
    }
}
