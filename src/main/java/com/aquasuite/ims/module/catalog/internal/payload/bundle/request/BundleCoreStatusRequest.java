package com.aquasuite.ims.module.catalog.internal.payload.bundle.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.validation.constraints.*;

import java.io.IOException;

/**
 * Body bật/tắt combo nội bộ: ACTIVE/INACTIVE và expectedVersion.
 * Luồng: Jackson kiểm kiểu/status → service khóa và kiểm version → đổi Product.status.
 * Không dùng DTO này để xóa hoặc publish; các thao tác đó có contract riêng.
 */
public record BundleCoreStatusRequest(
        @JsonDeserialize(using = BundleJsonDeserializers.StrictLong.class) @NotNull @PositiveOrZero
                Long expectedVersion,
        @JsonDeserialize(using = StrictStatus.class) @NotNull Status productStatus) {
    /**
     * Các trạng thái client được đổi qua endpoint status; không gồm DELETED hoặc publication.
     */
    public enum Status {
        ACTIVE,
        INACTIVE
    }

    /**
     * Chỉ nhận chuỗi ACTIVE/INACTIVE; không chuyển số hoặc giá trị lạ thành enum.
     */
    public static class StrictStatus extends JsonDeserializer<Status> {
        @Override
        public Status deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING))
                return context.reportInputMismatch(Status.class, "Expected ACTIVE or INACTIVE");
            try {
                return Status.valueOf(parser.getText());
            } catch (IllegalArgumentException ex) {
                return context.reportInputMismatch(Status.class, "Expected ACTIVE or INACTIVE");
            }
        }
    }

    @JsonAnySetter
    public void unknown(String key, Object value) {
        throw new IllegalArgumentException("Unknown field: " + key);
    }
}
