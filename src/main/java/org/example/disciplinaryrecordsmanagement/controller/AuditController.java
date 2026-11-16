package org.example.disciplinaryrecordsmanagement.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.example.disciplinaryrecordsmanagement.dto.AuditDto.Entry;
import org.example.disciplinaryrecordsmanagement.dto.AuditDto.Filter;
import org.example.disciplinaryrecordsmanagement.result.PageResult;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.AuditService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.*;

@RestController
@RequestMapping(ROOT + "/admin/audit")
public class AuditController {
    private final AccountService accounts;
    private final AuditService audit;

    AuditController(AccountService accounts, AuditService audit) {
        this.accounts = accounts;
        this.audit = audit;
    }

    @GetMapping
    Result<PageResult<Entry>> list(Principal principal, @Valid @ModelAttribute Filter filter,
            @RequestParam(defaultValue = DEFAULT_PAGE) @Min(1) @Max(MAX_PAGE) int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return Result.success(audit.list(accounts.viewer(principal), filter, page, size));
    }
}
