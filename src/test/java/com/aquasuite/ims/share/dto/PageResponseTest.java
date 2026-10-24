package com.aquasuite.ims.share.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageResponseTest {

    @Test
    @DisplayName("from(Page): Maps normal Spring Data Page correctly")
    void from_mapsPageCorrectly() {
        List<String> content = List.of("Apple", "Banana", "Cherry");
        Page<String> page = new PageImpl<>(content, PageRequest.of(1, 3), 10);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.getItems()).containsExactly("Apple", "Banana", "Cherry");
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(3);
        assertThat(response.getTotalElements()).isEqualTo(10L);
        assertThat(response.getTotalPages()).isEqualTo(4);
    }

    @Test
    @DisplayName("from(null): Rejects null page")
    void from_rejectsNullPage() {
        assertThatThrownBy(() -> PageResponse.from(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pageData must not be null");
    }

    @Test
    @DisplayName("from(Page, converter): Transforms elements correctly")
    void from_withConverterTransformsElements() {
        List<Integer> numbers = List.of(10, 20, 30);
        Page<Integer> page = new PageImpl<>(numbers, PageRequest.of(0, 3), 3);

        PageResponse<String> response = PageResponse.from(page, num -> "NUM-" + num);

        assertThat(response.getItems()).containsExactly("NUM-10", "NUM-20", "NUM-30");
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(3);
        assertThat(response.getTotalElements()).isEqualTo(3L);
        assertThat(response.getTotalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("from(null, converter): Rejects null page")
    void from_withConverterRejectsNullPage() {
        assertThatThrownBy(() -> PageResponse.from(null, Object::toString))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pageData must not be null");
    }

    @Test
    @DisplayName("from(Page, null): Throws NullPointerException when converter is null")
    void from_throwsWhenConverterIsNull() {
        Page<String> page = new PageImpl<>(List.of("A"));

        assertThatThrownBy(() -> PageResponse.from(page, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("converter must not be null");
    }

    @Test
    @DisplayName("of(): Handles manual constructor with null items gracefully")
    void of_handlesNullItemsGracefully() {
        PageResponse<String> response = PageResponse.of(null, 0, 10, 0, 0);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(10);
    }
}
