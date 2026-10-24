package com.aquasuite.ims.module.catalog.internal.mapper;

import com.aquasuite.ims.module.catalog.internal.entity.Brand;
import com.aquasuite.ims.module.catalog.internal.payload.res.BrandResponse;
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
public interface BrandMapper {

    @Mapping(source = "brandId", target = "id")
    @Mapping(source = "brandCode", target = "code")
    @Mapping(source = "brandName", target = "name")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "status", target = "status")
    BrandResponse mapBrand(Brand brand);

    default BrandResponse toResponse(Brand brand) {
        Objects.requireNonNull(brand, "brand must not be null");
        return mapBrand(brand);
    }
}
