package org.example.disciplinaryrecordsmanagement.controller;

import java.security.Principal;

import org.example.disciplinaryrecordsmanagement.dto.RosterDto.Check;
import org.example.disciplinaryrecordsmanagement.dto.RosterDto.ClassImport;
import org.example.disciplinaryrecordsmanagement.dto.RosterDto.StudentImport;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.ClassRosterImportService;
import org.example.disciplinaryrecordsmanagement.service.RosterImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;

@RestController
@RequestMapping(ROOT + "/admin/roster")
public class RosterController {
    private final AccountService accounts;
    private final RosterImportService roster;
    private final ClassRosterImportService classes;

    RosterController(AccountService accounts, RosterImportService roster, ClassRosterImportService classes) {
        this.accounts = accounts;
        this.roster = roster;
        this.classes = classes;
    }

    @GetMapping("/template")
    ResponseEntity<byte[]> template(Principal principal) {
        return WorkbookResponses.attachment(roster.template(accounts.viewer(principal)));
    }

    @PostMapping(path = "/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Result<Check> check(Principal principal, @RequestParam("file") MultipartFile file) {
        return Result.success(roster.check(accounts.viewer(principal), file));
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Result<StudentImport> importRoster(Principal principal, @RequestParam("file") MultipartFile file) {
        return Result.success(roster.importRoster(accounts.viewer(principal), file));
    }

    @GetMapping("/class-import/template")
    ResponseEntity<byte[]> classTemplate(Principal principal) {
        return WorkbookResponses.attachment(classes.template(accounts.viewer(principal)));
    }

    @PostMapping(path = "/class-import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Result<Check> checkClasses(Principal principal, @RequestParam("file") MultipartFile file) {
        return Result.success(classes.check(accounts.viewer(principal), file));
    }

    @PostMapping(path = "/class-import/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Result<ClassImport> importClasses(Principal principal, @RequestParam("file") MultipartFile file) {
        return Result.success(classes.importRoster(accounts.viewer(principal), file));
    }
}
