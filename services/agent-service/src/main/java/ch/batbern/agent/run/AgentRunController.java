package ch.batbern.agent.run;

import ch.batbern.agent.api.generated.AgentRunsApi;
import ch.batbern.agent.dto.generated.AgentRun;
import ch.batbern.agent.dto.generated.PaginatedAgentRunResponse;
import ch.batbern.shared.api.PaginationMetadata;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;

/** {@code GET /api/v1/agent/runs} (agent-api.openapi.yml): organizers read the runs, newest first. */
@RestController
@RequestMapping("/api/v1")
@Validated
public class AgentRunController implements AgentRunsApi {

    private final AgentRunQueryService queries;

    public AgentRunController(AgentRunQueryService queries) {
        this.queries = queries;
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PaginatedAgentRunResponse> listAgentRuns(Integer page, Integer limit) {
        Page<AgentRunEntity> result = queries.list(page, limit);
        PaginationMetadata pagination = PaginationMetadata.builder()
                .page(result.getNumber())
                .limit(result.getSize())
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasNext(result.hasNext())
                .hasPrev(result.hasPrevious())
                .build();
        return ResponseEntity.ok(new PaginatedAgentRunResponse(
                result.map(AgentRunController::toDto).getContent(), pagination));
    }

    private static AgentRun toDto(AgentRunEntity run) {
        AgentRun dto = new AgentRun(run.getId(),
                AgentRun.StatusEnum.fromValue(run.getStatus().name()),
                run.getStartedAt().atOffset(ZoneOffset.UTC));
        if (run.getEndedAt() != null) {
            dto.setEndedAt(run.getEndedAt().atOffset(ZoneOffset.UTC));
        }
        return dto;
    }
}
