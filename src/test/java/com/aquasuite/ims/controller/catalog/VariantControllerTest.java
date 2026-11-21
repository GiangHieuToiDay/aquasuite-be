package com.aquasuite.ims.controller.catalog;

import com.aquasuite.ims.controller.catalog.ProductController;
import com.aquasuite.ims.entity.catalog.Product;
import com.aquasuite.ims.entity.catalog.ProductVariant;
import com.aquasuite.ims.enums.catalog.ProductStatus;
import com.aquasuite.ims.enums.catalog.ProductType;
import com.aquasuite.ims.enums.catalog.VariantStatus;
import com.aquasuite.ims.repository.catalog.ProductRepository;
import com.aquasuite.ims.repository.catalog.ProductVariantRepository;
import com.aquasuite.ims.repository.catalog.WarrantyPolicyRepository;
import com.aquasuite.ims.service.catalog.ProductService;
import com.aquasuite.ims.service.catalog.impl.ProductServiceImpl;
import com.aquasuite.ims.service.catalog.impl.VariantServiceImpl;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.dto.iam.response.UserSummaryDto;
import com.aquasuite.ims.service.inventory.InventoryService;
import com.aquasuite.ims.dto.inventory.response.VariantInventoryUsage;
import com.aquasuite.ims.service.sales.SalesQueryService;
import com.aquasuite.ims.dto.sales.response.VariantDocumentUsage;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(VariantControllerTest.Config.class)
class VariantControllerTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class Config implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new PageableHandlerMethodArgumentResolver());
        }

        @Bean
        ProductRepository productRepository() {
            return mock(ProductRepository.class);
        }

        @Bean
        ProductVariantRepository variantRepository() {
            return mock(ProductVariantRepository.class);
        }

        @Bean
        WarrantyPolicyRepository warrantyPolicyRepository() {
            return mock(WarrantyPolicyRepository.class);
        }

        @Bean
        InventoryService inventoryApi() {
            return mock(InventoryService.class);
        }

        @Bean
        SalesQueryService salesVariantUsageApi() {
            return mock(SalesQueryService.class);
        }

        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean
        IamQueryService iamApi() {
            return mock(IamQueryService.class);
        }

        @Bean
        ApplicationEventPublisher eventPublisher() {
            return mock(ApplicationEventPublisher.class);
        }

        @Bean
        VariantServiceImpl variantService(
                ProductRepository productRepository,
                ProductVariantRepository variantRepository,
                WarrantyPolicyRepository warrantyPolicyRepository,
                InventoryService inventoryApi,
                SalesQueryService salesVariantUsageApi,
                JdbcTemplate jdbcTemplate,
                IamQueryService iamApi,
                ApplicationEventPublisher eventPublisher
        ) {
            return new VariantServiceImpl(
                    productRepository,
                    variantRepository,
                    warrantyPolicyRepository,
                    inventoryApi,
                    salesVariantUsageApi,
                    jdbcTemplate,
                    iamApi,
                    eventPublisher
            );
        }

        @Bean
        ProductService productService(VariantServiceImpl variantService) {
            return new ProductServiceImpl(null, variantService);
        }

        @Bean
        ProductController variantController(ProductService productService) {
            return new ProductController(productService, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .build();
        }
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    ProductVariantRepository variantRepository;

    @Autowired
    InventoryService inventoryApi;

    @Autowired
    SalesQueryService salesVariantUsageApi;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    IamQueryService iamApi;

    MockMvc mvc;

    private static final UUID PRODUCT_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID VARIANT_ID = UUID.fromString("30000000-0000-4000-8000-000000000002");

    private ProductVariant sampleVariant;

    @BeforeEach
    void setup() {
        reset(productRepository, variantRepository, inventoryApi, salesVariantUsageApi, jdbcTemplate, iamApi);

        Product product = new Product();
        product.setProductId(PRODUCT_ID);
        product.setProductType(ProductType.NORMAL);
        product.setStatus(ProductStatus.ACTIVE);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        sampleVariant = new ProductVariant();
        sampleVariant.setVariantId(VARIANT_ID);
        sampleVariant.setProductId(PRODUCT_ID);
        sampleVariant.setSku("SKU-NEO-WHT-305");
        sampleVariant.setVariantName("White 305");
        sampleVariant.setUnit("cái");
        sampleVariant.setConversionRate(BigDecimal.ONE);
        sampleVariant.setWeight(BigDecimal.ONE);
        sampleVariant.setCostPrice(new BigDecimal("30000000"));
        sampleVariant.setSellingPrice(new BigDecimal("45000000"));
        sampleVariant.setStatus(VariantStatus.ACTIVE);

        when(variantRepository.search(eq(PRODUCT_ID), any(), any(), anyBoolean(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleVariant), PageRequest.of(0, 10), 1));
        when(variantRepository.countByProductIdAndStatusNot(PRODUCT_ID, VariantStatus.DELETED)).thenReturn(2L);
        when(variantRepository.countByProductIdAndStatus(PRODUCT_ID, VariantStatus.ACTIVE)).thenReturn(1L);
        when(variantRepository.countByProductIdAndStatus(PRODUCT_ID, VariantStatus.INACTIVE)).thenReturn(1L);
        when(variantRepository.countByProductIdAndStatusNotAndSellingPriceIsNull(PRODUCT_ID, VariantStatus.DELETED)).thenReturn(1L);
        when(inventoryApi.getStockSummaries(any(), any())).thenReturn(Map.of());

        when(iamApi.getCurrentUser()).thenAnswer(invocation -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return null;
            }
            List<String> roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();
            return UserSummaryDto.builder()
                    .id(UUID.randomUUID())
                    .username(auth.getName())
                    .roles(roles)
                    .build();
        });

        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerIsForbiddenFromListAndStats() throws Exception {
        mvc.perform(get("/api/v1/catalog/products/{productId}/variants", PRODUCT_ID))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/catalog/products/{productId}/variants/stats", PRODUCT_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SHIPPER")
    void shipperIsForbiddenFromListAndStats() throws Exception {
        mvc.perform(get("/api/v1/catalog/products/{productId}/variants", PRODUCT_ID))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/catalog/products/{productId}/variants/stats", PRODUCT_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffCanReadVariantsWithMaskedCostPriceAndCannotSortByProfit() throws Exception {
        mvc.perform(get("/api/v1/catalog/products/{productId}/variants", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sku").value("SKU-NEO-WHT-305"))
                .andExpect(jsonPath("$.data.content[0].costPrice").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.content[0].sellingPrice").value(45000000));

        mvc.perform(get("/api/v1/catalog/products/{productId}/variants/stats", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mvc.perform(get("/api/v1/catalog/products/{productId}/variants", PRODUCT_ID)
                        .param("sortBy", "profit"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerReceivesFullCostPriceInList() throws Exception {
        mvc.perform(get("/api/v1/catalog/products/{productId}/variants", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].costPrice").value(30000000))
                .andExpect(jsonPath("$.data.content[0].sellingPrice").value(45000000));
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantReceivesFullCostPriceInList() throws Exception {
        mvc.perform(get("/api/v1/catalog/products/{productId}/variants", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].costPrice").value(30000000))
                .andExpect(jsonPath("$.data.content[0].sellingPrice").value(45000000));
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffIsForbiddenFromMutationAndDeletionEndpoints() throws Exception {
        String validUpsertJson = """
                {
                  "sku": "SKU-NEW-001",
                  "variantName": "New Variant",
                  "unit": "cái",
                  "conversionRate": 1,
                  "weight": 10,
                  "costPrice": 100000,
                  "sellingPrice": 150000,
                  "acknowledgeBelowCost": false
                }
                """;

        mvc.perform(post("/api/v1/catalog/products/{productId}/variants", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpsertJson))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/catalog/products/{productId}/variants/matrix", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[" + validUpsertJson + "]}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/catalog/products/{productId}/variants/{variantId}", PRODUCT_ID, VARIANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpsertJson))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/catalog/products/{productId}/variants/{variantId}/status", PRODUCT_ID, VARIANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/catalog/products/{productId}/variants/{variantId}/deletion-eligibility", PRODUCT_ID, VARIANT_ID))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/catalog/products/{productId}/variants/{variantId}", PRODUCT_ID, VARIANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true,\"reason\":\"Test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void validationRejectsInvalidPayloads() throws Exception {
        String invalidUpsertJson = """
                {
                  "sku": " ",
                  "variantName": "",
                  "unit": "",
                  "conversionRate": 0,
                  "weight": -1
                }
                """;

        mvc.perform(post("/api/v1/catalog/products/{productId}/variants", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidUpsertJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        mvc.perform(post("/api/v1/catalog/products/{productId}/variants/matrix", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        mvc.perform(patch("/api/v1/catalog/products/{productId}/variants/{variantId}/status", PRODUCT_ID, VARIANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void deleteBlockedReturns409Sku007WithEligibilityData() throws Exception {
        when(variantRepository.findByProductIdAndVariantIdForUpdate(PRODUCT_ID, VARIANT_ID))
                .thenReturn(Optional.of(sampleVariant));
        when(inventoryApi.lockAndGetVariantDeletionUsage(VARIANT_ID))
                .thenReturn(new VariantInventoryUsage(VARIANT_ID, new BigDecimal("5"), new BigDecimal("1"), 2, 1));
        when(salesVariantUsageApi.getVariantDocumentUsage(VARIANT_ID))
                .thenReturn(new VariantDocumentUsage(VARIANT_ID, 1, 0, 0, 3, 0, 2, 1, 0));

        mvc.perform(delete("/api/v1/catalog/products/{productId}/variants/{variantId}", PRODUCT_ID, VARIANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true,\"reason\":\"Created in error\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SKU_007"))
                .andExpect(jsonPath("$.data.variantId").value(VARIANT_ID.toString()))
                .andExpect(jsonPath("$.data.eligible").value(false))
                .andExpect(jsonPath("$.data.onHandQty").value(5))
                .andExpect(jsonPath("$.data.reservedQty").value(1))
                .andExpect(jsonPath("$.data.orderCount").value(3))
                .andExpect(jsonPath("$.data.purchaseOrderCount").value(2))
                .andExpect(jsonPath("$.data.deliveryItemCount").value(1));
    }
}
