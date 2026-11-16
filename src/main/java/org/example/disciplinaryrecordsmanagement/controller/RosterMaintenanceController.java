package org.example.disciplinaryrecordsmanagement.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.example.disciplinaryrecordsmanagement.dto.RosterMaintenanceDto.*;
import org.example.disciplinaryrecordsmanagement.result.PageResult;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.RosterMaintenanceService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.*;

@RestController
@RequestMapping(ROOT + "/admin/roster")
public class RosterMaintenanceController {
    private final AccountService accounts;
    private final RosterMaintenanceService roster;

    RosterMaintenanceController(AccountService accounts, RosterMaintenanceService roster) {
        this.accounts = accounts;
        this.roster = roster;
    }

    @GetMapping("/classes")
    Result<List<ManagedClass>> classes(Principal principal) {
        return Result.success(roster.classes(accounts.viewer(principal)));
    }

    @PutMapping("/classes/{id}")
    Result<ManagedClass> updateClass(Principal principal, @PathVariable @Positive long id,
                                     @Valid @RequestBody ChangeClass change) {
        return Result.success(roster.updateClass(accounts.viewer(principal), id, change));
    }

    @DeleteMapping("/classes/{id}")
    Result<Void> deleteClass(Principal principal, @PathVariable @Positive long id,
                             @RequestParam @PositiveOrZero long version) {
        roster.deleteClass(accounts.viewer(principal), id, version);
        return Result.success();
    }

    @DeleteMapping("/classes/{id}/students")
    Result<Integer> deleteClassStudents(Principal principal, @PathVariable @Positive long id,
                                        @RequestParam @PositiveOrZero long version,
                                        @RequestParam @Positive int studentCount) {
        return Result.success(roster.deleteClassStudents(accounts.viewer(principal), id, version, studentCount));
    }

    @GetMapping("/students")
    Result<PageResult<StudentProfile>> students(Principal principal,
            @RequestParam(required = false) @Positive Long classId,
            @RequestParam(defaultValue = "") @Size(max = MAX_STUDENT_LOOKUP_LENGTH) String query,
            @RequestParam(defaultValue = DEFAULT_PAGE) @Min(1) @Max(MAX_PAGE) int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return Result.success(roster.students(accounts.viewer(principal), classId, query, page, size));
    }

    @PutMapping("/students/{id}")
    Result<StudentProfile> updateStudent(Principal principal, @PathVariable @Positive long id,
                                         @Valid @RequestBody ChangeStudent change) {
        return Result.success(roster.updateStudent(accounts.viewer(principal), id, change));
    }

    @DeleteMapping("/students/{id}")
    Result<Void> deleteStudent(Principal principal, @PathVariable @Positive long id,
                               @RequestParam @PositiveOrZero long version) {
        roster.deleteStudent(accounts.viewer(principal), id, version);
        return Result.success();
    }

    @GetMapping("/student-number-rule")
    Result<StudentNumberRule> studentNumberRule(Principal principal) {
        return Result.success(roster.studentNumberRule(accounts.viewer(principal)));
    }

    @PutMapping("/student-number-rule")
    Result<StudentNumberRule> updateStudentNumberRule(Principal principal, @Valid @RequestBody StudentNumberRule rule) {
        return Result.success(roster.updateStudentNumberRule(accounts.viewer(principal), rule));
    }
}
