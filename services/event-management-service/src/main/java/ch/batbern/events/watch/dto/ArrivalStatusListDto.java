package ch.batbern.events.watch.dto;

import java.util.List;

/**
 * Response wrapper for arrival status list.
 * W2.4: GET /api/v1/watch/events/{eventCode}/arrivals.
 */
public record ArrivalStatusListDto(List<ArrivalStatusDto> arrivals) {}
