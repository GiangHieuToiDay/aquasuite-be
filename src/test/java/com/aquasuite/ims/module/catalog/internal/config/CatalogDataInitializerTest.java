package com.aquasuite.ims.module.catalog.internal.config;

import com.aquasuite.ims.module.catalog.internal.entity.Category;
import com.aquasuite.ims.module.catalog.internal.enums.CategoryStatus;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CatalogDataInitializerTest {
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final BrandRepository brands = mock(BrandRepository.class);
    private final CatalogDataInitializer initializer = new CatalogDataInitializer(categories, brands,
            mock(ProductRepository.class), mock(ProductVariantRepository.class), mock(ProductImageRepository.class));

    @BeforeEach
    void existingReferenceData() {
        when(categories.count()).thenReturn(7L);
        when(brands.count()).thenReturn(7L);
    }

    @Test
    void createsMissingComboEvenWhenOtherCategoriesExist() {
        initializer.run();

        var saved = ArgumentCaptor.forClass(Category.class);
        verify(categories).save(saved.capture());
        assertThat(saved.getValue().getCategoryCode()).isEqualTo("709");
        assertThat(saved.getValue().getCategoryName()).isEqualTo("Combo");
        assertThat(saved.getValue().getStatus()).isEqualTo(CategoryStatus.ACTIVE);
        verify(categories, never()).saveAll(any());
    }

    @Test
    void renamesLegacyCategoryWithoutReplacingItsIdentity() {
        Category legacy = new Category();
        var id = java.util.UUID.randomUUID();
        legacy.setCategoryId(id);
        legacy.setCategoryCode("COMBO");
        legacy.setCategoryName("Combo");
        when(categories.findByCategoryCodeIgnoreCase("COMBO"))
                .thenReturn(java.util.Optional.of(legacy));

        initializer.run();

        verify(categories).save(legacy);
        assertThat(legacy.getCategoryId()).isEqualTo(id);
        assertThat(legacy.getCategoryCode()).isEqualTo("709");
        assertThat(legacy.getCategoryName()).isEqualTo("Combo");
    }

    @Test
    void refusesToOverwriteExistingNumericCategory() {
        when(categories.findByCategoryCodeIgnoreCase("COMBO"))
                .thenReturn(java.util.Optional.of(new Category()));
        when(categories.existsByCategoryCodeIgnoreCase("709")).thenReturn(true);

        assertThatThrownBy(() -> initializer.run()).isInstanceOf(IllegalStateException.class);
        verify(categories, never()).save(any());
    }

    @Test
    void leavesExistingComboUnchanged() {
        when(categories.existsByCategoryCodeIgnoreCase("709")).thenReturn(true);

        initializer.run();

        verify(categories, never()).save(any());
    }

    @Test
    void createsReferenceCategoriesAndComboOnEmptyDatabase() {
        when(categories.count()).thenReturn(0L);

        initializer.run();

        verify(categories).saveAll(any());
        verify(categories).save(argThat(category -> "709".equals(category.getCategoryCode())));
    }
}
