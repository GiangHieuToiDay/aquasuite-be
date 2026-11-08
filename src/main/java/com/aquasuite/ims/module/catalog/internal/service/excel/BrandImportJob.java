package com.aquasuite.ims.module.catalog.internal.service.excel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BrandImportJob {
    public UUID jobId;
    public UUID ownerId;
    public String status = "QUEUED";
    public Integer totalRows;
    public int processedRows;
    public int createdRows;
    public int skippedRows;
    public int errorRows;
    public Integer percentage;
    public String message = "QUEUED";
    public String jobReason;
    public Instant startedAt = Instant.now();
    public Instant finishedAt;
    public List<BrandImportRow> rows = new ArrayList<>();

    public BrandImportJob() {}
    public BrandImportJob(UUID jobId, UUID ownerId) {
        this.jobId = jobId;
        this.ownerId = ownerId;
    }
    public record BrandImportRow(int rowNumber, String code, String result, String reasonCodes,
                                 Integer firstDuplicateCodeRow, Integer firstDuplicateNameRow) {}
}
