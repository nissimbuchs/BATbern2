package ch.batbern.companyuser.client;

import ch.batbern.shared.test.contract.ContractServer;

/**
 * Binds the S2S contract harness to the {@code RestTemplate} of the real
 * {@link HttpUserReferenceContract}, so a test runs the real contract caller against the
 * user-reference contract CUMS owns ({@code docs/api/internal/cums-internal.openapi.yml}) and
 * answers as each holder would. The test configuration names two holders, {@link #HOLDER_A} and
 * {@link #HOLDER_B} ({@code batbern.contracts.user-references.implementers}); match each with
 * {@code atHost}. {@code close()} the server in {@code @AfterEach}: the caller is a bean of the
 * cached context.
 */
public final class UserReferenceHolders {

    public static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";
    public static final String HOLDER_A = "holder-a.invalid";
    public static final String HOLDER_B = "holder-b.invalid";
    public static final String PREVIEW = "/internal/user-references/preview";
    public static final String MERGE = "/internal/user-references/merge";
    public static final String PRIMARY_EMAIL = "/internal/user-references/primary-email";

    private UserReferenceHolders() {
    }

    public static ContractServer bindTo(HttpUserReferenceContract contract) {
        return ContractServer.bindTo(contract.restTemplate(), CUMS_INTERNAL);
    }
}
