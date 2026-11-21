package com.aquasuite.ims.controller.inventory;

import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.inventory.StockReservationService;
import com.aquasuite.ims.dto.inventory.response.SkuReservationStock;
import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
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
    private final StockReservationService inventory;
    private final IamQueryService iam;

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
