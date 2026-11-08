package ch.batbern.partners.integration;

import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.FlushBeforeRollback;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Positive control for {@link FlushBeforeRollback} (17.0b3): a {@code @Transactional} test that
 * leaves an unflushed constraint violation behind fails, instead of passing because the rollback
 * discards SQL that was never sent.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("FlushBeforeRollback")
class FlushBeforeRollbackIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    @DisplayName("an unflushed duplicate fails at the flush with the database error")
    void should_failWithDatabaseError_when_unflushedViolationLeftBehind() {
        partnerRepository.saveAndFlush(partner("FlushCtrlCo"));
        partnerRepository.save(partner("FlushCtrlCo")); // pending insert, never flushed by the test

        assertThatThrownBy(() -> new FlushBeforeRollback().afterTestExecution(passingTest()))
                .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class)
                .hasMessageContaining("partners_company_name_key");
        entityManager.clear(); // the failed entity must not be flushed again by the real listener
    }

    @Test
    @Transactional
    @DisplayName("an already aborted transaction (25P02) is left to the test's own assertion")
    void should_skip_when_transactionAborted() {
        partnerRepository.saveAndFlush(partner("FlushAbrtCo"));
        assertThatThrownBy(() -> partnerRepository.saveAndFlush(partner("FlushAbrtCo")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatCode(() -> new FlushBeforeRollback().afterTestExecution(passingTest()))
                .doesNotThrowAnyException();
    }

    private TestContext passingTest() {
        TestContext context = mock(TestContext.class);
        when(context.getApplicationContext()).thenReturn(applicationContext);
        when(context.getTestException()).thenReturn(null);
        return context;
    }

    private static Partner partner(String companyName) {
        return Partner.builder()
                .companyName(companyName)
                .partnershipLevel(PartnershipLevel.SILVER)
                .partnershipStartDate(LocalDate.now())
                .build();
    }
}
