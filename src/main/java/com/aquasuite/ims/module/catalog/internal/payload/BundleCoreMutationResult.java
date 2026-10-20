package com.aquasuite.ims.module.catalog.internal.payload;

import jakarta.validation.constraints.*;

import java.util.*;

/**
 * Response gọn của đổi trạng thái/xóa mềm: ID, trạng thái và version mới của combo.
 * Client dùng version trả về cho thao tác tiếp theo hoặc retry DELETE; không cần tải lại toàn bộ items.
 */
public record BundleCoreMutationResult(
        UUID bundleId, String productStatus, String publicationStatus, Long version) {}
