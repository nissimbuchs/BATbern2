package ch.batbern.events.controller;

import ch.batbern.events.sessions.api.generated.AgendaConfigApi;
import ch.batbern.events.sessions.dto.generated.EventAgendaConfigResponse;
import ch.batbern.events.sessions.dto.generated.UpdateEventAgendaConfigRequest;
import ch.batbern.events.service.AgendaConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the per-event agenda config (Story 15.2).
 *
 * <p>{@code implements} the generated {@link AgendaConfigApi} interface (event-sessions spec,
 * ADR-006 contract-first). The interface supplies the verb/path/param mapping and the generated
 * DTO types; the class-level {@code @RequestMapping("/api/v1")} supplies the version prefix
 * (interface paths are {@code /events/{eventCode}/agenda-config}). Method-level
 * {@code @PreAuthorize} stays on the overrides.</p>
 *
 * <ul>
 *   <li>GET /api/v1/events/{eventCode}/agenda-config — resolved config (override or template).</li>
 *   <li>PUT /api/v1/events/{eventCode}/agenda-config — copy-on-edit upsert of the per-event row.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class AgendaConfigController implements AgendaConfigApi {

    private final AgendaConfigService agendaConfigService;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventAgendaConfigResponse> getEventAgendaConfig(String eventCode) {
        log.info("GET /api/v1/events/{}/agenda-config", eventCode);
        return ResponseEntity.ok(agendaConfigService.getResolvedConfig(eventCode));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventAgendaConfigResponse> updateEventAgendaConfig(
            String eventCode,
            UpdateEventAgendaConfigRequest request) {
        log.info("PUT /api/v1/events/{}/agenda-config", eventCode);
        return ResponseEntity.ok(agendaConfigService.upsert(eventCode, request));
    }
}
