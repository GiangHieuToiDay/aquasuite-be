package com.aquasuite.ims.module.catalog.internal.mapper;

import com.aquasuite.ims.module.catalog.internal.entity.Brand;
import com.aquasuite.ims.module.catalog.internal.entity.Category;
import com.aquasuite.ims.module.catalog.internal.enums.BrandStatus;
import com.aquasuite.ims.module.catalog.internal.enums.CategoryStatus;
import com.aquasuite.ims.module.catalog.internal.payload.res.BrandResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.CategoryResponse;
import com.aquasuite.ims.share.dto.PageResponse;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CatalogMapperTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private BrandMapper brandMapper;

    @Test
    @DisplayName("CategoryMapper maps all fields accurately including parentId and timestamps")
    void testCategoryMapperFullMapping() {
        UUID parentId = UUID.randomUUID();
        Category parent = new Category();
        parent.setCategoryId(parentId);

        UUID catId = UUID.randomUUID();
        Instant now = Instant.now();
        Category cat = new Category();
        cat.setCategoryId(catId);
        cat.setCategoryCode("CAT_PIPE");
        cat.setCategoryName("Water Pipes");
        cat.setDescription("High quality pipes");
        cat.setStatus(CategoryStatus.ACTIVE);
        cat.setParent(parent);
        cat.setCreatedAt(now);
        cat.setUpdatedAt(now.plusSeconds(60));

        CategoryResponse res = categoryMapper.toResponse(cat);

        assertThat(res).isNotNull();
        assertThat(res.getId()).isEqualTo(catId);
        assertThat(res.getCode()).isEqualTo("CAT_PIPE");
        assertThat(res.getName()).isEqualTo("Water Pipes");
        assertThat(res.getDescription()).isEqualTo("High quality pipes");
        assertThat(res.getStatus()).isEqualTo("ACTIVE");
        assertThat(res.getParentId()).isEqualTo(parentId);
        assertThat(res.getCreatedAt()).isEqualTo(now);
        assertThat(res.getUpdatedAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    @DisplayName("CategoryMapper handles null parent and null description")
    void testCategoryMapperNullParentAndDescription() {
        Category cat = new Category();
        cat.setCategoryId(UUID.randomUUID());
        cat.setCategoryCode("ROOT");
        cat.setCategoryName("Root Category");
        cat.setStatus(CategoryStatus.ACTIVE);

        CategoryResponse res = categoryMapper.toResponse(cat);

        assertThat(res.getParentId()).isNull();
        assertThat(res.getDescription()).isNull();
    }

    @Test
    @DisplayName("CategoryMapper rejects null category")
    void testCategoryMapperRejectsNull() {
        assertThatThrownBy(() -> categoryMapper.toResponse(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("category must not be null");
    }

    @Test
    @DisplayName("CategoryMapper maps Page properly and handles empty page")
    void testCategoryMapperPage() {
        Page<Category> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        PageResponse<CategoryResponse> pageRes = PageResponse.from(emptyPage, categoryMapper::toResponse);

        assertThat(pageRes.getItems()).isEmpty();
        assertThat(pageRes.getPage()).isEqualTo(0);
        assertThat(pageRes.getSize()).isEqualTo(20);
        assertThat(pageRes.getTotalElements()).isEqualTo(0);
        assertThat(pageRes.getTotalPages()).isEqualTo(0);

        assertThatThrownBy(() -> PageResponse.from(null, categoryMapper::toResponse))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("BrandMapper maps all fields accurately")
    void testBrandMapperFullMapping() {
        UUID brandId = UUID.randomUUID();
        Brand brand = new Brand();
        brand.setBrandId(brandId);
        brand.setBrandCode("TOTO");
        brand.setBrandName("Toto Sanitary");
        brand.setDescription("Japan sanitary ware");
        brand.setStatus(BrandStatus.ACTIVE);

        BrandResponse res = brandMapper.toResponse(brand);

        assertThat(res.getId()).isEqualTo(brandId);
        assertThat(res.getCode()).isEqualTo("TOTO");
        assertThat(res.getName()).isEqualTo("Toto Sanitary");
        assertThat(res.getDescription()).isEqualTo("Japan sanitary ware");
        assertThat(res.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("BrandMapper handles null description and rejects null brand")
    void testBrandMapperNullHandling() {
        Brand brand = new Brand();
        brand.setBrandId(UUID.randomUUID());
        brand.setBrandCode("INAX");
        brand.setBrandName("Inax Sanitary");
        brand.setStatus(BrandStatus.INACTIVE);

        BrandResponse res = brandMapper.toResponse(brand);
        assertThat(res.getDescription()).isNull();

        assertThatThrownBy(() -> brandMapper.toResponse(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("brand must not be null");
    }

    @Test
    @DisplayName("BrandMapper maps Page properly and handles empty page")
    void testBrandMapperPage() {
        Page<Brand> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        PageResponse<BrandResponse> pageRes = PageResponse.from(emptyPage, brandMapper::toResponse);

        assertThat(pageRes.getItems()).isEmpty();
        assertThat(pageRes.getPage()).isEqualTo(0);
        assertThat(pageRes.getSize()).isEqualTo(20);
        assertThat(pageRes.getTotalElements()).isEqualTo(0);
        assertThat(pageRes.getTotalPages()).isEqualTo(0);

        assertThatThrownBy(() -> PageResponse.from(null, brandMapper::toResponse))
                .isInstanceOf(NullPointerException.class);
    }
}
