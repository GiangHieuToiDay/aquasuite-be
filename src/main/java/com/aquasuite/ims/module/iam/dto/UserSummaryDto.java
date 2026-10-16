package com.aquasuite.ims.module.iam.dto;

import lombok.Builder;

/**
 * DTO công khai đại diện cho thông tin người dùng được chia sẻ giữa các module.
 */
@Builder
public record UserSummaryDto(
        Long id,
        String username,
        String email,
        String phone,
        String avatar,
        String status
) {}
