package ch.batbern.agent.run;

import ch.batbern.agent.api.generated.AgentRunsApi;
import ch.batbern.agent.dto.generated.AgentModelCallDetail;
import ch.batbern.agent.dto.generated.AgentRun;
import ch.batbern.agent.dto.generated.AgentRunDetail;
import ch.batbern.agent.dto.generated.PaginatedAgentModelCallResponse;
import ch.batbern.agent.dto.generated.PaginatedAgentRunResponse;
import ch.batbern.agent.dto.generated.PaginatedAgentStepResponse;
import ch.batbern.agent.dto.generated.PaginatedAgentToolCallResponse;
import ch.batbern.agent.dto.generated.PaginatedAgentVerdictResponse;
import ch.batbern.agent.security.ConsoleAccess;
import ch.batbern.shared.api.PaginationMetadata;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.UUID;

/**
 * The organizers' read API of agent-service ({@code agent-api.openapi.yml}, R8, AD-9): the runs,
 * newest first, and the decision log of one run. Organizers only, as persons
 * ({@link ConsoleAccess#ORGANIZER_PERSON}).
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class AgentRunController implements AgentRunsApi {

    private final AgentRunQueryService queries;
    private final RunLogReadService log;

    public AgentRunController(AgentRunQueryService queries, RunLogReadService log) {
        this.queries = queries;
        this.log = log;
    }

    @Override
    @PreAuthorize(ConsoleAccess.ORGANIZER_PERSON)
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

    @Override
    @PreAuthorize(ConsoleAccess.ORGANIZER_PERSON)
    public ResponseEntity<AgentRunDetail> getAgentRun(UUID runId) {
        RunLogReadService.RunDetail detail = log.detail(runId);
        return ResponseEntity.ok(new AgentRunDetail(toDto(detail.run()), detail.totals(), detail.inboundMessages()));
    }

    @Override
    @PreAuthorize(ConsoleAccess.ORGANIZER_PERSON)
    public ResponseEntity<PaginatedAgentStepResponse> listAgentRunSteps(UUID runId, Integer page, Integer limit) {
        var slice = log.steps(runId, page, limit);
        return ResponseEntity.ok(new PaginatedAgentStepResponse(slice.items(), pagination(slice)));
    }

    @Override
    @PreAuthorize(ConsoleAccess.ORGANIZER_PERSON)
    public ResponseEntity<PaginatedAgentModelCallResponse> listAgentRunModelCalls(UUID runId, Integer page,
                                                                                  Integer limit) {
        var slice = log.modelCalls(runId, page, limit);
        return ResponseEntity.ok(new PaginatedAgentModelCallResponse(slice.items(), pagination(slice)));
    }

    @Override
    @PreAuthorize(ConsoleAccess.ORGANIZER_PERSON)
    public ResponseEntity<AgentModelCallDetail> getAgentRunModelCall(UUID runId, UUID modelCallId) {
        return ResponseEntity.ok(log.modelCall(runId, modelCallId));
    }

    @Override
    @PreAuthorize(ConsoleAccess.ORGANIZER_PERSON)
    public ResponseEntity<PaginatedAgentToolCallResponse> listAgentRunToolCalls(UUID runId, Integer page,
                                                                                Integer limit) {
        var slice = log.toolCalls(runId, page, limit);
        return ResponseEntity.ok(new PaginatedAgentToolCallResponse(slice.items(), pagination(slice)));
    }

    @Override
    @PreAuthorize(ConsoleAccess.ORGANIZER_PERSON)
    public ResponseEntity<PaginatedAgentVerdictResponse> listAgentRunVerdicts(UUID runId, Integer page,
                                                                              Integer limit) {
        var slice = log.verdicts(runId, page, limit);
        return ResponseEntity.ok(new PaginatedAgentVerdictResponse(slice.items(), pagination(slice)));
    }

    private static PaginationMetadata pagination(RunLogReadService.Slice<?> slice) {
        int totalPages = (int) ((slice.total() + slice.limit() - 1) / slice.limit());
        return PaginationMetadata.builder()
                .page(slice.page())
                .limit(slice.limit())
                .totalItems(slice.total())
                .totalPages(totalPages)
                .hasNext(slice.page() + 1 < totalPages)
                .hasPrev(slice.page() > 0)
                .build();
    }

    private static AgentRun toDto(AgentRunEntity run) {
        AgentRun dto = new AgentRun(run.getId(),
                AgentRun.StatusEnum.fromValue(run.getStatus().name()),
                run.getStartedAt().atOffset(ZoneOffset.UTC),
                AgentRun.TriggerEnum.fromValue(run.getTrigger().name()));
        if (run.getEndedAt() != null) {
            dto.setEndedAt(run.getEndedAt().atOffset(ZoneOffset.UTC));
        }
        if (run.getEndReason() != null) {
            dto.setEndReason(AgentRun.EndReasonEnum.fromValue(run.getEndReason().name()));
        }
        dto.setEndDetail(run.getEndDetail());
        dto.setGrantRunId(run.getGrantRunId());
        if (run.getPrincipalKind() != null) {
            dto.setPrincipalKind(AgentRun.PrincipalKindEnum.fromValue(run.getPrincipalKind().name()));
        }
        return dto;
    }
}
