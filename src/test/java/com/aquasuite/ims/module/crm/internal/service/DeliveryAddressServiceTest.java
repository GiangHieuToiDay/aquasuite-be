package com.aquasuite.ims.module.crm.internal.service;

import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.entity.DeliveryAddressItem;
import com.aquasuite.ims.module.crm.internal.enums.CustomerType;
import com.aquasuite.ims.module.crm.internal.error.CrmErrorCode;
import com.aquasuite.ims.module.crm.internal.payload.req.CreateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.req.UpdateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.DeliveryAddressResponse;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.module.crm.internal.service.impl.DeliveryAddressServiceImpl;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryAddressServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private IamApi iamApi;

    @InjectMocks
    private DeliveryAddressServiceImpl deliveryAddressService;

    private UUID userId;
    private UUID customerId;
    private Customer sampleCustomer;
    private DeliveryAddressItem sampleAddress1;
    private DeliveryAddressItem sampleAddress2;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        customerId = UUID.randomUUID();

        sampleAddress1 = DeliveryAddressItem.builder()
                .addressId(UUID.randomUUID())
                .recipientName("Tran Van B")
                .phone("0987654321")
                .province("Ha Noi")
                .district("Thanh Xuan")
                .ward("Thuong Dinh")
                .streetAddress("72A Nguyen Trai")
                .addressText("72A Nguyen Trai, Thuong Dinh, Thanh Xuan, Ha Noi")
                .label("Home")
                .isDefault(true)
                .isDeleted(false)
                .createdAt(Instant.now().minusSeconds(3600))
                .updatedAt(Instant.now().minusSeconds(3600))
                .build();

        sampleAddress2 = DeliveryAddressItem.builder()
                .addressId(UUID.randomUUID())
                .recipientName("Tran Van B (VP)")
                .phone("0987654321")
                .province("Ha Noi")
                .district("Cau Giay")
                .ward("Dich Vong")
                .streetAddress("1 Duy Tan")
                .addressText("1 Duy Tan, Dich Vong, Cau Giay, Ha Noi")
                .label("Office")
                .isDefault(false)
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        sampleCustomer = Customer.builder()
                .customerId(customerId)
                .userId(userId)
                .customerCode("KH-202610-001")
                .customerName("Tran Van B")
                .phone("0987654321")
                .customerType(CustomerType.RETAIL)
                .status("ACTIVE")
                .deliveryAddresses(new ArrayList<>(List.of(sampleAddress1, sampleAddress2)))
                .build();
    }

    @Test
    @DisplayName("getAddressesByUserId: returns addresses sorted default first")
    void getAddressesByUserId_ReturnsSortedAddresses() {
        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(sampleCustomer));

        List<DeliveryAddressResponse> list = deliveryAddressService.getAddressesByUserId(userId);

        assertThat(list).hasSize(2);
        assertThat(list.getFirst().isDefault()).isTrue();
        assertThat(list.get(1).isDefault()).isFalse();
    }

    @Test
    @DisplayName("createAddress: first address for customer is automatically designated as default")
    void createAddress_FirstAddress_AutomaticallySetAsDefault() {
        sampleCustomer.getDeliveryAddresses().clear();

        CreateDeliveryAddressRequest request = CreateDeliveryAddressRequest.builder()
                .recipientName("Tran Van B")
                .phone("0987654321")
                .province("Ha Noi")
                .district("Thanh Xuan")
                .ward("Thuong Dinh")
                .streetAddress("72A Nguyen Trai")
                .label("Home")
                .isDefault(false) // Request does not specify default, but it's the 1st address
                .build();

        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(sampleCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        DeliveryAddressResponse response = deliveryAddressService.createAddress(userId, request);

        assertThat(response.isDefault()).isTrue();
        assertThat(response.recipientName()).isEqualTo("Tran Van B");
        assertThat(sampleCustomer.getDeliveryAddresses()).hasSize(1);
    }

    @Test
    @DisplayName("createAddress: subsequent address requested as default clears old defaults")
    void createAddress_SubsequentAddressAsDefault_ClearsOldDefault() {
        CreateDeliveryAddressRequest request = CreateDeliveryAddressRequest.builder()
                .recipientName("Tran Van B")
                .phone("0987654321")
                .province("Ha Noi")
                .district("Cau Giay")
                .ward("Dich Vong")
                .streetAddress("1 Duy Tan")
                .label("Office")
                .isDefault(true)
                .build();

        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(sampleCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        DeliveryAddressResponse response = deliveryAddressService.createAddress(userId, request);

        assertThat(response.isDefault()).isTrue();
        assertThat(sampleAddress1.isDefault()).isFalse();
        assertThat(sampleCustomer.getDeliveryAddresses()).hasSize(3);
    }

    @Test
    @DisplayName("updateAddress: forbidden access when updating another user's address")
    void updateAddress_AnotherCustomerAddress_ThrowsForbiddenAccess() {
        UUID otherCustomerId = UUID.randomUUID();
        DeliveryAddressItem otherAddress = DeliveryAddressItem.builder()
                .addressId(UUID.randomUUID())
                .recipientName("Victim")
                .phone("0911111111")
                .province("HN")
                .district("CG")
                .ward("DV")
                .streetAddress("100 Street")
                .isDefault(true)
                .isDeleted(false)
                .build();

        Customer otherCustomer = Customer.builder()
                .customerId(otherCustomerId)
                .userId(UUID.randomUUID())
                .customerCode("CUS-OTHER")
                .deliveryAddresses(new ArrayList<>(List.of(otherAddress)))
                .build();

        UpdateDeliveryAddressRequest request = UpdateDeliveryAddressRequest.builder()
                .recipientName("Hacker")
                .phone("0900000000")
                .province("DN")
                .district("HC")
                .ward("TB")
                .streetAddress("123 Fake")
                .build();

        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(sampleCustomer));
        when(customerRepository.findAll()).thenReturn(List.of(sampleCustomer, otherCustomer));

        assertThatThrownBy(() -> deliveryAddressService.updateAddress(userId, otherAddress.getAddressId(), request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(CrmErrorCode.FORBIDDEN_ADDRESS_ACCESS);
                });
    }

    @Test
    @DisplayName("deleteAddress: soft deletes and promotes next active address to default if deleting a default")
    void deleteAddress_DefaultAddress_PromotesNextActiveAddressToDefault() {
        UUID deletingId = sampleAddress1.getAddressId();

        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(sampleCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        deliveryAddressService.deleteAddress(userId, deletingId);

        assertThat(sampleAddress1.isDeleted()).isTrue();
        assertThat(sampleAddress1.isDefault()).isFalse();
        assertThat(sampleAddress2.isDefault()).isTrue();
        verify(customerRepository, times(1)).save(sampleCustomer);
    }

    @Test
    @DisplayName("setDefaultAddress: clears other defaults and designates target address as default")
    void setDefaultAddress_Success_DesignatesNewDefault() {
        UUID targetId = sampleAddress2.getAddressId();

        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(sampleCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        DeliveryAddressResponse response = deliveryAddressService.setDefaultAddress(userId, targetId);

        assertThat(response.isDefault()).isTrue();
        assertThat(sampleAddress1.isDefault()).isFalse();
        assertThat(sampleAddress2.isDefault()).isTrue();
        verify(customerRepository, times(1)).save(sampleCustomer);
    }
}
