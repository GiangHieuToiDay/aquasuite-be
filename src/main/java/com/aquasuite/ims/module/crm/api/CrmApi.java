package com.aquasuite.ims.module.crm.api;

import com.aquasuite.ims.module.crm.api.dto.CustomerAddressDto;
import com.aquasuite.ims.module.crm.api.dto.CustomerDebtProfileDto;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public synchronous interface for the CRM Module (FE-10).
 * Other modules (Sales, ServiceDesk, Finance, IAM) communicate with CRM exclusively via this contract.
 */
public interface CrmApi {

    Optional<CustomerSummaryDto> getCustomerByUserId(UUID userId);

    Optional<CustomerSummaryDto> getCustomerById(UUID customerId);

    Optional<CustomerSummaryDto> getCustomerByPhone(String phone);

    List<CustomerAddressDto> getDeliveryAddressesByUserId(UUID userId);

    List<CustomerAddressDto> getDeliveryAddressesByCustomerId(UUID customerId);

    Optional<CustomerAddressDto> getDefaultDeliveryAddress(UUID customerId);

    CustomerSummaryDto getOrCreateWalkInCustomer();

    /**
     * Lấy hoặc tạo mới hồ sơ khách lẻ vãng lai tại quầy theo SĐT và tên nhập tay.
     */
    CustomerSummaryDto getOrCreateWalkInCustomer(String customerName, String phone);

    List<CustomerDebtProfileDto> findAllDebtProfiles();

    Optional<CustomerDebtProfileDto> getDebtProfile(UUID customerId);

    void updateCustomerStatus(UUID customerId, String status);

    /**
     * Tạo yêu cầu cấp hoặc tăng hạn mức công nợ cho khách hàng từ luồng Báo giá dự án (UC-28 / UC-24).
     * @return requestId của yêu cầu chờ duyệt
     */
    UUID submitCreditProfileRequestFromQuotation(
            UUID customerId,
            java.math.BigDecimal requestedLimit,
            Integer creditTermDays,
            String reason,
            UUID requesterId);
}
