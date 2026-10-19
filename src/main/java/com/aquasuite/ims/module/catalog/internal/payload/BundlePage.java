package com.aquasuite.ims.module.catalog.internal.payload;

import jakarta.validation.constraints.*;

import java.util.*;

public record BundlePage<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
