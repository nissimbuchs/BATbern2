package ch.batbern.companyuser.service;

import ch.batbern.companyuser.service.CompanyDuplicateRules.Confidence;
import ch.batbern.companyuser.service.CompanyDuplicateRules.Facts;
import ch.batbern.companyuser.service.CompanyDuplicateRules.Match;
import ch.batbern.companyuser.service.CompanyDuplicateRules.Named;
import ch.batbern.companyuser.service.CompanyDuplicateRules.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The four rules that pair candidate duplicate companies (#1251, story 5), and the suggested target.
 * Pure functions over key, display name and website; the I/O matrix of the story plan row by row.
 */
@DisplayName("Candidate duplicate companies: rules and suggested target")
class CompanyDuplicateRulesTest {

    private static Named company(String key, String displayName) {
        return new Named(key, displayName, null);
    }

    private static Optional<Rule> rule(Named a, Named b) {
        return CompanyDuplicateRules.rule(CompanyDuplicateRules.normalize(a), CompanyDuplicateRules.normalize(b));
    }

    @Nested
    @DisplayName("rules")
    class Rules {

        @Test
        @DisplayName("Unic / Unic AG: SAME_NAME_WITHOUT_LEGAL_FORM, MEDIUM, not also SLUG_PREFIX")
        void legalForm() {
            List<Match> pairs = CompanyDuplicateRules.pairs(List.of(company("unic", "Unic"),
                    company("unicag", "Unic AG")));

            assertThat(pairs).hasSize(1);
            assertThat(pairs.get(0).rule()).isEqualTo(Rule.SAME_NAME_WITHOUT_LEGAL_FORM);
            assertThat(pairs.get(0).rule().confidence()).isEqualTo(Confidence.MEDIUM);
        }

        @Test
        @DisplayName("Die Post / Die Post and SBB CFF FFS / SBB CFF FFS: SAME_DISPLAY_NAME, HIGH")
        void sameDisplayName() {
            assertThat(rule(company("diepost", "Die Post"), company("post", "Die Post")))
                    .contains(Rule.SAME_DISPLAY_NAME);
            assertThat(rule(company("sbbcffffs", "SBB CFF FFS"), company("sbb", "SBB CFF FFS")))
                    .contains(Rule.SAME_DISPLAY_NAME);
            assertThat(Rule.SAME_DISPLAY_NAME.confidence()).isEqualTo(Confidence.HIGH);
        }

        @Test
        @DisplayName("display name compared folded: case, umlauts, accents, hyphens, punctuation, spaces")
        void sameDisplayName_folded() {
            assertThat(rule(company("muellerbau", "Müller-Bau"), company("mullerbau1", "  mueller  BAU ")))
                    .contains(Rule.SAME_DISPLAY_NAME);
            assertThat(rule(company("cafe", "Café Crème"), company("cafe2", "Cafe Creme!")))
                    .contains(Rule.SAME_DISPLAY_NAME);
            assertThat(rule(company("strasse", "Strasse/Weg"), company("strasse2", "Straße Weg")))
                    .contains(Rule.SAME_DISPLAY_NAME);
        }

        @Test
        @DisplayName("no display name: the key stands for it")
        void sameDisplayName_keyWhenNone() {
            assertThat(rule(company("acme", null), company("acmeag", "ACME"))).contains(Rule.SAME_DISPLAY_NAME);
        }

        @Test
        @DisplayName("puzzle / puzzleitc (Puzzle ITC): SLUG_PREFIX, LOW")
        void prefix() {
            assertThat(rule(company("puzzle", "Puzzle"), company("puzzleitc", "Puzzle ITC")))
                    .contains(Rule.SLUG_PREFIX);
            assertThat(Rule.SLUG_PREFIX.confidence()).isEqualTo(Confidence.LOW);
        }

        @Test
        @DisplayName("prefix compared ignoring case")
        void prefix_ignoringCase() {
            assertThat(rule(company("Puzzle", "Puzzle"), company("puzzleitc", "Puzzle ITC")))
                    .contains(Rule.SLUG_PREFIX);
        }

        @Test
        @DisplayName("ibm (3) / ibmswitzerla: no pair, the shorter key needs 4 characters")
        void shortPrefix() {
            assertThat(rule(company("ibm", "IBM"), company("ibmswitzerla", "IBM Switzerland"))).isEmpty();
            assertThat(rule(company("abbb", "ABBB"), company("abbbxyz", "Something"))).contains(Rule.SLUG_PREFIX);
        }

        @Test
        @DisplayName("Kanton Bern / Kanton Zürich: no pair, generic words are kept")
        void genericWords() {
            assertThat(CompanyDuplicateRules.pairs(List.of(company("kantonbern", "Kanton Bern"),
                    company("kantonzueric", "Kanton Zürich"), company("unibern", "Uni Bern"),
                    company("schweiz", "Schweiz AG")))).isEmpty();
        }

        @Test
        @DisplayName("legal forms removed anywhere, then a trailing co; an empty result never matches")
        void legalForms() {
            assertThat(rule(company("muellergmbh", "Müller GmbH & Co. KG"), company("muller", "Mueller")))
                    .contains(Rule.SAME_NAME_WITHOUT_LEGAL_FORM);
            assertThat(rule(company("acmesa", "ACME S.A."), company("acmeinc", "Acme Inc.")))
                    .contains(Rule.SAME_NAME_WITHOUT_LEGAL_FORM);
            assertThat(rule(company("foosarl", "Foo Sàrl"), company("fooltd", "Foo Ltd")))
                    .contains(Rule.SAME_NAME_WITHOUT_LEGAL_FORM);
            assertThat(rule(company("barllc", "Bar LLC"), company("barco", "Bar Co")))
                    .contains(Rule.SAME_NAME_WITHOUT_LEGAL_FORM);
            assertThat(rule(company("x1", "AG"), company("x2", "GmbH"))).isEmpty();
            assertThat(rule(company("cocacola", "Coca Cola"), company("colacoca", "Cola"))).isEmpty();
        }

        @Test
        @DisplayName("https://www.x.ch/de and http://x.ch: SAME_WEBSITE, HIGH, whatever the names")
        void sameWebsite() {
            Named a = new Named("alpha", "Alpha", "https://www.x.ch/de");
            Named b = new Named("beta", "Beta", "http://x.ch");

            assertThat(rule(a, b)).contains(Rule.SAME_WEBSITE);
            assertThat(Rule.SAME_WEBSITE.confidence()).isEqualTo(Confidence.HIGH);
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(delimiter = '|', value = {
            "https://www.x.ch/de|x.ch",
            "http://x.ch|x.ch",
            "HTTPS://WWW.Example.CH:443/path?q=1#top|example.ch",
            "www.example.ch|example.ch",
            "example.ch/|example.ch",
            "  https://user@shop.example.ch  |shop.example.ch",
            "https://www.|''",
        })
        void host(String website, String host) {
            assertThat(CompanyDuplicateRules.host(website)).isEqualTo(host == null ? "" : host);
        }

        @Test
        @DisplayName("a shared platform: linkedin.com/company/a and /company/b are no pair, the same profile is")
        void sharedPlatform_linkedIn() {
            Named a = new Named("alpha", "Alpha", "https://www.linkedin.com/company/a");
            Named b = new Named("beta", "Beta", "https://linkedin.com/company/b/");
            Named sameAsA = new Named("gamma", "Gamma", "https://ch.linkedin.com/company/A/about?trk=x");

            assertThat(rule(a, b)).isEmpty();
            Named delta = new Named("delta", "Delta", "https://www.linkedin.com/company/a");

            assertThat(rule(a, delta)).contains(Rule.SAME_WEBSITE);
            assertThat(rule(a, sameAsA)).contains(Rule.SAME_WEBSITE);
        }

        @ParameterizedTest(name = "{0} vs {1}: no pair")
        @CsvSource(delimiter = '|', value = {
            "https://facebook.com/alpha|https://www.facebook.com/beta",
            "https://www.facebook.com/pages/alpha/123|https://www.facebook.com/pages/beta/456",
            "https://www.xing.com/pages/alpha|https://www.xing.com/pages/beta",
            "https://www.xing.com/companies/alpha|https://www.xing.com/pages/alpha",
            "https://instagram.com/alpha|https://instagram.com/beta",
            "https://x.com/alpha|https://twitter.com/beta",
            "https://github.com/alpha|https://github.com/beta",
            "https://sites.google.com/view/alpha|https://sites.google.com/view/beta",
            "https://www.youtube.com/@alpha|https://www.youtube.com/@beta",
            "https://bit.ly/alpha|https://bit.ly/beta",
            "https://www.linkedin.com/company/|https://www.linkedin.com/company/",
            "https://www.linkedin.com|https://www.linkedin.com/",
            "https://www.facebook.com/profile.php?id=1|https://www.facebook.com/profile.php?id=2",
        })
        void sharedPlatform_differentProfiles(String websiteA, String websiteB) {
            assertThat(rule(new Named("alpha", "Alpha", websiteA), new Named("beta", "Beta", websiteB))).isEmpty();
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(delimiter = '|', value = {
            "https://www.linkedin.com/company/acme/|linkedin.com/company/acme",
            "https://de.linkedin.com/company/Acme/about|linkedin.com/company/acme",
            "https://m.facebook.com/acme?ref=1|facebook.com/acme",
            "https://www.facebook.com/pages/acme/123|facebook.com/pages/acme",
            "https://www.xing.com/pages/acme|xing.com/pages/acme",
            "https://twitter.com/acme|twitter.com/acme",
            "https://sites.google.com/view/acme/home|sites.google.com/view/acme",
            "https://www.linkedin.com/company/|''",
            "https://www.facebook.com|''",
            "https://www.acme.ch/de/home|acme.ch",
        })
        void site(String website, String site) {
            assertThat(CompanyDuplicateRules.site(website)).isEqualTo(site == null ? "" : site);
        }

        @Test
        @DisplayName("no website, an empty host or a host without a dot (placeholder) never matches")
        void noWebsite() {
            assertThat(rule(new Named("alpha", "Alpha", null), new Named("beta", "Beta", null))).isEmpty();
            assertThat(rule(new Named("alpha", "Alpha", "https://"), new Named("beta", "Beta", "http://")))
                    .isEmpty();
            for (String placeholder : List.of("-", "n/a", "none")) {
                assertThat(rule(new Named("alpha", "Alpha", placeholder), new Named("beta", "Beta", placeholder)))
                        .as(placeholder).isEmpty();
            }
        }

        @Test
        @DisplayName("a pair matching several rules is listed once, under the first")
        void firstRuleWins() {
            List<Match> pairs = CompanyDuplicateRules.pairs(List.of(new Named("post", "Die Post", "https://post.ch"),
                    new Named("diepost", "Die Post", "https://www.post.ch/de")));

            assertThat(pairs).singleElement().extracting(Match::rule).isEqualTo(Rule.SAME_WEBSITE);
        }

        @Test
        @DisplayName("over a list: every matching pair once, each with its two keys")
        void pairsOverAList() {
            List<Match> pairs = CompanyDuplicateRules.pairs(List.of(company("unic", "Unic"),
                    company("unicag", "Unic AG"), company("puzzle", "Puzzle"), company("puzzleitc", "Puzzle ITC"),
                    company("ibm", "IBM"), company("ibmswitzerla", "IBM Switzerland")));

            assertThat(pairs).extracting(m -> Set.of(m.a().key(), m.b().key()))
                    .containsExactlyInAnyOrder(Set.of("unic", "unicag"), Set.of("puzzle", "puzzleitc"));
        }
    }

    @Nested
    @DisplayName("suggested target")
    class Target {

        private static final Instant OLD = Instant.parse("2020-01-01T00:00:00Z");
        private static final Instant NEW = Instant.parse("2025-01-01T00:00:00Z");

        @Test
        @DisplayName("the partner first")
        void partner() {
            Facts partner = new Facts("zeta", true, false, 0, NEW);
            Facts other = new Facts("alpha", false, true, 9, OLD);

            assertThat(CompanyDuplicateRules.firstIsTarget(partner, other)).isTrue();
            assertThat(CompanyDuplicateRules.firstIsTarget(other, partner)).isFalse();
        }

        @Test
        @DisplayName("else the one with a logo")
        void logo() {
            Facts logo = new Facts("zeta", false, true, 0, NEW);
            Facts other = new Facts("alpha", false, false, 9, OLD);

            assertThat(CompanyDuplicateRules.firstIsTarget(other, logo)).isFalse();
            assertThat(CompanyDuplicateRules.firstIsTarget(logo, other)).isTrue();
        }

        @Test
        @DisplayName("else more users, else the older, else the smaller key")
        void usersAgeKey() {
            assertThat(CompanyDuplicateRules.firstIsTarget(new Facts("zeta", false, false, 3, NEW),
                    new Facts("alpha", false, false, 2, OLD))).isTrue();
            assertThat(CompanyDuplicateRules.firstIsTarget(new Facts("zeta", false, false, 2, OLD),
                    new Facts("alpha", false, false, 2, NEW))).isTrue();
            assertThat(CompanyDuplicateRules.firstIsTarget(new Facts("zeta", false, false, 2, OLD),
                    new Facts("alpha", false, false, 2, OLD))).isFalse();
            assertThat(CompanyDuplicateRules.firstIsTarget(new Facts("alpha", false, false, 2, null),
                    new Facts("zeta", false, false, 2, OLD))).isFalse();
        }
    }
}
