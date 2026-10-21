package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.enums.ProductType;
import com.aquasuite.ims.module.catalog.internal.exception.CatalogErrorCode;
import com.aquasuite.ims.module.catalog.internal.payload.res.*;
import com.aquasuite.ims.module.catalog.internal.service.ProductStorefrontService;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProductStorefrontControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProductStorefrontService storefrontService;

    @InjectMocks
    private ProductStorefrontController storefrontController;

    private UUID sampleProductId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(storefrontController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleProductId = UUID.randomUUID();
    }

    @Test
    @DisplayName("GET /api/v1/catalog/storefront/search - Tìm kiếm thành công (200 OK)")
    void searchProducts_Success() throws Exception {
        StorefrontProductCardResponse card = StorefrontProductCardResponse.builder()
                .productId(sampleProductId)
                .productCode("SP-TOTO-001")
                .productName("Bồn cầu TOTO CS886")
                .brandName("TOTO")
                .categoryName("Thiết bị vệ sinh")
                .startingPrice(new BigDecimal("8500000"))
                .stockStatus("IN_STOCK")
                .stockBadge("In Stock")
                .totalAvailableStock(new BigDecimal("10"))
                .totalVariants(1)
                .createdAt(LocalDateTime.now())
                .build();

        StorefrontSearchResultResponse resultResponse = StorefrontSearchResultResponse.builder()
                .products(new PageImpl<>(List.of(card), PageRequest.of(0, 20), 1))
                .message("Search completed successfully")
                .build();

        when(storefrontService.searchProducts(any(), any())).thenReturn(resultResponse);

        mockMvc.perform(get("/api/v1/catalog/storefront/search")
                        .param("keyword", "TOTO")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.products.content[0].productId").value(sampleProductId.toString()))
                .andExpect(jsonPath("$.data.products.content[0].productName").value("Bồn cầu TOTO CS886"))
                .andExpect(jsonPath("$.data.products.content[0].stockStatus").value("IN_STOCK"));

        verify(storefrontService, times(1)).searchProducts(any(), any());
    }

    @Test
    @DisplayName("GET /api/v1/catalog/storefront/facets - Lấy siêu dữ liệu bộ lọc (200 OK)")
    void getFacets_Success() throws Exception {
        ProductFacetResponse facetResponse = ProductFacetResponse.builder()
                .categories(List.of(new ProductFacetResponse.CategoryFacetItem(UUID.randomUUID(), "CAT_01", "Bồn cầu", 10)))
                .brands(List.of(new ProductFacetResponse.BrandFacetItem(UUID.randomUUID(), "TOTO", "TOTO Co.", 10)))
                .minPrice(new BigDecimal("500000"))
                .maxPrice(new BigDecimal("20000000"))
                .colors(List.of("Trắng sứ"))
                .sizes(List.of("305 mm"))
                .technicalSpecifications(Collections.emptyMap())
                .build();

        when(storefrontService.getFacets()).thenReturn(facetResponse);

        mockMvc.perform(get("/api/v1/catalog/storefront/facets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.categories[0].categoryName").value("Bồn cầu"))
                .andExpect(jsonPath("$.data.minPrice").value(500000));

        verify(storefrontService, times(1)).getFacets();
    }

    @Test
    @DisplayName("GET /api/v1/catalog/storefront/products/{id} - Xem chi tiết sản phẩm (200 OK)")
    void getProductDetail_Success() throws Exception {
        StorefrontProductDetailResponse detail = StorefrontProductDetailResponse.builder()
                .productId(sampleProductId)
                .productCode("SP-TOTO-001")
                .productName("Bồn cầu TOTO CS886")
                .productType(ProductType.NORMAL)
                .status(ProductStatus.ACTIVE)
                .startingPrice(new BigDecimal("8500000"))
                .stockStatus("IN_STOCK")
                .stockBadge("In Stock")
                .images(Collections.emptyList())
                .variants(Collections.emptyList())
                .warrantyTerms("10-year ceramic warranty")
                .createdAt(LocalDateTime.now())
                .build();

        when(storefrontService.getProductDetail(sampleProductId)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/catalog/storefront/products/{id}", sampleProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(sampleProductId.toString()))
                .andExpect(jsonPath("$.data.productName").value("Bồn cầu TOTO CS886"))
                .andExpect(jsonPath("$.data.warrantyTerms").value("10-year ceramic warranty"));

        verify(storefrontService, times(1)).getProductDetail(sampleProductId);
    }

    @Test
    @DisplayName("GET /api/v1/catalog/storefront/products/{id} - Không tìm thấy (404 Not Found)")
    void getProductDetail_NotFound() throws Exception {
        when(storefrontService.getProductDetail(sampleProductId))
                .thenThrow(new AppException(CatalogErrorCode.PRODUCT_NOT_FOUND, sampleProductId));

        mockMvc.perform(get("/api/v1/catalog/storefront/products/{id}", sampleProductId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CAT_001"));
    }
}
