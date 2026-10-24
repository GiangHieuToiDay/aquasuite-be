package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.enums.ProductType;
import com.aquasuite.ims.module.catalog.internal.exception.CatalogErrorCode;
import com.aquasuite.ims.module.catalog.internal.payload.req.*;
import com.aquasuite.ims.module.catalog.internal.payload.res.*;
import com.aquasuite.ims.module.catalog.internal.service.ProductAdminService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProductAdminControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private ProductAdminService productAdminService;

    @Mock
    private IamApi iamApi;

    @InjectMocks
    private ProductAdminController productAdminController;

    private UUID sampleProductId;
    private UUID sampleCategoryId;
    private UUID sampleBrandId;
    private ProductDetailResponse sampleDetailResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(productAdminController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleProductId = UUID.randomUUID();
        sampleCategoryId = UUID.randomUUID();
        sampleBrandId = UUID.randomUUID();

        sampleDetailResponse = ProductDetailResponse.builder()
                .productId(sampleProductId)
                .productCode("SP-001")
                .productName("Sản phẩm thử nghiệm")
                .description("Mô tả chi tiết")
                .productType(ProductType.NORMAL)
                .status(ProductStatus.ACTIVE)
                .category(new CategoryBriefResponse(sampleCategoryId, "CAT-01", "Danh mục 1"))
                .brand(new BrandBriefResponse(sampleBrandId, "BRAND-01", "Thương hiệu 1"))
                .images(Collections.emptyList())
                .variants(Collections.emptyList())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/catalog/products - Tạo sản phẩm thành công (201 Created)")
    void createProduct_Success() throws Exception {
        doNothing().when(iamApi).validateAdminAccess();

        CreateProductRequest request = CreateProductRequest.builder()
                .productCode("SP-001")
                .productName("Sản phẩm mới")
                .categoryId(sampleCategoryId)
                .build();

        when(productAdminService.createProduct(any(CreateProductRequest.class)))
                .thenReturn(sampleDetailResponse);

        mockMvc.perform(post("/api/v1/catalog/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product created successfully. Please configure SKUs and variants."))
                .andExpect(jsonPath("$.data.productId").value(sampleProductId.toString()))
                .andExpect(jsonPath("$.data.productCode").value("SP-001"));

        verify(iamApi, times(1)).validateAdminAccess();
        verify(productAdminService, times(1)).createProduct(any(CreateProductRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/catalog/products - Từ chối khi không có quyền Admin (403 Forbidden)")
    void createProduct_Forbidden_WhenNotAdmin() throws Exception {
        doThrow(new AppException(CommonErrorCode.ACCESS_DENIED))
                .when(iamApi).validateAdminAccess();

        CreateProductRequest request = CreateProductRequest.builder()
                .productCode("SP-001")
                .productName("Sản phẩm mới")
                .categoryId(sampleCategoryId)
                .build();

        mockMvc.perform(post("/api/v1/catalog/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        verify(productAdminService, never()).createProduct(any());
    }

    @Test
    @DisplayName("POST /api/v1/catalog/products - Báo lỗi validation khi thiếu trường bắt buộc (400 Bad Request)")
    void createProduct_ValidationError() throws Exception {
        // Missing productCode, productName, categoryId, and variants
        CreateProductRequest invalidRequest = CreateProductRequest.builder().build();

        mockMvc.perform(post("/api/v1/catalog/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(productAdminService, never()).createProduct(any());
    }

    @Test
    @DisplayName("GET /api/v1/catalog/products - Lấy danh sách sản phẩm phân trang thành công (200 OK)")
    void listProducts_Success() throws Exception {
        doNothing().when(iamApi).validateAdminAccess();

        ProductSummaryResponse summary = ProductSummaryResponse.builder()
                .productId(sampleProductId)
                .productCode("SP-001")
                .productName("Sản phẩm thử nghiệm")
                .productType(ProductType.NORMAL)
                .status(ProductStatus.ACTIVE)
                .categoryId(sampleCategoryId)
                .categoryName("Danh mục 1")
                .brandId(sampleBrandId)
                .brandName("Thương hiệu 1")
                .primaryImageUrl("https://example.com/thumbnail.jpg")
                .totalVariants(1)
                .minSellingPrice(new BigDecimal("150000"))
                .maxSellingPrice(new BigDecimal("150000"))
                .createdAt(LocalDateTime.now())
                .build();

        when(productAdminService.listProducts(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("keyword", "SP-001")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].productId").value(sampleProductId.toString()))
                .andExpect(jsonPath("$.data.content[0].productCode").value("SP-001"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(iamApi, times(1)).validateAdminAccess();
    }

    @Test
    @DisplayName("GET /api/v1/catalog/products/{id} - Xem chi tiết sản phẩm thành công (200 OK)")
    void getProductDetail_Success() throws Exception {
        doNothing().when(iamApi).validateAdminAccess();

        when(productAdminService.getProductDetail(sampleProductId)).thenReturn(sampleDetailResponse);

        mockMvc.perform(get("/api/v1/catalog/products/{id}", sampleProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(sampleProductId.toString()))
                .andExpect(jsonPath("$.data.productName").value("Sản phẩm thử nghiệm"));

        verify(iamApi, times(1)).validateAdminAccess();
        verify(productAdminService, times(1)).getProductDetail(sampleProductId);
    }

    @Test
    @DisplayName("GET /api/v1/catalog/products/{id} - Sản phẩm không tồn tại (404 Not Found)")
    void getProductDetail_NotFound() throws Exception {
        doNothing().when(iamApi).validateAdminAccess();

        when(productAdminService.getProductDetail(sampleProductId))
                .thenThrow(new AppException(CatalogErrorCode.PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/catalog/products/{id}", sampleProductId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CAT_001"));
    }

    @Test
    @DisplayName("PUT /api/v1/catalog/products/{id} - Cập nhật sản phẩm thành công (200 OK)")
    void updateProduct_Success() throws Exception {
        doNothing().when(iamApi).validateAdminAccess();

        UpdateProductRequest updateReq = UpdateProductRequest.builder()
                .productCode("SP-001-UPDATED")
                .productName("Sản phẩm sau cập nhật")
                .categoryId(sampleCategoryId)
                .build();

        when(productAdminService.updateProduct(eq(sampleProductId), any(UpdateProductRequest.class)))
                .thenReturn(sampleDetailResponse);

        mockMvc.perform(put("/api/v1/catalog/products/{id}", sampleProductId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product details updated successfully."));

        verify(iamApi, times(1)).validateAdminAccess();
        verify(productAdminService, times(1)).updateProduct(eq(sampleProductId), any(UpdateProductRequest.class));
    }

    @Test
    @DisplayName("PATCH /api/v1/catalog/products/{id}/status - Đổi trạng thái sản phẩm thành công (200 OK)")
    void updateProductStatus_Success() throws Exception {
        doNothing().when(iamApi).validateAdminAccess();

        UpdateProductStatusRequest statusReq = new UpdateProductStatusRequest(ProductStatus.INACTIVE);

        when(productAdminService.updateProductStatus(eq(sampleProductId), any(UpdateProductStatusRequest.class)))
                .thenReturn(sampleDetailResponse);

        mockMvc.perform(patch("/api/v1/catalog/products/{id}/status", sampleProductId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái sản phẩm thành công"));

        verify(iamApi, times(1)).validateAdminAccess();
        verify(productAdminService, times(1)).updateProductStatus(eq(sampleProductId), any(UpdateProductStatusRequest.class));
    }

    @Test
    @DisplayName("PATCH /api/v1/catalog/products/{id}/status - Đổi trạng thái DISCONTINUED trả về MSG38 (200 OK)")
    void updateProductStatus_Discontinued_Success() throws Exception {
        doNothing().when(iamApi).validateAdminAccess();

        UpdateProductStatusRequest statusReq = new UpdateProductStatusRequest(ProductStatus.DISCONTINUED);

        when(productAdminService.updateProductStatus(eq(sampleProductId), any(UpdateProductStatusRequest.class)))
                .thenReturn(sampleDetailResponse);

        mockMvc.perform(patch("/api/v1/catalog/products/{id}/status", sampleProductId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product marked as Discontinued."));

        verify(iamApi, times(1)).validateAdminAccess();
        verify(productAdminService, times(1)).updateProductStatus(eq(sampleProductId), any(UpdateProductStatusRequest.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/catalog/products/{id} - Xóa mềm sản phẩm thành công (200 OK)")
    void deleteProduct_Success() throws Exception {
        doNothing().when(iamApi).validateAdminAccess();
        doNothing().when(productAdminService).deleteProduct(sampleProductId);

        mockMvc.perform(delete("/api/v1/catalog/products/{id}", sampleProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Xóa sản phẩm thành công"));

        verify(iamApi, times(1)).validateAdminAccess();
        verify(productAdminService, times(1)).deleteProduct(sampleProductId);
    }

    @Test
    @DisplayName("GET /api/v1/catalog/categories - Lấy danh mục hoạt động (200 OK)")
    void getActiveCategories_Success() throws Exception {
        CategoryBriefResponse cat = new CategoryBriefResponse(sampleCategoryId, "CAT-01", "Thiết bị vệ sinh");
        when(productAdminService.getActiveCategories()).thenReturn(List.of(cat));

        mockMvc.perform(get("/api/v1/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].categoryCode").value("CAT-01"));

        verify(productAdminService, times(1)).getActiveCategories();
    }

    @Test
    @DisplayName("GET /api/v1/catalog/brands - Lấy thương hiệu hoạt động (200 OK)")
    void getActiveBrands_Success() throws Exception {
        BrandBriefResponse brand = new BrandBriefResponse(sampleBrandId, "TOTO", "TOTO Co.");
        when(productAdminService.getActiveBrands()).thenReturn(List.of(brand));

        mockMvc.perform(get("/api/v1/catalog/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].brandCode").value("TOTO"));

        verify(productAdminService, times(1)).getActiveBrands();
    }
}
