package com.aquasuite.ims.module.catalog.internal.config;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.entity.enums.BrandStatus;
import com.aquasuite.ims.module.catalog.internal.entity.enums.CategoryStatus;
import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductType;
import com.aquasuite.ims.module.catalog.internal.entity.enums.VariantStatus;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogDataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;

    @Override
    public void run(String... args) {
        initCategories();
        initBrands();
        initSampleProducts();
    }

    private void initCategories() {
        if (categoryRepository.count() == 0) {
            log.info("Seeding reference categories from Excel template...");
            categoryRepository.saveAll(List.of(
                    createCategory("109", "ỐNG GEN & PHỤ KIỆN"),
                    createCategory("202", "MẶT CÔNG TẮC & Ổ CẮM"),
                    createCategory("301", "DÂY VÀ CÁP ĐIỆN"),
                    createCategory("405", "THIẾT BỊ VỆ SINH TOTO"),
                    createCategory("502", "BỒN CẦU & THIẾT BỊ PHÒNG TẮM"),
                    createCategory("601", "BƠM NƯỚC & PHỤ KIỆN CẤP THOÁT"),
                    createCategory("708", "ĐÈN CHIẾU SÁNG & ĐÈN TRANG TRÍ")
            ));
            log.info("Seeded 7 reference categories successfully.");
        }
    }

    private void initBrands() {
        if (brandRepository.count() == 0) {
            log.info("Seeding reference brands from Excel template...");
            brandRepository.saveAll(List.of(
                    createBrand("SINO", "Sino Vanlock"),
                    createBrand("ART_DNA", "ART DNA Switches"),
                    createBrand("CADISUN", "Cáp điện Cadisun"),
                    createBrand("TOTO", "Thiết bị vệ sinh TOTO"),
                    createBrand("PANASONIC", "Panasonic Electric"),
                    createBrand("RANG_DONG", "Rạng Đông Lighting"),
                    createBrand("TIEN_PHONG", "Nhựa Tiền Phong")
            ));
            log.info("Seeded 7 reference brands successfully.");
        }
    }

    private void initSampleProducts() {
        log.info("Checking and seeding sample sanitary ware products for Storefront / POS catalog...");
        Category totoCategory = categoryRepository.findByCategoryCodeIgnoreCase("405")
                .orElseGet(() -> categoryRepository.findAll().stream().findFirst().orElse(null));
        Brand totoBrand = brandRepository.findByBrandCodeIgnoreCase("TOTO")
                .orElseGet(() -> brandRepository.findAll().stream().findFirst().orElse(null));

        if (totoCategory == null || totoBrand == null) {
            return;
        }

        // 1. Kiểm tra hoặc cập nhật sản phẩm TOTO CS886
        Product p1 = productRepository.findByProductCode("SP-TOTO-CS886").orElse(null);
        if (p1 == null) {
            p1 = Product.builder()
                    .productCode("SP-TOTO-CS886")
                    .productName("Bồn cầu thông minh TOTO CS886 305mm (TOTO smart toilet)")
                    .categoryId(totoCategory.getCategoryId())
                    .brandId(totoBrand.getBrandId())
                    .description("Bồn cầu thông minh TOTO CS886 305mm (TOTO smart toilet 305mm) nắp rửa điện tử đa chức năng, tâm xả 305mm, công nghệ xả xoáy Tornado, men CeFiONtect chống bám bẩn.")
                    .specifications(Map.of(
                            "flushingType", "Xả xoáy Tornado",
                            "roughInDimension", "305 mm",
                            "glazeTechnology", "CeFiONtect (Chống bám bẩn)",
                            "installationManualPdf", "https://aquasuite.com/manuals/TOTO-CS886-guide.pdf",
                            "warrantyTerms", "Bảo hành 10 năm phần sứ vệ sinh, 2 năm van điện tử",
                            "cadBlueprints", List.of("https://aquasuite.com/cad/TOTO-CS886-cad.dwg")
                    ))
                    .productType(ProductType.NORMAL)
                    .status(ProductStatus.ACTIVE)
                    .build();
            p1 = productRepository.save(p1);
            log.info("Created sample product SP-TOTO-CS886.");
        } else {
            // Cập nhật tên, mô tả và thông số kỹ thuật nếu đang thiếu để khớp mọi từ khóa tìm kiếm
            p1.setProductName("Bồn cầu thông minh TOTO CS886 305mm (TOTO smart toilet)");
            p1.setDescription("Bồn cầu thông minh TOTO CS886 305mm (TOTO smart toilet 305mm) nắp rửa điện tử đa chức năng, tâm xả 305mm, công nghệ xả xoáy Tornado, men CeFiONtect chống bám bẩn.");
            p1.setStatus(ProductStatus.ACTIVE);
            if (p1.getSpecifications() == null || p1.getSpecifications().isEmpty()) {
                p1.setSpecifications(Map.of(
                        "flushingType", "Xả xoáy Tornado",
                        "roughInDimension", "305 mm",
                        "glazeTechnology", "CeFiONtect (Chống bám bẩn)",
                        "installationManualPdf", "https://aquasuite.com/manuals/TOTO-CS886-guide.pdf",
                        "warrantyTerms", "Bảo hành 10 năm phần sứ vệ sinh, 2 năm van điện tử",
                        "cadBlueprints", List.of("https://aquasuite.com/cad/TOTO-CS886-cad.dwg")
                ));
            }
            p1 = productRepository.save(p1);
            log.info("Updated sample product SP-TOTO-CS886 with full specs and search tokens.");
        }

        // Tạo biến thể cho p1 nếu chưa có
        List<ProductVariant> p1Variants = variantRepository.findByProductId(p1.getProductId());
        if (p1Variants.isEmpty()) {
            ProductVariant v1_1 = ProductVariant.builder()
                    .productId(p1.getProductId())
                    .sku("TOTO-CS886-W")
                    .variantName("Bồn cầu TOTO CS886 Trắng sứ 305mm")
                    .unit("Bộ")
                    .color("Trắng sứ")
                    .size("305 mm")
                    .costPrice(new BigDecimal("10000000"))
                    .sellingPrice(new BigDecimal("15500000"))
                    .status(VariantStatus.ACTIVE)
                    .build();

            ProductVariant v1_2 = ProductVariant.builder()
                    .productId(p1.getProductId())
                    .sku("TOTO-CS886-B")
                    .variantName("Bồn cầu TOTO CS886 Đen nhám 305mm")
                    .unit("Bộ")
                    .color("Đen nhám")
                    .size("305 mm")
                    .costPrice(new BigDecimal("12000000"))
                    .sellingPrice(new BigDecimal("17500000"))
                    .status(VariantStatus.ACTIVE)
                    .build();

            variantRepository.saveAll(List.of(v1_1, v1_2));

            ProductImage img1 = ProductImage.builder()
                    .productId(p1.getProductId())
                    .variantId(v1_1.getVariantId())
                    .imageUrl("https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=800&q=80")
                    .isPrimary(true)
                    .sortOrder(0)
                    .build();

            imageRepository.save(img1);
            log.info("Created 2 sample variants for SP-TOTO-CS886.");
        }

        // 2. Chậu rửa mặt đặt bàn TOTO LT4706
        if (!productRepository.existsByProductCode("SP-TOTO-LT4706")) {
            Product p2 = Product.builder()
                    .productCode("SP-TOTO-LT4706")
                    .productName("Chậu rửa mặt đặt bàn TOTO LT4706 (Washbasin)")
                    .categoryId(totoCategory.getCategoryId())
                    .brandId(totoBrand.getBrandId())
                    .description("Chậu lavabo đặt bàn cao cấp TOTO vành mỏng, phủ men CeFiONtect sáng bóng chống bám cặn.")
                    .specifications(Map.of(
                            "glazeTechnology", "CeFiONtect (Chống bám bẩn)",
                            "warrantyTerms", "Bảo hành 10 năm chính hãng TOTO",
                            "installationManualPdf", "https://aquasuite.com/manuals/TOTO-LT4706-guide.pdf"
                    ))
                    .productType(ProductType.NORMAL)
                    .status(ProductStatus.ACTIVE)
                    .build();
            p2 = productRepository.save(p2);

            ProductVariant v2_1 = ProductVariant.builder()
                    .productId(p2.getProductId())
                    .sku("TOTO-LT4706-W")
                    .variantName("Chậu rửa TOTO LT4706 Trắng sứ")
                    .unit("Cái")
                    .color("Trắng sứ")
                    .size("600 x 420 mm")
                    .costPrice(new BigDecimal("2100000"))
                    .sellingPrice(new BigDecimal("3200000"))
                    .status(VariantStatus.ACTIVE)
                    .build();

            variantRepository.save(v2_1);

            ProductImage img2 = ProductImage.builder()
                    .productId(p2.getProductId())
                    .variantId(v2_1.getVariantId())
                    .imageUrl("https://images.unsplash.com/photo-1620626011761-996317b8d101?auto=format&fit=crop&w=800&q=80")
                    .isPrimary(true)
                    .sortOrder(0)
                    .build();

            imageRepository.save(img2);
            log.info("Created sample product SP-TOTO-LT4706.");
        }
    }

    private Category createCategory(String code, String name) {
        return Category.builder()
                .categoryCode(code)
                .categoryName(name)
                .status(CategoryStatus.ACTIVE)
                .build();
    }

    private Brand createBrand(String code, String name) {
        return Brand.builder()
                .brandCode(code)
                .brandName(name)
                .status(BrandStatus.ACTIVE)
                .build();
    }
}
