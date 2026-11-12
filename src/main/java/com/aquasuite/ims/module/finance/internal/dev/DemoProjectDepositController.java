package com.aquasuite.ims.module.finance.internal.dev;

import com.aquasuite.ims.module.finance.api.ProjectDepositPaymentApi.*;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@Profile("uc29-demo & !prod & !production")
@RequestMapping("/api/v1/dev/uc29/payments")
@RequiredArgsConstructor
public class DemoProjectDepositController {
    private final DemoProjectDepositPaymentApi payments;

    @PostMapping("/{paymentId}/verification")
    @PreAuthorize("hasAnyAuthority('ROLE_SHOP_OWNER', 'SHOP_OWNER', 'ROLE_ACCOUNTANT', 'ACCOUNTANT')")
    public ApiResponse<Payment> verify(@PathVariable UUID paymentId, @Valid @RequestBody Verification request) {
        return ApiResponse.ok(payments.verify(paymentId, request.status()), "SIMULATED verification; no money was posted");
    }

    public record Verification(@NotNull Status status) {}
}
