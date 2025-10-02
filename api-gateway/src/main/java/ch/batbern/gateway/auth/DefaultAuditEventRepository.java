package ch.batbern.gateway.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class DefaultAuditEventRepository implements AuditEventRepository {

    @Override
    public void save(Object auditEvent) {
        // For now, just log the audit event
        // In production, this would save to a database or audit log system
        log.info("Audit event saved: {}", auditEvent.toString());
    }
}