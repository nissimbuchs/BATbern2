package ch.batbern.shared.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offset pagination needs a total order, or PostgreSQL may order ties differently per page
 * (#1131: 248 duplicates and 337 hidden subscribers on the production list).
 */
@DisplayName("DeterministicPaging Unit Tests")
class DeterministicPagingTest {

    @Test
    @DisplayName("should_appendIdAscending_when_sortHasNoUniqueKey")
    void should_appendIdAscending_when_sortHasNoUniqueKey() {
        Sort sort = DeterministicPaging.withTieBreaker(Sort.by(Sort.Direction.DESC, "subscribedAt"));

        assertThat(sort.toList()).containsExactly(
                Sort.Order.desc("subscribedAt"),
                Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("should_sortById_when_sortIsUnsorted")
    void should_sortById_when_sortIsUnsorted() {
        assertThat(DeterministicPaging.withTieBreaker(Sort.unsorted()).toList())
                .containsExactly(Sort.Order.asc("id"));
        assertThat(DeterministicPaging.withTieBreaker((Sort) null).toList())
                .containsExactly(Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("should_keepSortUnchanged_when_uniqueKeyAlreadySorted")
    void should_keepSortUnchanged_when_uniqueKeyAlreadySorted() {
        Sort original = Sort.by(Sort.Order.asc("lastName"), Sort.Order.desc("id"));

        assertThat(DeterministicPaging.withTieBreaker(original)).isEqualTo(original);
    }

    @Test
    @DisplayName("should_useGivenUniqueProperty_when_entityKeyIsNotId")
    void should_useGivenUniqueProperty_when_entityKeyIsNotId() {
        Sort sort = DeterministicPaging.withTieBreaker(Sort.by("date"), "eventCode");

        assertThat(sort.toList()).containsExactly(Sort.Order.asc("date"), Sort.Order.asc("eventCode"));
    }

    @Test
    @DisplayName("should_buildPageRequestWithTieBreaker_when_pageRequested")
    void should_buildPageRequestWithTieBreaker_when_pageRequested() {
        Pageable pageable = DeterministicPaging.pageRequest(2, 50, Sort.by("createdAt").descending());

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort().toList()).containsExactly(
                Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("should_addTieBreaker_when_existingPageableGiven")
    void should_addTieBreaker_when_existingPageableGiven() {
        Pageable pageable = DeterministicPaging.withTieBreaker(PageRequest.of(1, 10, Sort.by("name")));

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().toList()).containsExactly(Sort.Order.asc("name"), Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("should_returnUnpagedWithStableSort_when_unpagedPageableGiven")
    void should_returnUnpagedWithStableSort_when_unpagedPageableGiven() {
        Pageable pageable = DeterministicPaging.withTieBreaker(Pageable.unpaged());

        assertThat(pageable.isPaged()).isFalse();
        assertThat(pageable.getSort().toList()).containsExactly(Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("should_addNoSort_when_queryOrdersItself")
    void should_addNoSort_when_queryOrdersItself() {
        Pageable pageable = DeterministicPaging.orderedByQuery(3, 20);

        assertThat(pageable.getPageNumber()).isEqualTo(3);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().isUnsorted()).isTrue();
    }

    @Test
    @DisplayName("should_reject_when_uniquePropertyBlank")
    void should_reject_when_uniquePropertyBlank() {
        assertThatThrownBy(() -> DeterministicPaging.withTieBreaker(Sort.by("x"), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
