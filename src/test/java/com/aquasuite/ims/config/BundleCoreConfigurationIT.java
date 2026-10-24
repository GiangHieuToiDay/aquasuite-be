package com.aquasuite.ims.config;

import static org.assertj.core.api.Assertions.*;

import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

class BundleCoreConfigurationIT extends BundleCoreTestSupport {
    @Autowired Environment env;

    @Test
    void isolatedTestProfile() {
        assertThat(env.getActiveProfiles()).containsExactly("test");
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo(DB.getJdbcUrl());
    }
}
