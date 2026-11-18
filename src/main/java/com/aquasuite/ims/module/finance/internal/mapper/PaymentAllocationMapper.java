package com.aquasuite.ims.module.finance.internal.mapper;

import com.aquasuite.ims.module.finance.internal.entity.PaymentInvoiceAllocation;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationLineResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PaymentAllocationMapper {
    PaymentAllocationLineResponse toResponse(PaymentInvoiceAllocation allocation);

    List<PaymentAllocationLineResponse> toResponses(List<PaymentInvoiceAllocation> allocations);
}
