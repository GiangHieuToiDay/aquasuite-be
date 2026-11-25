package com.aquasuite.ims.service.crm;

import com.aquasuite.ims.dto.crm.request.CustomerRegistrationRequest;
import com.aquasuite.ims.dto.crm.response.CustomerAddressDto;
import com.aquasuite.ims.dto.crm.response.CustomerDebtProfileDto;
import com.aquasuite.ims.dto.crm.response.CustomerRegistrationResponse;
import com.aquasuite.ims.dto.crm.response.CustomerSummaryDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public synchronous interface for the CRM Module (FE-10).
 * Other modules (Sales, ServiceDesk, Finance, IAM) communicate with CRM exclusively via this contract.
 */
public interface CustomerService extends CustomerRegistrationService {

    Optional<CustomerSummaryDto> getCustomerByUserId(UUID userId);

    Optional<CustomerSummaryDto> getCustomerById(UUID customerId);

    Optional<CustomerSummaryDto> getCustomerByPhone(String phone);

    List<CustomerAddressDto> getDeliveryAddressesByUserId(UUID userId);

    List<CustomerAddressDto> getDeliveryAddressesByCustomerId(UUID customerId);

    Optional<CustomerAddressDto> getDefaultDeliveryAddress(UUID customerId);

    /** Reuse by canonical Vietnamese mobile phone, or create an unlinked RETAIL customer.
     * Joins the sales transaction; no IAM user is created. Existing CRM fields are preserved. */
    CustomerSummaryDto getOrCreateCounterCustomer(String phone, String name);

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

    /** Tìm tập customerId theo từ khóa SĐT (phục vụ lọc danh sách bảo hành). */
    List<UUID> findCustomerIdsByPhoneKeyword(String phoneKeyword);

    /** Tìm tập customerId khớp gần đúng theo SĐT, tên hoặc mã khách hàng (phục vụ tra cứu đơn hàng). */
    List<UUID> findCustomerIdsByKeyword(String keyword);

    /** Tra cứu hàng loạt khách hàng theo tập customerIds (tránh N+1 queries). */
    Map<UUID, CustomerSummaryDto> getCustomersByIds(Collection<UUID> customerIds);

    @Override
    CustomerRegistrationResponse createProfile(CustomerRegistrationRequest request);
}
