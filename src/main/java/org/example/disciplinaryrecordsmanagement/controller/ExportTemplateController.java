package org.example.disciplinaryrecordsmanagement.controller;

import jakarta.validation.Valid;
import org.example.disciplinaryrecordsmanagement.dto.ExportTemplateDto.*;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.ExportTemplateService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;

@RestController
@RequestMapping(ROOT + "/admin/export-templates")
public class ExportTemplateController {
    private final AccountService accounts;
    private final ExportTemplateService templates;

    ExportTemplateController(AccountService accounts, ExportTemplateService templates) {
        this.accounts = accounts;
        this.templates = templates;
    }

    @GetMapping
    Result<List<Template>> templates(Principal principal) {
        return Result.success(templates.templates(accounts.viewer(principal)));
    }

    @PutMapping("/{kind}")
    Result<Template> update(Principal principal, @PathVariable Kind kind, @Valid @RequestBody Change change) {
        return Result.success(templates.update(accounts.viewer(principal), kind, change));
    }
}
