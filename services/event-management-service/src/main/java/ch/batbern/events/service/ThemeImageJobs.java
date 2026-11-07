package ch.batbern.events.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Theme images in the background (2026-11-07). An image takes about 40 s with the current OpenAI
 * model; the AWS HTTP API in front of the gateway ends every request at 30 s, so a synchronous call
 * never reached the browser although the image was stored. The page now starts a job and polls it.
 *
 * <p>Job state lives in memory for one hour. Event-management runs one task; a poll that reaches
 * another task during a rolling deploy gets 404 and the page asks to try again.
 */
@Component
@Slf4j
public class ThemeImageJobs {

    public enum Status { PENDING, DONE, FAILED }

    public record Job(UUID id, Status status, String imageUrl, String s3Key) {
    }

    private final BatbernAiService aiService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Cache<UUID, Job> jobs = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(200)
            .build();

    public ThemeImageJobs(BatbernAiService aiService) {
        this.aiService = aiService;
    }

    /** Starts the generation and returns the pending job at once. */
    public Job start(String eventCode, Map<String, String> vars, String seed) {
        UUID id = UUID.randomUUID();
        Job pending = new Job(id, Status.PENDING, null, null);
        jobs.put(id, pending);
        executor.execute(() -> {
            Job done;
            try {
                done = aiService.generateThemeImage(eventCode, vars, seed)
                        .map(r -> new Job(id, Status.DONE, r.imageUrl(), r.s3Key()))
                        .orElseGet(() -> new Job(id, Status.FAILED, null, null));
            } catch (RuntimeException e) {
                log.warn("Theme image job {} for {} failed: {}", id, eventCode, e.getMessage());
                done = new Job(id, Status.FAILED, null, null);
            }
            jobs.put(id, done);
            log.info("Theme image job {} for {} finished: {}", id, eventCode, done.status());
        });
        return pending;
    }

    public Optional<Job> get(UUID id) {
        return Optional.ofNullable(jobs.getIfPresent(id));
    }
}
