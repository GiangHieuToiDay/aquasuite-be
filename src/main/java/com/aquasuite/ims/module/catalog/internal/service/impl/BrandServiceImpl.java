package com.aquasuite.ims.module.catalog.internal.service.impl;

import com.aquasuite.ims.module.catalog.internal.entity.Brand;
import com.aquasuite.ims.module.catalog.internal.enums.BrandStatus;
import com.aquasuite.ims.module.catalog.internal.error.CatalogConstraintExceptionTranslator;
import com.aquasuite.ims.module.catalog.internal.error.CatalogErrorCode;
import com.aquasuite.ims.module.catalog.internal.mapper.BrandMapper;
import com.aquasuite.ims.module.catalog.internal.payload.req.CreateBrandRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateBrandRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.BrandResponse;
import com.aquasuite.ims.module.catalog.internal.repository.BrandRepository;
import com.aquasuite.ims.module.catalog.internal.service.BrandService;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import com.aquasuite.ims.share.dto.PageQuery;
import com.aquasuite.ims.share.dto.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class BrandServiceImpl implements BrandService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_]*$");
    private static final Map<String, String> ALLOWED_SORT_FIELDS = Map.of(
            "code", "brandCode",
            "name", "brandName",
            "status", "status"
    );

    private final BrandRepository brandRepository;
    private final BrandMapper brandMapper;

    public BrandServiceImpl(
            @org.springframework.beans.factory.annotation.Autowired(required = false) BrandRepository brandRepository,
            BrandMapper brandMapper) {
        this.brandRepository = brandRepository;
        this.brandMapper = brandMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BrandResponse> listBrands(PageQuery pageQuery, String search, String status) {
        int page = pageQuery.getPage();
        int size = pageQuery.getSize();
        String sortBy = pageQuery.getSortBy();
        String direction = pageQuery.getDirection();
        if (page < 0) {
            log.warn("Invalid page number: {}", page);
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        }
        if (size < 1 || size > 100) {
            log.warn("Invalid page size: {}", size);
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        }

        String sortKey = (sortBy == null || sortBy.isBlank()) ? "code" : sortBy.trim();
        String sortProperty = ALLOWED_SORT_FIELDS.get(sortKey);
        if (sortProperty == null) {
            log.warn("Invalid sortBy field: {}", sortBy);
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        }

        String dir = (direction == null || direction.isBlank()) ? "asc" : direction.trim().toLowerCase(Locale.ROOT);
        if (!"asc".equals(dir) && !"desc".equals(dir)) {
            log.warn("Invalid sort direction: {}", direction);
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        }

        String statusFilter = null;
        if (status != null && !status.isBlank()) {
            String trimmedStatus = status.trim();
            if (!"ACTIVE".equals(trimmedStatus) && !"INACTIVE".equals(trimmedStatus)) {
                log.warn("Invalid status filter: {}", status);
                throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
            }
            statusFilter = trimmedStatus;
        }

        String searchTerm = (search == null || search.isBlank()) ? null : search.trim();

        Sort sort = Sort.by(
                new Sort.Order("desc".equals(dir) ? Sort.Direction.DESC : Sort.Direction.ASC, sortProperty),
                new Sort.Order(Sort.Direction.ASC, "brandId")
        );
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Brand> pageResult = brandRepository.searchBrands(searchTerm, statusFilter, pageable);
        return PageResponse.from(pageResult, brandMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public BrandResponse getBrandDetail(UUID brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.BRAND_NOT_FOUND));
        return brandMapper.toResponse(brand);
    }

    @Override
    @Transactional
    public BrandResponse createBrand(CreateBrandRequest request) {
        // 1. Code normalization & validation
        String normalizedCode = normalizeAndValidateCode(request.getCode());

        // 2. Name normalization & validation
        String normalizedName = normalizeAndValidateName(request.getName());

        // 3. Description normalization & validation
        String normalizedDescription = cleanDescription(request.getDescription());

        // 4. Status validation
        String status = "ACTIVE";
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String s = request.getStatus().trim();
            if (!"ACTIVE".equals(s) && !"INACTIVE".equals(s)) {
                throw new AppException(CatalogErrorCode.BRAND_STATUS_INVALID);
            }
            status = s;
        }

        // 5. Uniqueness checks
        if (brandRepository.existsByBrandCodeIgnoreCase(normalizedCode)) {
            throw new AppException(CatalogErrorCode.BRAND_CODE_DUPLICATE);
        }
        if (brandRepository.existsBrandNameDuplicate(normalizedName, null)) {
            throw new AppException(CatalogErrorCode.BRAND_NAME_DUPLICATE);
        }

        // 6. Persist
        Brand brand = new Brand();
        brand.setBrandId(UUID.randomUUID());
        brand.setBrandCode(normalizedCode);
        brand.setBrandName(normalizedName);
        brand.setDescription(normalizedDescription);
        brand.setStatus(BrandStatus.valueOf(status));

        try {
            brand = brandRepository.saveAndFlush(brand);
            log.info("Created brand '{}' ({})", brand.getBrandCode(), brand.getBrandId());
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintExceptionTranslator.translateBrandCreateOrUpdateException(ex);
        }

        return brandMapper.toResponse(brand);
    }

    @Override
    @Transactional
    public BrandResponse updateBrand(UUID brandId, UpdateBrandRequest request) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.BRAND_NOT_FOUND));

        // 1. Name normalization & validation
        String normalizedName = normalizeAndValidateName(request.getName());

        // 2. Status validation (required in update)
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new AppException(CatalogErrorCode.BRAND_STATUS_INVALID);
        }
        String status = request.getStatus().trim();
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new AppException(CatalogErrorCode.BRAND_STATUS_INVALID);
        }

        // 3. Description normalization & validation
        String normalizedDescription = cleanDescription(request.getDescription());

        // 4. Name uniqueness check
        if (brandRepository.existsBrandNameDuplicate(normalizedName, brandId)) {
            throw new AppException(CatalogErrorCode.BRAND_NAME_DUPLICATE);
        }

        // 5. Apply updates (code is immutable and excluded)
        brand.setBrandName(normalizedName);
        brand.setStatus(BrandStatus.valueOf(status));
        brand.setDescription(normalizedDescription);

        try {
            brand = brandRepository.saveAndFlush(brand);
            log.info("Updated brand '{}' ({})", brand.getBrandCode(), brand.getBrandId());
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintExceptionTranslator.translateBrandCreateOrUpdateException(ex);
        }

        return brandMapper.toResponse(brand);
    }

    @Override
    @Transactional
    public void deleteBrand(UUID brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.BRAND_NOT_FOUND));

        try {
            brandRepository.delete(brand);
            brandRepository.flush();
            log.info("Deleted brand '{}' ({})", brand.getBrandCode(), brandId);
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintExceptionTranslator.translateBrandDeleteException(ex);
        }
    }

    private String normalizeAndValidateCode(String rawCode) {
        if (rawCode == null) {
            throw new AppException(CatalogErrorCode.INVALID_BRAND_CODE);
        }
        String trimmed = rawCode.trim();
        if (trimmed.isEmpty()) {
            throw new AppException(CatalogErrorCode.INVALID_BRAND_CODE);
        }
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if (normalized.length() > 50 || !CODE_PATTERN.matcher(normalized).matches()) {
            throw new AppException(CatalogErrorCode.INVALID_BRAND_CODE);
        }
        return normalized;
    }

    private String normalizeAndValidateName(String rawName) {
        if (rawName == null) {
            throw new AppException(CommonErrorCode.VALIDATION_ERROR);
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            throw new AppException(CommonErrorCode.VALIDATION_ERROR);
        }
        return trimmed;
    }

    private String cleanDescription(String rawDescription) {
        if (rawDescription == null) {
            return null;
        }
        String trimmed = rawDescription.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 5000) {
            throw new AppException(CommonErrorCode.VALIDATION_ERROR);
        }
        return trimmed;
    }
}
