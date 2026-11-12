package ch.batbern.shared.upload;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The shared upload mechanics (Story 17.11): state machine, rules, target check and expiry. S3 and
 * the clock are the boundary; the table is an in-memory stand-in for the service repository.
 */
@DisplayName("UploadService")
class UploadServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-12T10:00:00Z");

    /** The S3 boundary: records copies and deletes, knows which keys exist. */
    private final S3Client s3 = mock(S3Client.class);
    private final S3Presigner presigner = mock(S3Presigner.class);
    private final Map<String, TestUpload> table = new HashMap<>();
    private final List<String> copies = new ArrayList<>();
    private final List<String> deletes = new ArrayList<>();
    private final Set<String> missingKeys = new java.util.HashSet<>();
    private final Set<String> failingDeletes = new java.util.HashSet<>();
    private final TestHandler handler = new TestHandler();
    private MutableClock clock;
    private UploadService<TestUpload> service;

    static class TestUpload extends UploadRecord {
    }

    static class TestHandler implements UploadPurposeHandler {
        final List<String> attached = new ArrayList<>();
        boolean targetAllowed = true;
        boolean importAllowed;

        @Override
        public boolean supportsImport() {
            return importAllowed;
        }

        @Override
        public void checkMayImport() {
        }

        @Override
        public String purpose() {
            return "company-logo";
        }

        @Override
        public UploadRules rules() {
            return UploadRules.images5Mb();
        }

        @Override
        public String targetKey() {
            return "companyName";
        }

        @Override
        public String targetType() {
            return "COMPANY";
        }

        @Override
        public void checkTarget(String targetId) {
            if (!targetAllowed) {
                throw UploadException.forbidden("no");
            }
            if (!"acme".equals(targetId)) {
                throw UploadException.notFound("no company " + targetId);
            }
        }

        @Override
        public String finalKey(UploadRecord upload, String targetId) {
            return "logos/2026/companies/" + targetId + "/logo-" + upload.getUploadId() + "."
                    + upload.getFileExtension();
        }

        @Override
        public void attach(UploadRecord upload, String targetId) {
            attached.add(targetId + "=" + upload.getFileUrl());
        }
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @SuppressWarnings("unchecked")
    private UploadRecordRepository<TestUpload> inMemoryRepository() {
        return (UploadRecordRepository<TestUpload>) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {UploadRecordRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        TestUpload u = (TestUpload) args[0];
                        table.put(u.getUploadId(), u);
                        yield u;
                    }
                    case "delete" -> table.remove(((TestUpload) args[0]).getUploadId());
                    case "findByUploadId" -> Optional.ofNullable(table.get((String) args[0]));
                    case "findByStatusInAndExpiresAtBefore" -> table.values().stream()
                            .filter(u -> ((List<?>) args[0]).contains(u.getStatus()))
                            .filter(u -> u.getExpiresAt() != null && u.getExpiresAt().isBefore((Instant) args[1]))
                            .toList();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @BeforeEach
    void setUp() throws Exception {
        clock = new MutableClock(T0);
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://bucket.example/put").toURL());
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);
        when(s3.headObject(any(HeadObjectRequest.class))).thenAnswer(inv -> {
            HeadObjectRequest r = inv.getArgument(0);
            if (missingKeys.contains(r.key())) {
                throw NoSuchKeyException.builder().statusCode(404).build();
            }
            return HeadObjectResponse.builder().build();
        });
        when(s3.copyObject(any(CopyObjectRequest.class))).thenAnswer(inv -> {
            CopyObjectRequest r = inv.getArgument(0);
            copies.add(r.sourceKey() + "->" + r.destinationKey());
            return null;
        });
        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenAnswer(inv -> {
            DeleteObjectRequest r = inv.getArgument(0);
            if (failingDeletes.contains(r.key())) {
                throw S3Exception.builder().statusCode(500).message("boom").build();
            }
            deletes.add(r.key());
            return null;
        });
        service = new UploadService<>(inMemoryRepository(), TestUpload::new, List.of(handler), s3, presigner,
                new UploadService.UploadStorage("bucket", "https://cdn.batbern.ch"), clock);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("orga.nizer",
                null, List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String reserve() {
        return service.create("company-logo", "Logo.PNG", "image/png", 1000, Map.of()).uploadId();
    }

    @Test
    void should_reservePendingUpload_when_typeAndSizeAllowed() {
        UploadService.PresignedUpload slot = service.create("company-logo", "Logo.PNG", "image/png; charset=x",
                1000, Map.of());

        TestUpload stored = table.get(slot.uploadId());
        assertThat(stored.getStatus()).isEqualTo(UploadStatus.PENDING);
        assertThat(stored.getS3Key()).isEqualTo("logos/temp/" + slot.uploadId() + "/logo-" + slot.uploadId() + ".png");
        assertThat(stored.getContentType()).isEqualTo("image/png");
        assertThat(stored.getCreatedBy()).isEqualTo("orga.nizer");
        assertThat(stored.getExpiresAt()).isEqualTo(T0.plus(Duration.ofHours(24)));
        assertThat(slot.expiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(15)));
        assertThat(slot.requiredHeaders()).containsEntry("Content-Type", "image/png");
    }

    @Test
    void should_reject_when_typeOrSizeNotAllowed() {
        assertStatus(() -> service.create("company-logo", "logo.gif", "image/gif", 10, Map.of()),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create("company-logo", "logo.png", "application/pdf", 10, Map.of()),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create("company-logo", "logo", "image/png", 10, Map.of()),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create("company-logo", "logo.png", "image/png", 5L * 1024 * 1024 + 1, Map.of()),
                HttpStatus.BAD_REQUEST);
        assertThat(table).isEmpty();
    }

    @Test
    void should_return404_when_purposeIsNotThisServices() {
        assertStatus(() -> service.create("event-theme-image", "a.png", "image/png", 10, Map.of()),
                HttpStatus.NOT_FOUND);
    }

    @Test
    void should_checkTarget_when_reservingForOne() {
        assertStatus(() -> service.create("company-logo", "a.png", "image/png", 10, Map.of("companyName", "nope")),
                HttpStatus.NOT_FOUND);
        assertStatus(() -> service.create("company-logo", "a.png", "image/png", 10, Map.of("eventCode", "BATbern1")),
                HttpStatus.BAD_REQUEST);
        handler.targetAllowed = false;
        assertStatus(() -> service.create("company-logo", "a.png", "image/png", 10, Map.of("companyName", "acme")),
                HttpStatus.FORBIDDEN);
    }

    @Test
    void should_confirm_when_fileIsInStorage() {
        String id = reserve();
        clock.advance(Duration.ofMinutes(5));

        TestUpload confirmed = service.confirm("company-logo", id, Map.of());

        assertThat(confirmed.getStatus()).isEqualTo(UploadStatus.CONFIRMED);
        assertThat(confirmed.getExpiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(5)).plus(Duration.ofDays(7)));
        assertThat(handler.attached).isEmpty();
    }

    @Test
    void should_return409_when_fileNotInStorage() {
        String id = reserve();
        missingKeys.add(table.get(id).getS3Key());

        assertStatus(() -> service.confirm("company-logo", id, Map.of()), HttpStatus.CONFLICT);
        assertThat(table.get(id).getStatus()).isEqualTo(UploadStatus.PENDING);
    }

    @Test
    void should_associateAndAttach_when_confirmedWithTarget() {
        String id = reserve();
        String temp = table.get(id).getS3Key();

        TestUpload done = service.confirm("company-logo", id, Map.of("companyName", "acme"));

        String finalKey = "logos/2026/companies/acme/logo-" + id + ".png";
        assertThat(done.getStatus()).isEqualTo(UploadStatus.ASSOCIATED);
        assertThat(done.getS3Key()).isEqualTo(finalKey);
        assertThat(done.getFileUrl()).isEqualTo("https://cdn.batbern.ch/" + finalKey);
        assertThat(done.getTargetType()).isEqualTo("COMPANY");
        assertThat(done.getTargetId()).isEqualTo("acme");
        assertThat(done.getExpiresAt()).isNull();
        assertThat(copies).containsExactly(temp + "->" + finalKey);
        assertThat(deletes).containsExactly(temp);
        assertThat(handler.attached).containsExactly("acme=https://cdn.batbern.ch/" + finalKey);
    }

    @Test
    void should_keepTempObject_when_associationRollsBack() {
        String id = reserve();
        service.confirm("company-logo", id, Map.of());
        String temp = table.get(id).getS3Key();

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.associate("company-logo", id, "acme");
            assertThat(deletes).as("nothing deleted before the commit").isEmpty();
            // rollback: the synchronizations are dropped without afterCommit
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(deletes).doesNotContain(temp);
    }

    @Test
    void should_deleteTempObject_when_associationCommits() {
        String id = reserve();
        service.confirm("company-logo", id, Map.of());
        String temp = table.get(id).getS3Key();

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.associate("company-logo", id, "acme");
            assertThat(deletes).isEmpty();
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(deletes).containsExactly(temp);
    }

    @Test
    void should_return409_when_confirmingAnAssociatedUpload() {
        String id = reserve();
        service.confirm("company-logo", id, Map.of("companyName", "acme"));

        assertStatus(() -> service.confirm("company-logo", id, Map.of()), HttpStatus.CONFLICT);
    }

    @Test
    void should_return404_when_uploadExpiredOrUnknown() {
        String id = reserve();
        clock.advance(Duration.ofHours(24));
        assertThat(table.get(id).isExpired(clock.instant())).as("the cut-off itself is not expired yet").isFalse();
        clock.advance(Duration.ofSeconds(1));

        assertStatus(() -> service.confirm("company-logo", id, Map.of()), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.confirm("company-logo", "nope", Map.of()), HttpStatus.NOT_FOUND);
    }

    @Test
    void should_return409_when_associatingAnUnconfirmedUpload() {
        String id = reserve();

        assertStatus(() -> service.associate("company-logo", id, "acme"), HttpStatus.CONFLICT);
    }

    @Test
    void should_copyAndKeepSource_when_reassigned() {
        String id = reserve();
        service.confirm("company-logo", id, Map.of());
        service.associateAt("company-logo", id, "LOGO_REQUEST", "acme", "logos/requests/" + id + "/logo.png");
        copies.clear();
        deletes.clear();

        TestUpload moved = service.reassign("company-logo", id, "COMPANY", "acme", "logos/2026/companies/acme/x.png");

        assertThat(moved.getTargetType()).isEqualTo("COMPANY");
        assertThat(moved.getFileUrl()).isEqualTo("https://cdn.batbern.ch/logos/2026/companies/acme/x.png");
        assertThat(copies).containsExactly("logos/requests/" + id + "/logo.png->logos/2026/companies/acme/x.png");
        assertThat(deletes).isEmpty();
    }

    @Test
    void should_deleteExpiredRowsAndObjects_when_expiryRuns() {
        String pendingOld = reserve();
        String confirmedOld = reserve();
        service.confirm("company-logo", confirmedOld, Map.of());
        String associated = reserve();
        service.confirm("company-logo", associated, Map.of("companyName", "acme"));
        deletes.clear();

        clock.advance(Duration.ofHours(25)); // PENDING expired, CONFIRMED (7 d) not yet
        String pendingNew = reserve();

        assertThat(service.purgeExpired()).isEqualTo(new UploadService.ExpiryResult(1, 0));
        assertThat(table).containsOnlyKeys(confirmedOld, associated, pendingNew);
        assertThat(deletes).hasSize(1);

        clock.advance(Duration.ofDays(7));
        assertThat(service.purgeExpired()).isEqualTo(new UploadService.ExpiryResult(2, 0));
        assertThat(table).containsOnlyKeys(associated);
        assertThat(pendingOld).isNotIn(table.keySet());
    }

    @Test
    void should_keepRowAndCountFailure_when_s3DeleteFails() {
        String id = reserve();
        failingDeletes.add(table.get(id).getS3Key());
        clock.advance(Duration.ofHours(25));

        assertThat(service.purgeExpired()).isEqualTo(new UploadService.ExpiryResult(0, 1));
        assertThat(table).containsKey(id);
    }

    @Test
    void should_rejectNonHttps_when_importing() {
        handler.importAllowed = true;
        assertStatus(() -> service.importFromUrl("company-logo", "http://example.com/a.png", "logo"),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.importFromUrl("company-logo", "file:///etc/passwd", "logo"),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_return404_when_purposeHasNoImport() {
        handler.importAllowed = false;
        assertStatus(() -> service.importFromUrl("company-logo", "https://example.com/a.png", "logo"),
                HttpStatus.NOT_FOUND);
    }

    private static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(UploadException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }
}
