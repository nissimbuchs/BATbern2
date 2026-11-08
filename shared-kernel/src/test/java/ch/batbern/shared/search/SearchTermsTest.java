package ch.batbern.shared.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SearchTerms: split and escape a search input")
class SearchTermsTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n", "-", " ' _ - "})
    void should_haveNoTerms_when_inputIsBlankOrSeparatorsOnly(String input) {
        SearchTerms terms = SearchTerms.parse(input);
        assertThat(terms.isEmpty()).isTrue();
        assertThat(terms.terms()).isEmpty();
    }

    @Test
    void should_splitOnWhitespaceHyphenApostropheAndUnderscore() {
        assertThat(SearchTerms.parse("  Hans-Peter  O'Brien_Muster ").terms())
                .containsExactly("Hans", "Peter", "O", "Brien", "Muster");
    }

    @Test
    void should_escapeLikeWildcardsAndBackslash() {
        assertThat(SearchTerms.parse("100% a\\b").terms()).containsExactly("100\\%", "a\\\\b");
        assertThat(SearchTerms.escapeLike("a_b%c\\")).isEqualTo("a\\_b\\%c\\\\");
    }

    @Test
    void should_keepParenthesesAndPlusLiterally_when_inputIsHostile() {
        assertThat(SearchTerms.parse("(test C++").terms()).containsExactly("(test", "C++");
    }

    @Test
    void should_keepAtMostEightTerms() {
        assertThat(SearchTerms.parse("a b c d e f g h i j").terms())
                .hasSize(SearchTerms.MAX_TERMS)
                .containsExactly("a", "b", "c", "d", "e", "f", "g", "h");
    }

    @Test
    void should_exposeTrimmedCollapsedUnescapedPhrase_forRanking() {
        assertThat(SearchTerms.parse("  Hans   Muster 100% ").phrase()).isEqualTo("Hans Muster 100%");
    }
}
