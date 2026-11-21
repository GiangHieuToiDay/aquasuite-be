package com.aquasuite.ims.dto.finance.response;

import java.util.List;

public record CustomerDebtPageResponse(
        List<CustomerDebtSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
