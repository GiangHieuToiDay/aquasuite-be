package com.aquasuite.ims.module.catalog.internal.mapper;

import com.aquasuite.ims.module.catalog.internal.entity.ProductVariant;
import com.aquasuite.ims.module.catalog.internal.payload.res.VariantResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VariantMapper {
    @Mapping(target = "id", source = "variantId")
    VariantResponse toResponse(ProductVariant entity);
}
