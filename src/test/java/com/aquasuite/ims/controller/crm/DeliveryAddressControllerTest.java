package com.aquasuite.ims.controller.crm;

import com.aquasuite.ims.dto.crm.request.CreateDeliveryAddressRequest;
import com.aquasuite.ims.dto.crm.request.UpdateDeliveryAddressRequest;
import com.aquasuite.ims.dto.crm.response.DeliveryAddressResponse;
import com.aquasuite.ims.service.crm.UserProfileService;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeliveryAddressControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private IamQueryService iamApi;

    @InjectMocks
    private UserProfileController deliveryAddressController;

    private UUID userId;
    private UUID addressId;
    private DeliveryAddressResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(deliveryAddressController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        userId = UUID.randomUUID();
        addressId = UUID.randomUUID();
        org.mockito.Mockito.lenient().when(iamApi.getCurrentUserId()).thenReturn(userId);

        sampleResponse = DeliveryAddressResponse.builder()
                .addressId(addressId)
                .recipientName("Tran Van B")
                .phone("0987654321")
                .province("Ha Noi")
                .district("Thanh Xuan")
                .ward("Thuong Dinh")
                .streetAddress("72A Nguyen Trai")
                .addressText("72A Nguyen Trai, Thuong Dinh, Thanh Xuan, Ha Noi")
                .label("Home")
                .isDefault(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/profile/addresses: Tra ve danh sach dia chi cua user")
    void getAddresses_Success() throws Exception {
        when(userProfileService.getAddressesByUserId(userId)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/profile/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].addressId").value(addressId.toString()))
                .andExpect(jsonPath("$.data[0].recipientName").value("Tran Van B"));
    }

    @Test
    @DisplayName("POST /api/v1/profile/addresses: Them dia chi moi thanh cong tra ve 201 Created")
    void createAddress_Success() throws Exception {
        CreateDeliveryAddressRequest request = CreateDeliveryAddressRequest.builder()
                .recipientName("Tran Van B")
                .phone("0987654321")
                .province("Ha Noi")
                .district("Thanh Xuan")
                .ward("Thuong Dinh")
                .streetAddress("72A Nguyen Trai")
                .isDefault(false)
                .build();

        when(userProfileService.createAddress(eq(userId), any(CreateDeliveryAddressRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/profile/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.addressId").value(addressId.toString()))
                .andExpect(jsonPath("$.data.recipientName").value("Tran Van B"));
    }

    @Test
    @DisplayName("POST /api/v1/profile/addresses: Validate loi khi thieu cac truong bat buoc tra ve 400")
    void createAddress_ValidationError_MissingRequiredFields() throws Exception {
        CreateDeliveryAddressRequest invalidRequest = CreateDeliveryAddressRequest.builder()
                .recipientName("") // Empty
                .phone("123") // Invalid format
                .build();

        mockMvc.perform(post("/api/v1/profile/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SYS_003"));
    }

    @Test
    @DisplayName("GET /api/v1/profile/addresses/{id}: Tra ve chi tiet dia chi thanh cong")
    void getAddressDetail_Success() throws Exception {
        when(userProfileService.getAddressDetail(userId, addressId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/profile/addresses/{id}", addressId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.addressId").value(addressId.toString()));
    }

    @Test
    @DisplayName("PUT /api/v1/profile/addresses/{id}: Cap nhat dia chi thanh cong")
    void updateAddress_Success() throws Exception {
        UpdateDeliveryAddressRequest request = UpdateDeliveryAddressRequest.builder()
                .recipientName("Tran Van C")
                .phone("0987654321")
                .province("Ha Noi")
                .district("Cau Giay")
                .ward("Dich Vong")
                .streetAddress("100 Xuan Thuy")
                .isDefault(true)
                .build();

        DeliveryAddressResponse updatedResponse = DeliveryAddressResponse.builder()
                .addressId(addressId)
                .recipientName("Tran Van C")
                .phone("0987654321")
                .province("Ha Noi")
                .district("Cau Giay")
                .ward("Dich Vong")
                .streetAddress("100 Xuan Thuy")
                .addressText("100 Xuan Thuy, Dich Vong, Cau Giay, Ha Noi")
                .isDefault(true)
                .build();

        when(userProfileService.updateAddress(eq(userId), eq(addressId), any(UpdateDeliveryAddressRequest.class)))
                .thenReturn(updatedResponse);

        mockMvc.perform(put("/api/v1/profile/addresses/{id}", addressId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recipientName").value("Tran Van C"))
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/profile/addresses/{id}: Xoa mem dia chi thanh cong")
    void deleteAddress_Success() throws Exception {
        doNothing().when(userProfileService).deleteAddress(userId, addressId);

        mockMvc.perform(delete("/api/v1/profile/addresses/{id}", addressId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/profile/addresses/{id}/default: Dat dia chi mac dinh thanh cong")
    void setDefaultAddress_Success() throws Exception {
        DeliveryAddressResponse defaultResponse = DeliveryAddressResponse.builder()
                .addressId(addressId)
                .recipientName("Tran Van B")
                .isDefault(true)
                .build();

        when(userProfileService.setDefaultAddress(userId, addressId)).thenReturn(defaultResponse);

        mockMvc.perform(patch("/api/v1/profile/addresses/{id}/default", addressId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }
}
