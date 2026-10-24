package com.aquasuite.ims.share.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Base query parameter DTO for pagination and sorting in AquaSuite REST endpoints.
 * Can be extended by feature-specific filter requests (e.g. ProductFilterRequest extends PageQuery).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageQuery {

    /**
     * Page index (0-based according to Spring Data conventions). Default is 0.
     */
    @Builder.Default
    private int page = 0;

    /**
     * Number of items per page. Default is 20, capped at 100 by PageableUtils.
     */
    @Builder.Default
    private int size = 20;

    /**
     * Entity attribute name to sort by. Validated against a whitelist in PageableUtils.
     */
    private String sortBy;

    /**
     * Sort direction ("ASC" or "DESC"). Default is "DESC".
     */
    @Builder.Default
    private String sortDir = "DESC";
}
