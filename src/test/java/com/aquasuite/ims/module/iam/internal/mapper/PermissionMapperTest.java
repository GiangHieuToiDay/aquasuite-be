package com.aquasuite.ims.module.iam.internal.mapper;

import com.aquasuite.ims.module.iam.internal.entity.Permission;
import com.aquasuite.ims.module.iam.internal.payload.response.PermissionPageResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.PermissionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(MapperTestConfiguration.class)
class PermissionMapperTest {

    @Autowired
    private PermissionMapper permissionMapper;

    @Test
    @DisplayName("toResponse: maps all fields correctly from Permission entity")
    void testToResponse_Success() {
        UUID id = UUID.randomUUID();
        Permission permission = new Permission(id, "ORDER_READ", "Read orders");

        PermissionResponse response = permissionMapper.toResponse(permission);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getCode()).isEqualTo("ORDER_READ");
        assertThat(response.getDescription()).isEqualTo("Read orders");
    }

    @Test
    @DisplayName("toResponse: preserves null description without default or trimming")
    void testToResponse_NullableDescription() {
        UUID id = UUID.randomUUID();
        Permission permission = new Permission(id, "ORDER_WRITE", null);

        PermissionResponse response = permissionMapper.toResponse(permission);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getCode()).isEqualTo("ORDER_WRITE");
        assertThat(response.getDescription()).isNull();
    }

    @Test
    @DisplayName("toResponse: throws NullPointerException on null input")
    void testToResponse_NullInput_ThrowsNullPointerException() {
        assertThatThrownBy(() -> permissionMapper.toResponse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("toResponse: does not mutate entity")
    void testToResponse_EntityNotMutated() {
        UUID id = UUID.randomUUID();
        Permission permission = new Permission(id, "PRODUCT_READ", "Read products");

        permissionMapper.toResponse(permission);

        assertThat(permission.getId()).isEqualTo(id);
        assertThat(permission.getCode()).isEqualTo("PRODUCT_READ");
        assertThat(permission.getDescription()).isEqualTo("Read products");
    }

    @Test
    @DisplayName("toPageResponse: maps content and page metadata in encounter order")
    void testToPageResponse_Success() {
        Permission p1 = new Permission(UUID.randomUUID(), "PERM_A", "Description A");
        Permission p2 = new Permission(UUID.randomUUID(), "PERM_B", null);
        Page<Permission> page = new PageImpl<>(List.of(p1, p2), PageRequest.of(1, 2), 5);

        PermissionPageResponse response = permissionMapper.toPageResponse(page);

        assertThat(response).isNotNull();
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(2);
        assertThat(response.getTotalElements()).isEqualTo(5);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).getCode()).isEqualTo("PERM_A");
        assertThat(response.getItems().get(0).getDescription()).isEqualTo("Description A");
        assertThat(response.getItems().get(1).getCode()).isEqualTo("PERM_B");
        assertThat(response.getItems().get(1).getDescription()).isNull();
    }

    @Test
    @DisplayName("toPageResponse: empty page produces empty items list and correct metadata")
    void testToPageResponse_EmptyPage() {
        Page<Permission> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);

        PermissionPageResponse response = permissionMapper.toPageResponse(page);

        assertThat(response).isNotNull();
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(0);
        assertThat(response.getTotalPages()).isEqualTo(0);
        assertThat(response.getItems()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("toPageResponse: throws NullPointerException on null page input")
    void testToPageResponse_NullPage_ThrowsNullPointerException() {
        assertThatThrownBy(() -> permissionMapper.toPageResponse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("toPageResponse: preserves unmodifiable items list semantics")
    void testToPageResponse_ItemsListUnmodifiable() {
        Permission p = new Permission(UUID.randomUUID(), "PERM_X", "Desc");
        Page<Permission> page = new PageImpl<>(List.of(p), PageRequest.of(0, 5), 1);

        PermissionPageResponse response = permissionMapper.toPageResponse(page);

        assertThatThrownBy(() -> response.getItems().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
