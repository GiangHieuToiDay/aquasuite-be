package com.aquasuite.ims.module.catalog.internal.mapper;

import com.aquasuite.ims.module.catalog.internal.entity.Category;
import com.aquasuite.ims.module.catalog.internal.payload.res.CategoryPageResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.CategoryResponse;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Objects;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface CategoryMapper {

    @Mapping(source = "categoryId", target = "id")
    @Mapping(source = "categoryCode", target = "code")
    @Mapping(source = "categoryName", target = "name")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "parent.categoryId", target = "parentId")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    CategoryResponse mapCategory(Category category);

    default CategoryResponse toResponse(Category category) {
        Objects.requireNonNull(category, "category must not be null");
        return mapCategory(category);
    }

    default CategoryPageResponse toPageResponse(Page<Category> page) {
        Objects.requireNonNull(page, "page must not be null");
        List<CategoryResponse> items = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return CategoryPageResponse.builder()
                .items(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
