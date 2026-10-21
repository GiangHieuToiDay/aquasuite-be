package com.aquasuite.ims.share.excel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportProgressDto implements Serializable {
    private String jobId;
    private ImportJobStatus status;
    private int totalRows;
    private int processedRows;
    private int successRows;
    private int errorRows;
    private int percentage; // (processedRows * 100) / totalRows
    private String message;
    @Builder.Default private List<ExcelRowError> errors = new ArrayList<>();
    private Instant startedAt;
    private Instant finishedAt;
}
