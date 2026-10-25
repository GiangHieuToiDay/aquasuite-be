package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.enums.BrandStatus;
import com.aquasuite.ims.module.catalog.internal.enums.CategoryStatus;
import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.enums.ProductType;
import com.aquasuite.ims.module.catalog.internal.enums.VariantStatus;
import com.aquasuite.ims.module.catalog.internal.error.CatalogErrorCode;
import com.aquasuite.ims.module.catalog.internal.payload.req.ProductSearchCriteria;
import com.aquasuite.ims.module.catalog.internal.payload.res.*;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import com.aquasuite.ims.module.catalog.internal.service.impl.ProductStorefrontServiceImpl;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductStorefrontServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private ProductImageRepository imageRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private InventoryApi inventoryApi;

    @Mock
    private IamApi iamApi;

    @InjectMocks
    private ProductStorefrontServiceImpl storefrontService;

    private UUID categoryId;
    private UUID brandId;
    private UUID productId;
    private UUID variantId;
    private Category sampleCategory;
    private Brand sampleBrand;
    private Product sampleProduct;
    private ProductVariant sampleVariant;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        brandId = UUID.randomUUID();
        productId = UUID.randomUUID();
        variantId = UUID.randomUUID();

        sampleCategory = Category.builder()
                .categoryId(categoryId)
                .categoryCode("405")
                .categoryName("THIẾT BỊ VỆ SINH TOTO")
                .status(CategoryStatus.ACTIVE)
                .build();

        sampleBrand = Brand.builder()
                .brandId(brandId)
                .brandCode("TOTO")
                .brandName("TOTO Corporation")
                .status(BrandStatus.ACTIVE)
                .build();

        sampleProduct = Product.builder()
                .productId(productId)
                .productCode("SP-TOTO-CS886")
                .productName("Bồn cầu thông minh TOTO CS886 305mm")
                .categoryId(categoryId)
                .brandId(brandId)
                .productType(ProductType.NORMAL)
                .status(ProductStatus.ACTIVE)
                .specifications(Map.of("flushingType", "Tornado", "roughIn", "305mm"))
                .createdAt(LocalDateTime.now())
                .build();

        sampleVariant = ProductVariant.builder()
                .variantId(variantId)
                .productId(productId)
                .sku("TOTO-CS886-W")
                .variantName("Bồn cầu TOTO CS886 Trắng sứ")
                .unit("Bộ")
                .color("Trắng sứ")
                .size("305 mm")
                .sellingPrice(new BigDecimal("8500000"))
                .status(VariantStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Tìm kiếm sản phẩm thành công kèm thẻ sản phẩm và thông tin tồn kho thời gian thực")
    void searchProducts_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(sampleProduct), pageable, 1);

        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(categoryRepository.findAllById(anySet())).thenReturn(List.of(sampleCategory));
        when(brandRepository.findAllById(anySet())).thenReturn(List.of(sampleBrand));
        when(variantRepository.findByProductIdIn(anyList())).thenReturn(List.of(sampleVariant));
        when(imageRepository.findByProductIdIn(anyList())).thenReturn(Collections.emptyList());

        StockSummaryDto stockDto = StockSummaryDto.builder()
                .variantId(variantId)
                .warehouseCode("WH_HN")
                .availableQty(new BigDecimal("15"))
                .onHandQty(new BigDecimal("20"))
                .reservedQty(new BigDecimal("5"))
                .build();
        when(inventoryApi.getStockSummaries(anyCollection())).thenReturn(Map.of(variantId, stockDto));

        ProductSearchCriteria criteria = ProductSearchCriteria.builder()
                .keyword("TOTO CS886")
                .build();

        StorefrontSearchResultResponse result = storefrontService.searchProducts(criteria, pageable);

        assertThat(result).isNotNull();
        assertThat(result.products().getContent()).hasSize(1);
        StorefrontProductCardResponse card = result.products().getContent().get(0);
        assertThat(card.productId()).isEqualTo(productId);
        assertThat(card.productCode()).isEqualTo("SP-TOTO-CS886");
        assertThat(card.brandName()).isEqualTo("TOTO Corporation");
        assertThat(card.categoryName()).isEqualTo("THIẾT BỊ VỆ SINH TOTO");
        assertThat(card.startingPrice()).isEqualByComparingTo("8500000");
        assertThat(card.stockStatus()).isEqualTo("IN_STOCK");
        assertThat(card.stockBadge()).isEqualTo("In Stock");
        assertThat(card.totalAvailableStock()).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("Alternative Flow Step 2: Không tìm thấy sản phẩm -> Trả về MSG43 và danh mục phổ biến")
    void searchProducts_NoProductsFound_ReturnsMSG43() {
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));
        when(categoryRepository.findAllActive()).thenReturn(List.of(sampleCategory));

        ProductSearchCriteria criteria = ProductSearchCriteria.builder()
                .keyword("Mã không tồn tại")
                .build();

        StorefrontSearchResultResponse result = storefrontService.searchProducts(criteria, pageable);

        assertThat(result).isNotNull();
        assertThat(result.products().getContent()).isEmpty();
        assertThat(result.message()).isEqualTo(ProductStorefrontServiceImpl.MSG43_NO_PRODUCTS_FOUND);
        assertThat(result.popularCategories()).hasSize(1);
        assertThat(result.popularCategories().get(0).categoryName()).isEqualTo("THIẾT BỊ VỆ SINH TOTO");
    }

    @Test
    @DisplayName("Lấy dữ liệu Faceted Filter Sidebar thành công")
    void getFacets_Success() {
        when(categoryRepository.findAllActive()).thenReturn(List.of(sampleCategory));
        when(brandRepository.findAllActive()).thenReturn(List.of(sampleBrand));
        when(productRepository.count(any(Specification.class))).thenReturn(5L);
        when(variantRepository.findMinActiveSellingPrice()).thenReturn(new BigDecimal("1000000"));
        when(variantRepository.findMaxActiveSellingPrice()).thenReturn(new BigDecimal("25000000"));
        when(variantRepository.findDistinctActiveColors()).thenReturn(List.of("Trắng sứ", "Đen nhám"));
        when(variantRepository.findDistinctActiveSizes()).thenReturn(List.of("305 mm", "400 mm"));

        ProductFacetResponse facets = storefrontService.getFacets();

        assertThat(facets).isNotNull();
        assertThat(facets.categories()).hasSize(1);
        assertThat(facets.categories().get(0).productCount()).isEqualTo(5L);
        assertThat(facets.brands()).hasSize(1);
        assertThat(facets.minPrice()).isEqualByComparingTo("1000000");
        assertThat(facets.maxPrice()).isEqualByComparingTo("25000000");
        assertThat(facets.colors()).contains("Trắng sứ", "Đen nhám");
        assertThat(facets.sizes()).contains("305 mm", "400 mm");
        assertThat(facets.technicalSpecifications()).containsKey("flushingType");
    }

    @Test
    @DisplayName("Xem chi tiết sản phẩm cho Khách hàng: Hiển thị trạng thái chung, không có chi tiết kho")
    void getProductDetail_CustomerView_Success() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProduct));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(sampleCategory));
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(sampleBrand));
        when(variantRepository.findByProductId(productId)).thenReturn(List.of(sampleVariant));
        when(imageRepository.findByProductId(productId)).thenReturn(Collections.emptyList());
        when(iamApi.isSalesStaffOrAdmin()).thenReturn(false); // Guest / Customer

        StockSummaryDto stockDto = StockSummaryDto.builder()
                .variantId(variantId)
                .availableQty(new BigDecimal("8"))
                .build();
        when(inventoryApi.getStockSummaries(anyCollection())).thenReturn(Map.of(variantId, stockDto));

        StorefrontProductDetailResponse detail = storefrontService.getProductDetail(productId);

        assertThat(detail).isNotNull();
        assertThat(detail.productId()).isEqualTo(productId);
        assertThat(detail.productCode()).isEqualTo("SP-TOTO-CS886");
        assertThat(detail.stockStatus()).isEqualTo("IN_STOCK");
        assertThat(detail.variants()).hasSize(1);

        StorefrontVariantDetailResponse variantRes = detail.variants().get(0);
        assertThat(variantRes.isSalesStaff()).isFalse();
        assertThat(variantRes.warehouseBreakdown()).isNull(); // Customer không thấy chi tiết kho
        assertThat(variantRes.stockBadge()).isEqualTo("In Stock");
        assertThat(variantRes.canInstantBuy()).isTrue();
        assertThat(variantRes.allowNotifyOrQuote()).isFalse();
    }

    @Test
    @DisplayName("Xem chi tiết sản phẩm cho Sales Staff: Hiển thị chi tiết số lượng theo từng kho")
    void getProductDetail_SalesStaffView_Success() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProduct));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(sampleCategory));
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(sampleBrand));
        when(variantRepository.findByProductId(productId)).thenReturn(List.of(sampleVariant));
        when(imageRepository.findByProductId(productId)).thenReturn(Collections.emptyList());
        when(iamApi.isSalesStaffOrAdmin()).thenReturn(true); // Sales Staff!

        StockSummaryDto stockDto = StockSummaryDto.builder()
                .variantId(variantId)
                .warehouseCode("KHO_TONG_HN")
                .onHandQty(new BigDecimal("10"))
                .reservedQty(new BigDecimal("2"))
                .availableQty(new BigDecimal("8"))
                .minimumQty(new BigDecimal("3"))
                .isLowStock(false)
                .build();
        when(inventoryApi.getStockSummaries(anyCollection())).thenReturn(Map.of(variantId, stockDto));

        StorefrontProductDetailResponse detail = storefrontService.getProductDetail(productId);

        assertThat(detail).isNotNull();
        StorefrontVariantDetailResponse variantRes = detail.variants().get(0);
        assertThat(variantRes.isSalesStaff()).isTrue();
        assertThat(variantRes.warehouseBreakdown()).isNotNull().hasSize(1);
        assertThat(variantRes.warehouseBreakdown().get(0).warehouseCode()).isEqualTo("KHO_TONG_HN");
        assertThat(variantRes.warehouseBreakdown().get(0).availableQty()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("Alternative Flow Step 6: Biến thể hết hàng (0 inventory) -> Hiển thị badge Out of Stock và bật Add to Quote")
    void getProductDetail_OutOfStockVariant_Step6AlternativeFlow() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProduct));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(sampleCategory));
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(sampleBrand));
        when(variantRepository.findByProductId(productId)).thenReturn(List.of(sampleVariant));
        when(imageRepository.findByProductId(productId)).thenReturn(Collections.emptyList());
        when(iamApi.isSalesStaffOrAdmin()).thenReturn(false);

        // Tồn kho = 0
        StockSummaryDto stockDto = StockSummaryDto.builder()
                .variantId(variantId)
                .availableQty(BigDecimal.ZERO)
                .build();
        when(inventoryApi.getStockSummaries(anyCollection())).thenReturn(Map.of(variantId, stockDto));

        StorefrontProductDetailResponse detail = storefrontService.getProductDetail(productId);

        assertThat(detail).isNotNull();
        StorefrontVariantDetailResponse variantRes = detail.variants().get(0);
        assertThat(variantRes.stockStatus()).isEqualTo("OUT_OF_STOCK");
        assertThat(variantRes.stockBadge()).isEqualTo("Temporarily Out of Stock - Contact Sales for Reservation");
        assertThat(variantRes.canInstantBuy()).isFalse(); // Vô hiệu hóa Mua ngay
        assertThat(variantRes.allowNotifyOrQuote()).isTrue(); // Bật Nhận thông báo / Báo giá
    }

    @Test
    @DisplayName("Xem chi tiết thất bại khi sản phẩm không tồn tại hoặc không ACTIVE")
    void getProductDetail_NotFound_ThrowsException() {
        UUID nonExistentId = UUID.randomUUID();
        when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storefrontService.getProductDetail(nonExistentId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", CatalogErrorCode.PRODUCT_NOT_FOUND);
    }
}
