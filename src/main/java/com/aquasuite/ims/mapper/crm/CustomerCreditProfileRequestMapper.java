package com.aquasuite.ims.mapper.crm;

import com.aquasuite.ims.entity.crm.CustomerCreditProfileRequest;
import com.aquasuite.ims.dto.crm.response.CustomerCreditProfileRequestResponse;
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
        return entity.getIdentityDocumentObjectKey() != null
                && entity.getResidentialAddress() != null
                && entity.getWorksiteAddress() != null;
    }
}
