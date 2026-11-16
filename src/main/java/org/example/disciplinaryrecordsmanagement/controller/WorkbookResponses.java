package org.example.disciplinaryrecordsmanagement.controller;

import java.nio.charset.StandardCharsets;

import org.example.disciplinaryrecordsmanagement.dto.WorkbookDownload;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.example.disciplinaryrecordsmanagement.constant.ReportConstants.XLSX_MEDIA_TYPE;

final class WorkbookResponses {
    private WorkbookResponses() {}

    static ResponseEntity<byte[]> attachment(WorkbookDownload file) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .contentLength(file.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }
}
