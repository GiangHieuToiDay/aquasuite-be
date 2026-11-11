package com.aquasuite.ims.module.inventory.internal.controller;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.StockReservationApi;
import com.aquasuite.ims.module.inventory.api.dto.SkuReservationStock;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** UC36 read-only view of the units held and still available for each SKU/warehouse. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory/stock-reservations")
public class StockReservationQueryController {
    private final StockReservationApi inventory;
    private final IamApi iam;

    @GetMapping("/variants/{variantId}")
    public ApiResponse<List<SkuReservationStock>> stock(@PathVariable UUID variantId) {
        var actor = iam.getCurrentUser();
        if (actor == null || actor.id() == null || actor.roles() == null || actor.roles().stream()
                .noneMatch(Set.of("SHOP_OWNER", "ADMIN", "SUPER_ADMIN", "SALES_STAFF")::contains)) {
            throw new AppException(CommonErrorCode.ACCESS_DENIED);
        }
        return ApiResponse.ok(inventory.stockByVariant(variantId));
    }
}
