package com.aquasuite.ims.module.crm.internal.service;

import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.entity.enums.CustomerType;
import com.aquasuite.ims.module.crm.internal.exception.CrmErrorCode;
import com.aquasuite.ims.module.crm.internal.payload.req.UpdateProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.AvatarUploadResponse;
import com.aquasuite.ims.module.crm.internal.payload.res.DeliveryAddressResponse;
import com.aquasuite.ims.module.crm.internal.payload.res.UserProfileResponse;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.module.crm.internal.service.impl.UserProfileServiceImpl;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private DeliveryAddressService deliveryAddressService;

    @Mock
    private CloudinaryStorageService cloudinaryStorageService;

    @Mock
    private IamApi iamApi;

    @InjectMocks
    private UserProfileServiceImpl userProfileService;

    private UUID userId;
    private UserSummaryDto sampleUserDto;
    private Customer sampleCustomer;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleUserDto = UserSummaryDto.builder()
                .id(userId)
                .username("customer_user")
                .fullName("Nguyen Van A")
                .email("nguyenvana@example.com")
                .phone("0912345678")
                .avatarUrl("https://res.cloudinary.com/aquasuite/avatar1.png")
                .status("ACTIVE")
                .roles(List.of("CUSTOMER"))
                .build();

        sampleCustomer = Customer.builder()
                .customerId(UUID.randomUUID())
                .userId(userId)
                .customerCode("KH-202610-001")
                .customerName("Nguyen Van A")
                .phone("0912345678")
                .email("nguyenvana@example.com")
                .customerType(CustomerType.RETAIL)
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("getProfile: returns full profile details with customerTier and addresses")
    void getProfile_WhenCustomerExists_ReturnsProfileWithTierAndAddresses() {
        DeliveryAddressResponse address = DeliveryAddressResponse.builder()
                .addressId(UUID.randomUUID())
                .recipientName("Nguyen Van A")
                .phone("0912345678")
                .addressText("123 Nguyen Trai, Thanh Xuan, Ha Noi")
                .isDefault(true)
                .build();

        when(iamApi.getUserById(userId)).thenReturn(sampleUserDto);
        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(sampleCustomer));
        when(deliveryAddressService.getAddressesByUserId(userId)).thenReturn(List.of(address));

        UserProfileResponse response = userProfileService.getProfile(userId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.fullName()).isEqualTo("Nguyen Van A");
        assertThat(response.customerCode()).isEqualTo("KH-202610-001");
        assertThat(response.customerTier()).isEqualTo("RETAIL");
        assertThat(response.deliveryAddresses()).hasSize(1);
        assertThat(response.deliveryAddresses().getFirst().isDefault()).isTrue();
    }

    @Test
    @DisplayName("getProfile: when user is staff (no customer record), customerCode and customerTier are null")
    void getProfile_WhenCustomerNotFound_ReturnsProfileWithNullCustomerFields() {
        when(iamApi.getUserById(userId)).thenReturn(sampleUserDto);
        when(customerRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(deliveryAddressService.getAddressesByUserId(userId)).thenReturn(List.of());

        UserProfileResponse response = userProfileService.getProfile(userId);

        assertThat(response).isNotNull();
        assertThat(response.customerCode()).isNull();
        assertThat(response.customerTier()).isNull();
        assertThat(response.deliveryAddresses()).isEmpty();
    }

    @Test
    @DisplayName("updateProfile: duplicate phone number throws MSG25 conflict error")
    void updateProfile_DuplicatePhone_ThrowsAppExceptionMSG25() {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("Nguyen Van B")
                .phone("0987654321")
                .build();

        when(iamApi.existsByPhoneAndUserIdNot("0987654321", userId)).thenReturn(true);

        assertThatThrownBy(() -> userProfileService.updateProfile(userId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode().getCode()).isEqualTo("MSG25");
                });

        verify(iamApi, never()).updateUserProfile(any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateProfile: successful update synchronizes IAM and CRM customer")
    void updateProfile_Success_SyncsIamAndCrm() {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("Nguyen Van B")
                .phone("0987654321")
                .avatarUrl("https://res.cloudinary.com/aquasuite/avatar2.png")
                .build();

        UserSummaryDto updatedDto = UserSummaryDto.builder()
                .id(userId)
                .username("customer_user")
                .fullName("Nguyen Van B")
                .email("nguyenvana@example.com")
                .phone("0987654321")
                .avatarUrl("https://res.cloudinary.com/aquasuite/avatar2.png")
                .status("ACTIVE")
                .roles(List.of("CUSTOMER"))
                .build();

        when(iamApi.existsByPhoneAndUserIdNot("0987654321", userId)).thenReturn(false);
        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(sampleCustomer));
        when(iamApi.getUserById(userId)).thenReturn(updatedDto);
        when(deliveryAddressService.getAddressesByUserId(userId)).thenReturn(List.of());

        UserProfileResponse response = userProfileService.updateProfile(userId, request);

        verify(iamApi).updateUserProfile(userId, "Nguyen Van B", "0987654321", "https://res.cloudinary.com/aquasuite/avatar2.png");
        verify(customerRepository).save(sampleCustomer);
        assertThat(sampleCustomer.getCustomerName()).isEqualTo("Nguyen Van B");
        assertThat(sampleCustomer.getPhone()).isEqualTo("0987654321");

        assertThat(response.fullName()).isEqualTo("Nguyen Van B");
        assertThat(response.phone()).isEqualTo("0987654321");
    }

    @Test
    @DisplayName("uploadAvatar: uploads to Cloudinary and updates user profile in IAM")
    void uploadAvatar_Success_UploadsAndUpdatesIam() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3}
        );
        String uploadedUrl = "https://res.cloudinary.com/aquasuite/image/upload/v1/avatars/new_pic.png";

        when(cloudinaryStorageService.uploadAvatar(file)).thenReturn(uploadedUrl);

        AvatarUploadResponse response = userProfileService.uploadAvatar(userId, file);

        assertThat(response).isNotNull();
        assertThat(response.avatarUrl()).isEqualTo(uploadedUrl);
        verify(iamApi).updateUserProfile(userId, null, null, uploadedUrl);
    }
}
