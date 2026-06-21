package ch.batbern.events.controller;

import ch.batbern.events.dto.EventAgendaConfigResponse;
import ch.batbern.events.dto.UpdateEventAgendaConfigRequest;
import ch.batbern.events.service.AgendaConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the per-event agenda config (Story 15.2).
 *
 * <p>Hand-written (like {@link TimetableController}) — the OpenAPI spec is authored for
 * frontend type generation + contract docs, not for generating a {@code *Api} interface.</p>
 *
 * <ul>
 *   <li>GET /api/v1/events/{eventCode}/agenda-config — resolved config (override or template).</li>
 *   <li>PUT /api/v1/events/{eventCode}/agenda-config — copy-on-edit upsert of the per-event row.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/agenda-config")
@RequiredArgsConstructor
@Slf4j
public class AgendaConfigController {

    private final AgendaConfigService agendaConfigService;

    @GetMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventAgendaConfigResponse> getAgendaConfig(@PathVariable String eventCode) {
        log.info("GET /api/v1/events/{}/agenda-config", eventCode);
        return ResponseEntity.ok(agendaConfigService.getResolvedConfig(eventCode));
    }

    @PutMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventAgendaConfigResponse> updateAgendaConfig(
            @PathVariable String eventCode,
            @Valid @RequestBody UpdateEventAgendaConfigRequest request) {
        log.info("PUT /api/v1/events/{}/agenda-config", eventCode);
        return ResponseEntity.ok(agendaConfigService.upsert(eventCode, request));
    }
}
