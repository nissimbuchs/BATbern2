package ch.batbern.companyuser.client;

import ch.batbern.shared.test.contract.ContractServer;

/**
 * Binds the S2S contract harness to the {@code RestTemplate} the real
 * {@link HttpEventManagementUserMergeClient} builds for itself, so an integration test runs the
 * real client against {@code docs/api/internal/ems-internal.openapi.yml} instead of a mock of it.
 * {@code close()} the server in {@code @AfterEach}: the client is a bean of the cached context.
 */
public final class EventManagementContract {

    public static final String EMS_INTERNAL = "docs/api/internal/ems-internal.openapi.yml";

    private EventManagementContract() {
    }

    public static ContractServer bindTo(HttpEventManagementUserMergeClient client) {
        return ContractServer.bindTo(client.restTemplate(), EMS_INTERNAL);
    }
}
