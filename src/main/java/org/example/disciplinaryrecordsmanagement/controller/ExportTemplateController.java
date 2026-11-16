package org.example.disciplinaryrecordsmanagement.controller;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;

import org.example.disciplinaryrecordsmanagement.dto.ExportTemplateDto.Change;
import org.example.disciplinaryrecordsmanagement.dto.ExportTemplateDto.Kind;
import org.example.disciplinaryrecordsmanagement.dto.ExportTemplateDto.Template;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.ExportTemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
