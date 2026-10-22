package com.aquasuite.ims.module.iam.internal.mapper;


import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.payload.request.CreateUserAccountRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.UpdateUserAccountRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.RoleOptionResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.UserAccountResponse;
import org.mapstruct.*;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserAccountMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "provider", ignore = true)
    @Mapping(target = "providerId", ignore = true)
    @Mapping(target = "verified", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "username", source = "username", qualifiedByName = "requiredTrim")
    @Mapping(target = "email", source = "email", qualifiedByName = "normalizedEmail")
    @Mapping(target = "phone", source = "phone", qualifiedByName = "requiredTrim")
    @Mapping(target = "avatarUrl", source = "avatarUrl", qualifiedByName = "blankToNull")
    User toNewUser(CreateUserAccountRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "provider", ignore = true)
    @Mapping(target = "providerId", ignore = true)
    @Mapping(target = "verified", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "username", source = "username", qualifiedByName = "requiredTrim")
    @Mapping(target = "email", source = "email", qualifiedByName = "normalizedEmail")
    @Mapping(target = "phone", source = "phone", qualifiedByName = "requiredTrim")
    @Mapping(target = "avatarUrl", source = "avatarUrl", qualifiedByName = "blankToNull")
    void updateUser(UpdateUserAccountRequest request, @MappingTarget User user);

    @Mapping(source = "roleId", target = "id")
    @Mapping(source = "roleName", target = "code")
    RoleOptionResponse toRoleOption(Role role);

    List<RoleOptionResponse> toRoleOptions(Set<Role> roles);

    @Mapping(target = "verified", source = "user.verified")
    @Mapping(target = "currentUser", expression = "java(user.getId().equals(currentUserId))")
    UserAccountResponse toResponse(User user, UUID currentUserId);

    @Named("requiredTrim")
    default String requiredTrim(String value) {
        return value == null ? null : value.trim();
    }

    @Named("normalizedEmail")
    default String normalizedEmail(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    @Named("blankToNull")
    default String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
