package com.aquasuite.ims;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.api.Test;

class AquasuiteBeApplicationTests extends BundleCoreTestSupport {
    @Test
    void contextLoads() {
        assertThat(service).isNotNull();
        assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
    }
}
