package com.aquasuite.ims.module.catalog.internal.payload.req;

import com.aquasuite.ims.module.catalog.internal.entity.BundleConfiguration.PublicationStatus;
import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import com.aquasuite.ims.share.dto.PageQuery;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bundle filters reuse shared pagination fields; ordering remains defined by the use case. */
@Getter
@Setter
@NoArgsConstructor
public class BundlePageRequest extends PageQuery {
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
