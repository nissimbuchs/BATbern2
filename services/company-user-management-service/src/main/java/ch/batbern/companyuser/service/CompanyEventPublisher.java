package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for publishing company domain events
 * AC7: Domain events to EventBridge
 *
 * STUB - Implementation to be added in Task 8
 * For now, just logs events to allow Task 4 tests to pass
 */
@Service
@Slf4j
public class CompanyEventPublisher {

    public void publishCompanyCreatedEvent(Company company) {
        log.debug("Would publish CompanyCreatedEvent for company: {}", company.getId());
        // Implementation in Task 8
    }

    public void publishCompanyUpdatedEvent(Company company) {
        log.debug("Would publish CompanyUpdatedEvent for company: {}", company.getId());
        // Implementation in Task 8
    }

    public void publishCompanyDeletedEvent(Company company) {
        log.debug("Would publish CompanyDeletedEvent for company: {}", company.getId());
        // Implementation in Task 8
    }

    public void publishCompanyVerifiedEvent(Company company) {
        log.debug("Would publish CompanyVerifiedEvent for company: {}", company.getId());
        // Implementation in Task 8
    }
}
