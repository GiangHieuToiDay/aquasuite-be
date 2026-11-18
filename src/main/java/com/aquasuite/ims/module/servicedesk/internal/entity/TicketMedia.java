package com.aquasuite.ims.module.servicedesk.internal.entity;

import lombok.Builder;

import java.io.Serializable;

/**
 * Cấu trúc thông tin file đính kèm (ảnh hoặc video) của yêu cầu bảo hành (UC-47),
 * được lưu trong cột JSONB {@code media} của bảng {@code warranty_tickets}.
 */
@Builder
public record TicketMedia(
        String type,
        String url,
        String publicId,
        String contentType,
        long sizeBytes
) implements Serializable {
}

