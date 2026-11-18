package com.aquasuite.ims.module.servicedesk.internal.mapper;

import com.aquasuite.ims.module.servicedesk.internal.entity.WarrantyPolicy;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.UpsertWarrantyPolicyRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyPolicyResponse;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WarrantyPolicyMapper {

    @Mapping(source = "warrantyPolicyId", target = "policyId")
    @Mapping(target = "brandName", ignore = true)
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "variantReferenceCount", ignore = true)
    WarrantyPolicyResponse toResponse(WarrantyPolicy entity);

    @Mapping(target = "warrantyPolicyId", ignore = true)
    @Mapping(target = "policyCode", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void update(UpsertWarrantyPolicyRequest request, @MappingTarget WarrantyPolicy entity);
}
