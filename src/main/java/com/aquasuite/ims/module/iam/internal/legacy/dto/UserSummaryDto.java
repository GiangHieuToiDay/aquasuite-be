package com.aquasuite.ims.module.iam.internal.legacy.dto;

import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record UserSummaryDto(Long id, String username, String email, String phone,
                             String avatar, String status) {}
