package com.aquasuite.ims.module.crm.internal.mapper;

import com.aquasuite.ims.module.crm.internal.entity.CustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerCreditProfileRequestResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CustomerCreditProfileRequestMapper {

    @Mapping(target = "appliedImmediately", ignore = true)
    @Mapping(target = "qualifiedOrderCount", ignore = true)
    @Mapping(target = "identityEvidenceProvided", expression = "java(hasEvidence(entity))")
    CustomerCreditProfileRequestResponse toResponse(CustomerCreditProfileRequest entity);

    default boolean hasEvidence(CustomerCreditProfileRequest entity) {
        return entity.getIdentityDocumentNumber() != null
                && entity.getResidentialAddress() != null
                && entity.getWorksiteAddress() != null;
    }
}
