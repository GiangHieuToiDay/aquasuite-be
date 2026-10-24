package com.aquasuite.ims.module.crm.internal.controller;

import com.aquasuite.ims.module.crm.internal.payload.req.CreateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.req.UpdateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.DeliveryAddressResponse;
import com.aquasuite.ims.module.crm.internal.service.DeliveryAddressService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
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
    private DeliveryAddressService deliveryAddressService;

    @Mock
    private IamApi iamApi;

    @InjectMocks
    private DeliveryAddressController deliveryAddressController;

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
                .isDefault(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/profile/addresses: returns 200 OK with list of addresses")
    void getAddresses_Returns200OK() throws Exception {
        when(deliveryAddressService.getAddressesByUserId(userId)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/profile/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].addressId").value(addressId.toString()))
                .andExpect(jsonPath("$.data[0].recipientName").value("Tran Van B"));
    }

    @Test
    @DisplayName("POST /api/v1/profile/addresses: adds address and returns MSG14 message")
    void createAddress_Success_ReturnsMSG14() throws Exception {
        CreateDeliveryAddressRequest request = CreateDeliveryAddressRequest.builder()
                .recipientName("Tran Van B")
                .phone("0987654321")
                .province("Ha Noi")
                .district("Thanh Xuan")
                .ward("Thuong Dinh")
                .streetAddress("72A Nguyen Trai")
                .label("Home")
                .isDefault(true)
                .build();

        when(deliveryAddressService.createAddress(eq(userId), any(CreateDeliveryAddressRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/profile/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Delivery address added successfully."))
                .andExpect(jsonPath("$.data.addressId").value(addressId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/profile/addresses/{id}: returns address detail")
    void getAddressDetail_Returns200OK() throws Exception {
        when(deliveryAddressService.getAddressDetail(userId, addressId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/profile/addresses/" + addressId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recipientName").value("Tran Van B"));
    }

    @Test
    @DisplayName("PUT /api/v1/profile/addresses/{id}: updates address and returns MSG15 message")
    void updateAddress_Success_ReturnsMSG15() throws Exception {
        UpdateDeliveryAddressRequest request = UpdateDeliveryAddressRequest.builder()
                .recipientName("Tran Van B Updated")
                .phone("0987654321")
                .province("Ha Noi")
                .district("Thanh Xuan")
                .ward("Thuong Dinh")
                .streetAddress("72A Nguyen Trai")
                .label("Home")
                .isDefault(true)
                .build();

        when(deliveryAddressService.updateAddress(eq(userId), eq(addressId), any(UpdateDeliveryAddressRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(put("/api/v1/profile/addresses/" + addressId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Address updated successfully."));
    }

    @Test
    @DisplayName("DELETE /api/v1/profile/addresses/{id}: deletes address and returns MSG16 message")
    void deleteAddress_Success_ReturnsMSG16() throws Exception {
        doNothing().when(deliveryAddressService).deleteAddress(userId, addressId);

        mockMvc.perform(delete("/api/v1/profile/addresses/" + addressId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Address deleted successfully."));
    }

    @Test
    @DisplayName("PATCH /api/v1/profile/addresses/{id}/default: sets default and returns MSG15 message")
    void setDefaultAddress_Success_ReturnsMSG15() throws Exception {
        when(deliveryAddressService.setDefaultAddress(userId, addressId)).thenReturn(sampleResponse);

        mockMvc.perform(patch("/api/v1/profile/addresses/" + addressId + "/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Address updated successfully."));
    }
}
