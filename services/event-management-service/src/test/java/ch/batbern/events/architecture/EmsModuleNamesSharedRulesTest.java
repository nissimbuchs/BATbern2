package ch.batbern.events.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code scripts/architecture/ems-modules.json} is the one copy of the EMS module naming rules: the
 * module scripts and {@link EmsModuleNames} both read it. Its {@code examples} table is asserted here
 * and by {@code scripts/architecture/test_ems_modules.py}, so the two interpreters cannot disagree on it.
 */
class EmsModuleNamesSharedRulesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("should_classifyEveryExampleOfTheSharedRules_when_givenClassNames")
    void should_classifyEveryExampleOfTheSharedRules_when_givenClassNames() throws IOException {
        JsonNode examples = JSON.readTree(Files.readString(EmsModuleNames.rulesFile())).get("examples");
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonNode> example : examples.properties()) {
            String[] key = example.getKey().split("/", 2);
            String actual = EmsModuleNames.moduleOf(key[0], key[1]);
            if (!actual.equals(example.getValue().asText())) {
                mismatches.add(example.getKey() + " -> " + actual + ", file says " + example.getValue().asText());
            }
        }
        assertThat(examples.size()).isGreaterThanOrEqualTo(13);
        assertThat(mismatches).isEmpty();
    }

    @Test
    @DisplayName("should_followTheRulesFile_when_aKeywordChanges")
    void should_followTheRulesFile_when_aKeywordChanges() {
        JsonNode rules = EmsModuleNames.load(EmsModuleNames.rulesFile());
        assertThat(EmsModuleNames.moduleOf(rules, "service", "EventAnalyticsService")).isEqualTo("analytics");

        for (JsonNode rule : rules.get("rules")) {
            if ("analytics".equals(rule.get("module").asText())) {
                ((ArrayNode) rule.get("contains")).removeAll().add("Attendance");
            }
        }

        assertThat(EmsModuleNames.moduleOf(rules, "service", "EventAnalyticsService"))
                .isEqualTo(EmsModuleNames.EVENT_CORE);
    }
}
