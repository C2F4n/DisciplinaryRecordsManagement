package org.example.disciplinaryrecordsmanagement.controller;

import java.security.Principal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.example.disciplinaryrecordsmanagement.dto.MonthlyReportDto.Detail;
import org.example.disciplinaryrecordsmanagement.dto.MonthlyReportDto.Summary;
import org.example.disciplinaryrecordsmanagement.result.PageResult;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.MonthlyReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.DEFAULT_PAGE;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.DEFAULT_PAGE_SIZE;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_PAGE;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_PAGE_SIZE;

@RestController
@RequestMapping(ROOT + "/reports")
public class MonthlyReportController {
    private final AccountService accounts;
    private final MonthlyReportService reports;

    MonthlyReportController(AccountService accounts, MonthlyReportService reports) {
        this.accounts = accounts;
        this.reports = reports;
    }

    @GetMapping
    Result<PageResult<Summary>> list(Principal principal,
                                    @RequestParam(defaultValue = DEFAULT_PAGE) @Min(1) @Max(MAX_PAGE) int page,
                                    @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return Result.success(reports.list(accounts.viewer(principal), page, size));
    }

    @GetMapping("/{id}")
    Result<Detail> detail(Principal principal, @PathVariable @Positive long id,
                          @RequestParam(defaultValue = DEFAULT_PAGE) @Min(1) @Max(MAX_PAGE) int page,
                          @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return Result.success(reports.detail(accounts.viewer(principal), id, page, size));
    }

    @GetMapping("/{id}/download")
    ResponseEntity<byte[]> download(Principal principal, @PathVariable @Positive long id) {
        return WorkbookResponses.attachment(reports.download(accounts.viewer(principal), id));
    }
}
