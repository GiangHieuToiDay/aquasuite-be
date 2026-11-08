package com.aquasuite.ims.module.finance.internal.payload.response;

import java.util.List;

public record CustomerDebtPageResponse(
        List<CustomerDebtSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
