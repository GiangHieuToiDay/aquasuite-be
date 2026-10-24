package com.aquasuite.ims.module.iam.internal.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(MapperTestConfiguration.class)
class MapperSpringIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Test
    @DisplayName("Spring Context: exactly one bean per mapper interface and correctly wired")
    void testMapperBeansInSpringContext() {
        assertThat(permissionMapper).isNotNull();
        assertThat(roleMapper).isNotNull();

        String[] permissionBeans = applicationContext.getBeanNamesForType(PermissionMapper.class);
        assertThat(permissionBeans).hasSize(1);
        assertThat(applicationContext.getBean(PermissionMapper.class)).isNotNull();

        String[] roleBeans = applicationContext.getBeanNamesForType(RoleMapper.class);
        assertThat(roleBeans).hasSize(1);
        assertThat(applicationContext.getBean(RoleMapper.class)).isNotNull();
    }
}
