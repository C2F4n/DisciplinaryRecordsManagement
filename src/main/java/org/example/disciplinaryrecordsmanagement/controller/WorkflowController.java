package org.example.disciplinaryrecordsmanagement.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.*;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.WorkflowService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.*;

@RestController
@RequestMapping(ROOT + "/admin/workflow")
public class WorkflowController {
    private final AccountService accounts;
    private final WorkflowService workflow;

    WorkflowController(AccountService accounts, WorkflowService workflow) {
        this.accounts = accounts;
        this.workflow = workflow;
    }

    @GetMapping
    Result<Settings> settings(Principal principal) {
        return Result.success(workflow.settings(accounts.viewer(principal)));
    }

    @PostMapping("/inspections")
    Result<Option> addInspection(Principal principal, @Valid @RequestBody NewInspection input) {
        return Result.success(workflow.addInspection(accounts.viewer(principal), input));
    }

    @DeleteMapping("/inspections/{code}")
    Result<Void> deleteInspection(Principal principal, @PathVariable @Size(max = MAX_WORKFLOW_CODE_LENGTH) String code,
                                  @RequestParam @PositiveOrZero long version) {
        workflow.deleteInspection(accounts.viewer(principal), code, version);
        return Result.success();
    }

    @PutMapping("/options/{code}")
    Result<Option> updateOption(Principal principal, @PathVariable @Size(max = MAX_WORKFLOW_CODE_LENGTH) String code,
                                @Valid @RequestBody ChangeOption input) {
        return Result.success(workflow.updateOption(accounts.viewer(principal), code, input));
    }

    @PutMapping("/types/{code}")
    Result<TypeAssignment> updateType(Principal principal, @PathVariable @Size(max = MAX_VIOLATION_TYPE_LENGTH) String code,
                                      @Valid @RequestBody ChangeDepartment input) {
        return Result.success(workflow.updateType(accounts.viewer(principal), code, input));
    }
}
