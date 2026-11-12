package ch.batbern.companyuser.client;

import ch.batbern.shared.test.contract.ContractServer;

/**
 * Binds the S2S contract harness to the {@code RestTemplate} of the real
 * {@link HttpMailCenterRequestContract}, so a test runs the real contract caller against the
 * contract CUMS owns ({@code docs/api/internal/cums-internal.openapi.yml}) and answers as its
 * implementer would. {@code close()} the server in {@code @AfterEach}: the caller is a bean of the
 * cached context.
 */
public final class MailCenterContract {

    public static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";
    public static final String OPENED = "/internal/mail-center-requests";
    public static final String DECIDED = "/internal/mail-center-requests/{requestId}/decision";

    private MailCenterContract() {
    }

    public static ContractServer bindTo(HttpMailCenterRequestContract contract) {
        return ContractServer.bindTo(contract.restTemplate(), CUMS_INTERNAL);
    }
}
