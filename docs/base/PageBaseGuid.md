# Hướng Dẫn Sử Dụng Bộ Hạ Tầng Phân Trang (Pagination Infrastructure) - AquaSuite Backend

Tài liệu này hướng dẫn cách sử dụng bộ hạ tầng phân trang và làm sạch tham số dùng chung (`com.aquasuite.ims.share`) trong toàn bộ các module nghiệp vụ của AquaSuite Backend.

---

## I. Tổng Quan Các Thành Phần

Hạ tầng phân trang được đặt tập trung tại `com.aquasuite.ims.share`, bao gồm 3 thành phần chính:

| Thành phần | Vị trí Package | Vai trò | Cách sử dụng |
|---|---|---|---|
| **`PageQuery`** | `com.aquasuite.ims.share.dto.PageQuery` | DTO base nhận tham số phân trang (`page`, `size`, `sortBy`, `sortDir`) | **Kế thừa (`extends`)** khi có bộ lọc riêng, hoặc dùng trực tiếp. |
| **`PageableUtils`** | `com.aquasuite.ims.share.util.PageableUtils` | Helper làm sạch (sanitize) tham số & trích xuất `Pageable` an toàn | **Gọi hàm `static` trực tiếp**, không cần kế thừa. |
| **`PageResponse<T>`** | `com.aquasuite.ims.share.dto.PageResponse` | DTO generic chuẩn hóa dữ liệu trả về cho client | **Dùng generic trực tiếp** `PageResponse<MyDto>`, không cần tạo class con. |

### Lợi ích cốt lõi:
1. **Chống DoS (Resource Exhaustion)**: Tự động chặn trần `maxSize = 100`. Client truyền `size = 1000000` sẽ được giới hạn an toàn về 100.
2. **Chống SQL Injection & Hibernate Crash**: Whitelist các trường được phép sort. Nếu client cố tình truyền injection hoặc trường không tồn tại trong entity, hệ thống tự động fallback về trường mặc định an toàn.
3. **Triệt tiêu mã lặp (DRY)**: Không cần tạo hàng loạt class `BrandPageResponse`, `ProductPageResponse`, `OrderPageResponse`...

---

## II. Hướng Dẫn Sử Dụng Chi Tiết Từng Tầng

```text
Client Request (Query Params)
        │
        ▼
[1. Request DTO] (extends PageQuery)
        │
        ▼
[2. REST Controller] ──► Dùng PageableUtils (Whitelist Sort + Cap Size) ──► Tạo Pageable an toàn
        │
        ▼
[3. Service Layer]   ──► Query Repository ──► Trả về PageResponse.from(entityPage, mapper::toResponse)
        │
        ▼
Client Response (ApiResponse<PageResponse<T>>)
```

---

### Bước 1: Tầng Request DTO (Kế thừa `PageQuery`)

Khi một màn hình danh sách có các tiêu chí lọc riêng (keyword, khoảng giá, trạng thái...), hãy cho DTO Request kế thừa `PageQuery`.

```java
package com.aquasuite.ims.module.catalog.internal.payload.req;

import com.aquasuite.ims.share.dto.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Kế thừa PageQuery để tự động có sẵn:
 * - page (mặc định 0)
 * - size (mặc định 20)
 * - sortBy (null)
 * - sortDir (mặc định "DESC")
 */
@Getter
@Setter
public class ProductFilterRequest extends PageQuery {

    private String keyword;
    private UUID categoryId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String status;
}
```

> [!TIP]
> Nếu một endpoint chỉ cần phân trang đơn thuần mà không có bộ lọc bổ sung nào, bạn có thể dùng trực tiếp `PageQuery` làm tham số `@ModelAttribute PageQuery query` trong Controller mà không cần tạo class mới.

---

### Bước 2: Tầng Controller (Sử dụng `PageableUtils`)

Trong Controller:
1. Định nghĩa `Set<String> ALLOWED_SORTS` chứa các trường Entity được phép sắp xếp.
2. Dùng `PageableUtils.toPageable(...)` để trích xuất `Pageable` an toàn.
3. Bọc kết quả vào `ApiResponse.ok(...)`.

```java
package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.payload.req.ProductFilterRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.ProductResponse;
import com.aquasuite.ims.module.catalog.internal.service.ProductService;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.PageResponse;
import com.aquasuite.ims.share.util.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // 1. Whitelist các trường được phép sắp xếp của Entity Product
    private static final Set<String> ALLOWED_PRODUCT_SORTS = Set.of(
            "productName", 
            "createdAt", 
            "updatedAt", 
            "price"
    );

    @GetMapping
    @PreAuthorize("hasAuthority('CATALOG_PRODUCT_READ')")
    public ApiResponse<PageResponse<ProductResponse>> getProducts(
            @ModelAttribute ProductFilterRequest filter
    ) {
        // 2. Làm sạch tham số an toàn:
        // - size > 100 sẽ bị hạ về 100.
        // - sortBy không nằm trong ALLOWED_PRODUCT_SORTS sẽ fallback về "createdAt".
        // - sortDir không phải ASC/DESC sẽ fallback về Sort.Direction.DESC.
        Pageable pageable = PageableUtils.toPageable(filter, ALLOWED_PRODUCT_SORTS, "createdAt");

        PageResponse<ProductResponse> response = productService.getProducts(filter, pageable);
        return ApiResponse.ok(response);
    }
}
```

> [!NOTE]
> Nếu Controller nhận các `@RequestParam` lẻ thay vì một DTO object, bạn có thể gọi:
> ```java
> Pageable pageable = PageableUtils.createPageable(page, size, sortBy, sortDir, ALLOWED_SORTS, "createdAt");
> ```

---

### Bước 3: Tầng Service & Mapper (Sử dụng `PageResponse<T>`)

Trong Service:
1. Nhận `Pageable` đã được Controller làm sạch.
2. Gọi Repository truy vấn dữ liệu dạng `Page<Entity>`.
3. Dùng `PageResponse.from(...)` để vừa map sang DTO vừa đóng gói kết quả.

```java
package com.aquasuite.ims.module.catalog.internal.service.impl;

import com.aquasuite.ims.module.catalog.internal.entity.Product;
import com.aquasuite.ims.module.catalog.internal.mapper.ProductMapper;
import com.aquasuite.ims.module.catalog.internal.payload.req.ProductFilterRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.ProductResponse;
import com.aquasuite.ims.module.catalog.internal.repository.ProductRepository;
import com.aquasuite.ims.module.catalog.internal.service.ProductService;
import com.aquasuite.ims.share.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getProducts(ProductFilterRequest filter, Pageable pageable) {
        Specification<Product> spec = buildSpecification(filter);

        // 1. Lấy Page<Product> từ DB
        Page<Product> productPage = productRepository.findAll(spec, pageable);

        // 2. Chuyển đổi trực tiếp sang PageResponse<ProductResponse> thông qua Mapper
        return PageResponse.from(productPage, productMapper::toResponse);
    }

    private Specification<Product> buildSpecification(ProductFilterRequest filter) {
        // Build JPA Specification tùy nghiệp vụ...
        return (root, query, cb) -> cb.conjunction();
    }
}
```

---

## III. Định Dạng JSON Trả Về Cho Client

Khi gọi endpoint trên, kết quả trả về phía Frontend tuân thủ đầy đủ chuẩn của AquaSuite (`ApiResponse` bọc ngoài `PageResponse`):

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Thành công",
  "data": {
    "items": [
      {
        "id": "7fa8b9c0-1234-5678-9abc-def012345678",
        "productCode": "P-001",
        "productName": "Máy Lọc Nước RO AquaSuite Pro",
        "price": 8500000
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 145,
    "totalPages": 8,
    "hasNext": true,
    "hasPrevious": false
  },
  "timestamp": "2026-10-24T07:30:00Z"
}
```

---

## IV. Bảng Quy Tắc Nhanh (Cheat Sheet)

| Tình huống | Cách xử lý khuyến nghị |
|---|---|
| **Tạo bộ lọc mới cho một Entity** | Tạo DTO `class XxxFilterRequest extends PageQuery` |
| **Khởi tạo Pageable an toàn** | Luôn dùng `PageableUtils.toPageable(...)` kèm `ALLOWED_SORTS` |
| **Client không truyền `page` / `size`** | Mặc định tự lấy `page = 0`, `size = 20` |
| **Client truyền `size = 500`** | `PageableUtils` tự ép xuống trần tối đa `size = 100` |
| **Client truyền trường sort độc hại** | `PageableUtils` tự bỏ qua và sắp xếp theo trường an toàn mặc định |
| **Đóng gói trả về từ `Page<Entity>`** | `PageResponse.from(entityPage, mapper::toResponse)` |
| **Đóng gói trả về từ `Page<Dto>` sẵn có** | `PageResponse.from(dtoPage)` |
