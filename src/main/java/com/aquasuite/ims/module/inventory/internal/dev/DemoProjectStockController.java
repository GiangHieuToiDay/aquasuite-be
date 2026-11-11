package com.aquasuite.ims.module.inventory.internal.dev;

import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.UUID;

@RestController
@Profile("uc29-demo & !prod & !production")
@RequestMapping("/api/v1/dev/uc29/inventory-stock")
@RequiredArgsConstructor
public class DemoProjectStockController {
    private final DemoProjectReservationApi inventory;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_SHOP_OWNER', 'SHOP_OWNER', 'ROLE_ADMIN', 'ADMIN')")
    public ApiResponse<DemoProjectReservationApi.StockSnapshot> seed(@Valid @RequestBody Stock request) {
        return ApiResponse.ok(inventory.setStock(request.warehouseCode(), request.variantId(), request.onHandQty()),
                "SIMULATED stock only; inventories table is unchanged");
    }

    public record Stock(@NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,50}") String warehouseCode,
                        @NotNull UUID variantId,
                        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal onHandQty) {}
}
