package com.aquasuite.ims.module.crm.internal.controller;

import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.entity.DeliveryAddressItem;
import com.aquasuite.ims.module.crm.internal.enums.CustomerType;
import com.aquasuite.ims.module.crm.internal.payload.req.CreateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.req.UpdateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.req.UpdateProfileRequest;
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

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileAndAddressPostmanSimulationTest {

    private MockMvc profileMockMvc;
    private MockMvc avatarMockMvc;
    private MockMvc addressMockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private IamApi iamApi;

    private UserProfileServiceImpl userProfileService;
    private DeliveryAddressServiceImpl deliveryAddressService;
    private CloudinaryStorageServiceImpl cloudinaryStorageService;

    private UUID userId;
    private UUID customerId;
    private Customer customer;
    private UserSummaryDto userDto;
    private final String simulatedBearerToken = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlciIsImlhdCI6MTY3MDAwMDAwMH0";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        customerId = UUID.randomUUID();

        customer = Customer.builder()
                .customerId(customerId)
                .userId(userId)
                .customerCode("KH-202610-001")
                .customerName("Nguyen Van A")
                .phone("0912345678")
                .email("testuser@aquasuite.vn")
                .customerType(CustomerType.RETAIL)
                .status("ACTIVE")
                .deliveryAddresses(new ArrayList<>())
                .build();

        userDto = UserSummaryDto.builder()
                .id(userId)
                .username("testuser")
                .fullName("Nguyen Van A")
                .email("testuser@aquasuite.vn")
                .phone("0912345678")
                .avatarUrl("https://res.cloudinary.com/aquasuite/avatar_init.png")
                .status("ACTIVE")
                .roles(List.of("CUSTOMER"))
                .build();

        lenient().when(iamApi.getCurrentUserId()).thenReturn(userId);
        lenient().when(iamApi.getUserById(userId)).thenAnswer(inv -> userDto);
        lenient().when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(customer));
        lenient().when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        cloudinaryStorageService = new CloudinaryStorageServiceImpl();
        deliveryAddressService = new DeliveryAddressServiceImpl(customerRepository, iamApi);
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
    @DisplayName("Simulate Postman Flow: Test all UC-03 Profile & Address APIs end-to-end")
    void testAllApisLikePostman() throws Exception {
        System.out.println("\n===============================================================================");
        System.out.println(">>> POSTMAN API SIMULATION SUITE: PROFILE & DELIVERY ADDRESSES (NO ADDRESS TABLE)");
        System.out.println(">>> AUTH TOKEN: " + simulatedBearerToken.substring(0, 35) + "...");
        System.out.println("===============================================================================");

        // -----------------------------------------------------------------------------------------
        // 1. GET /api/v1/profile
        // -----------------------------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("[POSTMAN 1] GET /api/v1/profile");
        System.out.println("Headers: Authorization: " + simulatedBearerToken);
        MvcResult resGetProfile = profileMockMvc.perform(get("/api/v1/profile")
                        .header("Authorization", simulatedBearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van A"))
                .andReturn();
        printHttpResult("GET /api/v1/profile", null, resGetProfile);

        // -----------------------------------------------------------------------------------------
        // 2. POST /api/v1/profile/avatar (Upload file avatar)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("[POSTMAN 2] POST /api/v1/profile/avatar (Multipart File Upload)");
        System.out.println("Headers: Authorization: " + simulatedBearerToken + ", Content-Type: multipart/form-data");
        MockMultipartFile avatarFile = new MockMultipartFile(
                "file", "my_avatar.png", "image/png", new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61}
        );
        MvcResult resAvatar = avatarMockMvc.perform(multipart("/api/v1/profile/avatar")
                        .file(avatarFile)
                        .header("Authorization", simulatedBearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Avatar uploaded successfully."))
                .andExpect(jsonPath("$.data.avatarUrl").isNotEmpty())
                .andReturn();
        printHttpResult("POST /api/v1/profile/avatar", "[Form-data binary file: my_avatar.png (image/png)]", resAvatar);

        // -----------------------------------------------------------------------------------------
        // 2.1 POST /api/v1/profile/avatar with invalid file format (.txt)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("[POSTMAN 2.1 - VALIDATION] POST /api/v1/profile/avatar (Invalid file .txt)");
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "Hello world".getBytes()
        );
        MvcResult resBadAvatar = avatarMockMvc.perform(multipart("/api/v1/profile/avatar")
                        .file(textFile)
                        .header("Authorization", simulatedBearerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MSG17"))
                .andReturn();
        printHttpResult("POST /api/v1/profile/avatar (Invalid format)", "[Form-data file: notes.txt (text/plain)]", resBadAvatar);

        // -----------------------------------------------------------------------------------------
        // 3. PUT /api/v1/profile (Update Full Name & Phone)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("[POSTMAN 3] PUT /api/v1/profile (Update Profile)");
        UpdateProfileRequest updateProfileReq = UpdateProfileRequest.builder()
                .fullName("Nguyen Van A Updated")
                .phone("0912345999")
                .avatarUrl("https://res.cloudinary.com/aquasuite/image/upload/v1/avatars/new_avatar.png")
                .build();
        userDto = UserSummaryDto.builder()
                .id(userId)
                .username("testuser")
                .fullName("Nguyen Van A Updated")
                .email("testuser@aquasuite.vn")
                .phone("0912345999")
                .avatarUrl("https://res.cloudinary.com/aquasuite/image/upload/v1/avatars/new_avatar.png")
                .status("ACTIVE")
                .roles(List.of("CUSTOMER"))
                .build();

        MvcResult resUpdateProfile = profileMockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", simulatedBearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateProfileReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Profile updated successfully."))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van A Updated"))
                .andExpect(jsonPath("$.data.phone").value("0912345999"))
                .andReturn();
        printHttpResult("PUT /api/v1/profile", objectMapper.writeValueAsString(updateProfileReq), resUpdateProfile);

        // -----------------------------------------------------------------------------------------
        // 4. POST /api/v1/profile/addresses (Thêm địa chỉ giao hàng 1 -> tự động thành default)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("[POSTMAN 4] POST /api/v1/profile/addresses (Thêm địa chỉ thứ 1)");
        CreateDeliveryAddressRequest addrReq1 = CreateDeliveryAddressRequest.builder()
                .recipientName("Nguyen Van A")
                .phone("0987654321")
                .province("TP Ha Noi")
                .district("Quan Thanh Xuan")
                .ward("Phuong Nhan Chinh")
                .streetAddress("123 Le Van Luong")
                .label("Home")
                .isDefault(false) // Requested as false, but since it's the 1st address, must auto-become default
                .build();

        MvcResult resAddAddr1 = addressMockMvc.perform(post("/api/v1/profile/addresses")
                        .header("Authorization", simulatedBearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addrReq1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Delivery address added successfully."))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.addressText").value("123 Le Van Luong, Phuong Nhan Chinh, Quan Thanh Xuan, TP Ha Noi"))
                .andReturn();
        printHttpResult("POST /api/v1/profile/addresses (1st Address)", objectMapper.writeValueAsString(addrReq1), resAddAddr1);

        UUID addr1Id = UUID.fromString(objectMapper.readTree(resAddAddr1.getResponse().getContentAsString()).path("data").path("addressId").asText());

        // -----------------------------------------------------------------------------------------
        // 5. POST /api/v1/profile/addresses (Thêm địa chỉ giao hàng 2 với isDefault = true)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("[POSTMAN 5] POST /api/v1/profile/addresses (Thêm địa chỉ thứ 2 làm mặc định)");
        CreateDeliveryAddressRequest addrReq2 = CreateDeliveryAddressRequest.builder()
                .recipientName("Nguyen Van A (Cong ty)")
                .phone("0987654321")
                .province("TP Ha Noi")
                .district("Quan Cau Giay")
                .ward("Phuong Dich Vong Hau")
                .streetAddress("8 Pham Van Bach")
                .label("Office")
                .isDefault(true)
                .build();

        MvcResult resAddAddr2 = addressMockMvc.perform(post("/api/v1/profile/addresses")
                        .header("Authorization", simulatedBearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addrReq2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andReturn();
        printHttpResult("POST /api/v1/profile/addresses (2nd Address - isDefault=true)", objectMapper.writeValueAsString(addrReq2), resAddAddr2);

        UUID addr2Id = UUID.fromString(objectMapper.readTree(resAddAddr2.getResponse().getContentAsString()).path("data").path("addressId").asText());

        // -----------------------------------------------------------------------------------------
        // 6. GET /api/v1/profile/addresses (Lấy danh sách địa chỉ -> Sắp xếp mặc định lên đầu)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("[POSTMAN 6] GET /api/v1/profile/addresses (Lấy danh sách địa chỉ sắp xếp mặc định lên đầu)");
        MvcResult resListAddr = addressMockMvc.perform(get("/api/v1/profile/addresses")
                        .header("Authorization", simulatedBearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].addressId").value(addr2Id.toString()))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].addressId").value(addr1Id.toString()))
                .andExpect(jsonPath("$.data[1].isDefault").value(false))
                .andReturn();
        printHttpResult("GET /api/v1/profile/addresses", null, resListAddr);

        // -----------------------------------------------------------------------------------------
        // 7. GET /api/v1/profile/addresses/{id} (Chi tiết địa chỉ 1)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("[POSTMAN 7] GET /api/v1/profile/addresses/" + addr1Id + " (Chi tiết 1 địa chỉ)");
        MvcResult resGetAddr1 = addressMockMvc.perform(get("/api/v1/profile/addresses/" + addr1Id)
                        .header("Authorization", simulatedBearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.addressId").value(addr1Id.toString()))
                .andReturn();
        printHttpResult("GET /api/v1/profile/addresses/" + addr1Id, null, resGetAddr1);

        // -----------------------------------------------------------------------------------------
        // 8. PUT /api/v1/profile/addresses/{id} (Cập nhật địa chỉ 1)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("[POSTMAN 8] PUT /api/v1/profile/addresses/" + addr1Id + " (Cập nhật địa chỉ 1)");
        UpdateDeliveryAddressRequest updateAddrReq = UpdateDeliveryAddressRequest.builder()
                .recipientName("Nguyen Van A (Nha moi)")
                .phone("0987654321")
                .province("TP Ha Noi")
                .district("Quan Thanh Xuan")
                .ward("Phuong Nhan Chinh")
                .streetAddress("456 Khuat Duy Tien")
                .label("Villa")
                .isDefault(false)
                .build();

        MvcResult resUpdateAddr = addressMockMvc.perform(put("/api/v1/profile/addresses/" + addr1Id)
                        .header("Authorization", simulatedBearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateAddrReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Address updated successfully."))
                .andExpect(jsonPath("$.data.streetAddress").value("456 Khuat Duy Tien"))
                .andExpect(jsonPath("$.data.label").value("Villa"))
                .andReturn();
        printHttpResult("PUT /api/v1/profile/addresses/" + addr1Id, objectMapper.writeValueAsString(updateAddrReq), resUpdateAddr);

        // -----------------------------------------------------------------------------------------
        // 9. PATCH /api/v1/profile/addresses/{id}/default (Đổi mặc định 1 chạm)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("[POSTMAN 9] PATCH /api/v1/profile/addresses/" + addr1Id + "/default (Đặt làm mặc định)");
        MvcResult resPatchDefault = addressMockMvc.perform(patch("/api/v1/profile/addresses/" + addr1Id + "/default")
                        .header("Authorization", simulatedBearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Address updated successfully."))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andReturn();
        printHttpResult("PATCH /api/v1/profile/addresses/" + addr1Id + "/default", null, resPatchDefault);

        // -----------------------------------------------------------------------------------------
        // 10. DELETE /api/v1/profile/addresses/{id} (Xóa mềm địa chỉ mặc định -> tự động đôn địa chỉ còn lại lên)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("[POSTMAN 10] DELETE /api/v1/profile/addresses/" + addr1Id + " (Xóa mềm địa chỉ mặc định)");
        MvcResult resDelete = addressMockMvc.perform(delete("/api/v1/profile/addresses/" + addr1Id)
                        .header("Authorization", simulatedBearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Address deleted successfully."))
                .andReturn();
        printHttpResult("DELETE /api/v1/profile/addresses/" + addr1Id, null, resDelete);

        // -----------------------------------------------------------------------------------------
        // 11. GET /api/v1/profile (Kiểm tra lại toàn bộ Profile kèm mảng deliveryAddresses)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("[POSTMAN 11] GET /api/v1/profile (Kiểm tra profile kèm mảng deliveryAddresses còn lại)");
        MvcResult resFinalProfile = profileMockMvc.perform(get("/api/v1/profile")
                        .header("Authorization", simulatedBearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deliveryAddresses").isArray())
                .andExpect(jsonPath("$.data.deliveryAddresses[0].addressId").value(addr2Id.toString()))
                .andExpect(jsonPath("$.data.deliveryAddresses[0].isDefault").value(true))
                .andReturn();
        printHttpResult("GET /api/v1/profile", null, resFinalProfile);

        // Verify JSONB structure internally
        assertThat(customer.getDeliveryAddresses()).hasSize(2);
        DeliveryAddressItem item1 = customer.getDeliveryAddresses().stream().filter(a -> a.getAddressId().equals(addr1Id)).findFirst().orElseThrow();
        DeliveryAddressItem item2 = customer.getDeliveryAddresses().stream().filter(a -> a.getAddressId().equals(addr2Id)).findFirst().orElseThrow();
        assertThat(item1.isDeleted()).isTrue();
        assertThat(item2.isDeleted()).isFalse();
        assertThat(item2.isDefault()).isTrue();

        System.out.println("\n===============================================================================");
        System.out.println(">>> HOÀN TẤT KIỂM THỬ: TẤT CẢ 11 API CALLS ĐỀU CHUẨN XÁC 100% VỚI JSONB LIST!");
        System.out.println("===============================================================================\n");
    }

    private void printHttpResult(String endpoint, String requestBody, MvcResult mvcResult) throws Exception {
        System.out.println("Endpoint: " + endpoint);
        if (requestBody != null) {
            System.out.println("Request Body:\n" + prettyJson(requestBody));
        }
        System.out.println("HTTP Status: " + mvcResult.getResponse().getStatus());
        System.out.println("Response Body:\n" + prettyJson(mvcResult.getResponse().getContentAsString()));
    }

    private String prettyJson(String json) {
        try {
            Object obj = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return json;
        }
    }
}
