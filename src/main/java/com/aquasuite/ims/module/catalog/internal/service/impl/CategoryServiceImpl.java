package com.aquasuite.ims.module.catalog.internal.service.impl;

import com.aquasuite.ims.module.catalog.internal.entity.Category;
import com.aquasuite.ims.module.catalog.internal.entity.enums.CategoryStatus;
import com.aquasuite.ims.module.catalog.internal.error.CatalogConstraintExceptionTranslator;
import com.aquasuite.ims.module.catalog.internal.error.CatalogErrorCode;
import com.aquasuite.ims.module.catalog.internal.mapper.CategoryMapper;
import com.aquasuite.ims.module.catalog.internal.payload.request.CreateCategoryRequest;
import com.aquasuite.ims.module.catalog.internal.payload.request.UpdateCategoryRequest;
import com.aquasuite.ims.module.catalog.internal.payload.response.CategoryPageResponse;
import com.aquasuite.ims.module.catalog.internal.payload.response.CategoryResponse;
import com.aquasuite.ims.module.catalog.internal.repository.CategoryRepository;
import com.aquasuite.ims.module.catalog.internal.repository.projection.CategoryHierarchyProjection;
import com.aquasuite.ims.module.catalog.internal.service.CategoryService;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_]*$");
    private static final Map<String, String> ALLOWED_SORT_FIELDS = Map.of(
            "code", "categoryCode",
            "name", "categoryName",
            "status", "status",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt"
    );

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(
            @org.springframework.beans.factory.annotation.Autowired(required = false) CategoryRepository categoryRepository,
            CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryPageResponse listCategories(int page, int size, String search, String status, String sortBy, String direction) {
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
                new Sort.Order(Sort.Direction.ASC, "categoryId")
        );
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Category> pageResult = categoryRepository.searchCategories(searchTerm, statusFilter, pageable);
        return categoryMapper.toPageResponse(pageResult);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryDetail(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.CATEGORY_NOT_FOUND));
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        // Exact advisory transaction lock as first statement
        categoryRepository.acquireCategoryAdvisoryLock();

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
                throw new AppException(CatalogErrorCode.CATEGORY_STATUS_INVALID);
            }
            status = s;
        }

        // 5. Parent resolution & cycle / corrupt hierarchy checks
        Category parent = null;
        if (request.getParentId() != null) {
            parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AppException(CatalogErrorCode.CATEGORY_PARENT_NOT_FOUND));
            validateHierarchyAncestry(null, request.getParentId());
        }

        // 6. Uniqueness checks
        if (categoryRepository.existsByCategoryCodeIgnoreCase(normalizedCode)) {
            throw new AppException(CatalogErrorCode.CATEGORY_CODE_DUPLICATE);
        }
        if (request.getParentId() == null) {
            if (categoryRepository.existsRootNameDuplicate(normalizedName, null)) {
                throw new AppException(CatalogErrorCode.CATEGORY_NAME_DUPLICATE);
            }
        } else {
            if (categoryRepository.existsChildNameDuplicate(request.getParentId(), normalizedName, null)) {
                throw new AppException(CatalogErrorCode.CATEGORY_NAME_DUPLICATE);
            }
        }

        // 7. Persist
        Category category = new Category();
        category.setCategoryId(UUID.randomUUID());
        category.setCategoryCode(normalizedCode);
        category.setCategoryName(normalizedName);
        category.setDescription(normalizedDescription);
        category.setStatus(CategoryStatus.valueOf(status));
        category.setParent(parent);

        try {
            category = categoryRepository.saveAndFlush(category);
            log.info("Created category '{}' ({})", category.getCategoryCode(), category.getCategoryId());
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintExceptionTranslator.translateCategoryCreateOrUpdateException(ex);
        }

        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID categoryId, UpdateCategoryRequest request) {
        // Exact advisory transaction lock as first statement
        categoryRepository.acquireCategoryAdvisoryLock();

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.CATEGORY_NOT_FOUND));

        // 1. Name normalization & validation
        String normalizedName = normalizeAndValidateName(request.getName());

        // 2. Status validation (required in update)
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new AppException(CatalogErrorCode.CATEGORY_STATUS_INVALID);
        }
        String status = request.getStatus().trim();
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new AppException(CatalogErrorCode.CATEGORY_STATUS_INVALID);
        }

        // 3. Description normalization & validation
        String normalizedDescription = cleanDescription(request.getDescription());

        // 4. Parent validation & hierarchy / cycle checks
        UUID prospectiveParentId = request.getParentId();
        Category prospectiveParent = null;
        if (prospectiveParentId != null) {
            if (prospectiveParentId.equals(categoryId)) {
                throw new AppException(CatalogErrorCode.CATEGORY_SELF_PARENT);
            }
            prospectiveParent = categoryRepository.findById(prospectiveParentId)
                    .orElseThrow(() -> new AppException(CatalogErrorCode.CATEGORY_PARENT_NOT_FOUND));
            validateHierarchyAncestry(categoryId, prospectiveParentId);
        }

        // 5. Name uniqueness check
        if (prospectiveParentId == null) {
            if (categoryRepository.existsRootNameDuplicate(normalizedName, categoryId)) {
                throw new AppException(CatalogErrorCode.CATEGORY_NAME_DUPLICATE);
            }
        } else {
            if (categoryRepository.existsChildNameDuplicate(prospectiveParentId, normalizedName, categoryId)) {
                throw new AppException(CatalogErrorCode.CATEGORY_NAME_DUPLICATE);
            }
        }

        // 6. Apply updates (code is immutable and excluded)
        category.setCategoryName(normalizedName);
        category.setStatus(CategoryStatus.valueOf(status));
        category.setDescription(normalizedDescription);
        category.setParent(prospectiveParent);

        try {
            category = categoryRepository.saveAndFlush(category);
            log.info("Updated category '{}' ({})", category.getCategoryCode(), category.getCategoryId());
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintExceptionTranslator.translateCategoryCreateOrUpdateException(ex);
        }

        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public void deleteCategory(UUID categoryId) {
        // Exact advisory transaction lock as first statement
        categoryRepository.acquireCategoryAdvisoryLock();

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.CATEGORY_NOT_FOUND));

        if (categoryRepository.existsByParent_CategoryId(categoryId)) {
            log.warn("Cannot delete category '{}' ({}): has children", category.getCategoryCode(), categoryId);
            throw new AppException(CatalogErrorCode.CATEGORY_HAS_CHILDREN);
        }

        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
            log.info("Deleted category '{}' ({})", category.getCategoryCode(), categoryId);
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintExceptionTranslator.translateCategoryDeleteException(ex);
        }
    }

    private void validateHierarchyAncestry(UUID targetCategoryId, UUID startParentId) {
        Set<UUID> visited = new HashSet<>();
        UUID current = startParentId;
        long maxAllowed = categoryRepository.count() + 1;

        while (current != null) {
            if (targetCategoryId != null && current.equals(targetCategoryId)) {
                throw new AppException(CatalogErrorCode.CATEGORY_CYCLE);
            }
            if (visited.contains(current)) {
                throw new AppException(CatalogErrorCode.CATEGORY_HIERARCHY_CONFLICT);
            }
            visited.add(current);
            if (visited.size() > maxAllowed) {
                throw new AppException(CatalogErrorCode.CATEGORY_HIERARCHY_CONFLICT);
            }
            CategoryHierarchyProjection proj = categoryRepository.findHierarchyProjectionById(current)
                    .orElseThrow(() -> new AppException(CatalogErrorCode.CATEGORY_HIERARCHY_CONFLICT));
            current = proj.getParentId();
        }
    }

    private String normalizeAndValidateCode(String rawCode) {
        if (rawCode == null) {
            throw new AppException(CatalogErrorCode.INVALID_CATEGORY_CODE);
        }
        String trimmed = rawCode.trim();
        if (trimmed.isEmpty()) {
            throw new AppException(CatalogErrorCode.INVALID_CATEGORY_CODE);
        }
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if (normalized.length() > 50 || !CODE_PATTERN.matcher(normalized).matches()) {
            throw new AppException(CatalogErrorCode.INVALID_CATEGORY_CODE);
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
