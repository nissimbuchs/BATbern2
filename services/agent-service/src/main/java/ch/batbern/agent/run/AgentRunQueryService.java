package ch.batbern.agent.run;

import ch.batbern.shared.api.DeterministicPaging;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the runs for the organizers' read API. */
@Service
public class AgentRunQueryService {

    /** Newest first; DeterministicPaging adds the id as tie-breaker (#1131). */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("startedAt"));

    private final AgentRunRepository runs;

    public AgentRunQueryService(AgentRunRepository runs) {
        this.runs = runs;
    }

    /** One page of runs, newest first; {@code page} is 0-based. */
    @Transactional(readOnly = true)
    public Page<AgentRunEntity> list(int page, int limit) {
        return runs.findAll(DeterministicPaging.pageRequest(page, limit, NEWEST_FIRST));
    }
}
