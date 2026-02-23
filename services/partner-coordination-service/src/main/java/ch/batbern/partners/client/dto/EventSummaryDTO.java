package ch.batbern.partners.client.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Summary of a BATbern event fetched from event-management-service.
 *
 * Story 8.3: Used by IcsGeneratorService to build the second VEVENT (the main event)
 * in the partner meeting calendar invite.
 *
 * ADR-003: eventCode is the cross-service identifier.
 *
 * @param eventCode  ADR-003 meaningful event identifier (e.g. "BATbern57")
 * @param title      Human-readable event title
 * @param eventDate  Date of the event
 * @param startTime  Start time of the event
 * @param endTime    End time of the event
 * @param venue      Event venue / location
 */
public record EventSummaryDTO(
        String eventCode,
        String title,
        LocalDate eventDate,
        LocalTime startTime,
        LocalTime endTime,
        String venue
) {}
