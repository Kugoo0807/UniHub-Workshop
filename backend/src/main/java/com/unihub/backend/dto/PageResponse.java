package com.unihub.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Generic paginated response wrapper.
 *
 * @param <T> type of the content items
 */
@Getter
@Builder
@Schema(description = "Paginated response wrapper.")
public class PageResponse<T> {

    @Schema(description = "List of items on the current page")
    private List<T> content;

    @Schema(description = "Current page number (0-indexed)", example = "0")
    private int page;

    @Schema(description = "Page size (max items per page)", example = "12")
    private int size;

    @Schema(description = "Total number of items across all pages", example = "42")
    private long totalElements;

    @Schema(description = "Total number of pages", example = "4")
    private int totalPages;

    @Schema(description = "Whether this is the last page", example = "false")
    private boolean last;
}
