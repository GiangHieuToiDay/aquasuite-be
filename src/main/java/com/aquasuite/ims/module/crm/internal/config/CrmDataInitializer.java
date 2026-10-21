package com.aquasuite.ims.module.crm.internal.config;

import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.entity.CustomerAddress;
import com.aquasuite.ims.module.crm.internal.enums.CustomerType;
import com.aquasuite.ims.module.crm.internal.repository.CustomerAddressRepository;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class CrmDataInitializer implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final IamApi iamApi;

    @Override
    public void run(String... args) {
        initDefaultCustomerAndAddress();
    }

    private void initDefaultCustomerAndAddress() {
        Optional<UserSummaryDto> userOpt = iamApi.findByEmail("customer@aquasuite.com");
        if (userOpt.isEmpty()) {
            log.info("customer@aquasuite.com not found yet, skipping CRM initializer");
            return;
        }

        UserSummaryDto user = userOpt.get();
        Customer customer = customerRepository.findByUserId(user.id()).orElseGet(() -> {
            String customerCode = "CUS-202610-001";
            if (customerRepository.existsByCustomerCode(customerCode)) {
                customerCode = "CUS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }
            Customer newCustomer = Customer.builder()
                    .customerId(UUID.randomUUID())
                    .userId(user.id())
                    .customerCode(customerCode)
                    .customerName(user.fullName() != null ? user.fullName() : "Nguyễn Văn A")
                    .phone(user.phone() != null ? user.phone() : "0987654321")
                    .email(user.email())
                    .customerType(CustomerType.RETAIL)
                    .status("ACTIVE")
                    .build();
            Customer saved = customerRepository.save(newCustomer);
            log.info("Initialized default customer profile {} for userId={}", customerCode, user.id());
            return saved;
        });

        if (customerAddressRepository.countByCustomerIdAndIsDeletedFalse(customer.getCustomerId()) == 0) {
            CustomerAddress defaultAddress = CustomerAddress.builder()
                    .addressId(UUID.randomUUID())
                    .customerId(customer.getCustomerId())
                    .recipientName("Nguyễn Văn A")
                    .phone("0987654321")
                    .province("Hà Nội")
                    .district("Cầu Giấy")
                    .ward("Dịch Vọng")
                    .streetAddress("123 Duy Tân")
                    .addressText("123 Duy Tân, Dịch Vọng, Cầu Giấy, Hà Nội")
                    .label("Home")
                    .isDefault(true)
                    .isDeleted(false)
                    .build();
            customerAddressRepository.save(defaultAddress);
            log.info("Initialized default delivery address for customerId={}", customer.getCustomerId());
        }
    }
}
