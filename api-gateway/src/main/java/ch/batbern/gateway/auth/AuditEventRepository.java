package ch.batbern.gateway.auth;

public interface AuditEventRepository {

    void save(Object auditEvent);

}