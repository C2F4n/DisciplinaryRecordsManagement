package org.example.disciplinaryrecordsmanagement.controller;

import org.example.disciplinaryrecordsmanagement.dto.AccountDto.Candidate;
import org.example.disciplinaryrecordsmanagement.dto.StatisticsDto.Period;
import org.example.disciplinaryrecordsmanagement.dto.StatisticsDto.Summary;
import org.example.disciplinaryrecordsmanagement.dto.ViolationDto.*;
import org.example.disciplinaryrecordsmanagement.result.PageResult;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.ViolationService;
import org.example.disciplinaryrecordsmanagement.service.ViolationExportService;
import org.example.disciplinaryrecordsmanagement.service.ViolationStatisticsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.*;

@RestController
@RequestMapping(ROOT)
public class ViolationController {
    private final ViolationService records;
    private final ViolationExportService exports;
    private final ViolationStatisticsService statistics;
    private final AccountService accounts;

    ViolationController(ViolationService records, ViolationExportService exports,
                        ViolationStatisticsService statistics, AccountService accounts) {
        this.records = records;
        this.exports = exports;
        this.statistics = statistics;
        this.accounts = accounts;
    }

    @GetMapping("/metadata")
    Result<Metadata> metadata(Principal principal) {
        return Result.success(records.metadata(accounts.viewer(principal)));
    }

    @GetMapping("/students/lookup")
    Result<List<Candidate>> lookup(Principal principal, @RequestParam @NotBlank @Size(max = MAX_STUDENT_LOOKUP_LENGTH) String q) {
        return Result.success(records.lookup(accounts.viewer(principal), q));
    }

    @GetMapping("/violations")
    Result<PageResult<Entry>> list(Principal principal, @ModelAttribute Filter filters,
                                   @RequestParam(defaultValue = DEFAULT_PAGE) @Min(1) @Max(MAX_PAGE) int page,
                                   @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return Result.success(records.list(accounts.viewer(principal), filters, page, size));
    }

    @GetMapping("/violations/export")
    ResponseEntity<byte[]> export(Principal principal, @ModelAttribute Filter filters) {
        return WorkbookResponses.attachment(exports.download(accounts.viewer(principal), filters, false));
    }

    @GetMapping("/violations/bulletin")
    ResponseEntity<byte[]> bulletin(Principal principal, @ModelAttribute Filter filters) {
        return WorkbookResponses.attachment(exports.download(accounts.viewer(principal), filters, true));
    }

    @GetMapping("/violations/statistics")
    Result<Summary> statistics(Principal principal, @ModelAttribute Filter filters,
                               @RequestParam(defaultValue = DEFAULT_STATISTICS_PERIOD) Period period) {
        return Result.success(statistics.summarize(accounts.viewer(principal), filters, period));
    }

    @GetMapping("/violations/{id}")
    Result<Entry> find(Principal principal, @PathVariable long id) {
        return Result.success(records.find(accounts.viewer(principal), id));
    }

    @PostMapping("/violations")
    Result<Entry> create(Principal principal, @Valid @RequestBody Create input) {
        return Result.success(records.create(accounts.viewer(principal), input));
    }

    @PutMapping("/violations/{id}")
    Result<Entry> edit(Principal principal, @PathVariable long id, @Valid @RequestBody Edit input) {
        return Result.success(records.edit(accounts.viewer(principal), id, input));
    }

    @PutMapping("/admin/violations/{id}/correction")
    Result<Entry> correct(Principal principal, @PathVariable long id, @Valid @RequestBody Correction input) {
        return Result.success(records.correct(accounts.viewer(principal), id, input));
    }

    @DeleteMapping("/violations/{id}")
    Result<Void> delete(Principal principal, @PathVariable long id, @RequestParam @Min(0) long version) {
        records.delete(accounts.viewer(principal), id, version);
        return Result.success();
    }

    @PostMapping("/violations/review")
    Result<BatchResult> review(Principal principal, @Valid @RequestBody Review input) {
        return Result.success(records.review(accounts.viewer(principal), input));
    }

    @PostMapping("/violations/{id}/resolve")
    Result<Void> resolve(Principal principal, @PathVariable long id, @Valid @RequestBody Resolve input) {
        records.resolve(accounts.viewer(principal), id, input);
        return Result.success();
    }
}
