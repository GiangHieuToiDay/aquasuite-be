package com.aquasuite.ims.module.crm.internal.controller;

import com.aquasuite.ims.module.crm.internal.payload.req.CreateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.req.UpdateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.req.UpdateProfileRequest;
import com.aquasuite.ims.module.crm.internal.repository.CustomerAddressRepository;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.module.iam.internal.payload.request.LoginRequest;
import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/aquasuite",
        "spring.datasource.username=postgres",
        "spring.datasource.password=123",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class ProfileAndAddressPostmanSimulationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerAddressRepository customerAddressRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private Role customerRole;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        // 1. Ensure Customer Role exists
        customerRole = roleRepository.findByRoleNameIgnoreCase("CUSTOMER").orElseGet(() -> {
            Role r = new Role(UUID.randomUUID(), "CUSTOMER", "Customer", false, null, Set.of());
            return roleRepository.save(r);
        });

        // 2. Create clean Test User in PostgreSQL
        String testEmail = "testuser." + System.currentTimeMillis() + "@aquasuite.vn";
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser_" + System.currentTimeMillis() % 10000);
        testUser.setFullName("Nguyen Van A");
        testUser.setEmail(testEmail);
        testUser.setPhone("0912345678");
        testUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setRoles(Set.of(customerRole));
        testUser = userRepository.save(testUser);

        // 3. Perform live POST /api/v1/auth/login to obtain real JWT Access Token (just like Postman)
        LoginRequest loginReq = LoginRequest.builder()
                .email(testEmail)
                .password("Password123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginData = objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data");
        accessToken = loginData.path("access_token").asText();
        assertThat(accessToken).isNotEmpty();
    }

    @AfterEach
    void tearDown() {
        if (testUser != null && testUser.getId() != null) {
            customerAddressRepository.deleteAll();
            customerRepository.deleteAll();
            userRepository.deleteById(testUser.getId());
        }
    }

    @Test
    @DisplayName("Simulate Postman Flow: Test all UC-03 Profile & Address APIs end-to-end")
    void testAllApisLikePostman() throws Exception {
        System.out.println("\n===============================================================================");
        System.out.println(">>> POSTMAN API SIMULATION SUITE FOR UC-03: PROFILE & DELIVERY ADDRESSES");
        System.out.println(">>> AUTH TOKEN OBTAINED: Bearer " + accessToken.substring(0, 30) + "...");
        System.out.println("===============================================================================");

        // -----------------------------------------------------------------------------------------
        // 1. GET /api/v1/profile
        // -----------------------------------------------------------------------------------------
        System.out.println("\n[POSTMAN REQUEST 1] GET http://localhost:8080/api/v1/profile");
        System.out.println("Headers: Authorization: Bearer <token>");
        MvcResult resGetProfile = mockMvc.perform(get("/api/v1/profile")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van A"))
                .andReturn();
        System.out.println("Response Status: " + resGetProfile.getResponse().getStatus());
        System.out.println("Response Body:\n" + prettyJson(resGetProfile.getResponse().getContentAsString()));

        // -----------------------------------------------------------------------------------------
        // 2. POST /api/v1/profile/avatar (Upload file avatar)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n[POSTMAN REQUEST 2] POST http://localhost:8080/api/v1/profile/avatar");
        System.out.println("Headers: Authorization: Bearer <token>, Content-Type: multipart/form-data");
        MockMultipartFile avatarFile = new MockMultipartFile(
                "file", "my_avatar.png", "image/png", new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61}
        );
        MvcResult resAvatar = mockMvc.perform(multipart("/api/v1/profile/avatar")
                        .file(avatarFile)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Avatar uploaded successfully."))
                .andExpect(jsonPath("$.data.avatarUrl").isNotEmpty())
                .andReturn();
        System.out.println("Response Status: " + resAvatar.getResponse().getStatus());
        System.out.println("Response Body:\n" + prettyJson(resAvatar.getResponse().getContentAsString()));

        // -----------------------------------------------------------------------------------------
        // 2.1 POST /api/v1/profile/avatar with invalid file format (.txt)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n[POSTMAN REQUEST 2.1 - VALIDATION TEST] POST http://localhost:8080/api/v1/profile/avatar (Invalid file .txt)");
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "Hello world".getBytes()
        );
        MvcResult resBadAvatar = mockMvc.perform(multipart("/api/v1/profile/avatar")
                        .file(textFile)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MSG17"))
                .andReturn();
        System.out.println("Response Status: " + resBadAvatar.getResponse().getStatus());
        System.out.println("Response Body:\n" + prettyJson(resBadAvatar.getResponse().getContentAsString()));

        // -----------------------------------------------------------------------------------------
        // 3. PUT /api/v1/profile (Update Full Name & Phone)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n[POSTMAN REQUEST 3] PUT http://localhost:8080/api/v1/profile");
        System.out.println("Headers: Authorization: Bearer <token>, Content-Type: application/json");
        UpdateProfileRequest updateProfileReq = UpdateProfileRequest.builder()
                .fullName("Nguyen Van A Updated")
                .phone("0912345999")
                .avatarUrl("https://res.cloudinary.com/aquasuite/image/upload/v1/avatars/new_avatar.png")
                .build();
        System.out.println("Request Body:\n" + prettyJson(objectMapper.writeValueAsString(updateProfileReq)));

        MvcResult resUpdateProfile = mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateProfileReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Profile updated successfully."))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van A Updated"))
                .andExpect(jsonPath("$.data.phone").value("0912345999"))
                .andReturn();
        System.out.println("Response Status: " + resUpdateProfile.getResponse().getStatus());
        System.out.println("Response Body:\n" + prettyJson(resUpdateProfile.getResponse().getContentAsString()));

        // -----------------------------------------------------------------------------------------
        // 4. POST /api/v1/profile/addresses (Thêm địa chỉ giao hàng 1 -> tự động thành default)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n[POSTMAN REQUEST 4] POST http://localhost:8080/api/v1/profile/addresses (Thêm địa chỉ thứ 1)");
        CreateDeliveryAddressRequest addrReq1 = CreateDeliveryAddressRequest.builder()
                .recipientName("Nguyen Van A")
                .phone("0987654321")
                .province("TP Ha Noi")
                .district("Quan Thanh Xuan")
                .ward("Phuong Nhan Chinh")
                .streetAddress("123 Le Van Luong")
                .label("Home")
                .isDefault(false)
                .build();
        System.out.println("Request Body:\n" + prettyJson(objectMapper.writeValueAsString(addrReq1)));

        MvcResult resAddAddr1 = mockMvc.perform(post("/api/v1/profile/addresses")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addrReq1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Delivery address added successfully."))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andReturn();
        System.out.println("Response Status: " + resAddAddr1.getResponse().getStatus());
        System.out.println("Response Body:\n" + prettyJson(resAddAddr1.getResponse().getContentAsString()));

        UUID addr1Id = UUID.fromString(objectMapper.readTree(resAddAddr1.getResponse().getContentAsString()).path("data").path("addressId").asText());

        // -----------------------------------------------------------------------------------------
        // 5. POST /api/v1/profile/addresses (Thêm địa chỉ giao hàng 2 với isDefault = true)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n[POSTMAN REQUEST 5] POST http://localhost:8080/api/v1/profile/addresses (Thêm địa chỉ thứ 2 làm mặc định)");
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
        System.out.println("Request Body:\n" + prettyJson(objectMapper.writeValueAsString(addrReq2)));

        MvcResult resAddAddr2 = mockMvc.perform(post("/api/v1/profile/addresses")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addrReq2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andReturn();
        System.out.println("Response Status: " + resAddAddr2.getResponse().getStatus());
        System.out.println("Response Body:\n" + prettyJson(resAddAddr2.getResponse().getContentAsString()));

        UUID addr2Id = UUID.fromString(objectMapper.readTree(resAddAddr2.getResponse().getContentAsString()).path("data").path("addressId").asText());

        // -----------------------------------------------------------------------------------------
        // 6. GET /api/v1/profile/addresses (Lấy danh sách địa chỉ -> Sắp xếp mặc định lên đầu)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n[POSTMAN REQUEST 6] GET http://localhost:8080/api/v1/profile/addresses (Lấy danh sách địa chỉ)");
        MvcResult resListAddr = mockMvc.perform(get("/api/v1/profile/addresses")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].addressId").value(addr2Id.toString()))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].addressId").value(addr1Id.toString()))
                .andExpect(jsonPath("$.data[1].isDefault").value(false))
                .andReturn();
        System.out.println("Response Status: " + resListAddr.getResponse().getStatus());
        System.out.println("Response Body:\n" + prettyJson(resListAddr.getResponse().getContentAsString()));

        // -----------------------------------------------------------------------------------------
        // 7. PUT /api/v1/profile/addresses/{id} (Cập nhật địa chỉ 1)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n[POSTMAN REQUEST 7] PUT http://localhost:8080/api/v1/profile/addresses/" + addr1Id);
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
        System.out.println("Request Body:\n" + prettyJson(objectMapper.writeValueAsString(updateAddrReq)));

        MvcResult resUpdateAddr = mockMvc.perform(put("/api/v1/profile/addresses/" + addr1Id)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateAddrReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Address updated successfully."))
                .andExpect(jsonPath("$.data.streetAddress").value("456 Khuat Duy Tien"))
                .andExpect(jsonPath("$.data.label").value("Villa"))
                .andReturn();
        System.out.println("Response Status: " + resUpdateAddr.getResponse().getStatus());
        System.out.println("Response Body:\n" + prettyJson(resUpdateAddr.getResponse().getContentAsString()));

        // -----------------------------------------------------------------------------------------
        // 8. PATCH /api/v1/profile/addresses/{id}/default (Đổi mặc định 1 chạm)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n[POSTMAN REQUEST 8] PATCH http://localhost:8080/api/v1/profile/addresses/" + addr1Id + "/default");
        MvcResult resPatchDefault = mockMvc.perform(patch("/api/v1/profile/addresses/" + addr1Id + "/default")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Address updated successfully."))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andReturn();
        System.out.println("Response Status: " + resPatchDefault.getResponse().getStatus());
        System.out.println("Response Body:\n" + prettyJson(resPatchDefault.getResponse().getContentAsString()));

        // -----------------------------------------------------------------------------------------
        // 9. DELETE /api/v1/profile/addresses/{id} (Xóa mềm địa chỉ)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n[POSTMAN REQUEST 9] DELETE http://localhost:8080/api/v1/profile/addresses/" + addr1Id);
        MvcResult resDelete = mockMvc.perform(delete("/api/v1/profile/addresses/" + addr1Id)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Address deleted successfully."))
                .andReturn();
        System.out.println("Response Status: " + resDelete.getResponse().getStatus());
        System.out.println("Response Body:\n" + prettyJson(resDelete.getResponse().getContentAsString()));

        System.out.println("\n===============================================================================");
        System.out.println(">>> HOÀN TẤT KIỂM THỬ: TẤT CẢ REQUEST/RESPONSE ĐỀU CHUẨN XÁC 100%!");
        System.out.println("===============================================================================\n");
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
