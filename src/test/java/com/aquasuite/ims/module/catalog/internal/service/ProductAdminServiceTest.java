package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.enums.BrandStatus;
import com.aquasuite.ims.module.catalog.internal.enums.CategoryStatus;
import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.enums.ProductType;
import com.aquasuite.ims.module.catalog.internal.error.CatalogErrorCode;
import com.aquasuite.ims.module.catalog.internal.payload.req.*;
import com.aquasuite.ims.module.catalog.internal.payload.res.ProductDetailResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.ProductSummaryResponse;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import com.aquasuite.ims.module.catalog.internal.service.impl.ProductAdminServiceImpl;
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

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductAdminServiceTest {

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

    @InjectMocks
    private ProductAdminServiceImpl productAdminService;

    private UUID categoryId;
    private UUID brandId;
    private Category sampleCategory;
    private Brand sampleBrand;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        brandId = UUID.randomUUID();

        sampleCategory = Category.builder()
                .categoryId(categoryId)
                .categoryCode("CAT_01")
                .categoryName("Thiết bị vệ sinh")
                .status(CategoryStatus.ACTIVE)
                .build();

        sampleBrand = Brand.builder()
                .brandId(brandId)
                .brandCode("TOTO")
                .brandName("TOTO Corporation")
                .status(BrandStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Tạo sản phẩm thành công (chỉ quản lý sản phẩm cha và hình ảnh)")
    void createProduct_Success() {
        CreateProductRequest request = CreateProductRequest.builder()
                .productCode("SP-TOTO-001")
                .productName("Bồn cầu TOTO một khối CS886")
                .categoryId(categoryId)
                .brandId(brandId)
                .description("Bồn cầu cao cấp TOTO tráng men CeFiONtect chống bám bẩn")
                .imageUrls(List.of("https://example.com/product.jpg"))
                .build();

        when(productRepository.existsByProductCode("SP-TOTO-001")).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(sampleCategory));
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(sampleBrand));

        UUID productId = UUID.randomUUID();
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setProductId(productId);
            p.setCreatedAt(Instant.now());
            return p;
        });

        when(imageRepository.save(any(ProductImage.class))).thenAnswer(invocation -> {
            ProductImage img = invocation.getArgument(0);
            img.setImageId(UUID.randomUUID());
            return img;
        });

        ProductDetailResponse response = productAdminService.createProduct(request);

        assertThat(response).isNotNull();
        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.productCode()).isEqualTo("SP-TOTO-001");
        assertThat(response.category()).isNotNull();
        assertThat(response.category().categoryId()).isEqualTo(categoryId);
        assertThat(response.brand()).isNotNull();
        assertThat(response.brand().brandId()).isEqualTo(brandId);
        assertThat(response.images()).hasSize(1);

        verify(productRepository, times(1)).save(any(Product.class));
        verify(imageRepository, times(1)).save(any(ProductImage.class));
    }

    @Test
    @DisplayName("Tạo sản phẩm thất bại khi mã sản phẩm bị trùng")
    void createProduct_DuplicateProductCode_ThrowsException() {
        CreateProductRequest request = CreateProductRequest.builder()
                .productCode("SP-TOTO-001")
                .productName("Bồn cầu TOTO")
                .categoryId(categoryId)
                .build();

        when(productRepository.existsByProductCode("SP-TOTO-001")).thenReturn(true);

        assertThatThrownBy(() -> productAdminService.createProduct(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", CatalogErrorCode.PRODUCT_CODE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("Tạo sản phẩm thất bại khi danh mục không tồn tại")
    void createProduct_CategoryNotFound_ThrowsException() {
        UUID nonExistentCatId = UUID.randomUUID();
        CreateProductRequest request = CreateProductRequest.builder()
                .productCode("SP-002")
                .productName("Sản phẩm 2")
                .categoryId(nonExistentCatId)
                .build();

        when(productRepository.existsByProductCode("SP-002")).thenReturn(false);
        when(categoryRepository.findById(nonExistentCatId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productAdminService.createProduct(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", CatalogErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("Xem chi tiết sản phẩm thành công")
    void getProductDetail_Success() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .productId(productId)
                .productCode("P-001")
                .productName("Vòi lavabo")
                .categoryId(categoryId)
                .brandId(brandId)
                .status(ProductStatus.ACTIVE)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(sampleCategory));
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(sampleBrand));
        when(variantRepository.findByProductId(productId)).thenReturn(Collections.emptyList());
        when(imageRepository.findByProductId(productId)).thenReturn(Collections.emptyList());

        ProductDetailResponse response = productAdminService.getProductDetail(productId);

        assertThat(response).isNotNull();
        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.productCode()).isEqualTo("P-001");
        assertThat(response.category().categoryName()).isEqualTo("Thiết bị vệ sinh");
        assertThat(response.brand().brandName()).isEqualTo("TOTO Corporation");
    }

    @Test
    @DisplayName("Xem chi tiết thất bại khi sản phẩm không tồn tại")
    void getProductDetail_NotFound_ThrowsException() {
        UUID nonExistentId = UUID.randomUUID();
        when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productAdminService.getProductDetail(nonExistentId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", CatalogErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("Cập nhật trạng thái sản phẩm sang INACTIVE")
    void updateProductStatus_Deactivate_Success() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .productId(productId)
                .productCode("P-STATUS")
                .productName("Sản phẩm đổi trạng thái")
                .categoryId(categoryId)
                .status(ProductStatus.ACTIVE)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(sampleCategory));
        when(variantRepository.findByProductId(productId)).thenReturn(Collections.emptyList());
        when(imageRepository.findByProductId(productId)).thenReturn(Collections.emptyList());

        UpdateProductStatusRequest request = new UpdateProductStatusRequest(ProductStatus.INACTIVE);
        ProductDetailResponse response = productAdminService.updateProductStatus(productId, request);

        assertThat(response).isNotNull();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        verify(productRepository, times(1)).save(product);
    }

    @Test
    @DisplayName("Xóa mềm sản phẩm thành công (chuyển DELETED)")
    void deleteProduct_Success() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .productId(productId)
                .productCode("P-DELETE")
                .status(ProductStatus.ACTIVE)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        productAdminService.deleteProduct(productId);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.DELETED);
        verify(productRepository, times(1)).save(product);
    }

    @Test
    @DisplayName("Lấy danh sách sản phẩm phân trang")
    void listProducts_Success() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .productId(productId)
                .productCode("P-LIST")
                .productName("Sản phẩm danh sách")
                .categoryId(categoryId)
                .brandId(brandId)
                .productType(ProductType.NORMAL)
                .status(ProductStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);

        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(categoryRepository.findAllById(anySet())).thenReturn(List.of(sampleCategory));
        when(brandRepository.findAllById(anySet())).thenReturn(List.of(sampleBrand));
        when(variantRepository.findByProductIdIn(anyList())).thenReturn(Collections.emptyList());
        when(imageRepository.findByProductIdIn(anyList())).thenReturn(Collections.emptyList());

        Page<ProductSummaryResponse> result = productAdminService.listProducts(
                null, null, null, null, null, pageable
        );

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        ProductSummaryResponse summary = result.getContent().get(0);
        assertThat(summary.productId()).isEqualTo(productId);
        assertThat(summary.productCode()).isEqualTo("P-LIST");
        assertThat(summary.categoryName()).isEqualTo("Thiết bị vệ sinh");
        assertThat(summary.brandName()).isEqualTo("TOTO Corporation");
    }
}
