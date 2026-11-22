package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.api.generated.AgentSettingsApi;
import ch.batbern.companyuser.dto.generated.AgentSettings;
import ch.batbern.companyuser.dto.generated.UpdateAgentSettingsRequest;
import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.companyuser.service.AgentSettingsService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The organizer agent's switch, allowlist and delegate (agentic interaction, epic agent identity,
 * story 4): {@code GET} and {@code PATCH /api/v1/agent-settings} (partial, ADR-013).
 *
 * <p>Organizers own the switch (decision 2026-11-21). A token carrying {@code act} (an agent token)
 * is refused on both operations, even for an organizer's agent: a delegate run must neither read nor
 * change its own switch.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AgentSettingsController implements AgentSettingsApi {

    private final AgentSettingsService agentSettingsService;
    private final SecurityContextHelper securityContextHelper;

    @Override
    @Timed(value = "agentSettings.get", description = "Time to get the agent settings",
            percentiles = {0.5, 0.95, 0.99})
    @PreAuthorize("hasRole('ORGANIZER') and !@securityContextHelper.carriesAgentAct()")
    public ResponseEntity<AgentSettings> getAgentSettings() {
        return ResponseEntity.ok(agentSettingsService.get());
    }

    @Override
    @Timed(value = "agentSettings.update", description = "Time to change the agent settings",
            percentiles = {0.5, 0.95, 0.99})
    @PreAuthorize("hasRole('ORGANIZER') and !@securityContextHelper.carriesAgentAct()")
    public ResponseEntity<AgentSettings> updateAgentSettings(UpdateAgentSettingsRequest request) {
        return ResponseEntity.ok(agentSettingsService.update(request, securityContextHelper.getCurrentActor()));
    }
}
