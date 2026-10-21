package com.aquasuite.ims.share.excel.tracker;

import com.aquasuite.ims.share.excel.dto.ExcelRowError;
import com.aquasuite.ims.share.excel.dto.ImportJobStatus;
import com.aquasuite.ims.share.excel.dto.ImportProgressDto;
import com.aquasuite.ims.share.excel.exception.ExcelErrorCode;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisImportProgressTracker {

    private static final String KEY_PREFIX = "aquasuite:import:progress:";
    private static final Duration TTL = Duration.ofHours(1);

    private final RedisTemplate<String, Object> redisTemplate;

    public void initJob(String jobId, int totalRows) {
        ImportProgressDto dto =
                ImportProgressDto.builder()
                        .jobId(jobId)
                        .status(ImportJobStatus.PROCESSING)
                        .totalRows(totalRows)
                        .processedRows(0)
                        .successRows(0)
                        .errorRows(0)
                        .percentage(0)
                        .message("Bắt đầu xử lý dữ liệu...")
                        .startedAt(Instant.now())
                        .build();
        save(jobId, dto);
    }

    public void updateBatchProgress(
            String jobId, int batchProcessed, int batchSuccess, List<ExcelRowError> batchErrors) {
        ImportProgressDto dto = getProgress(jobId);
        dto.setProcessedRows(dto.getProcessedRows() + batchProcessed);
        dto.setSuccessRows(dto.getSuccessRows() + batchSuccess);
        dto.setErrorRows(dto.getErrorRows() + batchErrors.size());
        if (!batchErrors.isEmpty()) {
            dto.getErrors().addAll(batchErrors);
        }
        int percent =
                dto.getTotalRows() > 0
                        ? Math.min(99, (dto.getProcessedRows() * 100) / dto.getTotalRows())
                        : 0;
        dto.setPercentage(percent);
        dto.setMessage(
                String.format(
                        "Đã xử lý %d / %d dòng (%d%%)",
                        dto.getProcessedRows(), dto.getTotalRows(), percent));
        save(jobId, dto);
    }

    public void markCompleted(String jobId, String finalMessage) {
        ImportProgressDto dto = getProgress(jobId);
        dto.setPercentage(100);
        dto.setStatus(
                dto.getErrorRows() > 0
                        ? ImportJobStatus.COMPLETED_WITH_ERRORS
                        : ImportJobStatus.COMPLETED);
        dto.setMessage(finalMessage);
        dto.setFinishedAt(Instant.now());
        save(jobId, dto);
    }

    public void markFailed(String jobId, String errorReason) {
        ImportProgressDto dto = getProgress(jobId);
        dto.setStatus(ImportJobStatus.FAILED);
        dto.setMessage("Import thất bại: " + errorReason);
        dto.setFinishedAt(Instant.now());
        save(jobId, dto);
    }

    public ImportProgressDto getProgress(String jobId) {
        Object val = redisTemplate.opsForValue().get(KEY_PREFIX + jobId);
        if (val == null) {
            throw new AppException(ExcelErrorCode.JOB_NOT_FOUND, jobId);
        }
        return (ImportProgressDto) val;
    }

    private void save(String jobId, ImportProgressDto dto) {
        redisTemplate.opsForValue().set(KEY_PREFIX + jobId, dto, TTL);
    }
}
