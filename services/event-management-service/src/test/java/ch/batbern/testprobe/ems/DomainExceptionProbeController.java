package ch.batbern.testprobe.ems;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

/**
 * Throws one given exception, for {@code DomainExceptionResponseTest}.
 *
 * <p>Lives outside {@code ch.batbern.events} on purpose: EMS component-scans that package (test
 * classes included), and this controller would otherwise be registered in every integration test
 * context. {@code MockMvcBuilders.standaloneSetup} registers it explicitly instead.
 */
@RestController
public class DomainExceptionProbeController {

    public static final String PATH = "/domain-exception-probe";

    private final Supplier<? extends RuntimeException> exception;

    public DomainExceptionProbeController(Supplier<? extends RuntimeException> exception) {
        this.exception = exception;
    }

    @GetMapping(PATH)
    String probe() {
        throw exception.get();
    }
}
