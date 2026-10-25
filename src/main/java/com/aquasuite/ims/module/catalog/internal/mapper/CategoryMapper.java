package com.aquasuite.ims.module.catalog.internal.mapper;

import com.aquasuite.ims.module.catalog.internal.entity.Category;
import com.aquasuite.ims.module.catalog.internal.payload.res.CategoryResponse;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
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
}
