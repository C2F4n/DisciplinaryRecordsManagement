package org.example.disciplinaryrecordsmanagement.controller;

import java.security.Principal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.ChangeDepartment;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.ChangeOption;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.NewInspection;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.Option;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.Settings;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.TypeAssignment;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.WorkflowService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_VIOLATION_TYPE_LENGTH;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_WORKFLOW_CODE_LENGTH;

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
