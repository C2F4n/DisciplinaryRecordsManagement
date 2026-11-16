package org.example.disciplinaryrecordsmanagement.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.example.disciplinaryrecordsmanagement.dto.PermissionRequestDto.Decision;
import org.example.disciplinaryrecordsmanagement.dto.PermissionRequestDto.Entry;
import org.example.disciplinaryrecordsmanagement.dto.PermissionRequestDto.Submit;
import org.example.disciplinaryrecordsmanagement.model.PermissionRequestStatus;
import org.example.disciplinaryrecordsmanagement.result.PageResult;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.PermissionRequestService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.*;

@RestController
@RequestMapping(ROOT + "/permission-requests")
public class PermissionRequestController {
    private final AccountService accounts;
    private final PermissionRequestService requests;

    PermissionRequestController(AccountService accounts, PermissionRequestService requests) {
        this.accounts = accounts;
        this.requests = requests;
    }

    @GetMapping("/mine")
    Result<PageResult<Entry>> mine(Principal principal,
                                    @RequestParam(defaultValue = DEFAULT_PAGE) @Min(1) @Max(MAX_PAGE) int page,
                                    @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return Result.success(requests.mine(accounts.viewer(principal), page, size));
    }

    @PostMapping
    Result<Void> submit(Principal principal, @Valid @RequestBody Submit input) {
        requests.submit(accounts.viewer(principal), input);
        return Result.success();
    }

    @GetMapping("/queue")
    Result<PageResult<Entry>> queue(Principal principal,
                                     @RequestParam(defaultValue = "PENDING") PermissionRequestStatus status,
                                     @RequestParam(defaultValue = DEFAULT_PAGE) @Min(1) @Max(MAX_PAGE) int page,
                                     @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return Result.success(requests.queue(accounts.viewer(principal), status, page, size));
    }

    @PutMapping("/{id}/decision")
    Result<Void> decide(Principal principal, @PathVariable @Positive long id, @Valid @RequestBody Decision input) {
        requests.decide(accounts.viewer(principal), id, input);
        return Result.success();
    }
}
