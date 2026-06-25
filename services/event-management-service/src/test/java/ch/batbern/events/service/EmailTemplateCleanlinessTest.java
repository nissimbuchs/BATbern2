package ch.batbern.events.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fitness test for the consolidated email-template styling architecture
 * (feature/consolidate-email-templates).
 *
 * <p>All presentation lives in the shared {@code batbern-default} layout; content
 * templates must be clean, class-based HTML so organizers can edit them safely.
 * This test fails the build if a content template regresses by:
 * <ul>
 *   <li>introducing an inline {@code style="..."} attribute,</li>
 *   <li>carrying any HTML comment other than the leading {@code <!-- subject: ... -->},</li>
 *   <li>or referencing a CSS class that is not defined in the layout's {@code <style>} block.</li>
 * </ul>
 *
 * <p>Scans only the production seed files under {@code src/main/resources}
 * (i.e. {@code /resources/main/}), never the test-fixture copies.
 */
@DisplayName("Email Template Cleanliness Fitness Tests")
class EmailTemplateCleanlinessTest {

    private static final Pattern INLINE_STYLE = Pattern.compile("\\sstyle\\s*=");
    private static final Pattern COMMENT = Pattern.compile("<!--");
    private static final Pattern LEADING_SUBJECT =
            Pattern.compile("^\\s*<!--\\s*subject:.*?-->", Pattern.DOTALL);
    private static final Pattern CLASS_ATTR = Pattern.compile("class=\"([^\"]*)\"");
    private static final Pattern CSS_CLASS_DEF = Pattern.compile("\\.([A-Za-z][\\w-]*)");

    private List<Resource> mainSeedResources() throws Exception {
        Resource[] all = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:email-templates/*.html");
        List<Resource> main = new ArrayList<>();
        for (Resource r : all) {
            // Exclude test-fixture copies (build/resources/test/...) — only check production seeds.
            if (r.getURL().getPath().contains("/resources/main/")) {
                main.add(r);
            }
        }
        assertThat(main)
                .as("Should find production email-template seed files under /resources/main/")
                .isNotEmpty();
        return main;
    }

    private boolean isLayout(Resource r) {
        String name = r.getFilename();
        return name != null && name.startsWith("layout-");
    }

    private String read(Resource r) throws Exception {
        return r.getContentAsString(StandardCharsets.UTF_8);
    }

    /** Collects every CSS class defined in any layout's {@code <style>} block. */
    private Set<String> definedClasses(List<Resource> resources) throws Exception {
        Set<String> defined = new LinkedHashSet<>();
        for (Resource r : resources) {
            if (!isLayout(r)) {
                continue;
            }
            String html = read(r);
            int start = html.indexOf("<style>");
            int end = html.indexOf("</style>");
            assertThat(start).as("layout %s must contain a <style> block", r.getFilename())
                    .isGreaterThanOrEqualTo(0);
            String style = html.substring(start, end);
            Matcher m = CSS_CLASS_DEF.matcher(style);
            while (m.find()) {
                defined.add(m.group(1));
            }
        }
        assertThat(defined).as("layout <style> should define CSS classes").isNotEmpty();
        return defined;
    }

    @Test
    @DisplayName("content templates must contain no inline style= attributes")
    void should_haveNoInlineStyles_inContentTemplates() throws Exception {
        for (Resource r : mainSeedResources()) {
            if (isLayout(r)) {
                continue;
            }
            assertThat(INLINE_STYLE.matcher(read(r)).find())
                    .as("content template %s must not use inline style= (styling lives in the layout)",
                            r.getFilename())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("content templates must carry only the leading <!-- subject: ... --> comment")
    void should_haveNoStrayComments_inContentTemplates() throws Exception {
        for (Resource r : mainSeedResources()) {
            if (isLayout(r)) {
                continue;
            }
            String html = read(r);
            long comments = COMMENT.matcher(html).results().count();
            assertThat(comments)
                    .as("content template %s must have at most one comment (the subject line)",
                            r.getFilename())
                    .isLessThanOrEqualTo(1);
            if (comments == 1) {
                assertThat(LEADING_SUBJECT.matcher(html).find())
                        .as("the only comment in %s must be the leading <!-- subject: ... -->",
                                r.getFilename())
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("every CSS class used in a content template must be defined in the layout")
    void should_useOnlyLayoutDefinedClasses() throws Exception {
        List<Resource> resources = mainSeedResources();
        Set<String> defined = definedClasses(resources);
        for (Resource r : resources) {
            if (isLayout(r)) {
                continue;
            }
            String html = read(r);
            Matcher m = CLASS_ATTR.matcher(html);
            while (m.find()) {
                for (String token : m.group(1).trim().split("\\s+")) {
                    if (token.isEmpty()) {
                        continue;
                    }
                    assertThat(defined)
                            .as("content template %s uses class '%s' which is not defined in the layout",
                                    r.getFilename(), token)
                            .contains(token);
                }
            }
        }
    }
}
