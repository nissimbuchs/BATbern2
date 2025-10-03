package ch.batbern.gateway.validation;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Test Controller for Validation Testing
 *
 * Only active in test profile - used to verify @Valid annotation handling
 */
@RestController
@RequestMapping("/api/v1/validation")
@Profile("test")
@Slf4j
public class TestValidationController {

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testValidation(
            @Valid @RequestBody TestValidationRequest request) {

        log.info("Validation test request received: {}", request);

        return ResponseEntity.ok(Map.of(
                "message", "Validation passed",
                "data", request
        ));
    }
}
