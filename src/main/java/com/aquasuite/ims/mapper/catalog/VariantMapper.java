package com.aquasuite.ims.mapper.catalog;

import com.aquasuite.ims.entity.catalog.ProductVariant;
import com.aquasuite.ims.dto.catalog.response.VariantResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VariantMapper {
    @Mapping(target = "id", source = "variantId")
    VariantResponse toResponse(ProductVariant entity);
}
