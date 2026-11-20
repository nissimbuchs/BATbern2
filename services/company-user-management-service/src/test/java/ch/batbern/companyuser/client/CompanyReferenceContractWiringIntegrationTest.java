package ch.batbern.companyuser.client;

import ch.batbern.companyuser.client.CompanyReferenceContract.MergeCall;
import ch.batbern.companyuser.client.CompanyReferenceContract.Trigger;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static ch.batbern.companyuser.client.CompanyReferenceHolders.HOLDER_A;
import static ch.batbern.companyuser.client.CompanyReferenceHolders.HOLDER_B;
import static ch.batbern.companyuser.client.CompanyReferenceHolders.MERGE;
import static ch.batbern.companyuser.client.CompanyReferenceHolders.PREVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

/**
 * The company-reference contract bean of the running service calls exactly the holders configured
 * under {@code batbern.contracts.company-references.implementers}, in order (#1251). CUMS names no
 * holder in code: the test configuration lists two {@code .invalid} hosts, answered at the wire.
 */
@Import(TestAwsConfig.class)
@DisplayName("company-reference contract: the holders come from configuration")
class CompanyReferenceContractWiringIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private HttpCompanyReferenceContract contract;

    private ContractServer holders;

    @BeforeEach
    void bind() {
        holders = CompanyReferenceHolders.bindTo(contract);
    }

    @AfterEach
    void close() {
        holders.close();
    }

    @Test
    void should_callEveryConfiguredHolder() {
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).respondWithExample("nothingHeld");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWithExample("clean");
        holders.expect(POST, MERGE).atHost(HOLDER_A).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(HOLDER_B).respondWithExample("merged");

        assertThat(contract.preview("unicag", "unic")).hasSize(2);
        assertThat(contract.merge(new MergeCall("unicag", "unic", "organizer.alice", Trigger.MERGE)))
                .extracting(r -> r.moved().size()).containsExactly(1, 3);
        holders.verify();
    }
}
