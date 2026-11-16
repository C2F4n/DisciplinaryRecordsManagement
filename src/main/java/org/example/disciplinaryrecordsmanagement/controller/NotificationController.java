package org.example.disciplinaryrecordsmanagement.controller;

import org.example.disciplinaryrecordsmanagement.dto.NotificationDto.Notice;
import org.example.disciplinaryrecordsmanagement.dto.NotificationDto.Unread;
import org.example.disciplinaryrecordsmanagement.result.PageResult;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.*;

@RestController
@RequestMapping(ROOT + "/notices")
public class NotificationController {
    private final NotificationService notifications;
    private final AccountService accounts;

    NotificationController(NotificationService notifications, AccountService accounts) {
        this.notifications = notifications;
        this.accounts = accounts;
    }

    @GetMapping
    Result<PageResult<Notice>> list(Principal principal,
                                    @RequestParam(defaultValue = DEFAULT_PAGE) @Min(1) @Max(MAX_PAGE) int page,
                                    @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return Result.success(notifications.list(accounts.viewer(principal), page, size));
    }

    @GetMapping("/unread")
    Result<Unread> unread(Principal principal) {
        return Result.success(notifications.unread(accounts.viewer(principal)));
    }

    @PutMapping("/{id}/read")
    Result<Void> read(Principal principal, @PathVariable @Positive long id) {
        notifications.markRead(accounts.viewer(principal), id);
        return Result.success();
    }

    @PutMapping("/read-all")
    Result<Void> readAll(Principal principal) {
        notifications.markAllRead(accounts.viewer(principal));
        return Result.success();
    }
}
