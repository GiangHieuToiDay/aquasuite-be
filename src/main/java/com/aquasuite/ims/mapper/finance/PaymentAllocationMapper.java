package com.aquasuite.ims.mapper.finance;

import com.aquasuite.ims.entity.finance.PaymentInvoiceAllocation;
import com.aquasuite.ims.dto.finance.response.PaymentAllocationLineResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PaymentAllocationMapper {
    PaymentAllocationLineResponse toResponse(PaymentInvoiceAllocation allocation);

    List<PaymentAllocationLineResponse> toResponses(List<PaymentInvoiceAllocation> allocations);
}
