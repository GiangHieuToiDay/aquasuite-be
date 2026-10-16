package com.aquasuite.ims.module.catalog.internal.config;

import com.aquasuite.ims.module.catalog.internal.entity.Brand;
import com.aquasuite.ims.module.catalog.internal.entity.Category;
import com.aquasuite.ims.module.catalog.internal.entity.enums.BrandStatus;
import com.aquasuite.ims.module.catalog.internal.entity.enums.CategoryStatus;
import com.aquasuite.ims.module.catalog.internal.repository.BrandRepository;
import com.aquasuite.ims.module.catalog.internal.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CatalogDataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    @Override
    public void run(String... args) {
        initCategories();
        initBrands();
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
