package com.aquasuite.ims.share.excel.storage;

import com.aquasuite.ims.share.excel.exception.ExcelErrorCode;
import com.aquasuite.ims.share.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Component
public class TempExcelFileManager {

    public File saveToTempFile(MultipartFile multipartFile, String prefix) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new AppException(ExcelErrorCode.EMPTY_FILE);
        }
        try {
            Path tempPath =
                    Files.createTempFile("aquasuite_" + prefix + "_" + UUID.randomUUID(), ".xlsx");
            multipartFile.transferTo(tempPath.toFile());
            return tempPath.toFile();
        } catch (IOException e) {
            throw new AppException(ExcelErrorCode.FILE_READ_ERROR, e.getMessage());
        }
    }

    public void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                log.warn("Không thể xóa file Excel tạm: {}", file.getAbsolutePath());
            }
        }
    }
}
