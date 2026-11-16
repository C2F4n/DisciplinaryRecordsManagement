package org.example.disciplinaryrecordsmanagement.controller;

import org.example.disciplinaryrecordsmanagement.dto.RetentionDto.Overview;
import org.example.disciplinaryrecordsmanagement.dto.RetentionDto.Run;
import org.example.disciplinaryrecordsmanagement.result.PageResult;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.RetentionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.*;

@RestController
@RequestMapping(ROOT + "/admin/retention")
public class RetentionController {
    private final AccountService accounts;
    private final RetentionService retention;

    RetentionController(AccountService accounts, RetentionService retention) {
        this.accounts = accounts;
        this.retention = retention;
    }

    @GetMapping
    Result<Overview> preview(Principal principal,
                             @RequestParam(defaultValue = DEFAULT_PAGE) @Min(1) @Max(MAX_PAGE) int page,
                             @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return Result.success(retention.preview(accounts.viewer(principal), page, size));
    }

    @GetMapping("/history")
    Result<PageResult<Run>> history(Principal principal,
                                    @RequestParam(defaultValue = DEFAULT_PAGE) @Min(1) @Max(MAX_PAGE) int page,
                                    @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return Result.success(retention.history(accounts.viewer(principal), page, size));
    }
}
