package org.example.disciplinaryrecordsmanagement.controller;

import jakarta.validation.Valid;
import org.example.disciplinaryrecordsmanagement.dto.CounselorDto.Create;
import org.example.disciplinaryrecordsmanagement.dto.CounselorDto.Option;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.CounselorService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;

@RestController
@RequestMapping(ROOT + "/admin/counselors")
public class CounselorController {
    private final AccountService accounts;
    private final CounselorService counselors;

    CounselorController(AccountService accounts, CounselorService counselors) {
        this.accounts = accounts;
        this.counselors = counselors;
    }

    @GetMapping
    Result<List<Option>> list(Principal principal) {
        return Result.success(counselors.list(accounts.viewer(principal)));
    }

    @PostMapping
    Result<Option> create(Principal principal, @Valid @RequestBody Create input) {
        return Result.success(counselors.create(accounts.viewer(principal), input));
    }
}
