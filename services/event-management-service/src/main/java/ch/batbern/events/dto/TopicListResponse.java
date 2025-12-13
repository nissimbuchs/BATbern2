package ch.batbern.events.dto;

import java.util.List;

/**
 * Response DTO for paginated topic list (Story 5.2).
 */
public class TopicListResponse {

    private List<TopicResponse> data;
    private PaginationMetadata pagination;

    public TopicListResponse() {
    }

    public TopicListResponse(List<TopicResponse> data, PaginationMetadata pagination) {
        this.data = data;
        this.pagination = pagination;
    }

    public List<TopicResponse> getData() {
        return data;
    }

    public void setData(List<TopicResponse> data) {
        this.data = data;
    }

    public PaginationMetadata getPagination() {
        return pagination;
    }

    public void setPagination(PaginationMetadata pagination) {
        this.pagination = pagination;
    }

    /**
     * Pagination metadata.
     */
    public static class PaginationMetadata {
        private Integer page;
        private Integer limit;
        private Long total;

        public PaginationMetadata() {
        }

        public PaginationMetadata(Integer page, Integer limit, Long total) {
            this.page = page;
            this.limit = limit;
            this.total = total;
        }

        public Integer getPage() {
            return page;
        }

        public void setPage(Integer page) {
            this.page = page;
        }

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }

        public Long getTotal() {
            return total;
        }

        public void setTotal(Long total) {
            this.total = total;
        }
    }
}
