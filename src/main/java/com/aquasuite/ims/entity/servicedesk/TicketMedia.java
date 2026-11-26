package com.aquasuite.ims.entity.servicedesk;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.io.Serializable;

/**
 * Cấu trúc thông tin file đính kèm (ảnh hoặc video) của yêu cầu bảo hành (UC-47),
 * được lưu trong cột JSONB {@code media} của bảng {@code warranty_tickets}.
 */
@Builder
public record TicketMedia(
        @NotBlank(message = "Loại media không được để trống")
        @Size(max = 20, message = "Loại media không được vượt quá 20 ký tự")
        String type,
        @NotBlank(message = "URL media không được để trống")
        @Size(max = 2048, message = "URL media không được vượt quá 2048 ký tự")
        @Pattern(regexp = "^https?://[^\\s]+$", message = "URL media không hợp lệ")
        String url,
        @Size(max = 255, message = "publicId không được vượt quá 255 ký tự")
        String publicId,
        @Size(max = 100, message = "contentType không được vượt quá 100 ký tự")
        String contentType,
        @Min(value = 0, message = "Kích thước file không được âm")
        @Max(value = 20971520L, message = "Kích thước file vượt quá giới hạn")
        long sizeBytes
) implements Serializable {
}

