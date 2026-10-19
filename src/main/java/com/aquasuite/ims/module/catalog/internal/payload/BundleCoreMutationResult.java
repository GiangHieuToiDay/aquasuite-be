package com.aquasuite.ims.module.catalog.internal.payload;

import jakarta.validation.constraints.*;

import java.util.*;

public record BundleCoreMutationResult(
        UUID bundleId, String productStatus, String publicationStatus, Long version) {}
