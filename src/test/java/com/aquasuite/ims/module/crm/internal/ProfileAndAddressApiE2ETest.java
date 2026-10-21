package com.aquasuite.ims.module.crm.internal;

import com.aquasuite.ims.module.crm.internal.controller.AvatarUploadController;
import com.aquasuite.ims.module.crm.internal.controller.DeliveryAddressController;
import com.aquasuite.ims.module.crm.internal.controller.UserProfileController;
import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.entity.CustomerAddress;
import com.aquasuite.ims.module.crm.internal.entity.enums.CustomerType;
import com.aquasuite.ims.module.crm.internal.payload.req.CreateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.req.UpdateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.req.UpdateProfileRequest;
import com.aquasuite.ims.module.crm.internal.repository.CustomerAddressRepository;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.module.crm.internal.service.impl.CloudinaryStorageServiceImpl;
import com.aquasuite.ims.module.crm.internal.service.impl.DeliveryAddressServiceImpl;
import com.aquasuite.ims.module.crm.internal.service.impl.UserProfileServiceImpl;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileAndAddressApiE2ETest {

    private MockMvc profileMockMvc;
    private MockMvc avatarMockMvc;
    private MockMvc addressMockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerAddressRepository customerAddressRepository;

    @Mock
    private IamApi iamApi;

    private UserProfileServiceImpl userProfileService;
    private DeliveryAddressServiceImpl deliveryAddressService;
    private CloudinaryStorageServiceImpl cloudinaryStorageService;

    private UUID userId;
    private UUID customerId;
    private Customer customer;
    private UserSummaryDto userDto;
    private final Map<UUID, CustomerAddress> inMemoryAddressDb = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        customerId = UUID.randomUUID();

        customer = Customer.builder()
                .customerId(customerId)
                .userId(userId)
                .customerCode("KH-202610-001")
                .customerName("Nguyen Van Test")
                .phone("0912345678")
                .email("test@aquasuite.vn")
                .customerType(CustomerType.RETAIL)
                .status("ACTIVE")
                .build();

        userDto = UserSummaryDto.builder()
                .id(userId)
                .username("testuser")
                .fullName("Nguyen Van Test")
                .email("test@aquasuite.vn")
                .phone("0912345678")
                .avatarUrl("https://res.cloudinary.com/aquasuite/avatar_init.png")
                .status("ACTIVE")
                .roles(List.of("CUSTOMER"))
                .build();

        inMemoryAddressDb.clear();

        lenient().when(iamApi.getCurrentUserId()).thenReturn(userId);
        lenient().when(iamApi.getUserById(userId)).thenReturn(userDto);
        lenient().when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(customer));

        // Mock address repository using in-memory state
        lenient().when(customerAddressRepository.findByCustomerIdAndIsDeletedFalseOrderByIsDefaultDescUpdatedAtDesc(customerId))
                .thenAnswer(inv -> inMemoryAddressDb.values().stream()
                        .filter(a -> a.getCustomerId().equals(customerId) && !a.isDeleted())
                        .sorted(Comparator.comparing(CustomerAddress::isDefault).reversed()
                                .thenComparing(CustomerAddress::getUpdatedAt, Comparator.reverseOrder()))
                        .toList());

        lenient().when(customerAddressRepository.countByCustomerIdAndIsDeletedFalse(customerId))
                .thenAnswer(inv -> inMemoryAddressDb.values().stream()
                        .filter(a -> a.getCustomerId().equals(customerId) && !a.isDeleted())
                        .count());

        lenient().when(customerAddressRepository.findByAddressIdAndIsDeletedFalse(any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID id = inv.getArgument(0);
                    CustomerAddress a = inMemoryAddressDb.get(id);
                    return (a != null && !a.isDeleted()) ? Optional.of(a) : Optional.empty();
                });

        lenient().when(customerAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(any(UUID.class), eq(customerId)))
                .thenAnswer(inv -> {
                    UUID id = inv.getArgument(0);
                    CustomerAddress a = inMemoryAddressDb.get(id);
                    return (a != null && a.getCustomerId().equals(customerId) && !a.isDeleted()) ? Optional.of(a) : Optional.empty();
                });

        lenient().when(customerAddressRepository.findFirstByCustomerIdAndIsDeletedFalseOrderByUpdatedAtDesc(customerId))
                .thenAnswer(inv -> inMemoryAddressDb.values().stream()
                        .filter(a -> a.getCustomerId().equals(customerId) && !a.isDeleted())
                        .max(Comparator.comparing(CustomerAddress::getUpdatedAt)));

        lenient().doAnswer(inv -> {
            inMemoryAddressDb.values().forEach(a -> {
                if (a.getCustomerId().equals(customerId)) a.setDefault(false);
            });
            return null;
        }).when(customerAddressRepository).clearDefaultAddresses(customerId);

        lenient().when(customerAddressRepository.save(any(CustomerAddress.class))).thenAnswer(inv -> {
            CustomerAddress a = inv.getArgument(0);
            if (a.getAddressId() == null) a.setAddressId(UUID.randomUUID());
            if (a.getCreatedAt() == null) a.setCreatedAt(Instant.now());
            a.setUpdatedAt(Instant.now());
            inMemoryAddressDb.put(a.getAddressId(), a);
            return a;
        });

        cloudinaryStorageService = new CloudinaryStorageServiceImpl();
        deliveryAddressService = new DeliveryAddressServiceImpl(customerRepository, customerAddressRepository, iamApi);
        userProfileService = new UserProfileServiceImpl(customerRepository, deliveryAddressService, cloudinaryStorageService, iamApi);

        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

        profileMockMvc = MockMvcBuilders.standaloneSetup(new UserProfileController(userProfileService, iamApi))
                .setControllerAdvice(exceptionHandler).build();

        avatarMockMvc = MockMvcBuilders.standaloneSetup(new AvatarUploadController(userProfileService, iamApi))
                .setControllerAdvice(exceptionHandler).build();

        addressMockMvc = MockMvcBuilders.standaloneSetup(new DeliveryAddressController(deliveryAddressService, iamApi))
                .setControllerAdvice(exceptionHandler).build();
    }

    @Test
    @DisplayName("E2E Test: Full User Profile & Delivery Address Book Flow (UC-03)")
    void testFullProfileAndAddressBookFlow() throws Exception {
        System.out.println("==========================================================================");
        System.out.println(">>> STARTING E2E API VERIFICATION FOR UC-03: PROFILE & ADDRESSES");
        System.out.println("==========================================================================");

        // -------------------------------------------------------------
        // STEP 1: GET /api/v1/profile -> View Profile
        // -------------------------------------------------------------
        System.out.println("\n[1] TEST API: GET /api/v1/profile (View current profile)");
        MvcResult resGetProfile = profileMockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van Test"))
                .andExpect(jsonPath("$.data.customerCode").value("KH-202610-001"))
                .andExpect(jsonPath("$.data.customerTier").value("RETAIL"))
                .andReturn();
        System.out.println("HTTP Response 200 OK:");
        System.out.println(resGetProfile.getResponse().getContentAsString());

        // -------------------------------------------------------------
        // STEP 2: POST /api/v1/profile/avatar -> Upload Avatar without Cloudinary key
        // -------------------------------------------------------------
        System.out.println("\n[2] TEST API: POST /api/v1/profile/avatar (Upload new avatar file)");
        MockMultipartFile avatarFile = new MockMultipartFile(
                "file", "new_avatar.jpg", "image/jpeg", new byte[]{0x4A, 0x46, 0x49, 0x46, 0x01}
        );
        MvcResult resAvatar = avatarMockMvc.perform(multipart("/api/v1/profile/avatar").file(avatarFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Avatar uploaded successfully."))
                .andExpect(jsonPath("$.data.avatarUrl").isNotEmpty())
                .andReturn();
        System.out.println("HTTP Response 200 OK (Avatar Uploaded with Mock CDN URL):");
        System.out.println(resAvatar.getResponse().getContentAsString());

        // Verify avatar upload rejects invalid files (e.g. .pdf) with MSG17
        System.out.println("\n[2.1] TEST API: POST /api/v1/profile/avatar (Reject invalid file format)");
        MockMultipartFile badFile = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[]{1, 2, 3});
        MvcResult resBadAvatar = avatarMockMvc.perform(multipart("/api/v1/profile/avatar").file(badFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MSG17"))
                .andExpect(jsonPath("$.message").value("Avatar must be a JPEG, PNG, or WEBP image under 5MB."))
                .andReturn();
        System.out.println("HTTP Response 400 Bad Request (MSG17):");
        System.out.println(resBadAvatar.getResponse().getContentAsString());

        // -------------------------------------------------------------
        // STEP 3: PUT /api/v1/profile -> Update Full Name & Phone
        // -------------------------------------------------------------
        System.out.println("\n[3] TEST API: PUT /api/v1/profile (Update editable profile fields)");
        UpdateProfileRequest updateReq = UpdateProfileRequest.builder()
                .fullName("Nguyen Van Hoan")
                .phone("0988776655")
                .avatarUrl("https://res.cloudinary.com/aquasuite/image/upload/v1/avatars/new.jpg")
                .build();

        // Simulate updated user summary
        UserSummaryDto updatedUserDto = UserSummaryDto.builder()
                .id(userId)
                .username("testuser")
                .fullName("Nguyen Van Hoan")
                .email("test@aquasuite.vn")
                .phone("0988776655")
                .avatarUrl("https://res.cloudinary.com/aquasuite/image/upload/v1/avatars/new.jpg")
                .status("ACTIVE")
                .roles(List.of("CUSTOMER"))
                .build();
        when(iamApi.getUserById(userId)).thenReturn(updatedUserDto);

        MvcResult resUpdateProfile = profileMockMvc.perform(put("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Profile updated successfully."))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van Hoan"))
                .andExpect(jsonPath("$.data.phone").value("0988776655"))
                .andReturn();
        System.out.println("HTTP Response 200 OK (MSG13 - Profile updated successfully):");
        System.out.println(resUpdateProfile.getResponse().getContentAsString());

        // Verify duplicate phone returns MSG25
        System.out.println("\n[3.1] TEST API: PUT /api/v1/profile (Duplicate phone rejected with MSG25)");
        when(iamApi.existsByPhoneAndUserIdNot("0999999999", userId)).thenReturn(true);
        UpdateProfileRequest dupPhoneReq = UpdateProfileRequest.builder()
                .fullName("Nguyen Van Hoan")
                .phone("0999999999")
                .build();
        MvcResult resDupPhone = profileMockMvc.perform(put("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupPhoneReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MSG25"))
                .andReturn();
        System.out.println("HTTP Response 409 Conflict (MSG25):");
        System.out.println(resDupPhone.getResponse().getContentAsString());

        // -------------------------------------------------------------
        // STEP 4: POST /api/v1/profile/addresses -> Add 1st Address (auto default)
        // -------------------------------------------------------------
        System.out.println("\n[4] TEST API: POST /api/v1/profile/addresses (Add 1st Address - Auto Default)");
        CreateDeliveryAddressRequest addrReq1 = CreateDeliveryAddressRequest.builder()
                .recipientName("Nguyen Van Hoan")
                .phone("0988776655")
                .province("Ha Noi")
                .district("Thanh Xuan")
                .ward("Nhan Chinh")
                .streetAddress("123 Le Van Luong")
                .label("Home")
                .isDefault(false)
                .build();

        MvcResult resAddAddr1 = addressMockMvc.perform(post("/api/v1/profile/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addrReq1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Delivery address added successfully."))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.addressText").value("123 Le Van Luong, Nhan Chinh, Thanh Xuan, Ha Noi"))
                .andReturn();
        System.out.println("HTTP Response 201 Created (MSG14 - Delivery address added successfully):");
        System.out.println(resAddAddr1.getResponse().getContentAsString());

        UUID addr1Id = UUID.fromString(objectMapper.readTree(resAddAddr1.getResponse().getContentAsString()).get("data").get("addressId").asText());

        // -------------------------------------------------------------
        // STEP 5: POST /api/v1/profile/addresses -> Add 2nd Address with isDefault=true
        // -------------------------------------------------------------
        System.out.println("\n[5] TEST API: POST /api/v1/profile/addresses (Add 2nd Address as Default)");
        CreateDeliveryAddressRequest addrReq2 = CreateDeliveryAddressRequest.builder()
                .recipientName("Nguyen Van Hoan (VP)")
                .phone("0988776655")
                .province("Ha Noi")
                .district("Cau Giay")
                .ward("Dich Vong Hau")
                .streetAddress("8 Pham Van Bach")
                .label("Office")
                .isDefault(true)
                .build();

        MvcResult resAddAddr2 = addressMockMvc.perform(post("/api/v1/profile/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addrReq2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andReturn();
        System.out.println("HTTP Response 201 Created:");
        System.out.println(resAddAddr2.getResponse().getContentAsString());

        UUID addr2Id = UUID.fromString(objectMapper.readTree(resAddAddr2.getResponse().getContentAsString()).get("data").get("addressId").asText());

        // Verify addr1 was toggled off default
        assertThat(inMemoryAddressDb.get(addr1Id).isDefault()).isFalse();
        assertThat(inMemoryAddressDb.get(addr2Id).isDefault()).isTrue();

        // -------------------------------------------------------------
        // STEP 6: GET /api/v1/profile/addresses -> List Addresses (Sorted default first)
        // -------------------------------------------------------------
        System.out.println("\n[6] TEST API: GET /api/v1/profile/addresses (List addresses sorted default first)");
        MvcResult resListAddr = addressMockMvc.perform(get("/api/v1/profile/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].addressId").value(addr2Id.toString()))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].addressId").value(addr1Id.toString()))
                .andExpect(jsonPath("$.data[1].isDefault").value(false))
                .andReturn();
        System.out.println("HTTP Response 200 OK (List sorted default first):");
        System.out.println(resListAddr.getResponse().getContentAsString());

        // -------------------------------------------------------------
        // STEP 7: PUT /api/v1/profile/addresses/{id} -> Update Address
        // -------------------------------------------------------------
        System.out.println("\n[7] TEST API: PUT /api/v1/profile/addresses/" + addr1Id + " (Update address)");
        UpdateDeliveryAddressRequest updateAddrReq = UpdateDeliveryAddressRequest.builder()
                .recipientName("Nguyen Van Hoan (Nha rieng)")
                .phone("0988776655")
                .province("Ha Noi")
                .district("Thanh Xuan")
                .ward("Nhan Chinh")
                .streetAddress("456 Khuat Duy Tien")
                .label("Villa")
                .isDefault(false)
                .build();

        MvcResult resUpdateAddr = addressMockMvc.perform(put("/api/v1/profile/addresses/" + addr1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateAddrReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Address updated successfully."))
                .andExpect(jsonPath("$.data.streetAddress").value("456 Khuat Duy Tien"))
                .andExpect(jsonPath("$.data.label").value("Villa"))
                .andReturn();
        System.out.println("HTTP Response 200 OK (MSG15 - Address updated successfully):");
        System.out.println(resUpdateAddr.getResponse().getContentAsString());

        // -------------------------------------------------------------
        // STEP 8: PATCH /api/v1/profile/addresses/{id}/default -> Toggle Default
        // -------------------------------------------------------------
        System.out.println("\n[8] TEST API: PATCH /api/v1/profile/addresses/" + addr1Id + "/default (Fast toggle default)");
        MvcResult resToggleDefault = addressMockMvc.perform(patch("/api/v1/profile/addresses/" + addr1Id + "/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Address updated successfully."))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andReturn();
        System.out.println("HTTP Response 200 OK (MSG15 - Address updated successfully):");
        System.out.println(resToggleDefault.getResponse().getContentAsString());

        // -------------------------------------------------------------
        // STEP 9: DELETE /api/v1/profile/addresses/{id} -> Soft-delete default address
        // -------------------------------------------------------------
        System.out.println("\n[9] TEST API: DELETE /api/v1/profile/addresses/" + addr1Id + " (Delete default address -> promotes next active)");
        MvcResult resDeleteAddr = addressMockMvc.perform(delete("/api/v1/profile/addresses/" + addr1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Address deleted successfully."))
                .andReturn();
        System.out.println("HTTP Response 200 OK (MSG16 - Address deleted successfully):");
        System.out.println(resDeleteAddr.getResponse().getContentAsString());

        // Verify soft-deleted
        assertThat(inMemoryAddressDb.get(addr1Id).isDeleted()).isTrue();
        // Verify addr2 was promoted to default
        assertThat(inMemoryAddressDb.get(addr2Id).isDefault()).isTrue();

        System.out.println("\n==========================================================================");
        System.out.println(">>> ALL E2E API CALLS MATCHED SPECIFICATIONS PERFECTLY! (100% SUCCESS)");
        System.out.println("==========================================================================");
    }
}
