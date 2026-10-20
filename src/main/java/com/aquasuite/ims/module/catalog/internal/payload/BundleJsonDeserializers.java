package com.aquasuite.ims.module.catalog.internal.payload;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Bộ đọc số JSON dùng chung cho item quantity, expectedVersion và packagePrice.
 * Luồng: Jackson đọc token đúng kiểu trước Bean Validation; sai kiểu dẫn tới HTTP 400 SYS_002.
 * Không tự chuyển chuỗi thành số hoặc cắt phần thập phân của quantity/version.
 */
public final class BundleJsonDeserializers {
    private BundleJsonDeserializers() {}

    /**
     * Đọc quantity dưới dạng số nguyên JSON trong giới hạn Integer.
     */
    public static class StrictInteger
            extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(
                JsonParser parser,
                DeserializationContext context)
                throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
                return context.reportInputMismatch(Integer.class, "Expected an integer JSON number");
            return parser.getIntValue();
        }
    }

    /**
     * Đọc expectedVersion dưới dạng số nguyên JSON trong giới hạn Long.
     */
    public static class StrictLong extends JsonDeserializer<Long> {
        @Override
        public Long deserialize(
                JsonParser parser,
                DeserializationContext context)
                throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
                return context.reportInputMismatch(Long.class, "Expected an integer JSON number");
            return parser.getLongValue();
        }
    }

    /**
     * Đọc tiền bằng BigDecimal từ số JSON; giới hạn/độ chính xác được validation kiểm sau đó.
     */
    public static class StrictMoney
            extends JsonDeserializer<BigDecimal> {
        @Override
        public BigDecimal deserialize(
                JsonParser parser,
                DeserializationContext context)
                throws IOException {
            if (!parser.currentToken().isNumeric())
                return context.reportInputMismatch(BigDecimal.class, "Expected a JSON number");
            return parser.getDecimalValue();
        }
    }
}
