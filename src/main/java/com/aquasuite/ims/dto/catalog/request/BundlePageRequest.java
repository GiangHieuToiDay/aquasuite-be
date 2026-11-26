package com.aquasuite.ims.dto.catalog.request;

import com.aquasuite.ims.entity.catalog.BundleConfiguration.PublicationStatus;
import com.aquasuite.ims.enums.catalog.ProductStatus;
import com.aquasuite.ims.common.PageQuery;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bundle filters reuse shared pagination fields; ordering remains defined by the use case. */
@Getter
@Setter
@NoArgsConstructor
public class BundlePageRequest extends PageQuery {
    @Size(max = 100, message = "Từ khóa tìm kiếm tối đa 100 ký tự")
    private String keyword;
    private ProductStatus productStatus;
    private PublicationStatus publicationStatus;

    public BundlePageRequest(
            int page,
            int size,
            String keyword,
            ProductStatus productStatus,
            PublicationStatus publicationStatus) {
        setPage(page);
        setSize(size);
        this.keyword = keyword;
        this.productStatus = productStatus;
        this.publicationStatus = publicationStatus;
    }
}
