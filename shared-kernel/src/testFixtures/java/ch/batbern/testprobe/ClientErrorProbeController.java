package ch.batbern.testprobe;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.Map;

/**
 * Probe endpoints for {@code ch.batbern.shared.test.ClientErrorContract}.
 *
 * <p>Lives outside {@code ch.batbern.shared} on purpose: the services component-scan that
 * package, and this controller would otherwise be registered in every test context (and fail
 * {@code EndpointAuthorizationGuardTest}). {@code MockMvcBuilders.standaloneSetup} registers it
 * explicitly instead.
 */
@RestController
@RequestMapping(ClientErrorProbeController.BASE)
public class ClientErrorProbeController {

    public static final String BASE = "/client-error-probe";

    @GetMapping("/param")
    String param(@RequestParam("scope") String scope) {
        return scope;
    }

    @GetMapping("/header")
    String header(@RequestHeader("X-Attendee-Username") String username) {
        return username;
    }

    @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> json(@RequestBody Map<String, Object> body) {
        return body;
    }

    @GetMapping("/nul")
    String nul() {
        throw new DataIntegrityViolationException("JDBC exception executing SQL",
                new SQLException("ERROR: invalid byte sequence for encoding \"UTF8\": 0x00", "22021"));
    }
}
