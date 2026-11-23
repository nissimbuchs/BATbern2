package ch.batbern.agent.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.time.Clock;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No tool escapes the class check (story 5): every concrete {@link AgentTool} in agent-service's main
 * sources is one of {@link StandardTools}, which the registry and the build check see. Test-only
 * tools (fixtures, loop test tools) are left out by their class root. The scan sees top-level and
 * static nested classes only; the loop offers nothing but the registry's tools, so an anonymous
 * tool could not reach the model anyway.
 */
@DisplayName("Every shipped tool is in the registry")
class StandardToolsCoverageTest {

    @Test
    @DisplayName("the concrete AgentTool classes of main sources are exactly the standard tools")
    void should_listEveryMainTool_when_registryIsBuilt() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(AgentTool.class));
        Set<String> inMain = new TreeSet<>();
        Set<String> inTests = new TreeSet<>();
        // Main and test classes are told apart by their class root, not by a path fragment
        String mainRoot = root(StandardTools.class);
        String testRoot = root(FixtureTool.class);
        assertThat(mainRoot).isNotEqualTo(testRoot);
        for (BeanDefinition candidate : scanner.findCandidateComponents("ch.batbern.agent")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (root(type).equals(mainRoot)) {
                inMain.add(type.getName());
            } else if (root(type).equals(testRoot)) {
                inTests.add(type.getName());
            }
        }
        Set<String> standard = StandardTools.create(Clock.systemUTC()).stream()
                .map(tool -> tool.getClass().getName()).collect(Collectors.toCollection(TreeSet::new));

        assertThat(inMain).isEqualTo(standard);
        // Positive control: the scan sees tools at all, and tells test tools apart
        assertThat(inTests).contains(FixtureTool.class.getName());
    }

    private static String root(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation().toString();
    }
}
