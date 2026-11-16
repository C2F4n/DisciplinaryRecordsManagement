package org.example.disciplinaryrecordsmanagement.controller;

import org.example.disciplinaryrecordsmanagement.dto.RoleGuideDto;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.RoleGuideService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;

@RestController
public class RoleGuideController {
    private final AccountService accounts;
    private final RoleGuideService guide;

    RoleGuideController(AccountService accounts, RoleGuideService guide) {
        this.accounts = accounts;
        this.guide = guide;
    }

    @GetMapping(ROOT + "/information/permissions")
    Result<RoleGuideDto> permissions(Principal principal) {
        accounts.viewer(principal);
        return Result.success(guide.guide());
    }
}
