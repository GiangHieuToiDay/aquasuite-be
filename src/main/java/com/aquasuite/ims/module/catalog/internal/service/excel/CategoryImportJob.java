package com.aquasuite.ims.module.catalog.internal.service.excel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CategoryImportJob {
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
    public List<CategoryImportRow> rows = new ArrayList<>();

    public CategoryImportJob() {}
    public CategoryImportJob(UUID jobId, UUID ownerId) { this.jobId = jobId; this.ownerId = ownerId; }
    public record CategoryImportRow(int rowNumber, String code, String parentCode, String result,
                                    String reasonCodes, Integer firstCodeRow, Integer firstNameRow) {}
}
