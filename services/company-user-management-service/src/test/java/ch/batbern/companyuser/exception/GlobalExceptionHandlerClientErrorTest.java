package ch.batbern.companyuser.exception;

import ch.batbern.shared.test.ClientErrorContract;
import org.junit.jupiter.api.Test;

/**
 * A client mistake answers 4xx with severity LOW, not 500 CRITICAL (#1148, #1157, #1103).
 * See {@link ClientErrorContract}.
 */
class GlobalExceptionHandlerClientErrorTest {

    @Test
    void should_answerClientStatus_when_requestIsMalformed() throws Exception {
        ClientErrorContract.verify(new GlobalExceptionHandler());
    }
}
